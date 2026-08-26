package com.pdd.glassbar.ui

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import java.lang.reflect.Field
import java.lang.reflect.Method

object BarState {

    data class TabUi(val title: String, val group: Int)

    val tabs = mutableStateListOf<TabUi>()
    val dots = mutableStateListOf<Boolean>()
    var selected by mutableIntStateOf(0)
        private set

    private var hostListener: Any? = null
    private var selectMethod: Method? = null
    private var groupField: Field? = null
    private var fieldsReady = false

    fun markSelected(idx: Int) {
        if (idx in tabs.indices) selected = idx
        runCatching { selectMethod?.invoke(hostListener, idx) }
            .onFailure { e -> com.pdd.glassbar.loader.GlassLoader.bridge.log(e) }
    }

    fun mirrorFrom(rawList: List<Any?>?) {
        if (rawList.isNullOrEmpty()) return
        if (!fieldsReady) {
            fieldsReady = true
            val cls = rawList.firstNotNullOfOrNull { it }?.javaClass ?: return
            groupField = runCatching { cls.getField("group") }.getOrNull()
        }
        val gf = groupField
        tabs.clear()
        rawList.filterNotNull().forEachIndexed { i, t ->
            val g = gf?.let { f -> runCatching { (f.get(t) as? Number)?.toInt() }.getOrNull() } ?: -1
            val title = runCatching {
                javaClass.getDeclaredMethod("getTitleFallback", Int::class.java)
                    .invoke(this, g) as? String
            }.getOrNull() ?: "Tab${i+1}"
            tabs.add(TabUi(title, g))
        }
        if (selected >= tabs.size) selected = 0
    }

    fun getTitleFallback(group: Int): String? = null

    fun registerHidden(v: View) {
        v.alpha = 0f
    }

    fun reassertHidden() { /* 周期性由 Host 调用 */ }

    /** H3: 绑定原生监听器(用于点击路由)。 */
    fun bindListener(original: Any, g1Class: Class<*>) {
        hostListener = original
        selectMethod = runCatching {
            g1Class.getMethod("onTabSelected", Int::class.javaPrimitiveType)
        }.getOrNull()
    }

    /** 玻璃栏点击 → 调原生 onTabSelected 切页。 */
    fun requestSelect(displayIdx: Int) {
        markSelected(displayIdx)
    }
}
