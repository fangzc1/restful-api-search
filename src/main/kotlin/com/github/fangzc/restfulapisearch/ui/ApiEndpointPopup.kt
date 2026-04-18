package com.github.restfulapisearch.ui

import com.github.restfulapisearch.model.ApiEndpoint
import com.github.restfulapisearch.model.HttpMethod
import com.github.restfulapisearch.scanner.SpringApiScanner
import com.github.restfulapisearch.util.SearchMatcher
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * API 端点搜索弹窗
 * 参考 IntelliJ "在文件中查找" 弹窗风格
 */
class ApiEndpointPopup(private val project: Project) {

    companion object {
        /** 跨实例持久化搜索文本 */
        private var lastSearchQuery: String = ""

        /** SearchTextField 历史持久化 key */
        private const val HISTORY_PROPERTY = "RestfulApiSearch.SearchHistory"

        /** HTTP Method 过滤（多选，存储选中的 method 名集合） */
        private var selectedMethods: MutableSet<String> = mutableSetOf()

        /** Pin 状态 */
        private var pinned: Boolean = false
    }

    private lateinit var popup: JBPopup
    private lateinit var endpointList: JBList<ApiEndpoint>
    private lateinit var listModel: DefaultListModel<ApiEndpoint>
    private lateinit var searchField: SearchTextField
    private lateinit var cellRenderer: ApiEndpointCellRenderer
    private lateinit var pinButton: JButton
    private lateinit var filterButton: JButton

    /** 所有端点（完整列表，用于过滤） */
    private var allEndpoints: List<ApiEndpoint> = emptyList()

    /** 历史弹窗点击拦截器（需在弹窗关闭时移除，防止内存泄漏） */
    private var historyAwtListener: AWTEventListener? = null

    /**
     * 扫描端点并显示搜索弹窗
     */
    fun show() {
        // 扫描项目中所有 REST API 端点
        allEndpoints = SpringApiScanner.scanProject(project)

        // 构建面板
        val panel = buildPanel()

        // 创建弹窗（标题嵌入面板内，不使用 setTitle）
        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, searchField.textEditor)
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .setMinSize(Dimension(700, 450))
            .setCancelOnClickOutside(!pinned)
            .setCancelOnWindowDeactivation(false) // 防止历史弹窗（独立窗口）获焦时父弹窗因窗口失活而关闭
            .setCancelKeyEnabled(false) // 自己处理 Esc
            .setMayBeParent(true) // 允许子弹窗（如搜索历史）出现时不关闭本弹窗
            .createPopup()

