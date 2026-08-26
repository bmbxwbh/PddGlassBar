package com.pdd.glassbar.ui

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.pdd.glassbar.core.AppProfile
import com.pdd.glassbar.loader.GlassLoader
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 纯镜像状态中枢。全部行为由 [AppProfile] 配置驱动。
 */
object BarState {

    data class TabUi(val title: String, val group: Int, val nativeIndex: Int)

    val tabs = mutableStateListOf<TabUi>()
    val dots = mutableStateListOf<Boolean>()

    var selected by mutableIntStateOf(0)
        private set
    var dotTick by mutableIntStateOf(0)
        private set

    // ---- Profile 配置(install 时注入) ----
    private var displayOrder: List<Int>? = null
    private var titleByGroup: Map<Int, String> = emptyMap()
    private var groupReader: ((Any) -> Int?)? = null
    private var titleReader: ((Any) -> String?)? = null

    fun configure(p: AppProfile) {
        displayOrder = p.displayOrder
        titleByGroup = p.titleByGroup
        groupReader = p.groupReader
        titleReader = p.titleReader
    }

    // ---- 隐藏视图(alpha=0 看门狗名单; 不用 GONE —— 保布局供合成触摸) ----
    private val hiddenRefs = java.util.concurrent.CopyOnWriteArrayList<
        WeakReference<View>>()

    fun registerHidden(v: View) {
        v.alpha = 0f
        hiddenRefs += WeakReference(v)
    }

    /** 周期性再透明化(PDD 的可见性翻转碰不到 alpha)。 */
    fun reassertHidden() {
        hiddenRefs.removeAll { it.get() == null }
        hiddenRefs.forEach { runCatching { it.get()?.alpha = 0f } }
    }

    // ---- 原生视图引用 ----
    private var tabViewRef: WeakReference<View>? = null

    fun bindTabView(v: View) {
        tabViewRef = WeakReference(v)
    }

    // ---- 控制器(仅读 i 同步选中态) ----
    private var controllerRef: Any? = null
    private var controllerIField: Field? = null

    fun bindController(controller: Any) {
        controllerRef = controller
        controllerIField = runCatching { controller.javaClass.getField("i") }.getOrNull()
    }

    // ---- 原生位图(快照裁剪) ----
    private val nativeByIndex = ConcurrentHashMap<Int, androidx.compose.ui.graphics.ImageBitmap>()

    fun putNativeIcon(index: Int, bmp: androidx.compose.ui.graphics.ImageBitmap) {
        nativeByIndex[index] = bmp
        dotTick++
    }

    fun nativeIcon(index: Int): androidx.compose.ui.graphics.ImageBitmap? =
        nativeByIndex[index]

    // ---- 实体探测缓存 ----
    private var probedClass: Class<*>? = null
    private var groupField: Field? = null
    private var titleField: Field? = null
    private var dotMethod: Method? = null
    private val rawTabs = ArrayList<Any>()

    private fun readGroup(item: Any): Int =
        runCatching { (groupField?.get(item) as? Number)?.toInt() }.getOrNull() ?: -1

    private fun ensureProbe(item: Any) {
        if (probedClass != null) return
        probedClass = item.javaClass
        groupField = runCatching { item.javaClass.getField("group") }.getOrNull()
        titleField = runCatching { item.javaClass.getField("title") }.getOrNull()
        dotMethod = runCatching { item.javaClass.getMethod("showRedDot") }.getOrNull()
        GlassLoader.bridge.log(
            "probe cls=" + item.javaClass.name +
                " group=" + (groupField != null) +
                " title=" + (titleField != null) +
                " dot=" + (dotMethod != null)
        )
    }

    /**
     * 只读镜像: 同步玻璃栏展示与原生列表(含显示层过滤), 索引即原生索引。
     */
    fun mirrorFrom(rawList: List<Any?>?) {
        if (rawList.isNullOrEmpty()) return
        val nonNull = rawList.filterNotNull()
        synchronized(rawTabs) {
            rawTabs.clear(); rawTabs.addAll(nonNull)
        }
        ensureProbe(nonNull.first())

        val out = mutableListOf<TabUi>()
        val dts = mutableListOf<Boolean>()
        nonNull.forEachIndexed { idx, t ->
            val g = readGroup(t)
            val fallback = "页面" + (idx + 1)
            val title = when {
                g in titleByGroup -> titleByGroup[g]!!
                else -> runCatching {
                    (titleField?.get(t) as? String)?.takeIf { it.isNotBlank() }
                }.getOrNull() ?: fallback
            }
            out += TabUi(title, g, idx)
            dts += runCatching { dotMethod?.invoke(t) as? Boolean == true }.getOrDefault(false)
        }

        val display = displayOrder?.let { order ->
            out.filter { it.group in order }.sortedBy { order.indexOf(it.group) }
                .takeIf { it.isNotEmpty() }
        } ?: out

        if (display != tabs.toList()) {
            tabs.clear(); tabs.addAll(display)
        }
        if (dts != dots.toList()) {
            dots.clear(); dots.addAll(dts)
        }
        syncSelectedFromHost()
    }

    /** 固定页模式(无镜像入口的 Profile)。 */
    fun setFixedTabs(titles: List<String>) {
        val list = titles.mapIndexed { i, t -> TabUi(t, -1, i) }
        if (list != tabs.toList()) {
            tabs.clear(); tabs.addAll(list)
        }
        if (selected >= tabs.size) selected = 0
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

    // ---- 点击路由: 向原生子视图注入真实触摸 ----
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
            val upAt = now + 60
            tv.dispatchTouchEvent(
                MotionEvent.obtain(upAt, upAt, MotionEvent.ACTION_UP, x, y, 0)
            )
            GlassLoader.bridge.log("tap->native idx=$nativeIdx")
        }.onFailure { GlassLoader.bridge.log(it) }
    }
}
