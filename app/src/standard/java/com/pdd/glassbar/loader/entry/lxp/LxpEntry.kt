package com.pdd.glassbar.loader.entry.lxp

import androidx.annotation.Keep
import com.pdd.glassbar.loader.PddLoader
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/** libxposed 现代入口 (META-INF/xposed/java_init.list 指向本类)。 */
@Keep
class LxpEntry : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        // 框架就绪, 无需额外初始化
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName == PddLoader.TARGET_PACKAGE && param.isFirstPackage) {
            PddLoader.bootstrap(LxpBridge(this, param.classLoader))
        }
    }
}
