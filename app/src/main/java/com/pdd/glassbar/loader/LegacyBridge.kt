package com.pdd.glassbar.loader

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

class LegacyBridge(override val hostClassLoader: ClassLoader) : HookBridge {

    override fun hookBefore(member: Member, priority: Int, callback: (HookFrame) -> Unit) {
        // 优先级经 XC_MethodHook 构造器传入 (api-82 的 hookMethod 无 priority 重载)
        XposedBridge.hookMethod(member, object : XC_MethodHook(priority) {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val frame = HookFrame(null, param.thisObject, param.args)
                runCatching { callback(frame) }.onFailure(::log)
                if (frame.skipOriginal) {
                    param.result = frame.result
                } else {
                    @Suppress("UNCHECKED_CAST")
                    param.args = frame.args
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) = Unit
        })
    }

    override fun hookAfter(member: Member, priority: Int, callback: (HookFrame) -> Unit) {
        XposedBridge.hookMethod(member, object : XC_MethodHook(priority) {
            override fun beforeHookedMethod(param: MethodHookParam) = Unit

            override fun afterHookedMethod(param: MethodHookParam) {
                val frame = HookFrame(null, param.thisObject, param.args)
                    .apply { originalResult = param.result }
                runCatching { callback(frame) }.onFailure(::log)
                if (frame.result !== HookFrame.UNCHANGED) {
                    param.result = frame.result
                    param.throwable = null
                }
            }
        })
    }

    override fun log(message: String) {
        XposedBridge.log("[PddGlassBar] $message")
    }
}
