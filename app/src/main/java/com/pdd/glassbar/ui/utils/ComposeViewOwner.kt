package com.pdd.glassbar.ui.utils

import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

fun ComposeView.setLifecycleOwner(owner: LifecycleOwner) {
    setViewTreeLifecycleOwner(owner)
    setViewTreeViewModelStoreOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
}

fun View.setLifecycleOwnerCompat(owner: LifecycleOwner) {
    setViewTreeLifecycleOwner(owner)
}
