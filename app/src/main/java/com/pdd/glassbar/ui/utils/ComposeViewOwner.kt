package com.pdd.glassbar.ui.utils

import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** 把三件套 owner 标记打到指定 View(DecorView 或 ComposeView)。 */
fun View.setLifecycleOwner(owner: LifecycleOwner) {
    setViewTreeLifecycleOwner(owner)
    if (owner is ViewModelStoreOwner) setViewTreeViewModelStoreOwner(owner)
    if (owner is SavedStateRegistryOwner) setViewTreeSavedStateRegistryOwner(owner)
}
