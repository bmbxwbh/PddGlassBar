package com.pdd.glassbar.hooks

import android.app.Activity
import com.pdd.glassbar.loader.HookBridge

object BiliHooks {

    fun install(b: HookBridge) {
        val actCls = b.hostClassLoader.loadClass("android.app.Activity")
        val onResume = actCls.getDeclaredMethod("onResume")
        onResume.isAccessible = true
        b.hookAfter(onResume) { f ->
            val act = f.thisObject as? Activity ?: return@hookAfter
            if (act.javaClass.name != "tv.danmaku.bili.MainActivityV2") return@hookAfter
            act.window?.decorView?.postDelayed({
                runCatching {
                    com.pdd.glassbar.ui.GlassOverlay.installByScan(act)
                }.onFailure { e ->
                    b.log("bili FAILED", e)
                }
            }, 1500L)
        }
        b.log("BiliHooks armed")
    }
}
