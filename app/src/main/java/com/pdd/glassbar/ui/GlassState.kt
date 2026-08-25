package com.pdd.glassbar.ui

import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.pdd.glassbar.loader.PddLoader
import java.lang.ref.WeakReference
import java.lang.reflect.Field

/**
 * 纯镜像架构: 不修改 PDD 任何行为, 只观察 + 透明化 + 合成触摸。
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

    // ---- 原生视图引用 ----
    private val hiddenRefs = WeakReferenceHashSet()
    private var tabViewRef: WeakReference<View>? = null
    private var controllerRef: Any? = null
    private var controllerIField: Field? = null

    // ---- 探测缓存 ----
    private var probedClass: Class<*>? = null
    private var groupField: Field? = null
    private var titleField: Field? = null
    private var dotMethod: Method? = null

    fun bindTabView(v: View) {
        tabViewRef = WeakReference(v)
    }

    fun registerHidden(v: View) {
        v.alpha = 0f                      // 核心隐藏手段: 可见性翻转碰不到 alpha
        hiddenRefs.add(v)
    }

    /** 周期性再透明化。 */
    fun reassertHidden() {
        hiddenRefs.forEach { runCatching { it.viewRef()?.alpha = 0f } }
    }

    private fun View.viewRef(): View = this

    private class WeakReferenceHashSet {
        private val set = LinkedHashSet<WeakEntry>()
        fun add(v: View) { set += WeakEntry(v) }
        fun forEach(action: (View) -> Unit) {
            synchronized(set) {
                set.removeAll { it.get() == null }
                set.mapNotNull { it.get() }.forEach(action)
            }
        }
        private class WeakEntry(val v: View) : java.lang.ref.WeakReference<View>(v) {
            override fun equals(other: Any?) = other is WeakEntry && get() === other.get()
            override fun hashCode() = System.identityHashCode(get())
        }
    }

    private fun WeakEntry.viewRef(): View? = get()

    // ---- 只读镜像 ----
    fun mirrorFrom(rawList: List<Any?>?) {
        if (rawList.isNullOrEmpty()) return
        ensureProbe(rawList.filterNotNull().first())
        val out = mutableListOf<TabUi>()
        val dts = mutableListOf<Boolean>()
        rawList.forEachIndexed { idx, t ->
            t ?: return@forEachIndexed
            val g = readGroup(t)
            val known = FIXED_TITLES[g]
            val title = when {
                known != null -> known
                else -> runCatching { (titleField?.get(t) as? String)?.takeIf { it.isNotBlank() } }
                    .getOrNull() ?: "页面${idx + 1}"
            }
            out += TabUi(title, g, idx)
            dts += runCatching { dotMethod?.invoke(t) as? Boolean == true }.getOrDefault(false)
        }
        // 显示层过滤: 若能识别出固定页则仅展示它们(顺序固定), 原生索引保留用于触摸映射
        val fixedOnly = out.filter { it.group in GROUP_ORDER }.sortedBy { GROUP_ORDER.indexOf(it.group) }
        val display = if (fixedOnly.isNotEmpty()) fixedOnly else out
        if (display != tabs.toList()) {
            tabs.clear(); tabs.addAll(display)
        }
        if (dts != dots.toList()) {
            dots.clear(); dots.addAll(dts); dotTick++
        }
        // 同步选中态: 从控制器 i 字段读取原生当前索引 → 映射到展示索引
        syncSelected()
    }

    private fun syncSelected() {
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

    // ---- 点击路由: 合成真实触摸事件 ----
    fun requestSelect(displayIdx: Int) {
        markSelected(displayIdx)
        val tv = tabViewRef?.get() ?: return
        val nativeIdx = tabs.getOrNull(displayIdx)?.nativeIndex ?: return
        if (nativeIdx < 0 || nativeIdx >= tv.childCount) return
        val child = tv.getChildAt(nativeIdx)
        val cLoc = IntArray(2); child.getLocationOnScreen(cLoc)
        val tLoc = IntArray(2); tv.getLocationOnScreen(tLoc)
        val x = (cLoc[0] + child.width / 2f) - tLoc[0]
        val y = (cLoc[1] + child.height / 2f) - tLoc[1]
        val now = android.os.SystemClock.uptimeMillis()
        runCatching {
            tv.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
            val upAt = now + 60
            tv.dispatchTouchEvent(MotionEvent.obtain(upAt, upAt, MotionEvent.ACTION_UP, x, y, 0))
        }
        PddLoader.bridge.log("tap->native idx=$nativeIdx")
    }

    // ---- 控制器绑定(仅读 i 同步选中态) ----
    fun bindController(controller: Any) {
        controllerRef = controller
        controllerIField = runCatching { controller.javaClass.getField("i") }.getOrNull()
    }

    // ---- 探测 ----
    private fun ensureProbe(item: Any) {
        if (probedClass != null) return
        probedClass = item.javaClass
        runCatching { groupField = item.javaClass.getField("group") }
        runCatching { titleField = item.javaClass.getField("title") }
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
}
