package com.pdd.glassbar.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pdd.glassbar.core.AppProfile
import com.pdd.glassbar.hooks.BiliGlassContent
import com.pdd.glassbar.loader.GlassLoader
import java.io.File

object GlassOverlay {

    const val TAG = "pdd_glass_bar_compose"

    private val KILL_SWITCH_CANDIDATES = listOf(
        File("/sdcard/Android/data/com.xunmeng.pinduoduo/files/pddglassbar.disable"),
        File("/data/data/com.xunmeng.pinduoduo/files/pddglassbar.disable"),
        File("/sdcard/pddglassbar.disable")
    )

    fun install(container: ViewGroup, profile: AppProfile, activity: Activity?) {
        if (!checkFlag()) return
        if (container.findViewWithTag<View>(TAG) != null) return
        ModuleFileLog.init(container.context)
        runCatching { CrashCapture.install(activity ?: container.context) }

        var content: ViewGroup? = null
        var tabView: View? = null
        val targets = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            val c = container.getChildAt(i)
            when {
                c is ViewGroup && c !is ComposeView && content == null &&
                    c.javaClass.name == "android.widget.FrameLayout" -> content = c
                profile.placeholderSuffix != null &&
                    c.javaClass.name.endsWith(profile.placeholderSuffix!!) -> targets += c
                profile.tabViewClass != null && c.javaClass.name == profile.tabViewClass -> {
                    tabView = c; targets += c
                }
                c is View && c.javaClass == View::class.java -> targets += c
            }
        }
        val sv = content ?: return
        log("classified hide=${targets.size}")
        doActivate(container, profile, activity, sv, tabView, targets)
    }

    fun installByScan(activity: Activity) {
        if (!checkFlag()) return
        ModuleFileLog.init(activity)
        runCatching { CrashCapture.install(activity) }
        val root = activity.window?.decorView as? ViewGroup ?: return

        var barView: ViewGroup? = null
        findBottomStrip(root, 0) { v ->
            if (barView == null && v is ViewGroup && v.visibility == View.VISIBLE &&
                v.width >= root.width * 9 / 10 &&
                v.height > 0 && v.height < root.height / 8
            ) barView = v
        }
        val bar = barView ?: run { log("scan-no-bar"); return }
        log("scan-found cls=" + bar.javaClass.simpleName + " h=" + bar.height)

        // alpha=0 隐藏原栏(保留布局供合成触摸)
        bar.alpha = 0f

        // 创建玻璃栏 ComposeView(mode=None 因为无内容采样源)
        val owner = com.pdd.glassbar.ui.utils.LifecycleOwnerProvider.getOrCreate(activity)
        val cv = androidx.compose.ui.platform.ComposeView(activity).apply {
            tag = TAG; clipChildren = false; clipToPadding = false
            setLifecycleOwner(owner)
            setContent { BiliGlassContent() }
        }
        log("compose-created")

        val lp = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
        root.addView(cv, lp)
        log("attached")
    }

    private fun doActivate(
        attachTo: ViewGroup,
        profile: AppProfile,
        activity: Activity?,
        sourceView: ViewGroup?,
        tabView: View?,
        toTransparent: List<View>
    ) {
        var composeView: ComposeView? = null
        try {
            val ourOwner = activity?.let { LifecycleOwnerProvider.getOrCreate(it) }
                ?: LifecycleOwnerProvider.lifecycleOwner
            val decor = activity?.window?.decorView ?: attachTo.rootView
            decor.setViewTreeLifecycleOwner(ourOwner)
            (ourOwner as? ViewModelStoreOwner)?.let {
                decor.setViewTreeViewModelStoreOwner(it)
            }
            (ourOwner as? SavedStateRegistryOwner)?.let {
                decor.setViewTreeSavedStateRegistryOwner(it)
            }

            composeView = ComposeView(attachTo.context).apply {
                tag = TAG
                clipChildren = false
                clipToPadding = false
                setLifecycleOwner(ourOwner)
                setContent { GlassBarHost(sourceView = sourceView) }
            }
            log("compose-created")

            toTransparent.forEach { v ->
                v.alpha = 0f
                v.visibility = View.INVISIBLE
            }
            attachTo.clipChildren = false
            attachTo.clipToPadding = false
            sourceView?.clipChildren = false

            val lp = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
            attachTo.addView(composeView, lp)
            log("attached")
        } catch (t: Throwable) {
            runCatching {
                toTransparent.forEach { v ->
                    v.alpha = 1f; v.visibility = View.VISIBLE
                }
                composeView?.let { attachTo.removeView(it) }
                attachTo.clipChildren = true; attachTo.clipToPadding = true
            }
            log(t)
            log("install FAILED -> rolled back")
        }
    }

    private fun checkFlag(): Boolean {
        val f = KILL_SWITCH_CANDIDATES.firstOrNull { it.exists() } ?: return true
        val v = GlassFlags.load(f)
        return v == "on" || v == "noglass" || v == "noicons"
    }

    private fun walk(v: View, depth: Int, visit: (View, Int) -> Unit) {
        visit(v, depth)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1, visit)
        }
    }

    private fun log(msg: String) {
        runCatching { GlassLoader.bridge.log("install/" + msg) }
    }

    private fun log(t: Throwable) {
        runCatching { GlassLoader.bridge.log(t.stackTraceToString()) }
    }
}
