package com.grouptuity.grouptuity.ui.custom.views

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.animation.doOnEnd
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
    private var mTargetExpansion: Int? = null
    private var activeAnimation: ValueAnimator? = null

    var parallaxMultiplier = 0.5f
    var duration = resources.getInteger(R.integer.card_flip_time_full).toLong()

    fun setExpanded(targetExpansion: Int?=null) {
        activeAnimation?.apply {
            if (this.isRunning) {
                this.cancel()
            }
        }

        mExpansionState = EXPANDED

        mTargetExpansion = targetExpansion

        mFractionExpanded = 1f

        requestLayout()
    }

    fun expand(targetExpansion: Int?=null, initialProgress: Float?=null) {
        when(mExpansionState) {
            COLLAPSED, COLLAPSING -> {
                toggleExpansion(targetExpansion=targetExpansion, initialProgress=initialProgress)
            }
            else -> {  }
        }
    }

    fun setCollapsed() {
        activeAnimation?.apply {
            if (this.isRunning) {
                this.cancel()
            }
        }

        mExpansionState = COLLAPSED

        mFractionExpanded = 0f

        requestLayout()
    }

    fun collapse(initialProgress: Float?=null) {
        when(mExpansionState) {
            EXPANDED, EXPANDING -> { toggleExpansion(initialProgress=initialProgress) }
            else -> {  }
        }
    }

    fun toggleExpansion(targetExpansion: Int?=null, initialProgress: Float? = null) {
        activeAnimation?.apply {
            if (this.isRunning) {
                this.cancel()
            }
        }

        mExpansionState = when(mExpansionState) {
            COLLAPSED, COLLAPSING -> {
                mTargetExpansion = targetExpansion
                EXPANDING
            }
            else -> {
                COLLAPSING
            }
        }

        if (initialProgress != null) {
            mFractionExpanded = if (mExpansionState == EXPANDING) {
                AccelerateDecelerateInterpolator().getInterpolation(initialProgress)
            } else {
                AccelerateDecelerateInterpolator().getInterpolation(1f - initialProgress)
            }
        }

        val animation = ValueAnimator.ofFloat(mFractionExpanded, if (mExpansionState == COLLAPSING) 0f else 1f)
        animation.interpolator = AccelerateDecelerateInterpolator()
        animation.duration = duration
        animation.addUpdateListener {
            mFractionExpanded = (it.animatedValue as Float)
            requestLayout()
        }
        animation.doOnEnd {
            mExpansionState = if (mExpansionState == COLLAPSING) COLLAPSED else EXPANDED
        }
        activeAnimation = animation
        animation.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (orientation == VERTICAL) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.translationY = parallaxMultiplier * (mTargetExpansion ?: measuredHeight) * (mFractionExpanded - 1f)
            }

            setMeasuredDimension(measuredWidth, (mFractionExpanded*(mTargetExpansion ?: measuredHeight)).roundToInt())
        } else {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.translationX = parallaxMultiplier * (mTargetExpansion ?: measuredWidth) *(mFractionExpanded - 1f)
            }

            setMeasuredDimension((mFractionExpanded*(mTargetExpansion ?: measuredWidth)).roundToInt(), measuredHeight)
        }
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