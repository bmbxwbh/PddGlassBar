package com.pdd.glassbar.core

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.pdd.glassbar.loader.HookBridge
import com.pdd.glassbar.ui.BarState
import com.pdd.glassbar.ui.GlassOverlay

object GlassBarHooks {

    fun install(b: HookBridge, profile: AppProfile) {
        val cl = b.hostClassLoader
        var idx = 0
        fun ok() { idx++; b.log("hook/$idx ok") }
        fun fail(n: String, t: Throwable) { b.log("hook/$n FAILED"); b.log(t) }

        when (profile.packageName) {

            // ═══════════ 拼多多 ═══════════
            "com.xunmeng.pinduoduo" -> {
                val tabCls = runCatching { cl.loadClass(
                    "com.xunmeng.pinduoduo.ui_home_activity.widget.tab.PddTabView"
                )}.getOrElse { fail("pdd-tabCls", it); return }
                val containerCls = runCatching { cl.loadClass(
                    "com.xunmeng.pinduoduo.ui_home_activity.widget.MainFrameContainerView"
                )}.getOrElse { fail("pdd-container", it); return }

                // 镜像 setTabs
                runCatching {
                    val m = tabCls.methods.first { it.name == "setTabs" }
                    b.hookAfter(m) { f ->
                        @Suppress("UNCHECKED_CAST")
                        BarState.mirrorFrom(f.args.getOrNull(0) as? List<Any?>)
                    }
                    ok()
                }.onFailure { fail("pdd-mirror", it) }

                // initView 注入 overlay
                runCatching {
                    val im = containerCls.getDeclaredMethod("initView")
                    im.isAccessible = true
                    b.hookAfter(im) { f ->
                        val c = f.thisObject as? ViewGroup ?: return@hookAfter
                        c.post {
                            runCatching {
                                GlassOverlay.install(c, tabCls, c.context.findActivity())
                            }.onFailure { b.log(it) }
                        }
                    }
                    ok()
                }.onFailure { fail("pdd-overlay", it) }

                // drawCanvas 压制(重声明 alpha=0)
                runCatching {
                    val dc = tabCls.declaredMethods.first { it.name == "drawCanvas" }
                    dc.isAccessible = true
                    b.hookAfter(dc) { f -> BarState.reassertHidden() }
                    ok()
                }.onFailure { fail("pdd-suppress", it) }

                // g_1 监听器代理(点击路由回原生)
                runCatching {
                    val g1Name = "com.xunmeng.pinduoduo.ui_home_activity.widget.tab.PddTabView" + "$" + "g_1"
                    val g1 = cl.loadClass(g1Name)
                    val m = tabCls.methods.first { it.name == "setOnTabChangeListener" }
                    b.hookAfter(m) { f ->
                        val orig = f.args.getOrNull(0) ?: return@hookAfter
                        if (java.lang.reflect.Proxy.isProxyClass(orig.javaClass)) return@hookAfter
                        BarState.bindListener(orig, g1)
                    }
                    ok()
                }.onFailure { fail("pdd-g1", it) }
            }

            // ═══════════ 哔哩哔哩 ═══════════
            "tv.danmaku.bili" -> {
                runCatching {
                    val actCls = cl.loadClass("android.app.Activity")
                    val onResumeM = actCls.getDeclaredMethod("onResume")
                    onResumeM.isAccessible = true
                    b.hookAfter(onResumeM) { f ->
                        val act = f.thisObject as? Activity ?: return@hookAfter
                        if (!act.javaClass.name.startsWith("tv.danmaku.bili")) return@hookAfter
                        act.window?.decorView?.postDelayed({
                            runCatching { com.pdd.glassbar.ui.GlassOverlay.installByScan(act) }
                                .onFailure { e -> b.log("bili-scan FAILED", e) }
                        }, 1500L)
                    }
                    ok()
                }.onFailure { fail("bili-resume", it) }
            }
        }
    }
}
