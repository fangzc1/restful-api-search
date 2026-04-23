package com.github.restfulapisearch.ui

import com.github.restfulapisearch.model.ApiEndpoint
import com.github.restfulapisearch.model.FilteredEndpoint
import com.github.restfulapisearch.model.HttpMethod
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import javax.swing.Icon
import javax.swing.JList

/**
 * API 端点列表单元格渲染器
 * 展示格式：[HTTP方法图标] 路径 (类名#方法名)
 * 直接使用 FilteredEndpoint.matchPositions，不在渲染时重复计算匹配位置。
 */
class ApiEndpointCellRenderer : ColoredListCellRenderer<FilteredEndpoint>() {

    companion object {
        private val PATH_NORMAL = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        private val PATH_HIGHLIGHT = SimpleTextAttributes(
            null, null, null,
            SimpleTextAttributes.STYLE_BOLD or SimpleTextAttributes.STYLE_SEARCH_MATCH
        )
        private val LOCATION_NORMAL = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY)
    }

    override fun customizeCellRenderer(
        list: JList<out FilteredEndpoint>,
        value: FilteredEndpoint?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        if (value == null) return
        // 使用枚举级别缓存的图标，避免每次渲染 new 对象
        icon = value.endpoint.httpMethod.icon
        appendHighlighted(value.endpoint.path, value.matchPositions, PATH_HIGHLIGHT, PATH_NORMAL)
        append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append("(${value.endpoint.locationText})", LOCATION_NORMAL)
    }

    private fun appendHighlighted(
        text: String,
        matchPositions: Set<Int>,
        highlightAttr: SimpleTextAttributes,
        normalAttr: SimpleTextAttributes
    ) {
        if (matchPositions.isEmpty()) {
            append(text, normalAttr)
            return
        }
        var i = 0
        while (i < text.length) {
            val isMatch = i in matchPositions
            val start = i
            while (i < text.length && (i in matchPositions) == isMatch) i++
            append(text.substring(start, i), if (isMatch) highlightAttr else normalAttr)
        }
    }
}

/**
 * HTTP 方法图标
 * 绘制一个带颜色的圆角矩形标签，内含白色方法名文字
 */
class HttpMethodIcon(private val method: HttpMethod) : Icon {

    companion object {
        private const val WIDTH = 56
        private const val HEIGHT = 20
        private val FONT = Font("SansSerif", Font.BOLD, 11)
        private val FRC = FontRenderContext(AffineTransform(), true, true)
    }

    override fun getIconWidth(): Int = WIDTH
    override fun getIconHeight(): Int = HEIGHT

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.color = method.color
            g2.fillRoundRect(x, y, WIDTH, HEIGHT, 4, 4)
            g2.color = Color.WHITE
            g2.font = FONT
            val text = method.displayName
            val textBounds = FONT.getStringBounds(text, FRC)
            val textX = x + (WIDTH - textBounds.width.toInt()) / 2
            val textY = y + (HEIGHT + FONT.size) / 2 - 1
            g2.drawString(text, textX, textY)
        } finally {
            g2.dispose()
        }
    }
}
