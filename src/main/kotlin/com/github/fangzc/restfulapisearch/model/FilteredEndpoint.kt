package com.github.restfulapisearch.model

/**
 * 过滤后的端点包装，携带过滤阶段预计算的高亮位置，
 * 供 cellRenderer 直接使用，避免渲染时重复执行 match 计算。
 */
data class FilteredEndpoint(
    val endpoint: ApiEndpoint,
    val matchPositions: Set<Int>
)
