package com.pdd.glassbar.loader

import com.pdd.glassbar.core.AppProfile
import java.lang.reflect.Member

// ==================== 帧与桥接口 ====================

class HookFrame(
    val member: Member?,
    val thisObject: Any?,
    var args: Array<Any?>,
) {
    var originalResult: Any? = null
    var result: Any? = UNCHANGED
    var skipOriginal: Boolean = false

    companion object {
        val UNCHANGED = Any()
    }
}

interface HookBridge {
    val hostClassLoader: ClassLoader

    fun hookBefore(member: Member, priority: Int = 50, callback: (HookFrame) -> Unit)

    fun hookAfter(member: Member, priority: Int = 50, callback: (HookFrame) -> Unit)

    fun log(message: String)

    fun log(t: Throwable) = log(t.stackTraceToString())
}

// ==================== 路由加载器 ====================

object GlassLoader {

    @Volatile
    lateinit var profile: AppProfile
        private set

    @Volatile
    lateinit var bridge: HookBridge
        private set

    val installed: Boolean get() = this::bridge.isInitialized

    /** 由各入口在目标包内调用; 按 Profile 路由, 幂等。 */
    fun bootstrap(profile: AppProfile, b: HookBridge) {
        synchronized(this) {
            if (installed) return
            this.profile = profile
            this.bridge = b
        }
        try {
            if (profile.experimental) {
                b.log("experimental profile skipped: " + profile.packageName)
                return
            }
            com.pdd.glassbar.core.GlassBarHooks.install(b, profile)
            b.log("GlassBar hooks installed (" + profile.label + ")")
        } catch (t: Throwable) {
            b.log(t)
        }
    }

    fun bootstrapPdd(b: HookBridge) =
        bootstrap(com.pdd.glassbar.core.AppProfiles.forPackage("com.xunmeng.pinduoduo")!!, b)
}
