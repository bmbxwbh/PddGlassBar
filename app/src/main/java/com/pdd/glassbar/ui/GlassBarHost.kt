package com.pdd.glassbar.ui

import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdd.glassbar.ui.content.FloatingBottomBar
import com.pdd.glassbar.ui.content.FloatingBottomBarDefaults
import com.pdd.glassbar.ui.content.FloatingBottomBarMode
import com.pdd.glassbar.ui.content.rememberViewBackdrop

private val PDD_RED = Color(0xFFE02E24)
private val DARK_BG = Color(0xFF191919)
private val LIGHT_BG = Color(0xFFF7F7F7)

@Composable
fun GlassBarHost(sourceView: View) {
    val dark = isSystemInDarkTheme()

    // 订阅图标缓存/红点变化(ConcurrentHashMap 不可观察, 用版本号 tick 驱动重组)
    val ticks by remember { derivedStateOf { BarState.iconTick + BarState.dotTick } }

    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(primary = PDD_RED, background = DARK_BG)
        } else {
            lightColorScheme(primary = PDD_RED, background = LIGHT_BG)
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (BarState.tabs.isNotEmpty()) FloatingBottomBar(
                items = BarState.tabs.toList(),
                selectedIndex = { BarState.selected },
                onSelected = { index -> BarState.requestSelect(index) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = 12.dp +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                backdrop = rememberViewBackdrop(sourceView, LocalLifecycleOwner.current),
                mode = if (GlassFlags.glass) FloatingBottomBarMode.LiquidGlass else FloatingBottomBarMode.None,
                colors = FloatingBottomBarDefaults.colors(
                    containerColor = if (dark) DARK_BG else LIGHT_BG,
                    indicatorColor = PDD_RED,
                    contentColor = Color(0xFF666666),
                    activeContentColor = PDD_RED,
                ),
                liquidGlassBlurRadius = 18.dp,
                dynamicGravityHighlight = true,
                iconContent = { item, index ->
                    if (!GlassFlags.icons) { Box(Modifier.size(26.dp)); return@FloatingBottomBar }
                    @Suppress("UNUSED_EXPRESSION")
                    ticks // 订阅图标缓存更新

                    val isSelected = index == BarState.selected
                    val url = if (isSelected) item.selectedUrl ?: item.normalUrl else item.normalUrl
                    val bmp = BarState.resolveIcon(url)
                    BadgedBox(
                        badge = {
                            if (!isSelected && BarState.dots.getOrNull(index) == true) {
                                Badge(containerColor = Color(0xFFFF3B30))
                            }
                        },
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = item.title,
                                modifier = Modifier.size(26.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            // 图标尚未加载完成时的占位(仅首帧极短)
                            Box(Modifier.size(26.dp))
                        }
                    }
                },
                labelContent = { item, _ ->
                    Text(text = item.title, fontSize = 11.sp, maxLines = 1)
                },
            )
        }
    }
}
