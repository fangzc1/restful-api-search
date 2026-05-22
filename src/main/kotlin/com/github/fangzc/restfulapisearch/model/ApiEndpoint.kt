package com.github.restfulapisearch.model

import com.github.restfulapisearch.util.SearchMatcher
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
    val locationText: String = "$className#$methodName"

    /** 高频过滤直接使用的小写路径，避免重复 lowercase。 */
    val lowercasePath: String = path.lowercase()

    /** 相关度排序使用的归一化路径，避免排序阶段重复 normalize。 */
    val normalizedPath: String = SearchMatcher.normalizePathForRanking(path)

    /** 用于搜索匹配的组合文本（小写），覆盖 path / method / class / function。 */
    val searchableText: String = "$path ${httpMethod.displayName} $className $methodName".lowercase()
}
