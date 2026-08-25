package com.pdd.glassbar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 底栏状态中枢。PDD 的 tab 是服务端动态下发的(setTabs 可随时被再次调用),
 * 因此这里一切皆可变、全部走 Compose state 驱动重组。
 */
object BarState {

    data class TabUi(
        val title: String,
        val normalUrl: String?,
        val selectedUrl: String?,
        val group: Int,
    )

    val tabs = mutableStateListOf<TabUi>()
    val dots = mutableStateListOf<Boolean>()

    var selected by mutableIntStateOf(0)
        private set
    var iconTick by mutableIntStateOf(0)
        private set
    var dotTick by mutableIntStateOf(0)
        private set

    /** URL → 已解码图标(PddTabView.j 加载回调捕获)。 */
    internal val icons = ConcurrentHashMap<String, ImageBitmap>()
    private val iconsByBase = ConcurrentHashMap<String, ImageBitmap>()

    private fun baseOf(url: String) = url.substringBefore("?").substringAfterLast("/")

    /** a(tab,Z) 解析出的最终 URL 绑定(按 tab 实例身份关联)。 */
    class IconBinding(val imageUrl: String?, val gifUrl: String?)
    private val boundByTab = java.util.concurrent.ConcurrentHashMap<Int, IconBinding>()

    private var bindLogCount = 0

    fun bindIconUrl(tab: Any, imageUrl: String?, gifUrl: String?) {
        boundByTab[System.identityHashCode(tab)] = IconBinding(imageUrl, gifUrl)
        if (bindLogCount < 12) {
            bindLogCount++
            runCatching { com.pdd.glassbar.loader.PddLoader.bridge.log(
                "bind#" + bindLogCount + " img=" + (imageUrl?.take(60) ?: "null") + " gif=" + (gifUrl?.take(40) ?: "null")) }
        }
    }

    // ---- 原生侧引用(用于把玻璃栏的点击路由回宿主) ----
    private var hostListener: Any? = null
    private var selectMethod: Method? = null
    private var touchMethod: Method? = null
    private var doubleTapMethod: Method? = null
    private val rawTabs = ArrayList<Any>()
    private var dotMethod: Method? = null
    private var fieldGettersReady = false

    private val nativeByIndex = ConcurrentHashMap<Int, ImageBitmap>()

    fun putNativeIcon(index: Int, bmp: ImageBitmap) {
        nativeByIndex[index] = bmp
        iconTick++
    }

    fun nativeIcon(index: Int): ImageBitmap? = nativeByIndex[index]

    fun putIcon(url: String, bmp: ImageBitmap) {
        icons[url] = bmp
        iconsByBase[baseOf(url)] = bmp
        iconTick++
    }

    fun resolveIcon(rawUrl: String?): ImageBitmap? {
        if (rawUrl == null) return null
        icons[rawUrl]?.let { return it }
        // PDD 会给 URL 拼 CDN 参数, 精确键可能 miss —— 退化为按文件名匹配
        val b = baseOf(rawUrl)
        iconsByBase[b]?.let { return it }
        return icons.entries.firstOrNull { it.key.contains(b) }?.value
    }

