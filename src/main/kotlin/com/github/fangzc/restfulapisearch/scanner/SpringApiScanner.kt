package com.github.restfulapisearch.scanner

import com.github.restfulapisearch.model.ApiEndpoint
import com.github.restfulapisearch.model.HttpMethod
import com.github.restfulapisearch.util.PathUtils
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

/**
 * Spring REST API 端点扫描器
 * 通过 PSI 索引查找所有 Controller 类并解析其 Mapping 注解
 */
object SpringApiScanner {

    // 控制器注解
    private val CONTROLLER_ANNOTATIONS = listOf(
        "org.springframework.stereotype.Controller",
        "org.springframework.web.bind.annotation.RestController"
    )

    // 请求映射注解
    private const val REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping"

    // 所有 HTTP Mapping 注解
    private val MAPPING_ANNOTATIONS = listOf(
        REQUEST_MAPPING,
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping"
    )

    /**
     * 扫描项目中所有 Spring REST API 端点
     */
    fun scanProject(project: Project): List<ApiEndpoint> {
        return ReadAction.compute<List<ApiEndpoint>, Throwable> {
            val javaPsiFacade = JavaPsiFacade.getInstance(project)
            val projectScope = GlobalSearchScope.projectScope(project)
            val allScope = GlobalSearchScope.allScope(project)
            val endpoints = mutableListOf<ApiEndpoint>()

            // 遍历 @Controller 和 @RestController 注解标注的类
            for (controllerAnnotation in CONTROLLER_ANNOTATIONS) {
                val annotationClass = javaPsiFacade.findClass(controllerAnnotation, allScope) ?: continue
                val controllerClasses = AnnotatedElementsSearch.searchPsiClasses(annotationClass, projectScope)
                for (psiClass in controllerClasses) {
                    endpoints.addAll(scanClass(psiClass, allScope))
                }
            }

            endpoints.sortedBy { it.path }
        }
    }

    /**
     * 扫描单个 Controller 类的所有端点
     */
    private fun scanClass(psiClass: PsiClass, allScope: GlobalSearchScope): List<ApiEndpoint> {
        val endpoints = mutableListOf<ApiEndpoint>()
        val className = psiClass.name ?: return endpoints

        // 提取类级别的基础路径
        val basePaths = extractClassBasePaths(psiClass)

        // 遍历类中的所有方法
        for (method in psiClass.methods) {
            for (annotation in method.annotations) {
                val fqn = annotation.qualifiedName ?: continue
                if (fqn !in MAPPING_ANNOTATIONS) continue

                val httpMethod = HttpMethod.fromAnnotation(fqn) ?: continue
                val methodPaths = PathUtils.extractPaths(annotation)

                // 如果是 @RequestMapping，需要解析具体的 HTTP method
                val httpMethods = if (fqn == REQUEST_MAPPING) {
                    resolveRequestMappingMethods(annotation)
                } else {
                    listOf(httpMethod)
                }

                // 计算笛卡尔积：basePaths × methodPaths × httpMethods
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

    /**
     * 提取类级别的 @RequestMapping 基础路径
     */
    private fun extractClassBasePaths(psiClass: PsiClass): List<String> {
        for (annotation in psiClass.annotations) {
            if (annotation.qualifiedName == REQUEST_MAPPING) {
                val paths = PathUtils.extractPaths(annotation)
                if (paths.isNotEmpty()) return paths
            }
        }
        return listOf("")
    }

    /**
     * 解析 @RequestMapping 的 method 属性，获取具体的 HTTP 方法列表
     */
    private fun resolveRequestMappingMethods(annotation: PsiAnnotation): List<HttpMethod> {
        val methodAttr = annotation.findAttributeValue("method") ?: return listOf(HttpMethod.REQUEST)

        val methods = mutableListOf<HttpMethod>()

        when (methodAttr) {
            // 数组：method = {RequestMethod.GET, RequestMethod.POST}
            is PsiArrayInitializerMemberValue -> {
                for (initializer in methodAttr.initializers) {
                    val text = initializer.text
                    methods.add(HttpMethod.resolveRequestMappingMethod(text))
                }
            }
            // 单值：method = RequestMethod.GET
            else -> {
                val text = methodAttr.text
                // 排除空的数组初始化 "{}"
                if (text != null && text != "{}" && text.isNotBlank()) {
                    methods.add(HttpMethod.resolveRequestMappingMethod(text))
                }
            }
        }

        return if (methods.isEmpty()) listOf(HttpMethod.REQUEST) else methods
    }
}
