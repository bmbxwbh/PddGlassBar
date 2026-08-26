package com.pdd.glassbar.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pdd.glassbar.core.AppProfile
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
        if (!checkFlag()) return
        ModuleFileLog.init(activity)
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
        bar.alpha = 0f

        val owner = com.pdd.glassbar.ui.utils.LifecycleOwnerProvider.getOrCreate(activity)
        val cv = ComposeView(activity).apply {
            tag = TAG; clipChildren = false; clipToPadding = false
            setLifecycleOwner(owner)
            setContent { BiliTabContent() }
        }
        log("compose-created")

        val lp = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
        (root as ViewGroup).addView(cv, lp)
        log("attached")
    }

    @Composable
    private fun BiliTabContent() {
        val dark = androidx.compose.foundation.isSystemInDarkTheme()
        val bg = if (dark) Color(0xF01A1A1A) else Color(0xF0F5F5F5)
        val txtColor = if (dark) Color(0xFFEDEDED) else Color.Black

        Box(Modifier.fillMaxWidth().height(65.dp).background(bg)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("首页", "动态", "发布", "会员购", "我的").forEach { title ->
                    Text(title, fontSize = 12.sp, color = txtColor)
                }
            }
        }
    }

    private fun checkFlag(): Boolean {
        val f = KILL_SWITCH_CANDIDATES.firstOrNull { it.exists() } ?: return true
        val v = GlassFlags.load(f)
        return v == "on" || v == "noglass" || v == "noicons"
    }

    private fun findBottomStrip(v: View, depth: Int, visit: (View) -> Unit) {
        if (depth > 14 || !v.isShown) return
        visit(v)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findBottomStrip(v.getChildAt(i), depth + 1, visit)
            }
        }
    }
}
