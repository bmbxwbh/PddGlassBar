package com.pdd.glassbar.core

import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.graphics.asImageBitmap
import android.view.ViewGroup
import com.pdd.glassbar.loader.HookBridge
import com.pdd.glassbar.ui.BarState
import com.pdd.glassbar.ui.GlassOverlay
import com.pdd.glassbar.ui.utils.findActivity
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * 拼多多 8.21.0 悬浮玻璃底栏 —— Hook 安装器。
 * 对应蓝图: H1 注入 / H2 setTabs 同步 / H3 监听器包裹 / H4 图标捕获 / 红点刷新锚点。
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

        // ---- H1: 注入玻璃栏 overlay(双锚点: initView + 构造器兜底) ----
        fun trigger(obj: Any?) {
            val container = obj as? ViewGroup ?: return
            // 延迟到本轮消息循环之后: 避开 PDD 构造/initView 期间的子视图索引与接线窗口
            runCatching {
                container.post {
                    runCatching {
                        GlassOverlay.install(container, tabCls, container.context.findActivity())
                    }.onFailure { b.log(it) }
                }
            }
        }
        runCatching {
            val m = containerCls.getDeclaredMethod("initView")
            m.isAccessible = true
            b.hookAfter(m) { f -> trigger(f.thisObject) }
        }.onFailure { b.log(it) }
        runCatching {
            containerCls.declaredConstructors.forEach { c ->
                runCatching {
                    c.isAccessible = true
                    b.hookAfter(c) { f -> trigger(f.thisObject) }
                }
            }
        }.onFailure { b.log(it) }

        // ---- H2: setTabs —— 服务端 tab 列表动态同步 ----
        runCatching {
            val m = tabCls.methods.first { it.name == "setTabs" }
            b.hookBefore(m) { f ->
                @Suppress("UNCHECKED_CAST")
                BarState.syncTabs(f.args.getOrNull(0) as? List<Any?>, f.member?.javaClass?.classLoader ?: b.hostClassLoader)
                // 皮肤刷新流程会重新 add 新的 PddTabView —— 每次同步时强制再隐藏一次
                runCatching {
                    val tv = f.thisObject as? android.view.View
                    val p = tv?.parent as? ViewGroup ?: return@hookBefore
                    if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null) {
                        tv.visibility = View.GONE
                        for (i in 0 until p.childCount) {
                            val c = p.getChildAt(i)
                            if (c.javaClass.name.endsWith("PddTabPlaceholderLayout")) c.visibility = View.GONE
                        }
                    }
                }
            }
        }.onFailure { b.log(it) }

        // ---- H3: 包裹 PddTabView$g_1 监听器(保留原生切页语义, 双击回调原生自带) ----
        runCatching {
            val g1 = cl.loadClass("$TAB_VIEW\$g_1")
            val m = tabCls.methods.first { it.name == "setOnTabChangeListener" }
            b.hookAfter(m) { f ->
                val original = f.args.getOrNull(0) ?: return@hookAfter
                if (Proxy.isProxyClass(original.javaClass)) return@hookAfter
                BarState.attachListener(original, g1)
                f.args[0] = Proxy.newProxyInstance(g1.classLoader, arrayOf(g1)) { _, method, args ->
                    when (method.name) {
                        "onTabSelected" -> {
                            (args?.getOrNull(0) as? Int)?.let(BarState::select); null
                        }
                        "onTabDoubleTap" -> {
                            (args?.getOrNull(0) as? Int)?.let(BarState::select); null
                        }
                        "onTabTouched" -> null
                        else -> runCatching { method.invoke(original, *(args ?: emptyArray())) }
                            .getOrNull()
                    }
                }
            }
        }.onFailure { b.log(it) }

        // ---- H4: 图标捕获 —— PddTabView.j(PddTabView, String url, Bitmap, ImageView) 加载完成回调 ----
        runCatching {
            val m = tabCls.declaredMethods.first {
                it.name == "j" && Modifier.isStatic(it.modifiers) && it.parameterTypes.size == 4
            }
            m.isAccessible = true
            b.hookAfter(m) { f ->
                val url = f.args.getOrNull(1) as? String ?: return@hookAfter
                val src = f.args.getOrNull(2) as? Bitmap ?: return@hookAfter
                if (src.isRecycled) return@hookAfter
                runCatching { if (BarState.iconTick < 8) b.log("icon captured: $url") }
                // 深拷贝: 宿主图片库可能随时回收原位图, 引用原对象会在玻璃栏绘制时崩溃
                val safe = runCatching {
                    src.copy(android.graphics.Bitmap.Config.ARGB_8888, false)?.asImageBitmap()
                }.getOrNull() ?: return@hookAfter
                BarState.putIcon(url, safe)
            }
        }.onFailure {
            b.log("H4 j-hook NOT FOUND — 原生捕获不可用, 将走向量兜底")
            b.log(it)
        }

        // ---- H4b: URL 绑定 —— a(tab, Z) 解析最终 TabIconUrl, 按 tab 实例身份关联 ----
        runCatching {
            val entCls = cl.loadClass("com.xunmeng.pinduoduo.home.base.entity.HomeBottomTab")
            val m = tabCls.declaredMethods.first {
                it.name == "a" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == entCls &&
                    it.parameterTypes[1] == java.lang.Boolean.TYPE &&
                    it.returnType.name.endsWith(".TabIconUrl")
            }
            m.isAccessible = true
            val rcls = cl.loadClass("$TAB_VIEW_PKG.TabIconUrl")
            val gi = rcls.getMethod("getImageUrl")
            val gg = rcls.getMethod("getGifUrl")
            b.hookAfter(m) { f ->
                val tab = f.args.getOrNull(0) ?: return@hookAfter
                val res = f.result ?: return@hookAfter
                BarState.bindIconUrl(
                    tab,
                    runCatching { gi.invoke(res) as? String }.getOrNull(),
                    runCatching { gg.invoke(res) as? String }.getOrNull(),
                )
            }
        }.onFailure { b.log(it) }

        // ---- 换页/换肤压制点: c2() 是 tab 设置最外层(内部 remove/re-add 视图),
        // 结束后所有视图就位 —— 在此统一执行一次最终隐藏
        runCatching {
            val c2 = tabCls.getDeclaredMethod("c2")
            c2.isAccessible = true
            b.hookAfter(c2) { f ->
                val v = f.thisObject as? android.view.View ?: return@hookAfter
                val p = v.parent as? ViewGroup ?: return@hookAfter
                if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null) {
                    v.visibility = android.view.View.GONE
                    for (i in 0 until p.childCount) {
                        val c = p.getChildAt(i)
                        if (c.javaClass.name.endsWith("PddTabPlaceholderLayout")) {
                            c.visibility = android.view.View.GONE
                        }
                    }
                }
            }
        }.onFailure { b.log(it) }

        // ---- 骨架屏压制: PddTabPlaceholderLayout 走独立绘制路径, 单独覆盖 ----
        runCatching {
            val phCls = cl.loadClass("$TAB_VIEW_PKG.PddTabPlaceholderLayout")
            val dc = (phCls.declaredMethods.firstOrNull { it.name == "drawCanvas" }
                ?: phCls.methods.firstOrNull { it.name == "drawCanvas" })
            if (dc != null) {
                dc.isAccessible = true
                b.hookAfter(dc) { f ->
                    val v = f.thisObject as? android.view.View ?: return@hookAfter
                    val p = v.parent as? ViewGroup ?: return@hookAfter
                    if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null &&
                        v.visibility != android.view.View.GONE
                    ) v.visibility = android.view.View.GONE
                }
            }
            val at = phCls.methods.firstOrNull { it.name == "onAttachedToWindow" }
            if (at != null) {
                b.hookAfter(at) { f ->
                    val v = f.thisObject as? android.view.View ?: return@hookAfter
                    val p = v.parent as? ViewGroup ?: return@hookAfter
                    if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null)
                        v.visibility = android.view.View.GONE
                }
            }
        }.onFailure { b.log(it) }

        // ---- 新实例守卫: 任何 PddTabView 在已注入容器内挂载时立即隐藏 ----
        runCatching {
            val m = tabCls.methods.first { it.name == "onAttachedToWindow" }
            b.hookAfter(m) { f ->
                val v = f.thisObject as? android.view.View ?: return@hookAfter
                val p = v.parent as? ViewGroup ?: return@hookAfter
                if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null) {
                    v.visibility = android.view.View.GONE
                }
            }
        }.onFailure { b.log(it) }

        // ---- 红点刷新锚点: 底栏每次重绘后轻量同步一次 ----
        runCatching {
            val m = tabCls.declaredMethods.first { it.name == "drawCanvas" }
            m.isAccessible = true
            b.hookAfter(m) { f ->
                BarState.refreshDots()
                // 绘制期压制: 原栏一旦被 PDD 重新设为可见(滚动显隐/换页),
                // 它必然先经历一次绘制 —— 在此瞬间立刻按回 GONE
                runCatching {
                    val v = f.thisObject as? android.view.View ?: return@hookAfter
                    val p = v.parent as? ViewGroup ?: return@hookAfter
                    if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null &&
                        v.visibility != android.view.View.GONE
                    ) {
                        v.visibility = android.view.View.GONE
                        com.pdd.glassbar.loader.PddLoader.bridge.log("suppress: re-hidden after flip")
                    }
                }
            }
        }.onFailure { b.log(it) }
    }
}
