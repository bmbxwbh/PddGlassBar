package com.pdd.glassbar.loader

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LegacyEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {}

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val p = com.pdd.glassbar.core.AppProfiles.forPackage(lpparam.packageName) ?: return
        GlassLoader.bootstrap(p, LegacyBridge(lpparam.classLoader))
    }
}
