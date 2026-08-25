package com.pdd.glassbar.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Assignment
import com.composables.icons.materialsymbols.outlined.Chat
import com.composables.icons.materialsymbols.outlined.Explore
import com.composables.icons.materialsymbols.outlined.Favorite
import com.composables.icons.materialsymbols.outlined.Home
import com.composables.icons.materialsymbols.outlined.Person
import com.composables.icons.materialsymbols.outlined.Star
import com.composables.icons.materialsymbols.outlined.Videocam
import com.composables.icons.materialsymbols.outlinedfilled.Assignment as FAssignment
import com.composables.icons.materialsymbols.outlinedfilled.Chat as FChat
import com.composables.icons.materialsymbols.outlinedfilled.Explore as FExplore
import com.composables.icons.materialsymbols.outlinedfilled.Favorite as FFavorite
import com.composables.icons.materialsymbols.outlinedfilled.Home as FHome
import com.composables.icons.materialsymbols.outlinedfilled.Person as FPerson
import com.composables.icons.materialsymbols.outlinedfilled.Star as FStar
import com.composables.icons.materialsymbols.outlinedfilled.Videocam as FVideocam

/**
 * 按 HomeBottomTab.group 的内置图标映射(名称已从符号库 2.2.1 元数据逐一验证):
 * outlined 走 MaterialSymbols.Outlined 对象成员; filled 为顶层属性, 需别名导入。
 */
object PddIcons {

    /** 标题兜底(服务端 title 为空时使用)。 */
    val TITLE_FALLBACK = mapOf(
        0 to "首页", 1 to "推荐", 3 to "聊天", 4 to "个人中心", 5 to "分类",
        7 to "关注", 9 to "直播", 10 to "订单", 14 to "视频",
    )

    private data class Pair_(val o: ImageVector, val f: ImageVector)

    private fun p(o: ImageVector, f: ImageVector) = Pair_(o, f)

    private val MAP = mapOf(
        0 to p(MaterialSymbols.Outlined.Home, FHome),
        1 to p(MaterialSymbols.Outlined.Star, FStar),
        3 to p(MaterialSymbols.Outlined.Chat, FChat),
        4 to p(MaterialSymbols.Outlined.Person, FPerson),
        5 to p(MaterialSymbols.Outlined.Explore, FExplore),
        7 to p(MaterialSymbols.Outlined.Favorite, FFavorite),
        9 to p(MaterialSymbols.Outlined.Videocam, FVideocam),
        10 to p(MaterialSymbols.Outlined.Assignment, FAssignment),
        14 to p(MaterialSymbols.Outlined.Videocam, FVideocam),
    )

    private val DEFAULT = p(MaterialSymbols.Outlined.Home, FHome)

    fun icon(group: Int, selected: Boolean): ImageVector =
        (MAP[group] ?: DEFAULT).let { if (selected) it.f else it.o }

    fun titleFallback(group: Int): String? = TITLE_FALLBACK[group]
}
