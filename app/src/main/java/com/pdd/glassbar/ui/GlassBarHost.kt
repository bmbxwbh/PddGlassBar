package com.pdd.glassbar.ui

import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import com.pdd.glassbar.ui.content.FloatingBottomBar
import com.pdd.glassbar.ui.content.FloatingBottomBarDefaults
import com.pdd.glassbar.ui.content.FloatingBottomBarMode
import com.pdd.glassbar.ui.content.rememberViewBackdrop
import kotlinx.coroutines.delay

private val PDD_RED = Color(0xFFE02E24)
private val DARK_BG = Color(0xFF191919)
private val LIGHT_BG = Color(0xFFF7F7F7)
private val LIGHT_UNSELECTED = Color(0xFF000000)
private val DARK_UNSELECTED = Color(0xFFF2F2F2)

@Composable
fun GlassBarHost(sourceView: View?) {
    val dark = isSystemInDarkTheme()
    // 无内容采样源时降级纯色底栏(mode=None), 此时 backdrop 不被消费
    val glassUsable = GlassFlags.glass && sourceView != null
    val owner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            BarState.reassertHidden()
        }
    }

    MaterialTheme(
        colorScheme = if (dark)
            darkColorScheme(primary = PDD_RED, background = DARK_BG)
        else
            lightColorScheme(primary = PDD_RED, background = LIGHT_BG)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            // 周期性再透明化看门狗
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1000)
                    BarState.reassertHidden()
                }
            }

            if (BarState.tabs.isNotEmpty() &&
                (glassUsable || !GlassFlags.glass)) {

                FloatingBottomBar(
                    items = BarState.tabs.toList(),
                    selectedIndex = { BarState.selected },
                    onSelected = { idx -> BarState.requestSelect(idx) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = 12.dp +
                                WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding()
                        ),
                    backdrop = rememberViewBackdrop(sourceView ?: LocalView.current, owner),
                    mode = if (glassUsable) FloatingBottomBarMode.LiquidGlass
                           else FloatingBottomBarMode.None,
                    colors = FloatingBottomBarDefaults.colors(
                        containerColor = if (dark) DARK_BG else LIGHT_BG,
                        indicatorColor = PDD_RED,
                        contentColor = if (dark) DARK_UNSELECTED else LIGHT_UNSELECTED,
                        activeContentColor = PDD_RED,
                    ),
                    liquidGlassBlurRadius = 18.dp,
                    dynamicGravityHighlight = glassUsable,
                    iconContent = { item, index ->
                        val isSelected = index == BarState.selected
                        BadgedBox(badge = {
                            if (!isSelected &&
                                BarState.dots.getOrNull(index) == true
                            ) {
                                Badge(containerColor = Color(0xFFFF3B30))
                            }
                        }) {
                            Crossfade(targetState = isSelected,
                                      animationSpec = tween(200),
                                      label = "icon") { sel ->
                                Icon(
                                    imageVector = PddIcons.icon(item.group, sel),
                                    contentDescription = item.title,
                                    modifier = Modifier.size(26.dp)
                                )
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
}
