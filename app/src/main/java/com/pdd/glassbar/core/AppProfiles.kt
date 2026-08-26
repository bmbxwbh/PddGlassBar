package com.pdd.glassbar.core

import com.pdd.glassbar.app.BiliProfile
import com.pdd.glassbar.app.PddProfile
import com.pdd.glassbar.app.WeiboProfile
import com.pdd.glassbar.app.XianyuProfile

object AppProfiles {
    val all: List<AppProfile> = listOf(PddProfile, WeiboProfile, BiliProfile, XianyuProfile)

    fun forPackage(pkg: String): AppProfile? =
        all.firstOrNull { it.packageName == pkg }
}
