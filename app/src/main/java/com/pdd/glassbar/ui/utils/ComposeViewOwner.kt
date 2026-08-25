package com.pdd.glassbar.ui.utils

import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** 参数用具体类型 [XposedLifecycleOwner](同 WeKit): 它同时实现三类 Owner, 供扩展函数精确解析。 */
fun ComposeView.setLifecycleOwner(owner: XposedLifecycleOwner) {
    setViewTreeLifecycleOwner(owner)
    setViewTreeViewModelStoreOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
}

fun View.setLifecycleOwnerCompat(owner: XposedLifecycleOwner) {
    setViewTreeLifecycleOwner(owner)
}
