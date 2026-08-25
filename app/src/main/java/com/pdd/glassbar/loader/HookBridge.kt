package com.pdd.glassbar.loader

import com.pdd.glassbar.core.GlassBarHooks
import java.lang.reflect.Member

/**
 * 单帧 hook 上下文。语义与 de.robv / libxposed 两套 API 对齐后收敛的最小公共面:
 * - before 阶段: 改写 [args]; 置 [skipOriginal]=true 并给 [result] 可跳过原方法
 * - after 阶段: [originalResult] 为原方法返回值; 给 [result] 赋非 [UNCHANGED] 值可改写返回值
 */
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

object PddLoader {
    const val TARGET_PACKAGE = "com.xunmeng.pinduoduo"

    @Volatile
    lateinit var bridge: HookBridge
        private set

    val installed: Boolean get() = this::bridge.isInitialized

    /** 由两种入口(libxposed / legacy)在目标进程内调用, 幂等。 */
    fun bootstrap(b: HookBridge) {
        synchronized(this) {
            if (installed) return
            bridge = b
        }
        try {
            GlassBarHooks.install(b)
            b.log("PddGlassBar: hooks installed")
        } catch (t: Throwable) {
            b.log(t)
        }
    }
}
