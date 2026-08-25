package com.pdd.glassbar.core

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
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
    private const val CONTAINER =
        "com.xunmeng.pinduoduo.ui_home_activity.widget.MainFrameContainerView"

    fun install(b: HookBridge) {
        val cl = b.hostClassLoader
        val tabCls = cl.loadClass(TAB_VIEW)
        val containerCls = cl.loadClass(CONTAINER)

        // ---- H1: 注入玻璃栏 overlay(双锚点: initView + 构造器兜底) ----
        val trigger: (Any?) -> Unit = { obj ->
            val container = obj as? ViewGroup ?: return@trigger
            runCatching {
                GlassOverlay.install(container, tabCls, container.context.findActivity())
            }.onFailure { b.log(it) }
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
                // 深拷贝: 宿主图片库可能随时回收原位图, 引用原对象会在玻璃栏绘制时崩溃
                val safe = runCatching {
                    src.copy(android.graphics.Bitmap.Config.ARGB_8888, false)?.asImageBitmap()
                }.getOrNull() ?: return@hookAfter
                BarState.putIcon(url, safe)
            }
        }.onFailure { b.log(it) }

        // ---- 红点刷新锚点: 底栏每次重绘后轻量同步一次 ----
        runCatching {
            val m = tabCls.declaredMethods.first { it.name == "drawCanvas" }
            m.isAccessible = true
            b.hookAfter(m) { BarState.refreshDots() }
        }.onFailure { b.log(it) }
    }
}
