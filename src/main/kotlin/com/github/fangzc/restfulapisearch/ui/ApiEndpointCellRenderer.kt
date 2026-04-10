package com.github.restfulapisearch.ui

import com.github.restfulapisearch.model.ApiEndpoint
import com.github.restfulapisearch.model.HttpMethod
import com.github.restfulapisearch.util.SearchMatcher
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
 * 支持搜索关键字高亮
 */
class ApiEndpointCellRenderer : com.intellij.ui.ColoredListCellRenderer<ApiEndpoint>() {

    /** 当前搜索关键字，由 Popup 在过滤时更新 */
    var searchQuery: String = ""

    companion object {
        // 路径文本样式
        private val PATH_NORMAL = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES

        // 匹配高亮样式：使用 STYLE_SEARCH_MATCH，由 SimpleColoredComponent 内置的
        // UIUtil.drawSearchMatch() 在第二轮渲染中绘制高亮背景，选中/未选中状态均可正确显示
        private val PATH_HIGHLIGHT = SimpleTextAttributes(
            null, null, null,
            SimpleTextAttributes.STYLE_BOLD or SimpleTextAttributes.STYLE_SEARCH_MATCH
        )

        // 位置文本样式（灰色）
        private val LOCATION_NORMAL = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY)
    }

    override fun customizeCellRenderer(
        list: JList<out ApiEndpoint>,
        value: ApiEndpoint?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        if (value == null) return

        // HTTP 方法图标
        icon = HttpMethodIcon(value.httpMethod)

        // 路径文本（带搜索关键字高亮）
        val pathPositions = SearchMatcher.findMatchPositions(value.path, searchQuery)
        appendHighlighted(value.path, pathPositions, PATH_HIGHLIGHT, PATH_NORMAL)

        // 间隔
        append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)

        // 类名#方法名（不参与搜索匹配，仅显示）
        val locText = "(${value.locationText})"
        append(locText, LOCATION_NORMAL)
    }

    /**
     * 将文本按匹配位置分段渲染，匹配部分使用高亮样式
     */
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

            // 绘制圆角矩形背景
            g2.color = method.color
            g2.fillRoundRect(x, y, WIDTH, HEIGHT, 4, 4)

            // 绘制白色文字（居中）
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
