package com.pdd.glassbar.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

/** T1 通用快照裁剪: 整条底栏 → 按槽位等分裁成原生位图。 */
object Snapshot {

    fun harvest(bar: View, slots: Int) {
        if (slots <= 0 || bar.width <= 0 || bar.height <= 0) return
        runCatching {
            val src = Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(src)
            // 调用方负责临时恢复 alpha=1, 这里直接绘制当前视觉
            bar.draw(canvas)

            val w = bar.width / slots
            repeat(slots) { i ->
                if (i * w + w <= src.width) {
                    val crop = Bitmap.createBitmap(src, i * w, 0, w, bar.height)
                    BarState.putNativeIcon(i, androidx.compose.ui.graphics.asImageBitmap(crop))
                }
            }
            src.recycle()
        }
    }
}
