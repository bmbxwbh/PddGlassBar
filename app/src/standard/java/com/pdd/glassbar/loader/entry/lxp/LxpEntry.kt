package com.pdd.glassbar.loader.entry.lxp

import androidx.annotation.Keep
import com.pdd.glassbar.core.AppProfiles
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

@Keep
class LxpEntry : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {}

    override fun onPackageReady(param: PackageReadyParam) {
        val p = AppProfiles.forPackage(param.packageName) ?: return
        if (!param.isFirstPackage) return
        GlassLoaderBootstrap.bootstrap(this, param, p)
    }
}

/** 拆出以避免入口类直接依赖桥细节 */
private object GlassLoaderBootstrap {
    fun bootstrap(m: XposedModule, param: PackageReadyParam, p: com.pdd.glassbar.core.AppProfile) {
        com.pdd.glassbar.loader.GlassLoader.bootstrap(
            p,
            LxpBridge(m, param.classLoader)
        )
    }
}
