package com.pdd.glassbar.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.outlined.MaterialSymbols.Outlined
import com.composables.icons.materialsymbols.outlined.ChatBubble
import com.composables.icons.materialsymbols.outlined.Explore
import com.composables.icons.materialsymbols.outlined.Favorite
import com.composables.icons.materialsymbols.outlined.GridView
import com.composables.icons.materialsymbols.outlined.Home
import com.composables.icons.materialsymbols.outlined.Person
import com.composables.icons.materialsymbols.outlined.ReceiptLong
import com.composables.icons.materialsymbols.outlined.ThumbUp
import com.composables.icons.materialsymbols.outlined.Videocam
import com.composables.icons.materialsymbols.outlinedfilled.MaterialSymbols.OutlinedFilled
import com.composables.icons.materialsymbols.outlinedfilled.ChatBubble as FilledChatBubble
import com.composables.icons.materialsymbols.outlinedfilled.Explore as FilledExplore
import com.composables.icons.materialsymbols.outlinedfilled.Favorite as FilledFavorite
import com.composables.icons.materialsymbols.outlinedfilled.GridView as FilledGridView
import com.composables.icons.materialsymbols.outlinedfilled.Home as FilledHome
import com.composables.icons.materialsymbols.outlinedfilled.Person as FilledPerson
import com.composables.icons.materialsymbols.outlinedfilled.ReceiptLong as FilledReceiptLong
import com.composables.icons.materialsymbols.outlinedfilled.ThumbUp as FilledThumbUp
import com.composables.icons.materialsymbols.outlinedfilled.Videocam as FilledVideocam

/**
 * 按 HomeBottomTab.group 常量的内置图标映射(不再依赖宿主图片捕获)。
 * group 值来自反编译: HOME=0 RECOMMEND=1 CHAT=3 PERSONAL=4 SEARCH/分类=5
 * FAVORITE=7 LIVE=9 TRANSAC=10 DUODUO_VIDEO=14
 */
object PddIcons {

    private data class Pair_(val o: ImageVector, val f: ImageVector)

    private val MAP = mapOf(
        0 to Pair_(Outlined.Home, OutlinedFilled.Home),
        1 to Pair_(Outlined.ThumbUp, OutlinedFilled.ThumbUp),
        3 to Pair_(Outlined.ChatBubble, OutlinedFilled.ChatBubble),
        4 to Pair_(Outlined.Person, OutlinedFilled.Person),
        5 to Pair_(Outlined.GridView, OutlinedFilled.GridView),
        7 to Pair_(Outlined.Favorite, OutlinedFilled.Favorite),
        9 to Pair_(Outlined.Videocam, OutlinedFilled.Videocam),
        10 to Pair_(Outlined.ReceiptLong, OutlinedFilled.ReceiptLong),
        14 to Pair_(Outlined.Videocam, OutlinedFilled.Videocam),
    )

    private val DEFAULT = Pair_(Outlined.Home, OutlinedFilled.Home)

    /** 标题兜底(服务端 title 为空时使用)。 */
    val TITLE_FALLBACK = mapOf(
        0 to "首页", 1 to "推荐", 3 to "聊天", 4 to "个人中心", 5 to "分类",
        7 to "关注", 9 to "直播", 10 to "订单", 14 to "视频",
    )

    fun icon(group: Int, selected: Boolean): ImageVector =
        (MAP[group] ?: DEFAULT).let { if (selected) it.f else it.o }

    fun outlined(group: Int): ImageVector = (MAP[group] ?: DEFAULT).o
    fun filled(group: Int): ImageVector = (MAP[group] ?: DEFAULT).f

    fun titleFallback(group: Int): String? = TITLE_FALLBACK[group]
}
