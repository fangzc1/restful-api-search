package com.github.restfulapisearch.model

import com.intellij.psi.PsiMethod

/**
 * REST API 端点数据模型
 */
data class ApiEndpoint(
    val path: String,            // 完整路径，如 "/v1/demo/{paramId}"
    val httpMethod: HttpMethod,  // HTTP 方法类型
    val className: String,       // 所在类名，如 "DemoController"
    val methodName: String,      // 所在方法名，如 "getPostJson"
    val psiMethod: PsiMethod     // PSI 元素引用，用于导航跳转
) {
    /** 展示用的位置文本，如 "DemoController#getPostJson" */
    val locationText: String
        get() = "$className#$methodName"

    /** 用于搜索匹配的组合文本（小写） */
    val searchableText: String by lazy {
        "$path ${httpMethod.displayName} $className $methodName".lowercase()
    }
}
