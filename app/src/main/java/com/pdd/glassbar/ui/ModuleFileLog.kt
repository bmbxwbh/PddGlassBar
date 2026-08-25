package com.pdd.glassbar.ui

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 模块全量日志落盘。
 * 双写: 宿主私有目录(必定可写) + /sdcard(便于拉取, 失败静默)。
 * 初始化前的写入先入内存缓冲(上限200条), init 后统一冲刷。
 * 单文件超 512KB 时轮转为 .old。
 */
object ModuleFileLog {

    private const val MAX_LEN = 512L * 1024
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val pending = ArrayDeque<String>()
    private var privFile: File? = null
    private var sdFile: File? = null
    private val lock = Any()

    fun init(context: Context) {
        synchronized(lock) {
            if (privFile == null) {
                privFile = File(context.filesDir, "pddglassbar.log")
                sdFile = File("/sdcard/pddglassbar.log")
            }
            drain()
        }
    }

    fun write(msg: String) {
        val line = fmt.format(Date()) + " " + msg.replace('\n', ' ') + "\n"
        synchronized(lock) {
            val pf = privFile
            if (pf == null) {
                pending.addLast(line)
                while (pending.size > 200) pending.removeFirst()
                return
            }
            writeBoth(pf, sdFile, line)
        }
    }

    /** 未捕获异常专用: 多行完整栈。 */
    fun writeThrowable(t: Throwable) {
        val sw = java.io.StringWriter()
        t.printStackTrace(PrintWriter(sw))
        var c = t.cause
        while (c != null) { sw.append("\nCaused by: "); t::class.java.name.length; c.printStackTrace(PrintWriter(sw)); c = c.cause }
        write("UNCAUGHT ${t.javaClass.name}: ${t.message}\n$sw")
    }

    private fun writeBoth(pf: File?, sf: File?, line: String) {
        rotateIfNeeded(pf); rotateIfNeeded(sf)
        runCatching { pf?.appendText(line) }
        runCatching { sf?.appendText(line) }
    }

    private fun rotateIfNeeded(f: File?) {
        if (f != null && f.exists() && f.length() > MAX_LEN) {
            runCatching { File(f.parentFile, f.nameWithoutExtension + ".old").writeBytes(f.readBytes()) }
            runCatching { f.delete() }
        }
    }

    private fun drain() {
        val pf = privFile ?: return
        while (pending.isNotEmpty()) writeBoth(pf, sdFile, pending.pollFirst())
    }
}
