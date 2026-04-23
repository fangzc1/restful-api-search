package com.github.restfulapisearch.ui

import com.github.restfulapisearch.model.ApiEndpoint
import com.github.restfulapisearch.model.FilteredEndpoint
import com.github.restfulapisearch.model.HttpMethod
import com.github.restfulapisearch.util.SearchMatcher
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
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
import com.intellij.util.Alarm
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
 *
 * 变更：
 * - 接受外部传入的 endpoints（扫描由 ScanCache 统一管理，不再自行扫描）
 * - 列表改为 FilteredEndpoint，过滤时预计算高亮位置
 * - 搜索加 80ms Alarm 防抖
 * - 提供 refreshEndpoints() 供后台重扫完成后静默刷新
 */
class ApiEndpointPopup(
    private val project: Project,
    initialEndpoints: List<ApiEndpoint>
) {

    companion object {
        private var lastSearchQuery: String = ""
        private const val HISTORY_PROPERTY = "RestfulApiSearch.SearchHistory"
        private var selectedMethods: MutableSet<String> = mutableSetOf()
        private var pinned: Boolean = false
        private const val SEARCH_DEBOUNCE_MS = 80

        // 弹窗位置和大小的持久化 key
        private const val POPUP_X_KEY = "RestfulApiSearch.PopupX"
        private const val POPUP_Y_KEY = "RestfulApiSearch.PopupY"
        private const val POPUP_W_KEY = "RestfulApiSearch.PopupWidth"
        private const val POPUP_H_KEY = "RestfulApiSearch.PopupHeight"
        private const val DEFAULT_WIDTH = 600
        private const val DEFAULT_HEIGHT = 450
    }

    private lateinit var popup: JBPopup
    private lateinit var endpointList: JBList<FilteredEndpoint>
    private lateinit var listModel: DefaultListModel<FilteredEndpoint>
    private lateinit var searchField: SearchTextField
    private lateinit var cellRenderer: ApiEndpointCellRenderer
    private lateinit var pinButton: JButton
    private lateinit var filterButton: JButton
    private lateinit var searchAlarm: Alarm

    private var allEndpoints: List<ApiEndpoint> = initialEndpoints
    private var historyAwtListener: AWTEventListener? = null
    // 实时缓存弹窗位置和大小，onClosed 时窗口已销毁，直接读缓存写入持久化
    private var cachedBounds: Rectangle? = null

    fun show() {
        val panel = buildPanel()
        val props = PropertiesComponent.getInstance()

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, searchField.textEditor)
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .setMinSize(Dimension(400, 300))
            .setCancelOnClickOutside(!pinned)
            .setCancelOnWindowDeactivation(false)
            .setCancelKeyEnabled(false)
            .setMayBeParent(true)
            .createPopup()

        // popup 作为 Alarm 的 parentDisposable，弹窗关闭时自动释放
        searchAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, popup)

        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                lastSearchQuery = searchField.text.trim()
                historyAwtListener?.let {
                    Toolkit.getDefaultToolkit().removeAWTEventListener(it)
                    historyAwtListener = null
                }
                // onClosed 时窗口已销毁，使用 ComponentListener 实时更新的缓存
                cachedBounds?.let { b ->
                    props.setValue(POPUP_X_KEY, b.x.toString())
                    props.setValue(POPUP_Y_KEY, b.y.toString())
                    props.setValue(POPUP_W_KEY, b.width.toString())
                    props.setValue(POPUP_H_KEY, b.height.toString())
                }
            }
        })

        // 读取上次保存的大小，没有则用默认值
        val savedW = props.getValue(POPUP_W_KEY)?.toIntOrNull() ?: DEFAULT_WIDTH
        val savedH = props.getValue(POPUP_H_KEY)?.toIntOrNull() ?: DEFAULT_HEIGHT
        popup.setSize(Dimension(savedW, savedH))

        val savedX = props.getValue(POPUP_X_KEY)?.toIntOrNull()
        val savedY = props.getValue(POPUP_Y_KEY)?.toIntOrNull()

        if (savedX != null && savedY != null) {
            popup.show(com.intellij.ui.awt.RelativePoint(Point(savedX, savedY)))
        } else {
            val ideFrame = WindowManager.getInstance().getFrame(project)
            if (ideFrame != null) {
                val x = ideFrame.locationOnScreen.x + (ideFrame.width - savedW) / 2
                val y = ideFrame.locationOnScreen.y + (ideFrame.height - savedH) / 2
                popup.show(com.intellij.ui.awt.RelativePoint(Point(x, y)))
            } else {
                popup.showCenteredInCurrentWindow(project)
            }
        }

        // popup.show() 之后窗口才存在，安装 ComponentListener 实时同步 bounds 到缓存
        SwingUtilities.invokeLater {
            val window = SwingUtilities.getWindowAncestor(panel) ?: return@invokeLater
            val boundsListener = object : java.awt.event.ComponentAdapter() {
                override fun componentMoved(e: java.awt.event.ComponentEvent) { cachedBounds = window.bounds }
                override fun componentResized(e: java.awt.event.ComponentEvent) { cachedBounds = window.bounds }
            }
            window.addComponentListener(boundsListener)
            // 初始化缓存为当前实际 bounds
            cachedBounds = window.bounds
        }
    }

    /**
     * 后台重扫完成后由 Action 调用，静默刷新端点列表。
     * 若 popup 已关闭则忽略。
     */
    fun refreshEndpoints(endpoints: List<ApiEndpoint>) {
        if (!::popup.isInitialized || popup.isDisposed) return
        allEndpoints = endpoints
        filterEndpoints()
    }

    private fun buildPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        val headerPanel = JPanel()
        headerPanel.layout = BoxLayout(headerPanel, BoxLayout.Y_AXIS)
        headerPanel.add(buildTitleBar())

        searchField = SearchTextField(HISTORY_PROPERTY)
        searchField.textEditor.emptyText.text = "Enter request service url"
        headerPanel.add(searchField)

        panel.add(headerPanel, BorderLayout.NORTH)

        // 初始 model 为空，由 filterEndpoints() 统一批量填充
        listModel = DefaultListModel<FilteredEndpoint>()

        cellRenderer = ApiEndpointCellRenderer()
        endpointList = object : JBList<FilteredEndpoint>(listModel) {
            override fun isFocusOwner(): Boolean = true
        }
        endpointList.cellRenderer = cellRenderer
        endpointList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        endpointList.font = JBUI.Fonts.label(13f)
        endpointList.fixedCellHeight = 28
        endpointList.background = JBUI.CurrentTheme.Popup.BACKGROUND

        val scrollPane = JBScrollPane(endpointList)
        panel.add(scrollPane, BorderLayout.CENTER)

        val hintLabel = JBLabel("  ↑/↓ navigate  |  Alt+↑/↓ history  |  Enter jump  |  Esc close")
        hintLabel.isEnabled = false
        panel.add(hintLabel, BorderLayout.SOUTH)

        setupSearchListener()
        setupKeyboardNavigation()
        setupMouseNavigation()
        installHistoryClickInterceptor()

        if (lastSearchQuery.isNotEmpty()) {
            searchField.text = lastSearchQuery
            searchField.textEditor.selectAll()
        }

        filterEndpoints()

        return panel
    }

    private fun buildTitleBar(): JPanel {
        val titleBar = JPanel(BorderLayout())
        titleBar.border = JBUI.Borders.empty(6, 8, 4, 8)

        val titleLabel = JBLabel("Search REST API Endpoints")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
        titleBar.add(titleLabel, BorderLayout.WEST)

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
        makeTitleBarDraggable(titleBar, titleLabel)
        return titleBar
    }

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

    private fun togglePin() {
        pinned = !pinned
        updatePinButton()
    }

    private fun updatePinButton() {
        if (::pinButton.isInitialized) {
            pinButton.icon = if (pinned) AllIcons.General.PinSelected else AllIcons.General.Pin_tab
            pinButton.toolTipText = if (pinned) "Unpin popup" else "Pin popup"
        }
    }

    private fun showMethodFilterPopup() {
        val group = DefaultActionGroup()
        group.add(object : ToggleAction("ALL (任何方法)") {
            override fun isSelected(e: AnActionEvent): Boolean = selectedMethods.isEmpty()
            override fun setSelected(e: AnActionEvent, state: Boolean) {
                selectedMethods.clear()
                filterEndpoints()
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        })
        group.addSeparator()
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

    private fun setupSearchListener() {
        searchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = scheduleFilter()
            override fun removeUpdate(e: DocumentEvent?) = scheduleFilter()
            override fun changedUpdate(e: DocumentEvent?) = scheduleFilter()
        })
    }

    /** 防抖：80ms 内多次输入只触发一次过滤，避免快速输入时无效计算 */
    private fun scheduleFilter() {
        if (::searchAlarm.isInitialized) {
            searchAlarm.cancelAllRequests()
            searchAlarm.addRequest({ filterEndpoints() }, SEARCH_DEBOUNCE_MS)
        } else {
            // Alarm 尚未初始化（buildPanel 阶段的初始过滤）时直接执行
            filterEndpoints()
        }
    }

    private fun setupKeyboardNavigation() {
        val editor = searchField.textEditor
        val inputMap = editor.getInputMap(JComponent.WHEN_FOCUSED)
        val actionMap = editor.actionMap

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "selectionUp")
        actionMap.put("selectionUp", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = moveSelection(-1)
        })
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "selectionDown")
        actionMap.put("selectionDown", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = moveSelection(1)
        })
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "jumpToSelected")
        actionMap.put("jumpToSelected", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = navigateToSelected()
        })
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closePopup")
        actionMap.put("closePopup", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = popup.cancel()
        })
    }

    private fun setupMouseNavigation() {
        endpointList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) navigateToSelected()
            }
        })
    }

    private fun installHistoryClickInterceptor() {
        val editor = searchField.textEditor
        historyAwtListener = AWTEventListener { event ->
            if (event !is MouseEvent) return@AWTEventListener
            if (event.id != MouseEvent.MOUSE_PRESSED) return@AWTEventListener
            if (event.source !== editor) return@AWTEventListener
            if (event.x < JBUI.scale(28)) {
                event.consume()
                SwingUtilities.invokeLater { showSearchHistoryPopup() }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(historyAwtListener, AWTEvent.MOUSE_EVENT_MASK)
    }

    private fun showSearchHistoryPopup() {
        val history = searchField.history.filter { it.isNotBlank() }.distinct()
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
     * 链式过滤：HTTP Method → 文本搜索 → 预计算高亮位置。
     *
     * 在未挂载到列表的新 model 上批量填充，不触发任何 UI 布局事件；
     * 过滤阶段同步计算 matchPositions，cellRenderer 直接使用，消灭重复计算。
     */
    private fun filterEndpoints() {
        val query = searchField.text.trim()

        var filtered: List<ApiEndpoint> = allEndpoints

        if (selectedMethods.isNotEmpty()) {
            filtered = filtered.filter {
                it.httpMethod.displayName.uppercase() in selectedMethods
            }
        }
        if (query.isNotEmpty()) {
            filtered = filtered.filter { SearchMatcher.matches(it.path, query) }
        }

        val newModel = DefaultListModel<FilteredEndpoint>()
        newModel.addAll(filtered.map { ep ->
            FilteredEndpoint(ep, SearchMatcher.findMatchPositions(ep.path, query))
        })
        listModel = newModel
        endpointList.model = newModel

        if (newModel.size() > 0) {
            endpointList.selectedIndex = 0
            endpointList.ensureIndexIsVisible(0)
        }
    }

    private fun moveSelection(delta: Int) {
        val size = listModel.size()
        if (size == 0) return
        val next = (endpointList.selectedIndex + delta).coerceIn(0, size - 1)
        endpointList.selectedIndex = next
        endpointList.ensureIndexIsVisible(next)
    }

    private fun navigateToSelected() {
        val selected = endpointList.selectedValue ?: return
        val query = searchField.text.trim()
        if (query.isNotEmpty()) searchField.addCurrentTextToHistory()
        popup.cancel()
        selected.endpoint.psiMethod.navigate(true)
    }
}
