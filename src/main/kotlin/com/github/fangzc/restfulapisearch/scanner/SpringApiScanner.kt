package com.github.restfulapisearch.scanner

import com.github.restfulapisearch.model.ApiEndpoint
import com.github.restfulapisearch.model.HttpMethod
import com.github.restfulapisearch.util.PathUtils
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

/**
 * Spring REST API 端点扫描器。
 * 通过 PSI 索引查找所有 Controller 类并解析其 Mapping 注解。
 *
 * 注意：doScan 必须在 ReadAction 中调用，由 ScanCache 通过 ReadAction.nonBlocking 保证。
 */
object SpringApiScanner {

    private val CONTROLLER_ANNOTATIONS = listOf(
        "org.springframework.stereotype.Controller",
        "org.springframework.web.bind.annotation.RestController"
    )

    private const val REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping"

    private val MAPPING_ANNOTATIONS = listOf(
        REQUEST_MAPPING,
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping"
    )

    /**
     * 扫描项目中所有 Spring REST API 端点。
     * 必须在 ReadAction 中调用（由 ScanCache 通过 ReadAction.nonBlocking 统一管理）。
     */
    fun doScan(project: Project): List<ApiEndpoint> {
        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val projectScope = GlobalSearchScope.projectScope(project)
        val allScope = GlobalSearchScope.allScope(project)
        val endpoints = mutableListOf<ApiEndpoint>()

        for (controllerAnnotation in CONTROLLER_ANNOTATIONS) {
            val annotationClass = javaPsiFacade.findClass(controllerAnnotation, allScope) ?: continue
            val controllerClasses = AnnotatedElementsSearch.searchPsiClasses(annotationClass, projectScope).findAll()
            for (psiClass in controllerClasses) {
                endpoints.addAll(scanClass(psiClass, allScope))
            }
        }

        return endpoints.sortedBy { it.path }
    }

    private fun scanClass(psiClass: PsiClass, allScope: GlobalSearchScope): List<ApiEndpoint> {
        val endpoints = mutableListOf<ApiEndpoint>()
        val className = psiClass.name ?: return endpoints
        val basePaths = extractClassBasePaths(psiClass)

        for (method in psiClass.methods) {
            for (annotation in method.annotations) {
                val fqn = annotation.qualifiedName ?: continue
                if (fqn !in MAPPING_ANNOTATIONS) continue

                val httpMethod = HttpMethod.fromAnnotation(fqn) ?: continue
                val methodPaths = PathUtils.extractPaths(annotation)

                val httpMethods = if (fqn == REQUEST_MAPPING) {
                    resolveRequestMappingMethods(annotation)
                } else {
                    listOf(httpMethod)
                }

                for (basePath in basePaths) {
                    for (methodPath in methodPaths) {
                        val fullPath = PathUtils.combinePaths(basePath, methodPath)
                        for (hm in httpMethods) {
                            endpoints.add(
                                ApiEndpoint(
                                    path = fullPath,
                                    httpMethod = hm,
                                    className = className,
                                    methodName = method.name,
                                    psiMethod = method
                                )
                            )
                        }
                    }
                }
            }
        }

        return endpoints
    }

    private fun extractClassBasePaths(psiClass: PsiClass): List<String> {
        for (annotation in psiClass.annotations) {
            if (annotation.qualifiedName == REQUEST_MAPPING) {
                val paths = PathUtils.extractPaths(annotation)
                if (paths.isNotEmpty()) return paths
            }
        }
        return listOf("")
    }

    private fun resolveRequestMappingMethods(annotation: PsiAnnotation): List<HttpMethod> {
        val methodAttr = annotation.findAttributeValue("method") ?: return listOf(HttpMethod.REQUEST)
        val methods = mutableListOf<HttpMethod>()
        when (methodAttr) {
            is PsiArrayInitializerMemberValue -> {
                for (initializer in methodAttr.initializers) {
                    methods.add(HttpMethod.resolveRequestMappingMethod(initializer.text))
                }
            }
            else -> {
                val text = methodAttr.text
                if (text != null && text != "{}" && text.isNotBlank()) {
                    methods.add(HttpMethod.resolveRequestMappingMethod(text))
                }
            }
        }
        return if (methods.isEmpty()) listOf(HttpMethod.REQUEST) else methods
    }
}
