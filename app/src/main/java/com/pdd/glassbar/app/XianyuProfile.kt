package com.pdd.glassbar.app

import com.pdd.glassbar.core.AnchorMode
import com.pdd.glassbar.core.AppProfile
import com.pdd.glassbar.core.DispatchMode
import com.pdd.glassbar.core.TabMatchMode

/**
 * 闲鱼 7.27.90 —— 五页含中央卖闲置位。
 * 已验证(静态): maincontainer.BaseMainTabView 家族 / IMainTabController /
 *   FirstMainTabView(home) / CityMainTabView(city) / SecondMainTabView(fun.tab)。
 * 架构: 每页一个独立 View → INDEPENDENT_VIEWS 注入。
 */
val XianyuProfile = AppProfile(
    packageName = "com.taobao.idlefish",
    label = "闲鱼",
    anchorMode = AnchorMode.ACTIVITY_RESUME_SCAN,
    mainActivityClass = "com.taobao.idlefish.maincontainer.activity.MainActivity",
    tabMatchMode = TabMatchMode.SIMPLE_NAME_SUFFIXES,
    tabViewSimpleNameSuffixes = listOf(
        "FirstMainTabView",   // 首页
        "CityMainTabView",    // 同城
        // 中央"卖闲置"通常也是家族成员或独立按钮, 由后缀统一捕获
        "SecondMainTabView",  // 消息
        // 个人中心若为独立 View 也以 MyMainTabView 类后缀存在时同样命中
        "MyMainTabView",
    ),
    dispatchMode = DispatchMode.INDEPENDENT_VIEWS,
    mirrorMethodName = null,
    fixedTabsInOrder = listOf("首页", "同城", "卖闲置", "消息", "我的"),
    titleByGroup = emptyMap(),
    experimental = false, // 首发启用; 异常由回滚/看门狗兜底
)
