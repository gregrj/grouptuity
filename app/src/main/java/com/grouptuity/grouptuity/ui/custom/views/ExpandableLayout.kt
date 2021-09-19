package com.grouptuity.grouptuity.ui.custom.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
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
        const val STALLED = 4
    }

    var df = false
    var mExpansionState = COLLAPSED
        private set
    private var mFractionExpanded = 0f
    private var mTargetExpansion: Int? = null
    private val animation: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).also { animator ->
        animator.duration = resources.getInteger(R.integer.card_flip_time_full).toLong()
        animator.addUpdateListener {
            mFractionExpanded = (it.animatedValue as Float)
            requestLayout()
        }
        animator.doOnEnd {
            mExpansionState = when (mFractionExpanded) {
                0f -> {
                    COLLAPSED
                }
                1f -> {
                    EXPANDED
                }
                else -> {
                    STALLED
                }
            }
        }
    }

    var parallaxMultiplier = 0.5f

    fun collapse(initialProgress: Float?=null) {
        when (initialProgress) {
            null -> {
                if (mFractionExpanded != 0f) {
                    mExpansionState = COLLAPSING
                    animation.reverse()
                }
            }
            1f -> {
                animation.cancel()
                mFractionExpanded = 0f
                mExpansionState = COLLAPSED
                requestLayout()
            }
            else -> {
                animation.setCurrentFraction(1f - initialProgress)
                mExpansionState = COLLAPSING
                animation.reverse()
            }
        }
    }

    fun expand(initialProgress: Float?=null) {
        animation.cancel()
        when (initialProgress) {
            null -> {
                if (mFractionExpanded != 1f) {
                    mExpansionState = EXPANDING
                    animation.setCurrentFraction(mFractionExpanded)
                    animation.start()
                }
            }
            1f -> {
                mExpansionState = EXPANDED
                mFractionExpanded = 1f
                requestLayout()
            }
            else -> {
                mExpansionState = EXPANDING
                animation.setCurrentFraction(initialProgress)
                animation.start()
            }
        }
    }

    fun setCollapsed() {
        animation.cancel()
        mFractionExpanded = 0f
        mExpansionState = COLLAPSED
        requestLayout()
    }

    fun setExpanded() {
        animation.cancel()
        mFractionExpanded = 1f
        mExpansionState = EXPANDED
        requestLayout()
    }

    fun toggleExpansion() {
        when (mExpansionState){
            COLLAPSED, COLLAPSING -> {
                expand()
            }
            else -> {
                collapse()
            }
        }
    }

    fun setExpandedSize(size: Int?) {
        if(mTargetExpansion != size) {
            mTargetExpansion = size
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (orientation == VERTICAL) {
//            for (i in 0 until childCount) {
//                val child = getChildAt(i)
//                child.translationY = parallaxMultiplier * (mTargetExpansion ?: measuredHeight) * (mFractionExpanded - 1f)
//            }

            setMeasuredDimension(measuredWidth, (mFractionExpanded*(mTargetExpansion ?: measuredHeight)).roundToInt())
        } else {
//            for (i in 0 until childCount) {
////                val child = getChildAt(i)
////                child.translationX = parallaxMultiplier * (mTargetExpansion ?: measuredWidth) * (mFractionExpanded - 1f)
////            }

            if (df)
                Log.e("width", (mFractionExpanded*(mTargetExpansion ?: measuredWidth)).roundToInt().toString())

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
                else -> {
                    it.putInt("KEY_mExpansionState", EXPANDED)
                    it.putFloat("KEY_mFractionExpanded", 1f)
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
        animation.cancel()
        super.onConfigurationChanged(newConfig)
    }
}