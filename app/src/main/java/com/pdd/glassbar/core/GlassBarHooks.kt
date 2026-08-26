package com.pdd.glassbar.core

import android.view.View
import android.view.ViewGroup
import com.pdd.glassbar.loader.GlassLoader
import com.pdd.glassbar.loader.HookBridge
import com.pdd.glassbar.ui.BarState
import com.pdd.glassbar.ui.GlassOverlay
import com.pdd.glassbar.ui.Snapshot
import com.pdd.glassbar.ui.utils.findActivity

/**
 * 通用引擎: 按 Profile 安装只读镜像与透明化守卫。
 * 不含任何 App 特例 —— 特例全部在 Profile 文件里。
 */
object GlassBarHooks {

    fun install(b: HookBridge, profile: AppProfile) {
        val cl = b.hostClassLoader
        val containerCls = cl.loadClass(profile.containerClass)
        val tabCls = profile.tabViewClass?.let { cl.loadClass(it) }
        var hookIdx = 0

        fun ok() { hookIdx++; b.log("hook/$hookIdx ok") }
        fun fail(name: String, t: Throwable) { b.log("hook/$name FAILED"); b.log(t) }

        // ---- 1 容器锚点: initView 注入(延迟到本轮消息循环之后) ----
        runCatching {
            val m = containerCls.getDeclaredMethod("initView")
            m.isAccessible = true
            b.hookAfter(m) { f ->
                val container = f.thisObject as? ViewGroup ?: return@hookAfter
                container.post {
                    runCatching {
                        GlassOverlay.install(container, profile, container.context.findActivity())
                    }.onFailure { b.log(it) }
                }
            }
            ok()
        }.onFailure { fail("initView", it) }

        // ---- 2 镜像入口(只读) ----
        profile.mirrorMethodName?.let { mName ->
            runCatching {
                val m = tabCls!!.methods.first { it.name == mName }
                b.hookAfter(m) { f ->
                    @Suppress("UNCHECKED_CAST")
                    BarState.mirrorFrom(f.args.getOrNull(0) as? List<Any?>)
                }
                ok()
            }.onFailure { fail("mirror/$mName", it) }
        }

        // ---- 快照裁剪(原生位图优先显示) ----
        if (profile.useSnapshot && tabCls != null) {
            runCatching {
                tabCls.declaredMethods.firstOrNull { it.name == "drawCanvas" }?.let { m ->
                    m.isAccessible = true
                    b.hookAfter(m) { f ->
                        val v = f.thisObject as? View ?: return@hookAfter
                        Snapshot.harvest(v, BarState.slotsForSnapshot)
                    }
                }
                ok()
            }.onFailure { fail("snapshot", it) }
        }

        // ---- 透明化守卫组 ----
        fun suppress(f: com.pdd.glassbar.loader.HookFrame) {
            val v = f.thisObject as? View ?: return
            val p = v.parent as? ViewGroup ?: return
            if (p.findViewWithTag<View>(GlassOverlay.TAG) != null) BarState.registerHidden(v)
        }

        if (tabCls != null) {
            // 挂载守卫(若底栏类覆写了 onAttachedToWindow)
            runCatching {
                tabCls.declaredMethods.firstOrNull { it.name == "onAttachedToWindow" }?.let { m ->
                    m.isAccessible = true
                    b.hookAfter(m) { f -> suppress(f) }
                }
                ok()
            }.onFailure { fail("attach-guard", it) }

            // 绘制期压制(兼红点刷新由镜像层负责, 这里仅压制)
            runCatching {
                val m = tabCls.declaredMethods.first { it.name == "drawCanvas" }
                m.isAccessible = true
                b.hookAfter(m) { f -> suppress(f) }
                ok()
            }.onFailure { fail("draw-suppress", it) }
        }

        profile.placeholderSuffix?.let { suffix ->
            runCatching {
                val phCls = cl.loadClass(
                    profile.tabViewClass!!.substringBeforeLast('.') + "." + suffix
                )
                phCls.declaredMethods.firstOrNull { it.name == "onAttachedToWindow" }?.let { m ->
                    m.isAccessible = true
                    b.hookAfter(m) { f -> suppress(f) }
                }
                phCls.declaredMethods.firstOrNull { it.name == "drawCanvas" }?.let { m ->
                    m.isAccessible = true
                    b.hookAfter(m) { f -> suppress(f) }
                }
                ok()
            }.onFailure { fail("placeholder", it) }
        }
    }
}
