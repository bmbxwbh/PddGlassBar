package com.pdd.glassbar.core

import androidx.compose.ui.platform.ComposeView

enum class AnchorMode {
    /** 容器类 initView 后注入(PDD 模式) */
    CONTAINER_INITVIEW,
    /** 主页 Activity onResume 后全树扫描匹配(闲鱼/微博/B站模式) */
    ACTIVITY_RESUME_SCAN,
}

enum class TabMatchMode {
    /** 单一底栏类精确全名 */
    EXACT_CLASS,
    /** 类简单名后缀集合(如 MainTabView 家族) */
    SIMPLE_NAME_SUFFIXES,
}

enum class DispatchMode {
    /** 单底栏栏体, 子项按 getChildAt(nativeIndex) 定位(PDD) */
    BAR_CHILDREN,
    /** 每个 tab 是独立顶层 View, 直接对该 View 注入(闲鱼) */
    INDEPENDENT_VIEWS,
}

/**
 * 单个 App 的锚点与行为描述。引擎只读本描述, 不含任何 App 特例。
 */
class AppProfile(

    val packageName: String,
    val label: String,

    /** 锚点模式 */
    val anchorMode: AnchorMode = AnchorMode.CONTAINER_INITVIEW,

    /** CONTAINER_INITVIEW: 底栏宿主容器(initView 所在类) */
    val containerClass: String = "",

    /** ACTIVITY_RESUME_SCAN: 主页 Activity 全名(仅该 Activity 触发扫描) */
    val mainActivityClass: String = "",

    /**
     * 底栏视图定位:
     *  EXACT_CLASS      → tabViewClass 全名唯一匹配
     *  SIMPLE_NAME_SUFFIXES → 类简单名以任一后缀结尾即命中(可多个, 按容器顺序为索引)
     */
    val tabMatchMode: TabMatchMode = TabMatchMode.EXACT_CLASS,
    val tabViewClass: String? = null,
    val tabViewSimpleNameSuffixes: List<String> = emptyList(),

    /** 骨架屏类后缀(可空) */
    val placeholderSuffix: String? = null,

    /** 镜像入口方法名(null = 固定页模式) */
    val mirrorMethodName: String? = "setTabs",

    /** 显示层过滤顺序(null = 镜像全部) */
    val displayOrder: List<Int>? = null,

    /** 分组 → 标题 */
    val titleByGroup: Map<Int, String> = emptyMap(),

    val groupReader: ((Any) -> Int?)? = null,
    val titleReader: ((Any) -> String?)? = null,

    /** 点击注入方式 */
    val dispatchMode: DispatchMode = DispatchMode.BAR_CHILDREN,

    /** 安装后快照裁剪(T1 原生位图) */
    val useSnapshot: Boolean = false,

    /** 实验性配置默认跳过 */
    val experimental: Boolean = false,

    /** 无镜像入口时的固定页文案 */
    val fixedTabsInOrder: List<String>? = null,
) {
    companion object {
        fun isComposeView(v: android.view.View) = v is ComposeView
    }
}
