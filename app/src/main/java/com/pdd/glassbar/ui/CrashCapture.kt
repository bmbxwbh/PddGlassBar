package com.pdd.glassbar.ui

import android.content.Context
import com.pdd.glassbar.loader.PddLoader
import com.pdd.glassbar.ui.ModuleFileLog
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** 把未捕获异常完整栈写盘, 免去用户抓 logcat。链式调用原 handler, 不吞异常。 */
object CrashCapture {

    fun install(context: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                var cause = e.cause
                while (cause != null) {
                    sw.append("\nCaused by: "); cause.printStackTrace(PrintWriter(sw))
                    cause = cause.cause
                }
                val text = "thread=${t.name}\ne=${e.javaClass.name}: ${e.message}\n\n$sw"
                runCatching { File(context.filesDir, "pddglassbar-crash.log").writeText(text) }
                runCatching { File("/sdcard/pddglassbar-crash.log").writeText(text) }
                runCatching { PddLoader.bridge.log("UNCAUGHT ${e.javaClass.name}: ${e.message}") }
                runCatching { ModuleFileLog.write(text) }
            }
            prev?.uncaughtException(t, e)
        }
    }
}
