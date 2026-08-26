package com.pdd.glassbar.ui

import java.io.File

/** 运行时分级降密开关。停机文件内容决定级别(探测路径见 GlassOverlay): */
object GlassFlags {
    var glass = true    // false => FloatingBottomBarMode.None(无玻璃管线)
    var icons = true    // false => 图标区渲染占位

    /** 读停机文件: 不存在=正常; 含"noglass"=关玻璃; 含"noicons"=连图标也关; 其他内容=整体跳过注入 */
    fun load(f: File): String =
        when {
            !f.exists() -> "on"
            else -> f.readText().trim().lowercase().also {
                glass = "noglass" !in it
                icons = "noicons" !in it
            }
        }
}
