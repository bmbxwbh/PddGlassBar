package com.pdd.glassbar.app

import com.pdd.glassbar.core.AppProfile

/** 拼多多 8.21.0 —— 已验证配置(线上默认启用) */
val PddProfile = AppProfile(
    packageName = "com.xunmeng.pinduoduo",
    label = "拼多多",
    containerClass = "com.xunmeng.pinduoduo.ui_home_activity.widget.MainFrameContainerView",
    tabViewClass = "com.xunmeng.pinduoduo.ui_home_activity.widget.tab.PddTabView",
    placeholderSuffix = "PddTabPlaceholderLayout",
    mirrorMethodName = "setTabs",
    displayOrder = listOf(0, 14, 3, 4),
    titleByGroup = mapOf(0 to "首页", 14 to "视频", 3 to "聊天", 4 to "个人"),
    groupReader = { t ->
        runCatching { t.javaClass.getField("group").get(t) as? Int }.getOrNull()
    },
    titleReader = { t ->
        runCatching { t.javaClass.getField("title").get(t) as? String }.getOrNull()
    },
)
