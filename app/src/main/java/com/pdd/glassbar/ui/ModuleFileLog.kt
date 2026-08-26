package com.pdd.glassbar.ui

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 模块全量日志落盘。
 * 双写: 应用专属外部目录(adb 可直接 pull, 无需权限) + 私有目录(root 兜底)。
 * /sdcard 根目录因作用域存储不可写 —— 已弃用。
 */
object ModuleFileLog {

    private const val MAX_LEN = 512L * 1024
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val pending = ArrayDeque<String>()
    private var targets: List<File> = emptyList()
    private val lock = Any()

    fun init(context: Context) {
        synchronized(lock) {
            if (targets.isEmpty()) {
                targets = buildList {
                    context.getExternalFilesDir(null)?.let { add(File(it, "pddglassbar.log")) }
                    add(File(context.filesDir, "pddglassbar.log"))
                }
                drain()
            }
        }
    }

    fun write(msg: String) {
        val line = fmt.format(Date()) + " " + msg.replace('\n', ' ') + "\n"
        synchronized(lock) {
            if (targets.isEmpty()) {
                pending.addLast(line)
                while (pending.size > 200) pending.removeFirst()
                return
            }
            targets.forEach { f ->
                rotateIfNeeded(f)
                runCatching { f.appendText(line) }
            }
        }
    }

    /** 未捕获异常专用: 多行完整栈。 */
    fun writeThrowable(t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        var cause = t.cause
        while (cause != null) {
            sw.append("\nCaused by: ")
            cause.printStackTrace(PrintWriter(sw))
            cause = cause.cause
        }
        write("UNCAUGHT ${t.javaClass.name}: ${t.message}\n$sw")
    }

    private fun rotateIfNeeded(f: File) {
        if (f.exists() && f.length() > MAX_LEN) {
            runCatching { File(f.parentFile, f.nameWithoutExtension + ".old").writeBytes(f.readBytes()) }
            runCatching { f.delete() }
        }
    }

    private fun drain() {
        while (pending.isNotEmpty()) {
            val line = pending.pollFirst()
            targets.forEach { f ->
                rotateIfNeeded(f)
                runCatching { f.appendText(line) }
            }
        }
    }
}
