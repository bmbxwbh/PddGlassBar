package com.pdd.glassbar.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pdd.glassbar.loader.PddLoader
import com.pdd.glassbar.ui.utils.LifecycleOwnerProvider
import com.pdd.glassbar.ui.utils.findActivity
import com.pdd.glassbar.ui.utils.setLifecycleOwner
import java.io.File

/**
 * 纯镜像安装器。
 * Decor owner 写入使用 androidx 官方扩展函数 —— 编译期内联正确 key,
 * 不做运行时反射(宿主深度混淆/裁剪下反射方案会写入失效, 已由日志证实)。
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
            // ---- Owner: 无条件写入我们的桥接(该配置已验证可正常渲染) ----
            val ourOwner = activity?.let { LifecycleOwnerProvider.getOrCreate(it) }
                ?: LifecycleOwnerProvider.lifecycleOwner
            val decor: View = activity?.window?.decorView ?: container.rootView
            decor.setViewTreeLifecycleOwner(ourOwner)
            if (ourOwner is ViewModelStoreOwner) decor.setViewTreeViewModelStoreOwner(ourOwner)
            if (ourOwner is SavedStateRegistryOwner) decor.setViewTreeSavedStateRegistryOwner(ourOwner)
            log("lifecycle-ok")

            composeView = ComposeView(container.context).apply {
                tag = TAG
                clipChildren = false
                clipToPadding = false
                setLifecycleOwner(ourOwner)
                setContent { GlassBarHost(sourceView = content) }
            }
            log("compose-created")

            // ---- 原生三件套: alpha=0 透明化(GONE 会破坏布局, alpha 封死一切翻转闪现) ----
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
}
