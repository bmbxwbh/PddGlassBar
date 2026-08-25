package com.pdd.glassbar.core

import android.view.ViewGroup
import com.pdd.glassbar.loader.HookBridge
import com.pdd.glassbar.ui.BarState
import com.pdd.glassbar.ui.GlassOverlay
import com.pdd.glassbar.ui.utils.findActivity

/**
 * 纯镜像架构 Hook 安装器(只读, 不改 PDD 任何行为):
 *  1 initView 注入 overlay
 *  2 setTabs 只读镜像(不再修改参数!)
 *  3 g_1 监听器绑定控制器(仅读取 i 同步选中态)
 *  4 骨架屏挂载透明守卫
 */
object GlassBarHooks {

    private const val TAB_VIEW =
        "com.xunmeng.pinduoduo.ui_home_activity.widget.tab.PddTabView"
    private const val TAB_VIEW_PKG = "com.xunmeng.pinduoduo.ui_home_activity.widget.tab"
    private const val CONTAINER =
        "com.xunmeng.pinduoduo.ui_home_activity.widget.MainFrameContainerView"

    fun install(b: HookBridge) {
        val cl = b.hostClassLoader
        val tabCls = cl.loadClass(TAB_VIEW)
        val containerCls = cl.loadClass(CONTAINER)
        var hookIdx = 0

        fun ok() { hookIdx++; b.log("hook/$hookIdx ok") }
        fun fail(name: String, t: Throwable) { b.log("hook/$name FAILED"); b.log(t) }

        // ---- 1 initView 注入(延迟到本轮消息循环之后) ----
        runCatching {
            val m = containerCls.getDeclaredMethod("initView")
            m.isAccessible = true
            b.hookAfter(m) { f ->
                val container = f.thisObject as? ViewGroup ?: return@hookAfter
                container.post {
                    runCatching {
                        GlassOverlay.install(container, tabCls, container.context.findActivity())
                    }.onFailure { b.log(it) }
                }
            }
            ok()
        }.onFailure { fail("initView", it) }

        // ---- 2 setTabs 只读镜像 ----
        runCatching {
            val m = tabCls.methods.first { it.name == "setTabs" }
            b.hookAfter(m) { f ->
                @Suppress("UNCHECKED_CAST")
                BarState.mirrorFrom(f.args.getOrNull(0) as? List<Any?>)
            }
            ok()
        }.onFailure { fail("setTabs-mirror", it) }

        // ---- 3 绑定控制器(原生监听器即 dl1.p 控制器本身) ----
        runCatching {
            val g1 = cl.loadClass("$TAB_VIEW\$g_1")
            val m = tabCls.methods.first { it.name == "setOnTabChangeListener" }
            b.hookAfter(m) { f ->
                val original = f.args.getOrNull(0) ?: return@hookAfter
                if (java.lang.reflect.Proxy.isProxyClass(original.javaClass)) return@hookAfter
                BarState.bindController(original)
            }
            ok()
        }.onFailure { fail("controller-bind", it) }

        // ---- 4 骨架屏挂载透明守卫 ----
        runCatching {
            val phCls = cl.loadClass("$TAB_VIEW_PKG.PddTabPlaceholderLayout")
            phCls.declaredMethods.firstOrNull { it.name == "onAttachedToWindow" }?.let { m ->
                m.isAccessible = true
                b.hookAfter(m) { f ->
                    (f.thisObject as? android.view.View)?.let { BarState.registerHidden(it) }
                }
            }
            ok()
        }.onFailure { fail("placeholder", it) }
    }
}
