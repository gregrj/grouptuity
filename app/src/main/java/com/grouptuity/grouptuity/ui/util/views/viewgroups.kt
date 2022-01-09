package com.grouptuity.grouptuity.ui.util.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlinx.coroutines.flow.StateFlow


class LockableFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0): FrameLayout(context, attrs, defStyleAttr) {
    private var inputLock: StateFlow<Boolean>? = null

    override fun onInterceptTouchEvent(ev: MotionEvent?) = inputLock?.value ?: super.onInterceptTouchEvent(ev)

    fun attachLock(lock: StateFlow<Boolean>) { inputLock = lock }
}