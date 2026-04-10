package com.github.restfulapisearch.model

import java.awt.Color

/**
 * HTTP 请求方法枚举，包含展示名称和对应的颜色
 */
enum class HttpMethod(val displayName: String, val color: Color) {
    GET("GET", Color(0x61, 0xAF, 0xEF)),           // 蓝色
    POST("POST", Color(0x98, 0xC3, 0x79)),          // 绿色
    PUT("PUT", Color(0xE5, 0xC0, 0x7B)),            // 黄色
    DELETE("DELETE", Color(0xE0, 0x6C, 0x75)),       // 红色
    PATCH("PATCH", Color(0xC6, 0x78, 0xDD)),         // 紫色
    HEAD("HEAD", Color(0x56, 0xB6, 0xC2)),           // 青色
    OPTIONS("OPTIONS", Color(0xAB, 0xB2, 0xBF)),     // 灰色
    REQUEST("ALL", Color(0xAB, 0xB2, 0xBF));         // 灰色（泛化 @RequestMapping，未指定具体方法）

    companion object {
        private val ANNOTATION_MAP = mapOf(
            "org.springframework.web.bind.annotation.GetMapping" to GET,
            "org.springframework.web.bind.annotation.PostMapping" to POST,
            "org.springframework.web.bind.annotation.PutMapping" to PUT,
            "org.springframework.web.bind.annotation.DeleteMapping" to DELETE,
            "org.springframework.web.bind.annotation.PatchMapping" to PATCH,
            "org.springframework.web.bind.annotation.RequestMapping" to REQUEST
        )

        /**
         * 根据 Spring 注解全限定名获取对应的 HTTP 方法
         */
        fun fromAnnotation(annotationQualifiedName: String): HttpMethod? {
            return ANNOTATION_MAP[annotationQualifiedName]
        }

        /**
         * 解析 @RequestMapping 的 method 属性值，如 "GET"、"RequestMethod.POST"
         */
        fun resolveRequestMappingMethod(methodAttribute: String?): HttpMethod {
            if (methodAttribute.isNullOrBlank()) return REQUEST
            val normalized = methodAttribute.uppercase().let {
                // 处理 RequestMethod.GET 格式
                if (it.contains(".")) it.substringAfterLast(".") else it
            }
            return entries.find { it.name == normalized } ?: REQUEST
        }
    }
}
