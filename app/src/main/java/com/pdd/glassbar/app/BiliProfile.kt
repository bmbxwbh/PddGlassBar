package com.pdd.glassbar.app

import com.pdd.glassbar.core.AppProfile

/**
 * 哔哩哔哩 9.8.0 —— 实验性(Compose 底栏 khome)。
 */
val BiliProfile = AppProfile(
    packageName = "tv.danmaku.bili",
    label = "哔哩哔哩",
    anchorMode = com.pdd.glassbar.core.AnchorMode.ACTIVITY_RESUME_SCAN,
    mainActivityClass = "tv.danmaku.bili.MainActivityV2",    containerClass = "", // TODO: MainActivityV2 内容根待真机定位
    tabViewClass = null, // Compose 底栏无独立 View 类, 由 classify 兜底/快照模式
    mirrorMethodName = null,
    fixedTabsInOrder = listOf("首页", "动态", "会员购", "消息", "我的"),
    useSnapshot = true,
    experimental = false,
)
