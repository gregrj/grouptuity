package com.grouptuity.grouptuity.ui.custom.views

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.view.children
import com.google.android.material.button.MaterialButton
import com.grouptuity.grouptuity.R
import kotlin.math.roundToLong


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


class FlipButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0): FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        private const val DESELECTED = 0
        private const val SELECTED = 1
        private const val ANIMATING_SELECTION = 2
        private const val ANIMATING_DESELECTION = 3
    }

    var df = false

    private val animationDuration = resources.getInteger(R.integer.card_flip_time_full).toLong()

    private var selectionAnimation = AnimatorSet()
    private var selectionState = DESELECTED

    init {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.FlipButton)

        attributes.recycle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        buildAnimation()
        setSelectionState(false)
    }

    private fun buildAnimation() {
        val childViews = children.toList()
        val frontView = childViews[0]
        val backView = childViews[1]

        val frontImageRotateOut = ObjectAnimator.ofFloat(frontView, "rotationY", 0f, -180f)
        frontImageRotateOut.duration = animationDuration

        val backImageRotateIn = ObjectAnimator.ofFloat(backView, "rotationY", 180f, 0f)
        backImageRotateIn.duration = animationDuration

        /* HACK: duration needs to run for the whole animation set or the property resets to its
         * initial value when reverse() is called. To simulate an instantaneous switch in alpha
         * without using keyframes, multiple values are used compress the transition into the middle
         * of the animation set.
         */
        val frontImageAlphaOut = ObjectAnimator.ofFloat(frontView, "alpha", 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f)
        frontImageAlphaOut.duration = animationDuration

        val backImageAlphaIn = ObjectAnimator.ofFloat(backView, "alpha", 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)
        backImageAlphaIn.duration = animationDuration

        // Visibility changes halfway through the animation
        frontImageRotateOut.addUpdateListener {
            if (it.animatedFraction <= 0.5f) {
                frontView.visibility = View.VISIBLE
                backView.visibility = View.GONE
            } else {
                frontView.visibility = View.GONE
                backView.visibility = View.VISIBLE
            }
        }

        selectionAnimation = AnimatorSet()
        selectionAnimation.playTogether(
            frontImageRotateOut,
            frontImageAlphaOut,
            backImageAlphaIn,
            backImageRotateIn
        )
        selectionAnimation.interpolator = AccelerateDecelerateInterpolator()

        selectionAnimation.addListener(object : Animator.AnimatorListener {
            var active = false

            override fun onAnimationRepeat(p0: Animator?) {}
            override fun onAnimationEnd(p0: Animator?) {
                if (active) {
                    active = false

                    selectionState = when (selectionState) {
                        ANIMATING_SELECTION -> SELECTED
                        ANIMATING_DESELECTION -> DESELECTED
                        else -> SELECTED // should never be called
                    }

                    if (df) {
                        Log.e("onAnimationEnd", ""+selectionState)
                    }

                    if (selectionState == SELECTED) {
                        frontView.visibility = View.GONE
                    }
                    else if(selectionState == DESELECTED) {
                        backView.visibility = View.GONE
                    }

                }
            }

            override fun onAnimationCancel(p0: Animator?) {
                active = false
            }

            override fun onAnimationStart(animator: Animator?) {
                active = true

                frontView.visibility = View.VISIBLE
                backView.visibility = View.VISIBLE
            }
        })
    }

    fun setSelectionState(selected: Boolean) {
        selectionAnimation.cancel()

        val childViews = children.toList()

        if(selected) {
            if (df) {
                Log.e("setSelectionState", ""+true)
            }

            childViews.getOrNull(0)?.also { frontView ->
                frontView.rotationY = -180f
                frontView.alpha = 0f
                frontView.visibility = View.GONE
            }
            childViews.getOrNull(1)?.also { backView ->
                backView.rotationY = 0f
                backView.alpha = 1f
                backView.visibility = View.VISIBLE
            }
            selectionState = SELECTED
        }
        else {
            childViews.getOrNull(0)?.also { frontView ->
                frontView.rotationY = 0f
                frontView.alpha = 1f
                frontView.visibility = View.VISIBLE
            }
            childViews.getOrNull(1)?.also { backView ->
                backView.rotationY = 180f
                backView.alpha = 0f
                backView.visibility = View.GONE
            }
            selectionState = DESELECTED
        }
    }

    fun animateSelection(initialProgress: Float?) {
        if (df) {
            Log.e("animSelection", ""+initialProgress)
        }

        when (selectionState) {
            SELECTED -> {
                if (initialProgress != null) {
                    selectionAnimation.currentPlayTime =
                        (animationDuration * initialProgress).roundToLong()
                    selectionState = ANIMATING_SELECTION
                    selectionAnimation.start()
                }
            }
            ANIMATING_SELECTION -> {
                if (initialProgress != null) {
                    selectionAnimation.currentPlayTime =
                        (animationDuration * initialProgress).roundToLong()
                }
            }
            ANIMATING_DESELECTION -> {
                selectionAnimation.currentPlayTime = if(initialProgress == null) {
                    selectionAnimation.currentPlayTime
                } else {
                    (animationDuration * initialProgress).roundToLong()
                }
                selectionState = ANIMATING_SELECTION
                selectionAnimation.start()
            }
            DESELECTED -> {
                if (initialProgress != null) {
                    selectionAnimation.currentPlayTime =
                        (animationDuration * initialProgress).roundToLong()
                }
                selectionState = ANIMATING_SELECTION
                selectionAnimation.start()
            }
        }
    }

    fun animateDeselection(initialProgress: Float?) {

        if (df) {
            Log.e("animateDeselection", ""+initialProgress)
        }

        when (selectionState) {
            DESELECTED -> {
                if (initialProgress != null) {
                    selectionAnimation.currentPlayTime =
                        (animationDuration *(1f - initialProgress)).roundToLong()
                    selectionState = ANIMATING_DESELECTION
                    selectionAnimation.reverse()
                }
            }
            ANIMATING_DESELECTION -> {
                if (initialProgress != null) {
                    selectionAnimation.currentPlayTime =
                        (animationDuration *(1f - initialProgress)).roundToLong()
                }
            }
            ANIMATING_SELECTION -> {
                selectionAnimation.currentPlayTime = if(initialProgress == null) {
                    selectionAnimation.currentPlayTime
                } else {
                    (animationDuration * (1f - initialProgress)).roundToLong()
                }
                selectionState = ANIMATING_DESELECTION
                selectionAnimation.reverse()
            }
            SELECTED -> {
                if (initialProgress != null) {
                    selectionAnimation.currentPlayTime =
                        (animationDuration * (1f -initialProgress)).roundToLong()
                }
                selectionState = ANIMATING_DESELECTION
                selectionAnimation.reverse()
            }
        }
    }
}