package com.github.restfulapisearch.cache

import com.github.restfulapisearch.model.ApiEndpoint
import com.github.restfulapisearch.scanner.SpringApiScanner
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Callable

/**
 * 项目级 API 端点扫描缓存服务。
 *
 * 职责：
 * - 首次访问触发后台 PSI 扫描，结果缓存至内存。
 * - 监听 .java / .kt 文件变更，标记缓存 dirty；下次调用时后台重扫。
 * - 存在过期缓存时立即返回旧数据（弹窗秒开），后台扫完再调用 onRefresh 刷新。
 *
 * 线程模型：所有状态字段仅在 EDT 上读写，无需额外同步。
 */
@Service(Service.Level.PROJECT)
class ScanCache(private val project: Project) {

    private var cachedEndpoints: List<ApiEndpoint>? = null
    private var isDirty: Boolean = true
    private var scanning: Boolean = false

    init {
        registerListeners()
    }

    /**
     * 获取端点列表，并在必要时触发后台重扫。
     * 两个回调均在 EDT 上调用。
     *
     * - 缓存干净  → onReady 立即调用，onRefresh 不调用。
     * - 有过期缓存 → onReady 立即以旧数据调用；后台扫完后 onRefresh 以新数据调用。
     * - 无缓存    → 后台扫完后 onReady 以新数据调用，onRefresh 不调用。
     */
    fun getOrScanAsync(
        onReady: (List<ApiEndpoint>) -> Unit,
        onRefresh: ((List<ApiEndpoint>) -> Unit)? = null
    ) {
        val cached = cachedEndpoints
        if (cached != null && !isDirty) {
            onReady(cached)
            return
        }
        // 有过期缓存时立即返回，弹窗秒开
        if (cached != null) {
            onReady(cached)
        }
        // 避免并发重复触发扫描
        if (scanning) return
        scanning = true

        ReadAction.nonBlocking(Callable { SpringApiScanner.doScan(project) })
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { endpoints ->
                scanning = false
                isDirty = false
                cachedEndpoints = endpoints
                if (cached == null) {
                    onReady(endpoints)
                } else {
                    onRefresh?.invoke(endpoints)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    fun markDirty() {
        isDirty = true
    }

    private fun registerListeners() {
        // 监听 PSI 文件内容变更（编辑 .java / .kt 时）
        PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
            private fun handle(event: PsiTreeChangeEvent) {
                val name = event.file?.name ?: return
                if (name.endsWith(".java") || name.endsWith(".kt")) markDirty()
            }
            override fun childrenChanged(event: PsiTreeChangeEvent) = handle(event)
            override fun childAdded(event: PsiTreeChangeEvent) = handle(event)
            override fun childRemoved(event: PsiTreeChangeEvent) = handle(event)
            override fun childReplaced(event: PsiTreeChangeEvent) = handle(event)
        }, project) // project 作为 Disposable，项目关闭时自动解注册

        // 监听 VFS 文件新增/删除（如新建或删除 Controller 文件）
        project.messageBus.connect(project).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { it.path.endsWith(".java") || it.path.endsWith(".kt") }) {
                        markDirty()
                    }
                }
            }
        )
    }

    companion object {
        fun getInstance(project: Project): ScanCache = project.service()
    }
}
