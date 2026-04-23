package com.github.restfulapisearch.model

import com.github.restfulapisearch.ui.HttpMethodIcon
import java.awt.Color
import javax.swing.Icon

enum class HttpMethod(val displayName: String, val color: Color) {
    GET("GET", Color(0x61, 0xAF, 0xEF)),
    POST("POST", Color(0x98, 0xC3, 0x79)),
    PUT("PUT", Color(0xE5, 0xC0, 0x7B)),
    DELETE("DELETE", Color(0xE0, 0x6C, 0x75)),
    PATCH("PATCH", Color(0xC6, 0x78, 0xDD)),
    HEAD("HEAD", Color(0x56, 0xB6, 0xC2)),
    OPTIONS("OPTIONS", Color(0xAB, 0xB2, 0xBF)),
    REQUEST("ALL", Color(0xAB, 0xB2, 0xBF));

    val icon: Icon by lazy { HttpMethodIcon(this) }

    companion object {
        private val ANNOTATION_MAP = mapOf(
            "org.springframework.web.bind.annotation.GetMapping" to GET,
            "org.springframework.web.bind.annotation.PostMapping" to POST,
            "org.springframework.web.bind.annotation.PutMapping" to PUT,
            "org.springframework.web.bind.annotation.DeleteMapping" to DELETE,
            "org.springframework.web.bind.annotation.PatchMapping" to PATCH,
            "org.springframework.web.bind.annotation.RequestMapping" to REQUEST
        )

        fun fromAnnotation(annotationQualifiedName: String): HttpMethod? =
            ANNOTATION_MAP[annotationQualifiedName]

        fun resolveRequestMappingMethod(methodAttribute: String?): HttpMethod {
            if (methodAttribute.isNullOrBlank()) return REQUEST
            val normalized = methodAttribute.uppercase().let {
                if (it.contains(".")) it.substringAfterLast(".") else it
            }
            return entries.find { it.name == normalized } ?: REQUEST
        }
    }
}
