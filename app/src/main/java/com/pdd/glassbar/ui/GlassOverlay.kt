package com.pdd.glassbar.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.compose.ui.platform.ComposeView
import com.pdd.glassbar.loader.PddLoader
import com.pdd.glassbar.ui.utils.LifecycleOwnerProvider
import com.pdd.glassbar.ui.utils.findActivity
import com.pdd.glassbar.ui.utils.setLifecycleOwner
import java.io.File

/**
 * 纯镜像安装器:
 *  - 原生三件套仅做 alpha=0 透明化(保留布局与触摸能力, 供合成触摸定位),
 *    可见性/透明度由看门狗周期性再声明 —— PDD 的任何翻转都无效;
 *  - 任何一步失败自动恢复 alpha 并移除半成品。
 * 停机开关: /sdcard/pddglassbar.disable(noglass=关玻璃管线 / noicons=图标占位)。
 */
object GlassOverlay {

    const val TAG = "pdd_glass_bar_compose"
    private val KILL_SWITCH = File("/sdcard/pddglassbar.disable")

    fun install(container: ViewGroup, tabCls: Class<*>, activity: Activity?) {
        val log = { stage: String -> runCatching { PddLoader.bridge.log("install/$stage") } }
        val flag = GlassFlags.load(KILL_SWITCH)
        if (flag != "on" && flag != "noglass" && flag != "noicons") { log("kill-switch($flag)/skip"); return }
        if (container.findViewWithTag<View>(TAG) != null) return
        runCatching { ModuleFileLog.init(container.context) }
        runCatching { CrashCapture.install(container.context) }

        // ---- 分类子视图(只读) ----
        var sourceView: ViewGroup? = null
        var tabView: View? = null
        val toTransparent = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            val c = container.getChildAt(i)
            when {
                c is ViewGroup && c !is ComposeView && sourceView == null &&
                    c.javaClass.name == "android.widget.FrameLayout" -> sourceView = c
                c.javaClass.name.endsWith("PddTabPlaceholderLayout") -> toTransparent += c
                tabCls.isInstance(c) -> { tabView = c; toTransparent += c }
                c.javaClass == View::class.java -> toTransparent += c
            }
        }
        val content = sourceView ?: run { log("content-not-found/abort"); return }
        log("classified hide=${toTransparent.size}")

        var composeView: ComposeView? = null
        try {
            // ---- owner 解析(宿主有则复用, 无则写入桥接) ----
            val ourOwner = activity?.let { LifecycleOwnerProvider.getOrCreate(it) }
                ?: LifecycleOwnerProvider.lifecycleOwner
            var effOwner: androidx.lifecycle.LifecycleOwner = ourOwner
            runCatching {
                val decor: View = activity?.window?.decorView ?: container.rootView
                val existing = decor.getTag(
                    idOf("androidx.lifecycle", "view_tree_lifecycle_owner")
                ) as? androidx.lifecycle.LifecycleOwner
                if (existing != null) {
                    effOwner = existing
                    log("decor-owner-reuse-host")
                } else {
                    decor.setTag(
                        idOf("androidx.lifecycle", "view_tree_lifecycle_owner"), ourOwner
                    )
                    ourOwner.let { o ->
                        if (o is androidx.lifecycle.ViewModelStoreOwner) decor.setViewTreeViewModelStoreOwner(o)
                        if (o is androidx.savedstate.SavedStateRegistryOwner) decor.setViewTreeSavedStateRegistryOwner(o)
                    }
                    log("decor-owner-set")
                }
            }.onFailure { log("decor-owner-failed") }
            log("lifecycle-ok")

            composeView = ComposeView(container.context).apply {
                tag = TAG
                clipChildren = false
                clipToPadding = false
                setLifecycleOwner(effOwner)
                setContent { GlassBarHost(sourceView = content) }
            }
            log("compose-created")

            // ---- 透明化(保留布局! 合成触摸依赖真实坐标) ----
            toTransparent.forEach { BarState.registerHidden(it) }
            tabView?.let { BarState.bindTabView(it) }
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
            runCatching {
                toTransparent.forEach { it.alpha = 1f }
                composeView?.let { container.removeView(it) }
                container.clipChildren = true
                container.clipToPadding = true
            }
            runCatching {
                PddLoader.bridge.log(t)
                PddLoader.bridge.log("install FAILED -> rolled back")
            }
        }
    }

    private fun idOf(pkg: String, name: String): Int = runCatching {
        val fq = pkg + ".R$" + name
        Class.forName(fq).fields.first { it.name == name }.getInt(null)
    }.getOrDefault(-1)

    private fun View.setViewTreeViewModelStoreOwner(o: androidx.lifecycle.ViewModelStoreOwner) {
        setTag(idOf("androidx.lifecycle", "view_tree_view_model_store_owner"), o)
    }

    private fun View.setViewTreeSavedStateRegistryOwner(o: androidx.savedstate.SavedStateRegistryOwner) {
        setTag(idOf("androidx.savedstate", "view_tree_saved_state_registry_owner"), o)
    }
}
