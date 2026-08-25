package com.pdd.glassbar.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols.Outlined as MSO
import com.composables.icons.materialsymbols.MaterialSymbols.OutlinedFilled as MSOF
import com.composables.icons.materialsymbols.outlined.Assignment as OAssignment
import com.composables.icons.materialsymbols.outlined.Chat as OChat
import com.composables.icons.materialsymbols.outlined.Explore as OExplore
import com.composables.icons.materialsymbols.outlined.Favorite as OFavorite
import com.composables.icons.materialsymbols.outlined.Home as OHome
import com.composables.icons.materialsymbols.outlined.Person as OPerson
import com.composables.icons.materialsymbols.outlined.Star as OStar
import com.composables.icons.materialsymbols.outlined.Videocam as OVideocam
import com.composables.icons.materialsymbols.outlinedfilled.Assignment as FAssignment
import com.composables.icons.materialsymbols.outlinedfilled.Chat as FChat
import com.composables.icons.materialsymbols.outlinedfilled.Explore as FExplore
import com.composables.icons.materialsymbols.outlinedfilled.Favorite as FFavorite
import com.composables.icons.materialsymbols.outlinedfilled.Home as FHome
import com.composables.icons.materialsymbols.outlinedfilled.Person as FPerson
import com.composables.icons.materialsymbols.outlinedfilled.Star as FStar
import com.composables.icons.materialsymbols.outlinedfilled.Videocam as FVideocam

/**
 * 图标映射(结构已从符号库 2.2.1 字节码验证):
 * 根包嵌套对象 MaterialSymbols.Outlined / .OutlinedFilled 为接收者,
 * 各图标是声明在 outlined / outlinedfilled 子包的扩展属性。
 * 访问方式: 别名导入扩展属性后以嵌套对象为成员访问 (MSO.OHome)。
 */
object PddIcons {

    val TITLE_FALLBACK = mapOf(
        0 to "首页", 1 to "推荐", 3 to "聊天", 4 to "个人中心", 5 to "分类",
        7 to "关注", 9 to "直播", 10 to "订单", 14 to "视频",
    )

    private data class Pair_(val o: ImageVector, val f: ImageVector)

    private val MAP = mapOf(
        0 to Pair_(MSO.OHome, MSOF.FHome),                 // 首页
        1 to Pair_(MSO.OStar, MSOF.FStar),                 // 推荐
        3 to Pair_(MSO.OChat, MSOF.FChat),                 // 聊天
        4 to Pair_(MSO.OPerson, MSOF.FPerson),             // 个人中心
        5 to Pair_(MSO.OExplore, MSOF.FExplore),           // 分类
        7 to Pair_(MSO.OFavorite, MSOF.FFavorite),         // 关注
        9 to Pair_(MSO.OVideocam, MSOF.FVideocam),         // 直播
        10 to Pair_(MSO.OAssignment, MSOF.FAssignment),    // 订单
        14 to Pair_(MSO.OVideocam, MSOF.FVideocam),        // 多多视频
    )

    private val DEFAULT = Pair_(MSO.OHome, MSOF.FHome)

    fun icon(group: Int, selected: Boolean): ImageVector =
        (MAP[group] ?: DEFAULT).let { if (selected) it.f else it.o }

    fun titleFallback(group: Int): String? = TITLE_FALLBACK[group]
}
