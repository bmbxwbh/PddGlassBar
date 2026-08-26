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
import com.pdd.glassbar.ui.utils.setLifecycleOwner
import java.io.File

object GlassOverlay {

    const val TAG = "pdd_glass_bar_compose"
    private val KILL_SWITCH_CANDIDATES = listOf(
        File("/sdcard/Android/data/com.xunmeng.pinduoduo/files/pddglassbar.disable"),
        File("/data/data/com.xunmeng.pinduoduo/files/pddglassbar.disable"),
        File("/sdcard/pddglassbar.disable"),
    )

    fun install(container: ViewGroup, profile: AppProfile, activity: Activity?) {
        val ctx = activity ?: container.context
        if (!checkFlag()) return
        if (container.findViewWithTag<View>(TAG) != null) return
        runCatching { ModuleFileLog.init(ctx) }
        runCatching { CrashCapture.install(ctx) }

        var content: ViewGroup? = null
        var tabView: View? = null
        val toTransparent = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            val c = container.getChildAt(i)
            when {
                c is ViewGroup && c !is ComposeView && content == null &&
                    c.javaClass.name == "android.widget.FrameLayout" -> content = c
                profile.placeholderSuffix != null &&
                    c.javaClass.name.endsWith(profile.placeholderSuffix!!) -> toTransparent += c
                profile.tabViewClass != null && c.javaClass.name == profile.tabViewClass -> {
                    tabView = c; toTransparent += c
                }
                c.javaClass == View::class.java -> toTransparent += c
            }
        }
        log("classified hide=${toTransparent.size} content=${content != null}")
        activate(container, profile, activity, content, tabView, toTransparent)
    }

    fun installByScan(activity: Activity, profile: AppProfile) {
        if (!checkFlag()) return
        runCatching { ModuleFileLog.init(activity) }
        runCatching { CrashCapture.install(activity) }
        val root = activity.window?.decorView ?: return

        val tabViews = mutableListOf<View>()
        walk(root, 0) { v, _ ->
            if (profile.tabMatchMode == com.pdd.glassbar.core.TabMatchMode.SIMPLE_NAME_SUFFIXES &&
                profile.tabViewSimpleNameSuffixes.any { v.javaClass.simpleName.endsWith(it) }
            ) tabViews += v
        }
        log("scan tabs=${tabViews.size}")
        if (tabViews.isEmpty()) return
        tabViews.sortBy { it.x + it.left.toFloat() }

        activate(activity.window?.decorView as? ViewGroup ?: return, profile, activity,
                 sourceView = null, tabView = tabViews.first(),
                 toTransparent = tabViews.toList())
    }

    private fun checkFlag(): Boolean {
        val f = KILL_SWITCH_CANDIDATES.firstOrNull { it.exists() } ?: return true
        val v = GlassFlags.load(f)
        return v == "on" || v == "noglass" || v == "noicons"
    }

    private fun walk(v: View, depth: Int, visit: (View, Int) -> Unit) {
        visit(v, depth)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
    }

    private fun activate(
        attachTo: ViewGroup,
        profile: AppProfile,
        activity: Activity?,
        sourceView: ViewGroup?,
        tabView: View?,
        toTransparent: List<View>,
    ) {
        var composeView: ComposeView? = null
        try {
            val ourOwner = activity?.let { LifecycleOwnerProvider.getOrCreate(it) }
                ?: LifecycleOwnerProvider.lifecycleOwner
            val decor: View = activity?.window?.decorView ?: attachTo.rootView
            decor.setViewTreeLifecycleOwner(ourOwner)
            if (ourOwner is ViewModelStoreOwner) decor.setViewTreeViewModelStoreOwner(ourOwner)
            if (ourOwner is SavedStateRegistryOwner) decor.setViewTreeSavedStateRegistryOwner(ourOwner)

            composeView = ComposeView(attachTo.context).apply {
                tag = TAG
                clipChildren = false
                clipToPadding = false
                setLifecycleOwner(ourOwner)
                setContent { GlassBarHost(sourceView = sourceView) }
            }
            log("compose-created")

            toTransparent.forEach { it.alpha = 0f }   // 纯透明: 保留布局供合成触摸
            attachTo.clipChildren = false
            attachTo.clipToPadding = false
            sourceView?.clipChildren = false

            val lp = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
            attachTo.addView(composeView, lp)
            log("attached")

            if (profile.useSnapshot && tabView != null) {
                tabView.postDelayed({
                    runCatching {
                        val slots = BarState.tabs.size.coerceAtLeast(1)
                        val saved = tabView.alpha
                        tabView.alpha = 1f
                        Snapshot.harvest(tabView, slots)
                        tabView.alpha = saved
                        log("snapshot slots=$slots")
                    }
                }, 400)
            }
        } catch (t: Throwable) {
            runCatching {
                toTransparent.forEach { it.alpha = 1f }
                composeView?.let { attachTo.removeView(it) }
                attachTo.clipChildren = true
                attachTo.clipToPadding = true
            }
            runCatching {
                GlassLoader.bridge.log(t)
                GlassLoader.bridge.log("install FAILED -> rolled back")
            }
        }
    }

    private fun log(stage: String) {
        runCatching { GlassLoader.bridge.log("install/$stage") }
    }
}
