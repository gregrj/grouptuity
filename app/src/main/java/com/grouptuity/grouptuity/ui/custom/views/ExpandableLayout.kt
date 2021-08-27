package com.grouptuity.grouptuity.ui.custom.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import com.grouptuity.grouptuity.R
import kotlin.math.roundToInt


class ExpandableLayout @JvmOverloads constructor(context: Context,
                                                 attrs: AttributeSet? = null,
                                                 defStyleAttr: Int = 0): LinearLayout(context, attrs, defStyleAttr) {
    companion object {
        const val COLLAPSED = 0
        const val EXPANDED = 1
        const val COLLAPSING = 2
        const val EXPANDING = 3
    }

    private var mExpansionState = COLLAPSED
    private var mFractionExpanded = 0f
    private var activeAnimation: ValueAnimator? = null

    private val duration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()
    private val parallaxMultiplier = 0.5f

    init {
        orientation = VERTICAL
    }

    fun toggleExpansion() {
        activeAnimation?.apply { this.cancel() }

        mExpansionState = when(mExpansionState) {
            COLLAPSED, COLLAPSING -> { EXPANDING }
            else -> { COLLAPSING }
        }

        val animation = ValueAnimator.ofFloat(
            mFractionExpanded,
            if (mExpansionState == COLLAPSING) 0f else 1f
        )
        animation.interpolator = AccelerateDecelerateInterpolator()
        animation.duration = duration
        animation.addUpdateListener {
            mFractionExpanded = it.animatedValue as Float
            requestLayout()
        }
        animation.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                mExpansionState = if (mExpansionState == COLLAPSING) COLLAPSED else EXPANDED
            }
        })
        animation.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.translationY = parallaxMultiplier * measuredHeight*(mFractionExpanded - 1f)
        }

        setMeasuredDimension(measuredWidth, (mFractionExpanded*measuredHeight).roundToInt())
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().also {
            it.putParcelable("KEY_onSaveInstanceState", super.onSaveInstanceState())
            when(mExpansionState) {
                COLLAPSED, COLLAPSING -> {
                    it.putInt("KEY_mExpansionState", COLLAPSED)
                    it.putFloat("KEY_mFractionExpanded", 0f)
                }
            }
        }
    }

    override fun onRestoreInstanceState(parcelable: Parcelable) {
        val bundle = parcelable as Bundle
        mExpansionState = bundle.getInt("KEY_mExpansionState")
        mFractionExpanded = bundle.getFloat("KEY_mFractionExpanded")
        super.onRestoreInstanceState(bundle.getParcelable("KEY_onSaveInstanceState"))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        activeAnimation?.apply { cancel() }
        super.onConfigurationChanged(newConfig)
    }
}