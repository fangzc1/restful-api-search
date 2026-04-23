package com.github.restfulapisearch.action

import com.github.restfulapisearch.cache.ScanCache
import com.github.restfulapisearch.ui.ApiEndpointPopup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * 搜索 REST API 端点的 Action。
 * 通过 ScanCache 异步获取端点，首次扫描在后台线程执行，不阻塞 EDT。
 */
class SearchApiEndpointAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val cache = ScanCache.getInstance(project)
        var popup: ApiEndpointPopup? = null

        cache.getOrScanAsync(
            onReady = { endpoints ->
                // 首次可用数据（缓存命中或首次扫描完成）：创建并展示弹窗
                if (popup == null) {
                    popup = ApiEndpointPopup(project, endpoints)
                    popup!!.show()
                }
            },
            onRefresh = { freshEndpoints ->
                // 后台重扫完成：若弹窗仍开着则静默刷新列表
                popup?.refreshEndpoints(freshEndpoints)
            }
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
