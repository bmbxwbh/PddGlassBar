package com.pdd.glassbar.core

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.pdd.glassbar.loader.HookBridge

object GlassBarHooks {

    fun install(b: HookBridge, profile: AppProfile) {
        val cl = b.hostClassLoader
        var idx = 0
        fun ok() { idx++; b.log("hook/$idx ok") }
        fun fail(n: String, t: Throwable) { b.log("hook/$n FAILED"); b.log(t) }

        when (profile.packageName) {

            // ═══════════ 拼多多 ═══════════
            "com.xunmeng.pinduoduo" -> {
                runCatching {
                    val tabCls = cl.loadClass(
                        "com.xunmeng.pinduoduo.ui_home_activity.widget.tab.PddTabView"
                    )
                    // 镜像
                    val setTabs = tabCls.methods.first { it.name == "setTabs" }
                    b.hookAfter(setTabs) { f ->
                        @Suppress("UNCHECKED_CAST")
                        BarState.mirrorFrom(f.args.getOrNull(0) as? List<Any?>)
                    }
                    ok()
                }.onFailure { fail("pdd-mirror", it) }

                runCatching {
                    val containerCls = cl.loadClass(
                        "com.xunmeng.pinduoduo.ui_home_activity.widget.MainFrameContainerView"
                    )
                    val initM = containerCls.getDeclaredMethod("initView")
                    initM.isAccessible = true
                    b.hookAfter(initM) { f ->
                        val c = f.thisObject as? ViewGroup ?: return@hookAfter
                        c.post { runCatching {
                            com.pdd.glassbar.ui.GlassOverlay.install(c,
                                cl.loadClass("com.xunmeng.pinduoduo.ui_home_activity.widget.tab.PddTabView"),
                                c.context.findActivity())
                        }.onFailure { b.log(it) } }
                    }
                    ok()
                }.onFailure { fail("pdd-overlay", it) }

                runCatching {
                    val tabCls2 = cl.loadClass(
                        "com.xunmeng.pinduoduo.ui_home_activity.widget.tab.PddTabView"
                    )
                    val dc = tabCls2.declaredMethods.first { it.name == "drawCanvas" }
                    dc.isAccessible = true
                    b.hookAfter(dc) { f -> BarState.reassertHidden() }
                    ok()
                }.onFailure { fail("pdd-suppress", it) }

                runCatching {
                    val g1Name = "com.xunmeng.pinduoduo.ui_home_activity.widget.tab.PddTabView" + "$" + "g_1"
                    val g1 = cl.loadClass(g1Name)
                    val m = tabCls2.methods.first { it.name == "setOnTabChangeListener" }
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
                    val onResume = actCls.getDeclaredMethod("onResume")
                    onResume.isAccessible = true
                    b.hookAfter(onResume) { f ->
                        val act = f.thisObject as? Activity ?: return@hookAfter
                        if (!act.javaClass.name.startsWith("tv.danmaku.bili")) return@hookAfter
                        act.window?.decorView?.postDelayed({
                            runCatching { com.pdd.glassbar.ui.GlassOverlay.installByScan(act, profile) }
                                .onFailure { b.log(it) }
                        }, 1500L)
                    }
                    ok()
                }.onFailure { fail("bili-resume", it) }
            }
        }
    }
}
