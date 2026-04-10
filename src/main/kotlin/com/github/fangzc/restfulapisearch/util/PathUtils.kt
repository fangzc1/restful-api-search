package com.github.restfulapisearch.util

import com.intellij.psi.*

/**
 * 路径操作工具类
 */
object PathUtils {

    /**
     * 拼接类级别基础路径和方法级别路径
     * 确保结果以 "/" 开头且中间无重复斜杠
     */
    fun combinePaths(basePath: String, methodPath: String): String {
        val base = basePath.trimEnd('/')
        val method = methodPath.trimStart('/')
        val combined = if (base.isEmpty() && method.isEmpty()) {
            "/"
        } else if (base.isEmpty()) {
            "/$method"
        } else if (method.isEmpty()) {
            base
        } else {
            "$base/$method"
        }
        return if (combined.startsWith("/")) combined else "/$combined"
    }

    /**
     * 从 Spring Mapping 注解中提取路径字符串列表
     * 支持 value、path 属性和数组值
     */
    fun extractPaths(annotation: PsiAnnotation): List<String> {
        // 优先取 value 属性，再取 path 属性
        val value = annotation.findAttributeValue("value")
            ?: annotation.findAttributeValue("path")

        val paths = extractStringValues(value)
        return if (paths.isEmpty()) listOf("") else paths
    }

    /**
     * 从注解属性值中递归提取所有字符串
     */
    private fun extractStringValues(value: PsiAnnotationMemberValue?): List<String> {
        return when (value) {
            null -> emptyList()

            // 字符串字面量："/api/users"
            is PsiLiteralExpression -> {
                val text = value.value as? String
                if (text != null) listOf(text) else emptyList()
            }

            // 数组值：{"/foo", "/bar"}
            is PsiArrayInitializerMemberValue -> {
                value.initializers.flatMap { extractStringValues(it) }
            }

            // 常量引用：PathConstants.USERS_PATH
            is PsiReferenceExpression -> {
                val resolved = value.resolve()
                if (resolved is PsiField) {
                    val initializer = resolved.initializer
                    extractStringValues(initializer as? PsiAnnotationMemberValue)
                } else {
                    // 无法解析，使用文本表示
                    listOf(value.text)
                }
            }

            else -> {
                // 其他情况尝试用文本
                val text = value.text?.removeSurrounding("\"")
                if (!text.isNullOrBlank()) listOf(text) else emptyList()
            }
        }
    }
}
