package com.pdd.glassbar.core

import android.view.View
import android.view.ViewGroup
import com.pdd.glassbar.core.AppProfile.AnchorMode
import com.pdd.glassbar.loader.HookBridge
import com.pdd.glassbar.ui.BarState
import com.pdd.glassbar.ui.GlassOverlay
import com.pdd.glassbar.ui.Snapshot
import com.pdd.glassbar.ui.utils.findActivity

/** 通用引擎: 按 Profile 路由锚点模式并安装只读镜像/透明化守卫。 */
object GlassBarHooks {

    fun install(b: HookBridge, profile: AppProfile) {
        BarState.configure(profile)
        val cl = b.hostClassLoader
        var hookIdx = 0
        fun ok() { hookIdx++; b.log("hook/$hookIdx ok") }
        fun fail(name: String, t: Throwable) { b.log("hook/$name FAILED"); b.log(t) }

        // ---- 镜像入口(只读) ----
        profile.mirrorMethodName?.let { mName ->
            runCatching {
                val tabCls = cl.loadClass(
                    profile.containerClass.substringBeforeLast('.') + ".tab." +
                        profile.tabViewClass!!.substringAfterLast('.')
                )
                val m = tabCls.methods.first { it.name == mName }
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
                            com.pdd.glassbar.ui.GlassOverlay.install(
                                container, profile, container.context.findActivity()
                            )
                        }.onFailure { b.log(it) }
                    }
                }
                ok()

                // 绘制期压制(透明化兜底)
                val tabCls = cl.loadClass(profile.tabViewClass!!)
                tabCls.declaredMethods.firstOrNull { it.name == "drawCanvas" }?.let { dm ->
                    dm.isAccessible = true
                    b.hookAfter(dm) { f ->
                        val v = f.thisObject as? View ?: return@hookAfter
                        val p = v.parent as? ViewGroup ?: return@hookAfter
                        if (p.findViewWithTag<View>(com.pdd.glassbar.ui.GlassOverlay.TAG) != null &&
                            (v.visibility != View.VISIBLE || v.alpha != 0f)
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
                        runCatching {
                            com.pdd.glassbar.ui.GlassOverlay.installByScan(act, profile)
                        }.onFailure { b.log(it) }
                    }, 800)
                }
                ok()

                // 家族成员挂载守卫(独立 View 模式下新实例出现即透明化)
                val phSuffix = profile.placeholderSuffix
                val suffixes = profile.tabViewSimpleNameSuffixes
                cl.loadClass("android.app.Activity").methods.firstOrNull {
                    it.name == "onAttachedToWindow"
                }?.let { base ->
                    b.hookAfter(base) { f ->
                        val v = f.thisObject as? View ?: return@hookAfter
                        val sn = v.javaClass.simpleName
                        val hit = suffixes.any { sn.endsWith(it) } ||
                            (phSuffix != null && sn.endsWith(phSuffix))
                        if (hit) BarState.registerHidden(v)
                    }
                }
            }.onFailure { fail("resume-scan", it) }
        }
    }
}
