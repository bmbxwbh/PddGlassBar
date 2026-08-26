package com.pdd.glassbar.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.compose.ui.platform.ComposeView
import com.pdd.glassbar.core.AppProfile
import com.pdd.glassbar.loader.GlassLoader
import com.pdd.glassbar.hooks.BiliHooks
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
        doActivate(attachTo = container, sourceView = sv, tabView = tabView,
                   extras = targets, activity = activity)
    }

    fun installByScan(activity: Activity, profile: AppProfile) {
        if (!checkFlag()) { log("scan kill-switch/skip"); return }
        ModuleFileLog.init(activity)
        val root = activity.window?.decorView as? ViewGroup ?: return
        log("scan screen=${root.width}x${root.height}")

        // 位置启发式: 找屏幕底部区域的全宽 ViewGroup 作为底栏
        var barView: ViewGroup? = null
        findBottomStrip(root, 0, root.height) { v ->
            if (barView == null && v is ViewGroup &&
                v.visibility == View.VISIBLE &&
                v.width >= root.width * 9 / 10 &&
                v.height > 0 && v.height < root.height / 8
            ) barView = v
        }
        val bar = barView ?: run { log("scan-no-bar"); return }
        log("scan-found cls=" + bar.javaClass.simpleName + " h=" + bar.height)

        // alpha=0 隐藏原栏(保留布局供合成触摸)
        bar.alpha = 0f

        // 挂载玻璃栏到 DecorView 底部
        val cv = ComposeView(activity).apply {
            tag = TAG
            clipChildren = false; clipToPadding = false
            setContent { com.pdd.glassbar.hooks.BiliGlassContent() }
        }
        log("compose-created")

        val lp = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
        root.addView(cv, lp)
        log("attached")
    }

    private fun checkFlag(): Boolean {
        val f = KILL_SWITCH_CANDIDATES.firstOrNull { it.exists() } ?: return true
        val v = GlassFlags.load(f)
        return v == "on" || v == "noglass" || v == "noicons"
    }

    private fun findBottomStrip(v: View, depth: Int, scrH: Int, visit: (View) -> Unit) {
        if (depth > 14 || !v.isShown) return
        visit(v)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findBottomStrip(v.getChildAt(i), depth + 1, scrH, visit)
            }
        }
    }

    private fun log(msg: String) {
        runCatching { GlassLoader.bridge.log("install/$msg") }
    }
}
