package com.pdd.glassbar.app

import com.pdd.glassbar.core.AppProfile

/**
 * 闲鱼 7.27.90 —— 实验性(五页含中央卖闲置位)。
 */
val XianyuProfile = AppProfile(
    packageName = "com.taobao.idlefish",
    label = "闲鱼",
    containerClass = "", // TODO: maincontainer 内深挖
    tabViewClass = null,
    mirrorMethodName = null,
    fixedTabsInOrder = listOf("首页", "同城", "卖闲置", "消息", "我的"),
    experimental = true,
)
