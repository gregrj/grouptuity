package com.grouptuity.grouptuity.ui.custom.views

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
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

inline fun <T : View> T.runAfterGainingFocus(crossinline callback: T.() -> Unit) {
    if (isFocused) {
        callback()
    } else {
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                onFocusChangeListener = null
                callback()
            }
        }
    }
}

inline fun <T : View> T.runAfterLosingFocus(crossinline callback: T.() -> Unit) {
    if (!isFocused) {
        callback()
    } else {
        setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                onFocusChangeListener = null
                callback()
            }
        }
    }
}

fun View.focusAndShowKeyboard() {
    requestFocus()
    if (hasWindowFocus()) {
        if (isFocused) {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    } else {
        viewTreeObserver.addOnWindowFocusChangeListener(
            object : ViewTreeObserver.OnWindowFocusChangeListener {
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (hasFocus) {
                        viewTreeObserver.removeOnWindowFocusChangeListener(this)

                        if (isFocused) {
                            post {
                                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                    .showSoftInput(this@focusAndShowKeyboard, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                    }
                }
            }
        )
    }
}

fun View.clearFocusAndHideKeyboard() {
    if (isFocused) {
        clearFocus()

        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(this.windowToken, 0)
    }
}
