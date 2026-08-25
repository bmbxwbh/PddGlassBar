package com.pdd.glassbar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import java.lang.reflect.Method

/**
 * 固定四页模式: 只保留 首页/多多视频/聊天/个人中心。
 * setTabs 时过滤服务端列表并【回传给 PDD】, 原生侧与玻璃栏索引天然一致。
 */
object BarState {

    data class TabUi(val title: String, val group: Int)

    /** 固定页面与其顺序 */
    val GROUP_ORDER = listOf(0, 14, 3, 4)
    val FIXED_TITLES = mapOf(0 to "首页", 14 to "视频", 3 to "聊天", 4 to "个人")

    private val rawTabs = ArrayList<Any>()
    private var dotMethod: Method? = null
    private var groupField: java.lang.reflect.Field? = null
    private var fieldsReady = false

    private var hostListener: Any? = null    private var selectMethod: Method? = null    private var touchMethod: Method? = null    private var doubleTapMethod: Method? = null

    val tabs = mutableStateListOf<TabUi>()
    val dots = mutableStateListOf<Boolean>()

    var selected by mutableIntStateOf(0)
        private set
    var dotTick by mutableIntStateOf(0)
        private set

    fun select(index: Int) {
        selected = index.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    }

    /** H2: 过滤服务端列表 → 仅保留固定四页, 按固定顺序排序。返回值回传给宿主。 */
    fun filterAndSync(rawList: List<Any?>?, loader: ClassLoader?): List<Any?> {
        if (rawList.isNullOrEmpty()) return rawList ?: emptyList()
        synchronized(rawTabs) {
            rawTabs.clear()
            rawTabs.addAll(rawList.filterNotNull())
        }
        ensureFields(loader)
        val gf = groupField

        data class Item(val entity: Any, val group: Int)

        val items = rawList.mapNotNull { t ->
            t ?: return@mapNotNull null
            val g = runCatching { (gf?.get(t) as? Number)?.toInt() }.getOrNull() ?: return@mapNotNull null
            if (g in GROUP_ORDER) Item(t, g) else null
        }
        val ordered = GROUP_ORDER.mapNotNull { g -> items.firstOrNull { it.group == g } }

        // 同步 UI 状态
        tabs.clear()
        ordered.forEach { tabs.add(TabUi(FIXED_TITLES[it.group] ?: "Tab", it.group)) }
        if (selected >= tabs.size) selected = 0
        refreshDots()

        return ordered.map { it.entity }
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

    /** H3: 绑定原生监听器(玻璃栏点击路由回去)。 */
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

    private fun ensureFields(loader: ClassLoader?) {
        if (fieldsReady) return
        fieldsReady = true
        runCatching {
            val cl = loader ?: this::class.java.classLoader ?: return
            val cls = cl.loadClass("com.xunmeng.pinduoduo.home.base.entity.HomeBottomTab")
            groupField = cls.getField("group")
            dotMethod = try { cls.getMethod("showRedDot") } catch (_: Throwable) { null }
        }
    }

}
