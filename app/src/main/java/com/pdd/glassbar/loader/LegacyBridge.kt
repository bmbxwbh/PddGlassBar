package com.pdd.glassbar.loader

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

class LegacyBridge(override val hostClassLoader: ClassLoader) : HookBridge {

    /** 不同年代 api jar 里字段名为 member 或 method, 反射双探。 */
    private fun XC_MethodHook.MethodHookParam.extractMember(): Member? =
        runCatching { javaClass.getField("member").get(this) as? Member }
            .recoverCatching { javaClass.getField("method").get(this) as? Member }
            .getOrNull()

    override fun hookBefore(member: Member, priority: Int, callback: (HookFrame) -> Unit) {
        XposedBridge.hookMethod(member, object : XC_MethodHook(priority) {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val frame = HookFrame(param.extractMember(), param.thisObject, param.args)
                runCatching { callback(frame) }.onFailure(::log)
                if (frame.skipOriginal) {
                    param.result = frame.result
                } else {
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
                val frame = HookFrame(param.extractMember(), param.thisObject, param.args)
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
