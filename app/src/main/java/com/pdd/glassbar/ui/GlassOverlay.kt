package com.pdd.glassbar.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.compose.ui.platform.ComposeView
import com.pdd.glassbar.loader.PddLoader
import com.pdd.glassbar.ui.utils.LifecycleOwnerProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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

    const val TAG = "pdd_glass_bar_compose"
    private val KILL_SWITCH = File("/sdcard/pddglassbar.disable")

    fun install(container: ViewGroup, tabCls: Class<*>, activity: Activity?) {
        val log = { stage: String -> runCatching { PddLoader.bridge.log("install/$stage") } }
        runCatching { CrashCapture.install(container.context) }
        val flag = GlassFlags.load(KILL_SWITCH)
        if (flag != "on" && flag != "noglass" && flag != "noicons") { log("kill-switch($flag)/skip"); return }
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
            val ourOwner = activity?.let { LifecycleOwnerProvider.getOrCreate(it) }
                ?: LifecycleOwnerProvider.lifecycleOwner
            var effOwner: LifecycleOwner = ourOwner
            log("lifecycle-ok")

            // Recomposer 从 rootView(DecorView) 解析 owner:
            //  - 宿主已有(如 AppCompatActivity 体系) => 复用宿主的, 零侵入
            //  - 宿主没有(原生 Activity)          => 才写入我们的桥接 owner
            runCatching {
                val decor: android.view.View = activity?.window?.decorView ?: container.rootView
                val existing = androidx.lifecycle.ViewTreeLifecycleOwner.get(decor)
                if (existing != null) {
                    effOwner = existing
                    if (androidx.lifecycle.ViewTreeViewModelStoreOwner.get(decor) == null &&
                        ourOwner is ViewModelStoreOwner
                    ) decor.setViewTreeViewModelStoreOwner(ourOwner)
                    if (androidx.savedstate.ViewTreeSavedStateRegistryOwner.get(decor) == null &&
                        ourOwner is SavedStateRegistryOwner
                    ) decor.setViewTreeSavedStateRegistryOwner(ourOwner)
                    log("decor-owner-reuse-host")
                } else {
                    decor.setLifecycleOwner(ourOwner)
                    if (ourOwner is ViewModelStoreOwner) decor.setViewTreeViewModelStoreOwner(ourOwner)
                    if (ourOwner is SavedStateRegistryOwner) decor.setViewTreeSavedStateRegistryOwner(ourOwner)
                    log("decor-owner-set")
                }
            }.onFailure { log("decor-owner-failed") }

            composeView = ComposeView(container.context).apply {
                tag = TAG
                clipChildren = false
                clipToPadding = false
                setLifecycleOwner(effOwner)
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
