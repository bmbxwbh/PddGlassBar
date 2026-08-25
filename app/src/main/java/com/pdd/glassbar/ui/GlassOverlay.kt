package com.pdd.glassbar.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.compose.ui.platform.ComposeView
import com.pdd.glassbar.loader.PddLoader
import com.pdd.glassbar.ui.utils.LifecycleOwnerProvider
import com.pdd.glassbar.ui.utils.setLifecycleOwner
import java.io.File

/**
 * H1: 玻璃栏安装器。
 *
 * 安全承诺: 本方法任何一步失败 —— 包括 ComposeView 创建/挂载阶段 —— 都会
 * 恢复原生底栏可见性并移除半成品 overlay。模块永远不把崩溃带给宿主。
 * 物理停机开关: 创建 /sdcard/pddglassbar.disable 后冷启拼多多即完全跳过注入。
 */
object GlassOverlay {

    private const val TAG = "pdd_glass_bar_compose"
    private val KILL_SWITCH = File("/sdcard/pddglassbar.disable")

    fun install(container: ViewGroup, tabCls: Class<*>, activity: Activity?) {
        val log = { stage: String -> runCatching { PddLoader.bridge.log("install/$stage") } }
        if (KILL_SWITCH.exists()) { log("kill-switch-on/skip"); return }
        if (container.findViewWithTag<View>(TAG) != null) return
        runCatching { PddLoader.bridge.log("install/entered") }

        // ---- 阶段 0: 分类子视图(只读, 不可能崩) ----
        var sourceView: ViewGroup? = null
        val originals = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            val c = container.getChildAt(i)
            when {
                c is ViewGroup && c !is ComposeView && sourceView == null &&
                    c.javaClass.name == "android.widget.FrameLayout" -> sourceView = c
                c.javaClass.name.endsWith("PddTabPlaceholderLayout") -> originals += c
                tabCls.isInstance(c) -> originals += c
                c.javaClass == View::class.java -> originals += c
            }
        }
        val content = sourceView ?: run { log("content-not-found/abort"); return }
        log("classified hide=${originals.size}")

        // ---- 阶段 1~3 全程保护; 失败即回滚 ----
        var composeView: ComposeView? = null
        try {
            val owner = activity?.let { LifecycleOwnerProvider.getOrCreate(it) }
                ?: LifecycleOwnerProvider.lifecycleOwner
            log("lifecycle-ok")

            composeView = ComposeView(container.context).apply {
                tag = TAG
                clipChildren = false
                clipToPadding = false
                setLifecycleOwner(owner)
                setContent { GlassBarHost(sourceView = content) }
            }
            log("compose-created")

            originals.forEach { it.visibility = View.GONE }
            container.clipChildren = false
            container.clipToPadding = false
            content.clipChildren = false

            val lp = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
            container.addView(composeView, lp)
            log("attached")
        } catch (t: Throwable) {
            // 回滚: 原底栏全部恢复, 半成品移除 —— 宿主零感知
            runCatching {
                originals.forEach { it.visibility = View.VISIBLE }
                composeView?.let { container.removeView(it) }
                container.clipChildren = true
                container.clipToPadding = true
            }
            runCatching {
                PddLoader.bridge.log(t)
                PddLoader.bridge.log("install FAILED -> rolled back to stock bar")
            }
        }
    }
}