        // 弹窗关闭时保存状态，并清理 AWTEventListener
        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                lastSearchQuery = searchField.text.trim()
                historyAwtListener?.let {
                    Toolkit.getDefaultToolkit().removeAWTEventListener(it)
                    historyAwtListener = null
                }
            }
        })

        // 通过 WindowManager 获取 IDE 主窗口，手动计算绝对坐标居中显示
        // showInCenterOf 依赖组件可见区域，rootPane 仅代表编辑器内容区，导致偏向编辑器中央
        // 直接用 JFrame bounds 计算弹窗左上角坐标，确保相对整个 IDE 窗口（含工具栏/工具窗口）居中
        val ideFrame = WindowManager.getInstance().getFrame(project)
        if (ideFrame != null) {
            val popupSize = Dimension(700, 450)
            val fx = ideFrame.locationOnScreen.x
            val fy = ideFrame.locationOnScreen.y
            val x = fx + (ideFrame.width - popupSize.width) / 2
            val y = fy + (ideFrame.height - popupSize.height) / 2
            popup.show(com.intellij.ui.awt.RelativePoint(Point(x, y)))
        } else {
            popup.showCenteredInCurrentWindow(project)
        }
    }

    /**
     * 构建弹窗面板
     */
    private fun buildPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // ===== 顶部区域（标题栏 + 搜索框 + Method 过滤）=====
        val headerPanel = JPanel()
        headerPanel.layout = BoxLayout(headerPanel, BoxLayout.Y_AXIS)

        // 标题栏：左侧标题 + 右侧 Pin 按钮
        headerPanel.add(buildTitleBar())

        // 搜索输入框
        searchField = SearchTextField(HISTORY_PROPERTY)
        searchField.textEditor.emptyText.text = "Enter request service url"
        headerPanel.add(searchField)

        panel.add(headerPanel, BorderLayout.NORTH)

        // ===== 中部列表 =====
        listModel = DefaultListModel<ApiEndpoint>()
        allEndpoints.forEach { listModel.addElement(it) }

        cellRenderer = ApiEndpointCellRenderer()
        endpointList = object : JBList<ApiEndpoint>(listModel) {
            override fun isFocusOwner(): Boolean = true
        }
        endpointList.cellRenderer = cellRenderer
        endpointList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        endpointList.font = JBUI.Fonts.label(13f)
        endpointList.fixedCellHeight = 28
        // 列表背景色与 IntelliJ 弹窗一致
        endpointList.background = JBUI.CurrentTheme.Popup.BACKGROUND
        if (listModel.size() > 0) {
            endpointList.selectedIndex = 0
        }

        val scrollPane = JBScrollPane(endpointList)
        panel.add(scrollPane, BorderLayout.CENTER)

        // ===== 底部提示 =====
        val hintLabel = JBLabel("  \u2191/\u2193 navigate  |  Alt+\u2191/\u2193 history  |  Enter jump  |  Esc close")
        hintLabel.isEnabled = false
        panel.add(hintLabel, BorderLayout.SOUTH)

        // 绑定事件
        setupSearchListener()
        setupKeyboardNavigation()
        setupMouseNavigation()
        // 安装 AWTEventListener 拦截放大镜区域点击，展示历史弹窗
        installHistoryClickInterceptor()

        // 恢复上次搜索文本
        if (lastSearchQuery.isNotEmpty()) {
            searchField.text = lastSearchQuery
            searchField.textEditor.selectAll()
        }

        // 初始过滤
        filterEndpoints()

        return panel
    }

    /**
     * 构建标题栏：左侧标题 + 右侧过滤按钮 + Pin 按钮
     */
    private fun buildTitleBar(): JPanel {
        val titleBar = JPanel(BorderLayout())
        titleBar.border = JBUI.Borders.empty(6, 8, 4, 8)

        // 左侧标题
        val titleLabel = JBLabel("Search REST API Endpoints")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
        titleBar.add(titleLabel, BorderLayout.WEST)

        // 右侧：过滤按钮 + Pin 按钮
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        rightPanel.isOpaque = false

        filterButton = JButton(AllIcons.General.Filter).also {
            it.isOpaque = false
            it.isContentAreaFilled = false
            it.isBorderPainted = false
            it.isFocusPainted = false
            it.margin = Insets(0, 0, 0, 0)
            it.preferredSize = JBUI.size(22, 22)
            it.toolTipText = "Filter by HTTP Method"
            it.addActionListener { showMethodFilterPopup() }
        }
        rightPanel.add(filterButton)

        val pinIcon = if (pinned) AllIcons.General.PinSelected else AllIcons.General.Pin_tab
        pinButton = JButton(pinIcon).also {
            it.isOpaque = false
            it.isContentAreaFilled = false
            it.isBorderPainted = false
            it.isFocusPainted = false
            it.margin = Insets(0, 0, 0, 0)
            it.preferredSize = JBUI.size(22, 22)
            it.toolTipText = if (pinned) "Unpin popup" else "Pin popup"
            it.addActionListener { togglePin() }
        }
        updatePinButton()
        rightPanel.add(pinButton)

        titleBar.add(rightPanel, BorderLayout.EAST)

        // 启用标题栏拖动弹窗
        makeTitleBarDraggable(titleBar, titleLabel)

        return titleBar
    }

    /**
     * 为标题栏及标题文本添加拖动监听器，实现弹窗拖拽移动
     */
    private fun makeTitleBarDraggable(titleBar: JPanel, titleLabel: JBLabel) {
        val dragStart = Point()
        val pressListener = object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                dragStart.location = e.locationOnScreen
            }
        }
        val dragListener = object : java.awt.event.MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                val window = SwingUtilities.getWindowAncestor(titleBar) ?: return
                val loc = window.location
                window.setLocation(
                    loc.x + e.locationOnScreen.x - dragStart.x,
                    loc.y + e.locationOnScreen.y - dragStart.y
                )
                dragStart.location = e.locationOnScreen
            }
        }
        titleBar.addMouseListener(pressListener)
        titleBar.addMouseMotionListener(dragListener)
        titleLabel.addMouseListener(pressListener)
        titleLabel.addMouseMotionListener(dragListener)
    }

    /**
     * 切换 Pin 状态
     * 注意：pin 状态变化在弹窗下次打开时生效（避免使用内部 API AbstractPopup.setCancelOnClickOutside）
     */
    private fun togglePin() {
        pinned = !pinned
        updatePinButton()
    }

    /**
     * 根据 Pin 状态更新按钮图标和提示
     */
    private fun updatePinButton() {
        if (::pinButton.isInitialized) {
            pinButton.icon = if (pinned) AllIcons.General.PinSelected else AllIcons.General.Pin_tab
            pinButton.toolTipText = if (pinned) "Unpin popup" else "Pin popup"
        }
    }

    /**
     * 显示 HTTP Method 过滤弹窗（使用 IntelliJ 原生 ToggleAction 风格）
     */
    private fun showMethodFilterPopup() {
        val group = DefaultActionGroup()

        // "全部方法"选项
        group.add(object : ToggleAction("ALL (任何方法)") {
            override fun isSelected(e: AnActionEvent): Boolean = selectedMethods.isEmpty()
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                selectedMethods.clear()
                filterEndpoints()
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        })

        group.addSeparator()

        // 各 HTTP Method 选项
        for (method in listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")) {
            group.add(object : ToggleAction(method) {
                override fun isSelected(e: AnActionEvent): Boolean = method in selectedMethods
                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    if (state) selectedMethods.add(method) else selectedMethods.remove(method)
                    filterEndpoints()
                }
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })
        }

        val context = DataManager.getInstance().getDataContext(filterButton)
        JBPopupFactory.getInstance()
            .createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
            .showUnderneathOf(filterButton)
    }

    /**
     * 搜索输入实时过滤
     */
    private fun setupSearchListener() {
        searchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterEndpoints()
            override fun removeUpdate(e: DocumentEvent?) = filterEndpoints()
            override fun changedUpdate(e: DocumentEvent?) = filterEndpoints()
        })
    }

    /**
     * 键盘导航：上下移动选中项，Enter 跳转，Esc 关闭
     */
    private fun setupKeyboardNavigation() {
        val editor = searchField.textEditor
        val inputMap = editor.getInputMap(JComponent.WHEN_FOCUSED)
        val actionMap = editor.actionMap

        // 上 — 列表上移
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "selectionUp")
        actionMap.put("selectionUp", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                moveSelection(-1)
            }
        })

        // 下 — 列表下移
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "selectionDown")
        actionMap.put("selectionDown", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                moveSelection(1)
            }
        })

        // Enter — 跳转
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "jumpToSelected")
        actionMap.put("jumpToSelected", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                navigateToSelected()
            }
        })

        // Esc — 关闭弹窗
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closePopup")
        actionMap.put("closePopup", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                popup.cancel()
            }
        })
    }

    /**
     * 鼠标双击导航
     */
    private fun setupMouseNavigation() {
        endpointList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    navigateToSelected()
                }
            }
        })
    }

    /**
     * 安装 AWTEventListener 拦截放大镜图标区域的鼠标点击。
     *
     * 背景：SearchTextField 的放大镜不是独立的 AbstractButton，而是在 processMouseEvent()
     * 中检测 x < JBUIScale.scale(28) 的点击区域（与 IntelliJ 内部实现保持一致）。
     * 直接 addMouseListener 无法先于 SearchTextField 自身的 processMouseEvent() 运行，
     * 因此使用 AWTEventListener（在组件事件分发之前执行）来拦截并消费该点击事件，
     * 替换为通过 JBPopupFactory 创建的正规子弹窗，确保父弹窗不会异常关闭。
     */
    private fun installHistoryClickInterceptor() {
        val editor = searchField.textEditor
        historyAwtListener = AWTEventListener { event ->
            if (event !is MouseEvent) return@AWTEventListener
            if (event.id != MouseEvent.MOUSE_PRESSED) return@AWTEventListener
            if (event.source !== editor) return@AWTEventListener
            // 放大镜图标占据文本框左侧约 28px（与 IntelliJ 内部实现一致）
            if (event.x < JBUI.scale(28)) {
                event.consume()
                SwingUtilities.invokeLater { showSearchHistoryPopup() }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(historyAwtListener, AWTEvent.MOUSE_EVENT_MASK)
    }

    /**
     * 通过 JBPopupFactory 显示历史记录弹窗。
     * 使用 createPopupChooserBuilder 可确保弹窗被正确注册为父弹窗的子弹窗。
     *
     * 注意：SearchTextField 内部通过 PropertiesComponent.setValue() 以换行分隔字符串的形式存储历史，
     * 而非 setList()/getList()（后者使用数组存储，两者不兼容）。
     * 因此必须通过 searchField.history 读取内部模型数据，而不是直接访问 PropertiesComponent。
     */
    private fun showSearchHistoryPopup() {
        val history = searchField.history
            .filter { it.isNotBlank() }
            .distinct()

        if (history.isEmpty()) return

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(history)
            .setItemChosenCallback { chosen ->
                searchField.text = chosen
                filterEndpoints()
            }
            .createPopup()
            .showUnderneathOf(searchField)
    }

    /**
     * 链式过滤：HTTP Method → 文本搜索
     */
    private fun filterEndpoints() {
        val query = searchField.text.trim()

        // 更新渲染器的搜索关键字（用于高亮）
        cellRenderer.searchQuery = query

        listModel.clear()

        var filtered: List<ApiEndpoint> = allEndpoints

        // 第1级：HTTP Method 过滤（多选，未选中任何则不过滤）
        if (selectedMethods.isNotEmpty()) {
            filtered = filtered.filter {
                it.httpMethod.displayName.uppercase() in selectedMethods
            }
        }

        // 第2级：文本搜索过滤
        if (query.isNotEmpty()) {
            filtered = filtered.filter { endpoint ->
                SearchMatcher.matches(endpoint.path, query)
            }
        }

        filtered.forEach { listModel.addElement(it) }

        if (listModel.size() > 0) {
            endpointList.selectedIndex = 0
            endpointList.ensureIndexIsVisible(0)
        }
    }

    /**
     * 移动列表选中项
     */
    private fun moveSelection(delta: Int) {
        val size = listModel.size()
        if (size == 0) return
        val current = endpointList.selectedIndex
        val next = (current + delta).coerceIn(0, size - 1)
        endpointList.selectedIndex = next
        endpointList.ensureIndexIsVisible(next)
    }

    /**
     * 跳转到选中端点对应的源码位置，并将当前搜索保存到历史
     */
    private fun navigateToSelected() {
        val selected = endpointList.selectedValue ?: return

        // 只有实际跳转时才保存搜索历史
        val query = searchField.text.trim()
        if (query.isNotEmpty()) {
            searchField.addCurrentTextToHistory()
        }

        popup.cancel()
        // 导航到方法定义处并获取编辑器焦点
        selected.psiMethod.navigate(true)
    }
}
