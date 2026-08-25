package com.pdd.glassbar.loader.entry.lxp

import com.pdd.glassbar.loader.HookBridge
import com.pdd.glassbar.loader.HookFrame
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Member

/** Chain 的 SAM 化包装(与 WeKit LxpHookWrapper 同款手法)。 */
private fun interface SimpleHooker : XposedInterface.Hooker {
    override fun intercept(chain: XposedInterface.Chain): Any?
}

class LxpBridge(
    private val self: XposedInterface,
    override val hostClassLoader: ClassLoader,
) : HookBridge {

    private fun intercept(member: Member, priority: Int, hooker: SimpleHooker) {
        self.hook(member as Executable)
            .setPriority(priority)
            .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
            .intercept(hooker)
    }

    override fun hookBefore(member: Member, priority: Int, callback: (HookFrame) -> Unit) {
        intercept(member, priority, SimpleHooker { chain ->
            val frame = HookFrame(chain.executable, chain.thisObject, chain.args.toTypedArray())
            runCatching { callback(frame) }.onFailure(::log)
            if (frame.skipOriginal) frame.result else chain.proceed(frame.args)
        })
    }

    override fun hookAfter(member: Member, priority: Int, callback: (HookFrame) -> Unit) {
        intercept(member, priority, SimpleHooker { chain ->
            val args = chain.args.toTypedArray()
            val original = chain.proceed(args)
            val frame = HookFrame(chain.executable, chain.thisObject, args)
                .apply { originalResult = original }
            runCatching { callback(frame) }.onFailure(::log)
            if (frame.result !== HookFrame.UNCHANGED) frame.result else original
        })
    }

    override fun log(message: String) {
        try { self.log(android.util.Log.INFO, "PddGlassBar", message, null) } catch (_: Throwable) {}
    }
}
