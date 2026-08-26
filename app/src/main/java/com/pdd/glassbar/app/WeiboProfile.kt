package com.pdd.glassbar.app

import com.pdd.glassbar.core.AppProfile

/**
 * 微博 16.8.2 —— 实验性(锚点待真机验证)。
 * 已侦察: MainTabActivity / WBXAppConfig\$TabBarItem / BottomTabBarPageView
 */
val WeiboProfile = AppProfile(
    packageName = "com.sina.weibo",
    label = "微博",
    anchorMode = com.pdd.glassbar.core.AnchorMode.ACTIVITY_RESUME_SCAN,
    mainActivityClass = "com.sina.weibo.MainTabActivity",    containerClass = "", // TODO: 真机 dumpsys activity top 后填入底栏宿主
    tabViewClass = "com.sina.weibo.wboxsdk.page.view.BottomTabBarPageView",
    mirrorMethodName = null,
    fixedTabsInOrder = listOf("首页", "视频", "发现", "消息", "我"),
    experimental = true,
)
