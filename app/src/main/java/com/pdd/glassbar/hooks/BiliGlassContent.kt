package com.pdd.glassbar.hooks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BiliGlassContent() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val bg = if (dark) Color(0xF01A1A1A) else Color(0xF0F5F5F5)
    val txtColor = if (dark) Color(0xFFEDEDED) else Color.Black
    val titles = listOf("首页", "动态", "发布", "会员购", "我的")

    Box(
        Modifier.fillMaxWidth().height(65.dp).background(bg),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            Modifier.fillMaxWidth().height(65.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            titles.forEach { title ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(title, fontSize = 12.sp, color = txtColor)
                }
            }
        }
    }
}
