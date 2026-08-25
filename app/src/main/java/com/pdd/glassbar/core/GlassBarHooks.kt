package com.pdd.glassbar.core

import android.view.View
import android.view.ViewGroup
import com.pdd.glassbar.loader.HookBridge
import com.pdd.glassbar.ui.BarState
import com.pdd.glassbar.ui.GlassOverlay
import com.pdd.glassbar.ui.utils.findActivity
import java.lang.reflect.Proxy

/**
 * 固定四页模式 Hook 安装器。
 * H1 注入 / H2 过滤+回传 setTabs / H3 监听器代理 / 压制守卫组。
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

        // ---- H1: 容器就绪后注入(延迟到本轮消息循环之后) ----
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
        }.onFailure { b.log(it) }

        // ---- H2: setTabs 过滤 —— 精确锁定泛型含 HomeBottomTab 的重载 ----
        runCatching {
            val m = tabCls.methods.filter { it.name == "setTabs" }.firstOrNull { mi ->
                runCatching { mi.genericParameterTypes.joinToString().contains("HomeBottomTab") }.getOrDefault(false)
            } ?: tabCls.methods.first { it.name == "setTabs" }
            b.hookBefore(m) { f ->
                @Suppress("UNCHECKED_CAST")
                val filtered = BarState.filterAndSync(
                    f.args.getOrNull(0) as? List<Any?>,
                    f.member?.javaClass?.classLoader ?: b.hostClassLoader,
                )
                f.args[0] = filtered
                // 原生栏同步时顺带压制
                runCatching {
                    val tv = f.thisObject as? View ?: return@hookBefore
                    val p = tv.parent as? ViewGroup ?: return@hookBefore
                    if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null)
                        com.pdd.glassbar.ui.BarState.vanish(tv)
                }
            }
        }.onFailure { b.log(it) }

        // ---- H3: 包裹原生 g_1 监听器 ----
        runCatching {
            val g1 = cl.loadClass("$TAB_VIEW\$g_1")
            val m = tabCls.methods.first { it.name == "setOnTabChangeListener" }
            b.hookAfter(m) { f ->
                val original = f.args.getOrNull(0) ?: return@hookAfter
                if (Proxy.isProxyClass(original.javaClass)) return@hookAfter
                BarState.attachListener(original, g1)
                f.args[0] = Proxy.newProxyInstance(g1.classLoader, arrayOf(g1)) { _, method, args ->
                    when (method.name) {
                        "onTabSelected", "onTabDoubleTap" -> {
                            (args?.getOrNull(0) as? Int)?.let(BarState::select); null
                        }
                        "onTabTouched" -> null
                        else -> runCatching { method.invoke(original, *(args ?: emptyArray())) }.getOrNull()
                    }
                }
            }
        }.onFailure { b.log(it) }

        // ---- 绘制期压制 + 红点刷新锚点 ----
        runCatching {
            val m = tabCls.declaredMethods.first { it.name == "drawCanvas" }
            m.isAccessible = true
            b.hookAfter(m) { f ->
                BarState.refreshDots()
                runCatching {
                    val v = f.thisObject as? View ?: return@hookAfter
                    val p = v.parent as? ViewGroup ?: return@hookAfter
                    if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null &&
                        (v.visibility != View.GONE || v.alpha != 0f)
                    ) com.pdd.glassbar.ui.BarState.vanish(v)
                }
            }
        }.onFailure { b.log(it) }

        // ---- 新实例挂载守卫 ----
        runCatching {
            val m = tabCls.methods.first { it.name == "onAttachedToWindow" }
            b.hookAfter(m) { f ->
                val v = f.thisObject as? View ?: return@hookAfter
                val p = v.parent as? ViewGroup ?: return@hookAfter
                if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null)
                    com.pdd.glassbar.ui.BarState.vanish(v)
                }
            }.onFailure { b.log(it) }

        // ---- 骨架屏压制 ----
        runCatching {
            val phCls = cl.loadClass("$TAB_VIEW_PKG.PddTabPlaceholderLayout")
            val dc = phCls.declaredMethods.firstOrNull { it.name == "drawCanvas" }
                ?: phCls.methods.firstOrNull { it.name == "drawCanvas" }
            dc?.let {
                it.isAccessible = true
                b.hookAfter(it) { f ->
                    val v = f.thisObject as? View ?: return@hookAfter
                    val p = v.parent as? ViewGroup ?: return@hookAfter
                    if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null)
                        com.pdd.glassbar.ui.BarState.vanish(v)
                }
            }
            phCls.methods.firstOrNull { it.name == "onAttachedToWindow" }?.let {
                b.hookAfter(it) { f ->
                    val v = f.thisObject as? View ?: return@hookAfter
                    val p = v.parent as? ViewGroup ?: return@hookAfter
                    if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null)
                        com.pdd.glassbar.ui.BarState.vanish(v)
                }
            }
        }.onFailure { b.log(it) }
    }
}
