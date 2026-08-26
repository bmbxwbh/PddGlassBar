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
        File("/sdcard/pddglassbar.disable"), // 兼容旧文档
    )

    fun install(container: ViewGroup, profile: AppProfile, activity: Activity?) {
        val ctx = activity ?: container.context
        val flag0 = KILL_SWITCH_CANDIDATES.firstOrNull { it.exists() }?.let { GlassFlags.load(it) } ?: "on"
        if (flag0 != "on" && flag0 != "noglass" && flag0 != "noicons") { log("kill-switch($flag0)/skip"); return }
        runCatching { ModuleFileLog.init(ctx) }
        runCatching { CrashCapture.install(ctx) }

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
        val content = sourceView
        log("classified hide=${toTransparent.size} content=${content != null}")
        activate(ctx, container, profile, activity, content, tabView, toTransparent,
                 glassAllowed = true, independentTabViews = null)
    }

    fun installByScan(activity: Activity, profile: AppProfile) {
        val flagS = KILL_SWITCH_CANDIDATES.firstOrNull { it.exists() }?.let { GlassFlags.load(it) } ?: "on"
        if (flagS != "on" && flagS != "noglass" && flagS != "noicons") return
        val root = activity.window?.decorView ?: return
        val tabViews = mutableListOf<View>()
        val extras = mutableListOf<View>()
        walk(root, 0) { v, _ ->
            when {
                profile.tabMatchMode == com.pdd.glassbar.core.TabMatchMode.SIMPLE_NAME_SUFFIXES &&
                    profile.tabViewSimpleNameSuffixes.any {
                        v.javaClass.simpleName.endsWith(it)
                    } -> tabViews += v
                profile.placeholderSuffix != null &&
                    v.javaClass.name.endsWith(profile.placeholderSuffix!!) -> extras += v
            }
        }
        log("scan tabs=${tabViews.size} ph=${extras.size}")
        if (tabViews.isEmpty()) return
        tabViews.sortBy { it.x + it.left.toFloat() }
        activate(activity, root as? ViewGroup ?: return, profile, activity,
                 sourceView = null, tabView = tabViews.firstOrNull(),
                 extras = extras, glassAllowed = true,
                 independentTabViews = tabViews.toList())
    }

    private fun activate(
        activity: Activity?,
        attachTo: ViewGroup,
        profile: AppProfile,
        activityAgain: Activity?,
        sourceView: ViewGroup?,
        tabView: View?,
        extras: List<View>,
        glassAllowed: Boolean,
        independentTabViews: List<View>?,
    ) {
        val ctx = activity ?: attachTo.context
        var composeView: ComposeView? = null
        try {
            val ourOwner = activity?.let { LifecycleOwnerProvider.getOrCreate(it) }
                ?: LifecycleOwnerProvider.lifecycleOwner
            val decor: View = activity2Decor(activity) ?: attachTo.rootView
            decor.setViewTreeLifecycleOwner(ourOwner)
            if (ourOwner is ViewModelStoreOwner) decor.setViewTreeViewModelStoreOwner(ourOwner)
            if (ourOwner is SavedStateRegistryOwner) decor.setViewTreeSavedStateRegistryOwner(ourOwner)

            composeView = ComposeView(ctx).apply {
                tag = TAG
                clipChildren = false
                clipToPadding = false
                setLifecycleOwner(ourOwner)
                setContent { GlassBarHost(sourceView = sourceView) }
            }
            log("compose-created")

            extras.forEach { BarState.registerHidden(it) }
            tabView?.let { BarState.registerHidden(it) }
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
                extras.forEach { it.alpha = 1f; it.visibility = View.VISIBLE }
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

    private fun activity2Decor(activity: Activity?): View =
        activity?.window?.decorView ?: error("no decor")

    private inline fun walk(root: View, depth: Int, crossinline visit: (View, Int) -> Unit) {
        visit(root, depth)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) walk(root.getChildAt(i), depth + 1)
        }
    }

    private fun log(stage: String) {
        runCatching { GlassLoader.bridge.log("install/$stage") }
    }
}
