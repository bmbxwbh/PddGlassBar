package com.pdd.glassbar.loader

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

/** 传统 de.robv 入口 (assets/xposed_init 指向本类)。 */
class LegacyEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        // modulePath 目前未使用, 保留以对齐 WeKit 结构
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PddLoader.TARGET_PACKAGE) return
        PddLoader.bootstrap(LegacyBridge(lpparam.classLoader))
    }
}
