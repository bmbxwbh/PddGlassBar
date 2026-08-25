package com.pdd.glassbar.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.compose.ui.platform.ComposeView
import com.pdd.glassbar.ui.utils.LifecycleOwnerProvider
import com.pdd.glassbar.ui.utils.setLifecycleOwner

/** H1: 把玻璃底栏 ComposeView 挂到 MainFrameContainerView 底部, 并隐藏原生三件套。 */
object GlassOverlay {

    private const val TAG = "pdd_glass_bar_compose"

    fun install(container: ViewGroup, tabCls: Class<*>, activity: Activity?) {
        if (container.findViewWithTag<View>(TAG) != null) return

        var content: ViewGroup? = null
        val toHide = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            val c = container.getChildAt(i)
            when {
                c is FrameLayout && content == null ->
                    content = c                                   // 内容区 (id 0x7f090266)

                c.javaClass.name.endsWith("PddTabPlaceholderLayout") ->
                    toHide += c                                   // 骨架屏

                tabCls.isInstance(c) ->
                    toHide += c                                   // 原生 PddTabView

                c.javaClass == View::class.java ->
                    toHide += c                                   // 1px 分割线
            }
        }
        val sourceView = content ?: return

        // 与 WeKit 相同: 保留原生视图对象但 GONE(其监听器仍被我们引用)
        toHide.forEach { it.visibility = View.GONE }

        // 药丸按压放大会溢出边界, 关闭裁剪
        container.clipChildren = false
        container.clipToPadding = false
        sourceView.clipChildren = false

        val owner = activity?.let { LifecycleOwnerProvider.getOrCreate(it) }
            ?: LifecycleOwnerProvider.lifecycleOwner

        val composeView = ComposeView(container.context).apply {
            tag = TAG
            clipChildren = false
            clipToPadding = false
            setLifecycleOwner(owner)
            setContent { GlassBarHost(sourceView = sourceView) }
        }

        val lp = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
        container.addView(composeView, lp)
    }
}
