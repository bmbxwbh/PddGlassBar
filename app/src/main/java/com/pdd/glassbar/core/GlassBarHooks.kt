package com.pdd.glassbar.core

import android.view.View
import android.view.ViewGroup
import com.pdd.glassbar.core.AppProfile.AnchorMode
import com.pdd.glassbar.loader.HookBridge
import com.pdd.glassbar.ui.BarState
import com.pdd.glassbar.ui.GlassOverlay
import com.pdd.glassbar.ui.Snapshot
import com.pdd.glassbar.ui.utils.findActivity

object GlassBarHooks {

    fun install(b: HookBridge, profile: AppProfile) {
        BarState.configure(profile)
        val cl = b.hostClassLoader
        val tabCls = profile.tabViewClass?.let { cl.loadClass(it) }
        var hookIdx = 0
        fun ok() { hookIdx++; b.log("hook/$hookIdx ok") }
        fun fail(name: String, t: Throwable) { b.log("hook/$name FAILED"); b.log(t) }

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

        when (profile.anchorMode) {

            AnchorMode.CONTAINER_INITVIEW -> runCatching {
                val containerCls = cl.loadClass(profile.containerClass)
                val m = containerCls.getDeclaredMethod("initView")
                m.isAccessible = true
                b.hookAfter(m) { f ->
                    val container = f.thisObject as? ViewGroup ?: return@hookAfter
                    container.post {
                        runCatching {
                            GlassOverlay.install(
                                container, profile, container.context.findActivity()
                            )
                        }.onFailure { b.log(it) }
                    }
                }
                ok()

                val tvCls = cl.loadClass(profile.tabViewClass!!)
                tvCls.declaredMethods.firstOrNull { it.name == "drawCanvas" }?.let { dm ->
                    dm.isAccessible = true
                    b.hookAfter(dm) { f ->
                        val v = f.thisObject as? View ?: return@hookAfter
                        val p = v.parent as? ViewGroup ?: return@hookAfter
                        if (p.findViewWithTag<View>(GlassOverlay.TAG) != null &&
                            (v.visibility != android.view.View.VISIBLE || v.alpha != 0f)
                        ) BarState.registerHidden(v)
                    }
                }
            }.onFailure { fail("container-anchor", it) }

            AnchorMode.ACTIVITY_RESUME_SCAN -> runCatching {
                val actCls = cl.loadClass("android.app.Activity")
                val m = actCls.getDeclaredMethod("onResume")
                m.isAccessible = true
                b.hookAfter(m) { f ->
                    val act = f.thisObject as? android.app.Activity ?: return@hookAfter
                    if (act.javaClass.name != profile.mainActivityClass) return@hookAfter
                    act.window?.decorView?.postDelayed({
                        runCatching { GlassOverlay.installByScan(act, profile) }
                            .onFailure { b.log(it) }
                    }, 800)
                }
                ok()

                val suffixes = profile.tabViewSimpleNameSuffixes
                val phSuffix = profile.placeholderSuffix
                actCls.getDeclaredMethod("onAttachedToWindow").let { base ->
                    base.isAccessible = true
                    b.hookAfter(base) { f ->
                        val v = f.thisObject as? View ?: return@hookAfter
                        val sn = v.javaClass.simpleName
                        if (suffixes.any { sn.endsWith(it) } ||
                            (phSuffix != null && sn.endsWith(phSuffix))
                        ) BarState.registerHidden(v)
                    }
                }
            }.onFailure { fail("resume-scan", it) }
        }
    }
}
