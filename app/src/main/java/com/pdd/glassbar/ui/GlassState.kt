package com.pdd.glassbar.ui

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.pdd.glassbar.loader.PddLoader
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 固定四页模式(带三级降级):
 *  ① 按实体 group 字段精确过滤 [首页0,视频14,聊天3,个人4]
 *  ② 反射不可用   → 原样放行全部 tab(玻璃栏镜像, 索引对齐, 标题占位)
 *  ③ 空列表       → 原样返回
 */
object BarState {

    data class TabUi(val title: String, val group: Int)

    val GROUP_ORDER = listOf(0, 14, 3, 4)
    val FIXED_TITLES = mapOf(0 to "首页", 14 to "视频", 3 to "聊天", 4 to "个人")

    private val rawTabs = ArrayList<Any>()
    private var dotMethod: Method? = null
    private var groupField: Field? = null
    private var probedClass: Class<*>? = null

    private val hiddenRefs = java.util.concurrent.CopyOnWriteArrayList<
        java.lang.ref.WeakReference<View>>()

    val tabs = mutableStateListOf<TabUi>()
    val dots = mutableStateListOf<Boolean>()

    var selected by mutableIntStateOf(0)
        private set
    var dotTick by mutableIntStateOf(0)
        private set

    fun select(index: Int) {
        selected = index.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    }

    /** 彻底隐身: GONE 不占位 + alpha0 防 VISIBLE 翻回瞬帧。 */
    fun vanish(v: View) {
        v.visibility = View.GONE
        v.alpha = 0f
    }

    fun registerHidden(v: View) {
        vanish(v)
        hiddenRefs += java.lang.ref.WeakReference(v)
    }

    // 周期性再压制(同时恢复 alpha, 防第三方改透明度)。
    fun reassertHidden() {
        hiddenRefs.removeAll { it.get() == null }
        hiddenRefs.forEach { runCatching { it.get()?.let(::vanish) } }
    }

    private fun ensureProbe(item: Any) {
        if (probedClass != null) return
        val cls = item.javaClass
        probedClass = cls
        runCatching { groupField = cls.getField("group") }
        dotMethod = runCatching { cls.getMethod("showRedDot") }.getOrNull()
        PddLoader.bridge.log(
            "probe cls=" + cls.name +
                " groupField=" + (groupField != null) +
                " dot=" + (dotMethod != null)
        )
    }

    private fun readGroup(item: Any): Int =
        runCatching { (groupField?.get(item) as? Number)?.toInt() }.getOrNull() ?: -1

    /** 返回实际传回宿主的列表; 玻璃栏状态与其严格同步。 */
    fun filterAndSync(rawList: List<Any?>?, loader: ClassLoader?): List<Any?> {
        if (rawList.isNullOrEmpty()) return rawList ?: emptyList()
        val nonNull = rawList.filterNotNull()
        synchronized(rawTabs) {
            rawTabs.clear()
            rawTabs.addAll(nonNull)
        }
        ensureProbe(nonNull.first())
        PddLoader.bridge.log("filter in=" + rawList.size)

        // ① 精确 group 过滤
        val tagged = nonNull.mapNotNull { t ->
            val g = readGroup(t)
            if (g in GROUP_ORDER) Triple(t, g, FIXED_TITLES[g] ?: "Tab") else null
        }

        return if (tagged.isNotEmpty()) {
            val ordered = GROUP_ORDER.mapNotNull { g -> tagged.firstOrNull { it.second == g } }
            rebuildTabs(ordered.map { TabUi(it.third, it.second) })
            PddLoader.bridge.log(
                "synced " + tabs.size + " tabs: " +
                    tabs.joinToString("|") { it.group.toString() + ":" + it.title }
            )
            ordered.map { it.first }
        } else {
            // ② 降级直通: 镜像全部 tab
            rebuildTabs(nonNull.mapIndexed { i, t -> TabUi("页面" + (i + 1), -1) })
            PddLoader.bridge.log("passthrough " + nonNull.size + " tabs")
            rawList
        }
    }

    private fun rebuildTabs(list: List<TabUi>) {
        tabs.clear()
        tabs.addAll(list)
        if (selected >= tabs.size) selected = 0
        refreshDots()
    }

    fun refreshDots() {
        val dm = dotMethod ?: return
        val fresh = synchronized(rawTabs) {
            rawTabs.map { runCatching { dm.invoke(it) as? Boolean == true }.getOrDefault(false) }
        }
        if (fresh != dots.toList()) {
            dots.clear(); dots.addAll(fresh); dotTick++
        }
    }

    fun attachListener(original: Any, g1Class: Class<*>) {
        hostListener = original
        selectMethod = runCatching { g1Class.getMethod("onTabSelected", Int::class.javaPrimitiveType) }.getOrNull()
        touchMethod = runCatching { g1Class.getMethod("onTabTouched", Int::class.javaPrimitiveType) }.getOrNull()
        doubleTapMethod = runCatching { g1Class.getMethod("onTabDoubleTap", Int::class.javaPrimitiveType) }.getOrNull()
    }

    fun requestSelect(index: Int) {
        val l = hostListener ?: return
        runCatching { selectMethod?.invoke(l, index) }
            .onFailure { runCatching { touchMethod?.invoke(l, index) } }
    }

    private var hostListener: Any? = null
    private var selectMethod: Method? = null
    private var touchMethod: Method? = null
    private var doubleTapMethod: Method? = null
}
