package com.pdd.glassbar.ui.utils

import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.LifecycleOwner

fun ComposeView.setLifecycleOwner(owner: LifecycleOwner) {
    setViewTreeLifecycleOwner(owner)
    if (owner is ViewModelStoreOwner) setViewTreeViewModelStoreOwner(owner)
    if (owner is SavedStateRegistryOwner) setViewTreeSavedStateRegistryOwner(owner)
}

fun View.setLifecycleOwnerCompat(owner: LifecycleOwner) {
    setViewTreeLifecycleOwner(owner)
}
