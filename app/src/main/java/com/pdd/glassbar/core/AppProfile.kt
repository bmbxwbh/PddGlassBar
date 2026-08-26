package com.pdd.glassbar.core

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView

/**
 * 单个 App 的锚点与行为描述。
 * 引擎(GlassBarHooks/GlassOverlay)只读本描述, 不含任何 App 特例。
 */
class AppProfile(

    val packageName: String,
    val label: String,

    /** 底栏宿主容器(initView 所在或等价锚点所在类) */
    val containerClass: String,

    /** 底栏视图类(null = 由 classify 兜底: 容器内最后一个非内容 ViewGroup) */
    val tabViewClass: String? = null,

    /** 骨架屏类后缀(可空) */
    val placeholderSuffix: String? = null,

    /**
     * 镜像入口方法名(挂在底栏类上, hookAfter 只读观察)。
     * null = 无此入口(固定页模式, 直接用 fixedTabs 渲染)。
     */
    val mirrorMethodName: String? = "setTabs",

    /** 显示层过滤顺序(仅展示这些分组; null = 全部镜像显示) */
    val displayOrder: List<Int>? = null,

    /** 分组 → 固定标题 */
    val titleByGroup: Map<Int, String> = emptyMap(),

    /** 条目 group 字段/方法读取器(镜像模式下用) */
    val groupReader: ((Any) -> Int?)? = null,

    /** 条目标题读取器(镜像模式下优先, 失败回退 titleByGroup/页面N) */
    val titleReader: ((Any) -> String?)? = null,

    /** 是否在安装后对底栏做快照裁剪(原生位图优先显示) */
    val useSnapshot: Boolean = false,

    /** 固定页文案(无镜像入口时直接渲染) */
    val fixedTabsInOrder: List<String>? = null,

    /** 实验性配置: 默认跳过安装(锚点未经验证), 打开调试开关后才生效 */
    val experimental: Boolean = false,
) {
    fun classify(container: ViewGroup): Triple<ViewGroup?, View?, List<View>> {
        var content: ViewGroup? = null
        var tab: View? = null
        val extras = mutableListOf<View>()
        tabViewClass?.let { tcls ->
            for (i in 0 until container.childCount) {
                val c = container.getChildAt(i)
                if (c.javaClass.name == tcls) { tab = c; break }
            }
        }
        for (i in 0 until container.childCount) {
            val c = container.getChildAt(i)
            when {
                c === tab -> Unit
                c is ViewGroup && c !is ComposeView && content == null &&
                    c.javaClass.name == "android.widget.FrameLayout" -> content = c
                placeholderSuffix != null && c.javaClass.name.endsWith(placeholderSuffix) -> extras += c
                c.javaClass == View::class.java -> extras += c
            }
        }
        return Triple(content, tab, extras)
    }

}
