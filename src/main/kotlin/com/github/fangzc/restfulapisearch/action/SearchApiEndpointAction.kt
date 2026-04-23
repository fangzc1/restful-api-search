package com.github.restfulapisearch.action

import com.github.restfulapisearch.scanner.SpringApiScanner
import com.github.restfulapisearch.ui.ApiEndpointPopup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * 搜索 REST API 端点的 Action
 * 通过快捷键或导航菜单触发，弹出搜索弹窗
 * TODO(Task5): 改为通过 ScanCache 异步获取端点
 */
class SearchApiEndpointAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val endpoints = SpringApiScanner.scanProject(project)
        ApiEndpointPopup(project, endpoints).show()
    }

    override fun update(e: AnActionEvent) {
        // 仅在项目打开时可用
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
