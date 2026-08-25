package com.pdd.glassbar.ui

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.pdd.glassbar.loader.PddLoader
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 纯镜像架构状态中枢: 不修改 PDD 行为, 只观察/透明化/合成触摸。
 */
object BarState {

    data class TabUi(val title: String, val group: Int, val nativeIndex: Int)

    val GROUP_ORDER = listOf(0, 14, 3, 4)
    val FIXED_TITLES = mapOf(0 to "首页", 14 to "视频", 3 to "聊天", 4 to "个人")

    val tabs = mutableStateListOf<TabUi>()
    val dots = mutableStateListOf<Boolean>()

    var selected by mutableIntStateOf(0)
        private set
    var dotTick by mutableIntStateOf(0)
        private set

    // ---- 隐藏视图(alpha=0 看门狗名单) ----
    private val hiddenRefs = java.util.concurrent.CopyOnWriteArrayList<WeakReference<View>>()

    fun registerHidden(v: View) {
        v.alpha = 0f
        hiddenRefs += WeakReference(v)
    }

    /** 周期性再透明化。 */
    fun reassertHidden() {
        hiddenRefs.removeAll { it.get() == null }
        hiddenRefs.forEach { runCatching { it.get()?.alpha = 0f } }
    }

    // ---- 原生视图引用 ----
    private var tabViewRef: WeakReference<View>? = null

    fun bindTabView(v: View) {
        tabViewRef = WeakReference(v)
    }

    // ---- 控制器(仅读取 i 同步选中态) ----
    private var controllerRef: Any? = null
    private var controllerIField: Field? = null

    fun bindController(controller: Any) {
        controllerRef = controller
        controllerIField = runCatching { controller.javaClass.getField("i") }.getOrNull()
    }

    // ---- 实体探测缓存 ----
    private var probedClass: Class<*>? = null
    private var groupField: Field? = null
    private var titleField: Field? = null
    private var dotMethod: Method? = null

    private fun ensureProbe(item: Any) {
        if (probedClass != null) return
        probedClass = item.javaClass
        groupField = runCatching { item.javaClass.getField("group") }.getOrNull()
        titleField = runCatching { item.javaClass.getField("title") }.getOrNull()
        dotMethod = runCatching { item.javaClass.getMethod("showRedDot") }.getOrNull()
        PddLoader.bridge.log(
            "probe cls=" + item.javaClass.name +
                " group=" + (groupField != null) +
                " title=" + (titleField != null) +
                " dot=" + (dotMethod != null)
        )
    }

    private fun readGroup(item: Any): Int =
        runCatching { (groupField?.get(item) as? Number)?.toInt() }.getOrNull() ?: -1

    // ---- 只读镜像 ----
    fun mirrorFrom(rawList: List<Any?>?) {
        if (rawList.isNullOrEmpty()) return
        val nonNull = rawList.filterNotNull()
        ensureProbe(nonNull.first())

        synchronized(rawTabs) {
            rawTabs.clear()
            rawTabs.addAll(nonNull)
        }

        val out = mutableListOf<TabUi>()
        val dts = mutableListOf<Boolean>()
        nonNull.forEachIndexed { idx, t ->
            val g = readGroup(t)
            val fallbackTitle = "页面" + (idx + 1)
            val title = when {
                FIXED_TITLES.containsKey(g) -> FIXED_TITLES[g]!!
                else -> runCatching {
                    (titleField?.get(t) as? String)?.takeIf { it.isNotBlank() }
                }.getOrNull() ?: fallbackTitle
            }
            out += TabUi(title, g, idx)
            dts += runCatching { dotMethod?.invoke(t) as? Boolean == true }.getOrDefault(false)
        }

        // 显示层过滤: 能识别出固定页则只展示固定四页(原生索引保留用于触摸映射)
        val fixedOnly = out.filter { it.group in GROUP_ORDER }
            .sortedBy { GROUP_ORDER.indexOf(it.group) }
        val display = if (fixedOnly.isNotEmpty()) fixedOnly else out

        if (display != tabs.toList()) {
            tabs.clear(); tabs.addAll(display)
        }
        if (dts != dots.toList()) {
            dots.clear(); dots.addAll(dts); dotTick++
        }
        syncSelectedFromHost()
    }

    private fun syncSelectedFromHost() {
        val f = controllerIField ?: return
        val c = controllerRef ?: return
        runCatching {
            val nativeIdx = (f.get(c) as? Number)?.toInt() ?: return
            val disp = tabs.indexOfFirst { it.nativeIndex == nativeIdx }
            if (disp >= 0 && selected != disp) selected = disp
        }
    }

    fun markSelected(displayIdx: Int) {
        if (displayIdx in tabs.indices) selected = displayIdx
    }

    // ---- 点击路由: 向原 PddTabView 对应子视图注入真实触摸 ----
    fun requestSelect(displayIdx: Int) {
        markSelected(displayIdx)
        val tv = tabViewRef?.get() as? ViewGroup ?: return
        val nativeIdx = tabs.getOrNull(displayIdx)?.nativeIndex ?: return
        if (nativeIdx < 0 || nativeIdx >= tv.childCount) return
        val child = tv.getChildAt(nativeIdx)

        val cLoc = IntArray(2); child.getLocationOnScreen(cLoc)
        val tLoc = IntArray(2); tv.getLocationOnScreen(tLoc)
        val x = (cLoc[0] + child.width / 2f) - tLoc[0]
        val y = (cLoc[1] + child.height / 2f) - tLoc[1]

        val now = android.os.SystemClock.uptimeMillis()
        runCatching {
            tv.dispatchTouchEvent(
                MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            )
            val up = now + 60
            tv.dispatchTouchEvent(
                MotionEvent.obtain(up, up, MotionEvent.ACTION_UP, x, y, 0)
            )
            PddLoader.bridge.log("tap->native idx=$nativeIdx")
        }.onFailure { PddLoader.bridge.log(it) }
    }

    private val rawTabs = ArrayList<Any>()
}
