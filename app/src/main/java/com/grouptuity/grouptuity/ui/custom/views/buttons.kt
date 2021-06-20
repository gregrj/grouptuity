package com.grouptuity.grouptuity.ui.custom.views

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import com.google.android.material.button.MaterialButton
import com.grouptuity.grouptuity.R


class MaterialRadioButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0): MaterialButton(context, attrs, defStyleAttr) {
    companion object {
        private val STATE_COLOR = intArrayOf(R.attr.state_color)
    }

    var hasColor: Boolean = true
        set(value) {
            if (value != field) {
                field = value
                Handler(Looper.getMainLooper()).post { refreshDrawableState() }
            }
        }

    override fun onCreateDrawableState(extraSpace: Int): IntArray? {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (hasColor) { mergeDrawableStates(drawableState, STATE_COLOR) }
        return drawableState
    }
}