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
import com.pdd.glassbar.core.AppProfile
import com.pdd.glassbar.loader.GlassLoader
import com.pdd.glassbar.ui.utils.LifecycleOwnerProvider
import com.pdd.glassbar.ui.utils.findActivity
import com.pdd.glassbar.ui.utils.setLifecycleOwner
import java.io.File

/**
 * 纯镜像安装器(Profile 驱动):
 *  - 原生三件套 alpha=0 透明化(保留布局供合成触摸; 看门狗再声明);
 *  - 任何失败自动恢复 alpha 并移除半成品;
 *  - 停机开关 /sdcard/pddglassbar.disable(noglass/noicons 分级)。
 */
object GlassOverlay {

    const val TAG = "pdd_glass_bar_compose"
    private val KILL_SWITCH = File("/sdcard/pddglassbar.disable")

    fun install(container: ViewGroup, profile: AppProfile, activity: Activity?) {
        val log = { stage: String -> runCatching { GlassLoader.bridge.log("install/$stage") } }
        val flag = GlassFlags.load(KILL_SWITCH)
        if (flag != "on" && flag != "noglass" && flag != "noicons") { log("kill-switch($flag)/skip"); return }
        if (container.findViewWithTag<View>(TAG) != null) return
        runCatching { ModuleFileLog.init(container.context) }
        runCatching { CrashCapture.install(container.context) }

        // ---- 分类(按 Profile) ----
        var sourceView: ViewGroup? = null
        var tabView: View? = null
        val toTransparent = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            val c = container.getChildAt(i)
            when {
                c is ViewGroup && c !is ComposeView && sourceView == null &&
                    c.javaClass.name == "android.widget.FrameLayout" -> sourceView = c
                profile.placeholderSuffix != null &&
                    c.javaClass.name.endsWith(profile.placeholderSuffix!!) -> toTransparent += c
                profile.tabViewClass != null && c.javaClass.name == profile.tabViewClass -> {
                    tabView = c; toTransparent += c
                }
                c.javaClass == View::class.java -> toTransparent += c
            }
        }
        val content = sourceView ?: run { log("content-not-found/abort"); return }
        log("classified hide=${toTransparent.size}")

        var composeView: ComposeView? = null
        try {
            // ---- Owner 无条件写入(官方扩展函数, key 编译期内联) ----
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

            // ---- alpha=0 透明化(保留布局; 不用 GONE —— 合成触摸需要真实坐标) ----
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

            // ---- 快照裁剪(原生位图优先显示; alpha 暂时拉满后立即还原) ----
            if (profile.useSnapshot && tabView != null) {
                val sv = tabView!!
                sv.postDelayed({
                    runCatching {
                        val slots = BarState.tabs.size.coerceAtLeast(1)
                        val saved = sv.alpha
                        sv.alpha = 1f
                        Snapshot.harvest(sv, slots)
                        sv.alpha = saved
                        log("snapshot slots=$slots")
                    }.onFailure { log("snapshot-failed") }
                }, 400)
            }
        } catch (t: Throwable) {
            runCatching {
                toTransparent.forEach { it.alpha = 1f }
                composeView?.let { container.removeView(it) }
                container.clipChildren = true
                container.clipToPadding = true
            }
            runCatching {
                GlassLoader.bridge.log(t)
                GlassLoader.bridge.log("install FAILED -> rolled back")
            }
        }
    }
}
