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
 * 钩子清单:
 *  1 initView 注入      2 setTabs 过滤回传     3 g_1 监听器代理
 *  4 drawCanvas 压制+红点    5 tab 挂载守卫        6/7 骨架屏压制
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

        fun ok() {
            hookIdx++; b.log("hook/$hookIdx ok")
        }
        fun fail(name: String, t: Throwable) {
            b.log("hook/$name FAILED"); b.log(t)
        }

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

        // ---- 2 setTabs 过滤回传 ----
        runCatching {
            val m = tabCls.methods.filter { it.name == "setTabs" }.firstOrNull { mi ->
                runCatching { mi.genericParameterTypes.joinToString().contains("HomeBottomTab") }
                    .getOrDefault(false)
            } ?: tabCls.methods.first { it.name == "setTabs" }
            b.hookBefore(m) { f ->
                @Suppress("UNCHECKED_CAST")
                val filtered = BarState.filterAndSync(
                    f.args.getOrNull(0) as? List<Any?>,
                    f.member?.javaClass?.classLoader ?: b.hostClassLoader,
                )
                f.args[0] = filtered
                runCatching {
                    val tv = f.thisObject as? View
                    val p = tv?.parent as? ViewGroup
                    if (p != null && p.findViewWithTag<View>(GlassOverlay.TAG) != null)
                        com.pdd.glassbar.ui.BarState.vanish(tv)
                }
            }
            ok()
        }.onFailure { fail("setTabs", it) }

        // ---- 3 包裹原生 g_1 监听器 ----
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
                        else -> runCatching { method.invoke(original, *(args ?: emptyArray())) }
                            .getOrNull()
                    }
                }
            }
            ok()
        }.onFailure { fail("g1-proxy", it) }

        // ---- 3b: dl1.p.Z 入口 —— 对 HomeTabList.bottom_tabs 原地过滤 ----
        // Fragment 层与子视图层都从该列表构建; 仅过滤 setTabs 副本会导致
        // 视图层4项 / 页面层5项错位(切"聊天"打开活动页)。原地过滤三层同源。
        runCatching {
            val pCls = cl.loadClass("dl1.p")
            val zM = pCls.declaredMethods.firstOrNull { mi ->
                mi.name == "Z" && mi.parameterTypes.size == 3 &&
                    mi.parameterTypes[1].name.endsWith(".HomeTabList")
            } ?: throw NoSuchMethodException("dl1.p.Z(HomeTabList)")
            zM.isAccessible = true
            val hlField = cl.loadClass(
                "com.xunmeng.pinduoduo.home.base.entity.HomeTabList"
            ).getField("bottom_tabs")
            val entG = cl.loadClass(
                "com.xunmeng.pinduoduo.home.base.entity.HomeBottomTab"
            ).getField("group")
            val order = com.pdd.glassbar.ui.BarState.GROUP_ORDER
            b.hookBefore(zM) { f ->
                val hl = f.args.getOrNull(1) ?: return@hookBefore
                val list = hlField.get(hl) as? MutableList<Any> ?: return@hookBefore
                val kept = list.mapNotNull { t ->
                    val g = runCatching { entG.get(t) as? Int }.getOrNull()
                    if (g != null && g in order) g to t else null
                }.sortedBy { order.indexOf(it.first) }.map { it.second }
                if (kept.size != list.size) {
                    list.clear()
                    list.addAll(kept)
                    b.log("zfilter " + list.size + "/" + kept.size + " -> applied")
                }
                BarState.filterAndSync(kept, b.hostClassLoader)
            }
            ok()
        }.onFailure { fail("zfilter", it) }

        // ---- 4 drawCanvas: 红点刷新 + 绘制期压制 ----
        runCatching {
            val m = tabCls.declaredMethods.first { it.name == "drawCanvas" }
            m.isAccessible = true
            b.hookAfter(m) { f ->
                BarState.refreshDots()
                runCatching {
                    val v = f.thisObject as? View ?: return@hookAfter
                    val p = v.parent as? ViewGroup ?: return@hookAfter
                    if (p.findViewWithTag<View>(GlassOverlay.TAG) != null &&
                        (v.visibility != View.GONE || v.alpha != 0f)
                    ) com.pdd.glassbar.ui.BarState.vanish(v)
                }
            }
            ok()
        }.onFailure { fail("drawCanvas", it) }

        // ---- 5 PddTabView 挂载守卫(未覆写则跳过) ----
        runCatching {
            tabCls.declaredMethods.firstOrNull { it.name == "onAttachedToWindow" }?.let { m ->
                m.isAccessible = true
                b.hookAfter(m) { f ->
                    val v = f.thisObject as? View ?: return@hookAfter
                    val p = v.parent as? ViewGroup ?: return@hookAfter
                    if (p.findViewWithTag<View>(GlassOverlay.TAG) != null) com.pdd.glassbar.ui.BarState.vanish(v)
                }
            }
            ok()
        }.onFailure { fail("tab-attach", it) }

        // ---- 6/7 骨架屏压制(drawCanvas + 挂载) ----
        runCatching {
            val phCls = cl.loadClass("$TAB_VIEW_PKG.PddTabPlaceholderLayout")
            phCls.declaredMethods.firstOrNull { it.name == "drawCanvas" }?.let { m ->
                m.isAccessible = true
                b.hookAfter(m) { f ->
                    val v = f.thisObject as? View ?: return@hookAfter
                    val p = v.parent as? ViewGroup ?: return@hookAfter
                    if (p.findViewWithTag<View>(GlassOverlay.TAG) != null) com.pdd.glassbar.ui.BarState.vanish(v)
                }
            }
            phCls.declaredMethods.firstOrNull { it.name == "onAttachedToWindow" }?.let { m ->
                m.isAccessible = true
                b.hookAfter(m) { f ->
                    val v = f.thisObject as? View ?: return@hookAfter
                    val p = v.parent as? ViewGroup ?: return@hookAfter
                    if (p.findViewWithTag<View>(GlassOverlay.TAG) != null) com.pdd.glassbar.ui.BarState.vanish(v)
                }
            }
            ok()
        }.onFailure { fail("placeholder", it) }
    }
}
