package com.pdd.glassbar.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pdd.glassbar.loader.PddLoader
import com.pdd.glassbar.ui.utils.LifecycleOwnerProvider
import com.pdd.glassbar.ui.utils.setLifecycleOwner
import java.io.File

/**
 * H1: 玻璃栏安装器。
 * 安全承诺: 任何一步失败都会恢复原底栏可见性并移除半成品 overlay。
 * 停机开关: /sdcard/pddglassbar.disable(内容 noglass/noicons 为分级降密)。
 */
object GlassOverlay {

    const val TAG = "pdd_glass_bar_compose"
    private val KILL_SWITCH = File("/sdcard/pddglassbar.disable")

    // ---- ViewTree owner 读取: 运行时反射解析库内资源 id ----
    // (非传递 R 类下无法直接引用 androidx.*.R; 反射取到的最终值与库写入方一致)
    private val KEY_LIFECYCLE by lazy { idOf("androidx.lifecycle", "view_tree_lifecycle_owner") }
    private val KEY_VM by lazy { idOf("androidx.lifecycle", "view_tree_view_model_store_owner") }
    private val KEY_SAVED_STATE by lazy { idOf("androidx.savedstate", "view_tree_saved_state_registry_owner") }

    private fun idOf(pkg: String, name: String): Int = runCatching {
        val fq = pkg + ".R$" + name
        Class.forName(fq).fields.first { it.name == name }.getInt(null)
    }.getOrDefault(-1)

    private fun View.tagLifecycleOwner(): LifecycleOwner? =
        getTag(KEY_LIFECYCLE) as? LifecycleOwner

    private fun View.tagVmOwner(): ViewModelStoreOwner? =
        getTag(KEY_VM) as? ViewModelStoreOwner

    private fun View.tagSavedStateOwner(): SavedStateRegistryOwner? =
        getTag(KEY_SAVED_STATE) as? SavedStateRegistryOwner

    fun install(container: ViewGroup, tabCls: Class<*>, activity: Activity?) {
        val log = { stage: String -> runCatching { PddLoader.bridge.log("install/$stage") } }
        val flag = GlassFlags.load(KILL_SWITCH)
        if (flag != "on" && flag != "noglass" && flag != "noicons") { log("kill-switch($flag)/skip"); return }
        if (container.findViewWithTag<View>(TAG) != null) return
        runCatching { CrashCapture.install(container.context) }

        // ---- 阶段 0: 子视图分类(只读) ----
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

        var composeView: ComposeView? = null
        try {
            // ---- 阶段 1: owner 解析 ----
            // Recomposer 从 rootView(DecorView) 解析 ViewTreeLifecycleOwner。
            // PDD 首页基类链(BaseActivity→BaseFragmentActivity)为自研框架,
            // Decor 上没有宿主 owner —— 此时才写入我们的桥接; 有则复用宿主的。
            val ourOwner = activity?.let { LifecycleOwnerProvider.getOrCreate(it) }
                ?: LifecycleOwnerProvider.lifecycleOwner
            var effOwner: LifecycleOwner = ourOwner
            runCatching {
                val decor: View = activity?.window?.decorView ?: container.rootView
                val existing = decor.tagLifecycleOwner()
                if (existing != null) {
                    effOwner = existing
                    if (decor.tagVmOwner() == null && ourOwner is ViewModelStoreOwner)
                        decor.setViewTreeViewModelStoreOwner(ourOwner)
                    if (decor.tagSavedStateOwner() == null && ourOwner is SavedStateRegistryOwner)
                        decor.setViewTreeSavedStateRegistryOwner(ourOwner)
                    log("decor-owner-reuse-host")
                } else {
                    decor.setLifecycleOwner(ourOwner)
                    if (ourOwner is ViewModelStoreOwner) decor.setViewTreeViewModelStoreOwner(ourOwner)
                    if (ourOwner is SavedStateRegistryOwner) decor.setViewTreeSavedStateRegistryOwner(ourOwner)
                    log("decor-owner-set")
                }
            }.onFailure { log("decor-owner-failed") }
            log("lifecycle-ok")

            // ---- 阶段 2: 创建 ComposeView ----
            composeView = ComposeView(container.context).apply {
                tag = TAG
                clipChildren = false
                clipToPadding = false
                setLifecycleOwner(effOwner)
                setContent { GlassBarHost(sourceView = content) }
            }
            log("compose-created")

            // ---- 阶段 3: 收割原生图标(必须在隐藏前, 此时 Drawable 已就位) ----
            runCatching {
                var n = 0
                originals.filterIsInstance<ViewGroup>().forEach { sv ->
                    collectImageViews(sv).forEach { iv ->
                        val d = iv.drawable ?: return@forEach
                        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 64
                        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 64
                        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        val c = android.graphics.Canvas(bmp)
                        d.setBounds(0, 0, w, h)
                        d.draw(c)
                        BarState.putNativeIcon(n++, bmp.asImageBitmap())
                    }
                }
                log("harvested=$n")
            }.onFailure { log("harvest-failed") }

            // ---- 阶段 4: 隐藏原生三件套 + 关裁剪 + 挂载 ----
            originals.forEach {
                it.visibility = View.GONE
                BarState.registerHidden(it)
            }
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

private fun collectImageViews(root: View, out: MutableList<android.widget.ImageView>) {
    when (root) {
        is android.widget.ImageView -> out += root
        is ViewGroup -> for (i in 0 until root.childCount) collectImageViews(root.getChildAt(i), out)
    }
}

private fun collectImageViews(root: ViewGroup): List<android.widget.ImageView> =
    buildList { collectImageViews(root as View, this) }
