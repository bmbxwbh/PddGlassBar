package com.pdd.glassbar.ui

/** Recon 等非 UI 组件的稳定入口(内部转调 ModuleFileLog)。 */
object ModuleFileLogCompat {
    fun init(context: android.content.Context) = ModuleFileLog.init(context)
    fun write(msg: String) = ModuleFileLog.write(msg)
}
