package com.pdd.glassbar.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.outlined.Contacts
import com.composables.icons.materialsymbols.outlined.Explore
import com.composables.icons.materialsymbols.outlined.Home
import com.composables.icons.materialsymbols.outlined.Person
import com.composables.icons.materialsymbols.outlinedfilled.Contacts as FContacts
import com.composables.icons.materialsymbols.outlinedfilled.Explore as FExplore
import com.composables.icons.materialsymbols.outlinedfilled.Home as FHome
import com.composables.icons.materialsymbols.outlinedfilled.Person as FPerson

/** 过渡版: 仅使用 WeKit 已验证的 4 图标; 完整映射待探针结果后启用。 */
object PddIcons {

    val TITLE_FALLBACK = mapOf(
        0 to "首页", 3 to "聊天", 4 to "个人中心", 5 to "分类",
        7 to "关注", 9 to "直播", 10 to "订单", 14 to "视频",
    )

    private data class Pair_(val o: ImageVector, val f: ImageVector)

    private val HOME = Pair_(Home, FHome)
    private val CHAT = Pair_(Contacts, FContacts)
    private val EXPLORE = Pair_(Explore, FExplore)
    private val PERSON = Pair_(Person, FPerson)

    fun icon(group: Int, selected: Boolean): ImageVector {
        val p = when (group) {
            0 -> HOME
            3 -> CHAT
            4 -> PERSON
            5 -> EXPLORE
            else -> HOME
        }
        return if (selected) p.f else p.o
    }

    fun titleFallback(group: Int): String? = TITLE_FALLBACK[group]
}
