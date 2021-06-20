package com.grouptuity.grouptuity.ui.custom.views

import android.view.View
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


fun <T> Fragment.setNullOnDestroy(initialValue: T? = null): ReadWriteProperty<LifecycleOwner, T> =
        object : ReadWriteProperty<LifecycleOwner, T>, DefaultLifecycleObserver {
            private var value = initialValue

            init { this@setNullOnDestroy.lifecycle.addObserver(this) }

            override fun onDestroy(owner: LifecycleOwner) {
                value = null
                this@setNullOnDestroy.lifecycle.removeObserver(this)
                super.onDestroy(owner)
            }

            override fun setValue(thisRef: LifecycleOwner, property: KProperty<*>, value: T) { this.value = value }

            override fun getValue(thisRef: LifecycleOwner, property: KProperty<*>): T { return value!! }
        }

inline fun <T : View> T.runAfterMeasuring(crossinline callback: T.() -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (measuredWidth > 0 && measuredHeight > 0) {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                callback()
            }
        }
    })
}