    /** H2: setTabs 拦截 —— 服务端每次下发都重新同步。 */
    fun syncTabs(rawList: List<Any?>?, loader: ClassLoader? = null) {
        runCatching { com.pdd.glassbar.loader.PddLoader.bridge.log("syncTabs size=${rawList?.size ?: -1}") }
        if (rawList.isNullOrEmpty()) return
        synchronized(rawTabs) {
            rawTabs.clear()
            rawTabs.addAll(rawList.filterNotNull())
        }
        if (!fieldGettersReady) {
            fieldGettersReady = true
            runCatching {
                val cls = (loader ?: this::class.java.classLoader)
                    .loadClass("com.xunmeng.pinduoduo.home.base.entity.HomeBottomTab")
                titleF = cls.getField("title")
                imageF = cls.getField("image")
                imageSelF = cls.getField("image_selected")
                groupF = cls.getField("group")
                dotMethod = try { cls.getMethod("showRedDot") } catch (_: Throwable) { null }
            }.onFailure { dotMethod = null }
        }
        val mapped = rawList.mapNotNull { t ->
            t ?: return@mapNotNull null
            runCatching {
                TabUi(
                    title = (titleF?.get(t) as? String)?.takeIf { it.isNotBlank() }
                        ?: PddIcons.titleFallback((groupF?.get(t) as? Number)?.toInt() ?: -1) ?: "Tab",
                    normalUrl = imageF?.get(t) as? String,
                    selectedUrl = imageSelF?.get(t) as? String,
                    group = (groupF?.get(t) as? Number)?.toInt() ?: -1,
                )
            }.getOrNull()
        }
        if (mapped != tabs.toList()) {
            tabs.clear(); tabs.addAll(mapped)
            runCatching { com.pdd.glassbar.loader.PddLoader.bridge.log(
                "synced ${mapped.size} tabs: " + mapped.joinToString("|") { "${it.group}:${it.title}" }) }
            if (selected >= mapped.size) selected = 0
            refreshDots()
            // 清理已不在当前列表中的绑定
            val live = synchronized(rawTabs) { rawTabs.map { System.identityHashCode(it) } }.toSet()
            boundByTab.keys.retainAll { it in live }
        }
    }

    /** 玻璃栏渲染取图标: 运行时捕获(绑定URL)最优先, 收割位图仅作首帧兜底。 */
    fun resolveIconFor(index: Int, selected: Boolean): ImageBitmap? {
        val raw = synchronized(rawTabs) { rawTabs.getOrNull(index) }
        val binding = raw?.let { boundByTab[System.identityHashCode(it)] }
        val candidates = buildList {
            binding?.imageUrl?.let(::add)
            if (!selected) binding?.gifUrl?.let(::add)
            tabs.getOrNull(index)?.let {
                if (selected) { it.selectedUrl?.let(::add); it.normalUrl?.let(::add) }
                else { it.normalUrl?.let(::add); it.selectedUrl?.let(::add) }
            }
        }
        for (u in candidates) {
            icons[u]?.let { return it }
            val bs = baseOf(u)
            if (bs.isNotEmpty()) {
                iconsByBase[bs]?.let { return it }
                icons.entries.firstOrNull { it.key.contains(bs) }?.let { return it.value }
            }
        }
        // 末位兜底: 安装期收割(动态 tab 此刻可能是占位图, 会被上方运行时捕获自然覆盖)
        return nativeByIndex[index]
    }

    /** 每次 PddTabView.drawCanvas 后调用 —— 轻量读取红点状态。 */
    fun refreshDots() {
        val dm = dotMethod ?: return
        val fresh = synchronized(rawTabs) { rawTabs.map { runCatching { dm.invoke(it) as? Boolean == true }.getOrDefault(false) } }
        if (fresh != dots.toList()) {
            dots.clear(); dots.addAll(fresh)
            dotTick++
        }
    }

    /** H3: 包裹原生 g_1 监听器, 保留其页面切换语义。 */
    fun attachListener(original: Any, g1Class: Class<*>) {
        hostListener = original
        selectMethod = runCatching { g1Class.getMethod("onTabSelected", Int::class.javaPrimitiveType) }.getOrNull()
        touchMethod = runCatching { g1Class.getMethod("onTabTouched", Int::class.javaPrimitiveType) }.getOrNull()
        doubleTapMethod = runCatching { g1Class.getMethod("onTabDoubleTap", Int::class.javaPrimitiveType) }.getOrNull()
    }

    fun select(index: Int) {
        selected = index.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    }

    /** 玻璃栏点击 → 调原生监听器完成真实切页。 */
    fun requestSelect(index: Int) {
        val l = hostListener ?: return
        runCatching { selectMethod?.invoke(l, index) }
            .onFailure { runCatching { touchMethod?.invoke(l, index) } }
    }

    // 反射字段缓存
    private var titleF: java.lang.reflect.Field? = null
    private var imageF: java.lang.reflect.Field? = null
    private var imageSelF: java.lang.reflect.Field? = null
    private var groupF: java.lang.reflect.Field? = null
}
