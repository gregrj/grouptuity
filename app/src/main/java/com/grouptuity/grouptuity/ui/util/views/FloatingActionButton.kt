package com.grouptuity.grouptuity.ui.util.views

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.grouptuity.grouptuity.R


class EnhancedExtendedFAB(context: Context, attrs: AttributeSet): ExtendedFloatingActionButton(context, attrs) {
    companion object {
        const val HIDDEN = 0
        const val HIDING = 1
        const val SHOWING = 2
        const val SHOWN = 3
    }

    private var showState: Int = SHOWN
    private var showAnimation = AnimatorSet()

    init {
        showState = if (visibility == View.VISIBLE) SHOWN else HIDDEN

        val linearInterpolator = LinearInterpolator()
        val animationDuration = resources.getInteger(R.integer.viewprop_animation_duration).toLong()

        val scaleX = ObjectAnimator.ofFloat(this, "scaleX", 0f, 1f).also {
            it.interpolator = linearInterpolator
            it.duration = animationDuration
        }

        val scaleY = ObjectAnimator.ofFloat(this, "scaleY", 0f, 1f).also {
            it.interpolator = linearInterpolator
            it.duration = animationDuration
        }

        showAnimation.playTogether(scaleX, scaleY)
        showAnimation.interpolator = AccelerateDecelerateInterpolator()

        showAnimation.addListener(object : Animator.AnimatorListener {
            var active = false

            override fun onAnimationRepeat(p0: Animator?) {}
            override fun onAnimationEnd(p0: Animator?) {
                if (active) {
                    showState = when (showState) {
                        HIDING -> {
                            visibility = View.INVISIBLE
                            HIDDEN
                        }
                        SHOWING -> SHOWN
                        else -> SHOWN // should never be called
                    }
                    active = false
                }
            }

            override fun onAnimationCancel(p0: Animator?) {
                active = false
            }

            override fun onAnimationStart(animator: Animator?) {
                active = true
            }
        })
    }

    fun extendFABToCenter(iconId: Int) {
        // Extend FAB and replace icon
        extend()
        icon = ResourcesCompat.getDrawable(resources, iconId, context.theme)

        // HACK: Use small delay to give the illusion of animated translation. Real layout animation
        // using TransitionManager.beginDelayedTransition() causes delays in other animations
        Handler(Looper.getMainLooper()).postDelayed({
            // Move FAB to middle of screen
            val layoutParams  = layoutParams as CoordinatorLayout.LayoutParams
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM

            // Restore FAB hide on scroll behavior
            val behavior = HideBottomViewOnScrollBehavior<ExtendedFloatingActionButton>()
            layoutParams.behavior = behavior

            this.layoutParams = layoutParams
        }, 100)
    }

    fun shrinkFABToCorner(iconId: Int) {
        // Shrink FAB and replace arrow icon
        shrink()
        icon = ResourcesCompat.getDrawable(resources, iconId, context.theme)

        // HACK: Use small delay to give the illusion of animated translation. Real layout animation
        // using TransitionManager.beginDelayedTransition() causes delays in other animations
        Handler(Looper.getMainLooper()).postDelayed({
            // Move FAB to end of screen
            val layoutParams  = layoutParams as CoordinatorLayout.LayoutParams
            layoutParams.gravity = Gravity.END or Gravity.BOTTOM
            this.layoutParams = layoutParams

            // Slide FAB back into raised position if hidden and then disable hide on scroll behavior
            val behavior = layoutParams.behavior as? HideBottomViewOnScrollBehavior<ExtendedFloatingActionButton> ?: HideBottomViewOnScrollBehavior<ExtendedFloatingActionButton>()
            behavior.slideUp(this)
            layoutParams.behavior = null
        }, 50)
    }

    fun showWithAnimation() {
        when (showState) {
            SHOWN, SHOWING -> {
                return
            }
            HIDING -> {
                showState = SHOWING
                showAnimation.currentPlayTime = showAnimation.currentPlayTime
                showAnimation.start()
            }
            HIDDEN -> {
                visibility = View.VISIBLE
                showState = SHOWING
                showAnimation.start()
            }
        }
    }

    fun hideWithAnimation(){
        when (showState) {
            HIDDEN, HIDING -> {
                return
            }
            SHOWING -> {
                showState = HIDING
                showAnimation.currentPlayTime = showAnimation.currentPlayTime
                showAnimation.reverse()
            }
            SHOWN -> {
                showState = HIDING
                showAnimation.reverse()
            }
        }
    }

    fun hideWithAnimatsion() {
//        when {
//            visibility != View.VISIBLE -> {
//                if (showAnimation != null) {
//                    // Show animation is instantiated, but has not started yet. Cancel and clear show
//                    // animation and force FAB to be hidden.
//                    showAnimation?.cancel()
//                    showAnimation = null
//                    visibility = View.INVISIBLE
//                }
//            }
//            showAnimation != null -> {
//                // Show animation is running. Cancel and clear show animation and start hide animation.
//                showAnimation?.cancel()
//                showAnimation = null
//                hideAnimation = AnimationUtils.loadAnimation(context, R.anim.scale_down).also {
//                    it.setAnimationListener(object: Animation.AnimationListener {
//                        override fun onAnimationStart(p0: Animation?) {
//                            visibility = View.VISIBLE
//                        }
//
//                        override fun onAnimationEnd(p0: Animation?) {
//                            if (hideAnimation != null) {
//                                visibility = View.INVISIBLE
//                            }
//                            hideAnimation = null
//                        }
//
//                        override fun onAnimationRepeat(p0: Animation?) { }
//                    })
//                }
//                hideAnimation?.also { startAnimation(it) }
//            }
//            hideAnimation == null -> {
//                // FAB is showing without any active animation so start a new hide animation.
//                hideAnimation = AnimationUtils.loadAnimation(context, R.anim.scale_down).also {
//                    it.setAnimationListener(object: Animation.AnimationListener {
//                        override fun onAnimationStart(p0: Animation?) {
//                            visibility = View.VISIBLE
//                        }
//
//                        override fun onAnimationEnd(p0: Animation?) {
//                            if (hideAnimation != null) {
//                                visibility = View.INVISIBLE
//                            }
//                            hideAnimation = null
//                        }
//
//                        override fun onAnimationRepeat(p0: Animation?) { }
//                    })
//                }
//                hideAnimation?.also { startAnimation(it) }
//            }
//            else -> {
//                /* Hide animation is already running */
//            }
//        }
    }

    fun showWithAnimatigon() {
//        when {
//            visibility != View.VISIBLE -> {
//                // FAB is showing without any active animation so start a new hide animation.
//            }
//        }
//
//
//        if (visibility == View.INVISIBLE || hideAnimation != null) {
//            showAnimation = AnimationUtils.loadAnimation(context, R.anim.scale_up).also {
//                it.setAnimationListener(object: Animation.AnimationListener {
//                    override fun onAnimationStart(p0: Animation?) {
//                        visibility = View.VISIBLE
//                    }
//
//                    override fun onAnimationEnd(p0: Animation?) {
//                        showAnimation = null
//                    }
//
//                    override fun onAnimationRepeat(p0: Animation?) { }
//                })
//            }
//            hideAnimation?.cancel()
//            hideAnimation = null
//            showAnimation?.also { startAnimation(it) }
//        }
    }

    fun slideUp() {
        ((layoutParams as CoordinatorLayout.LayoutParams).behavior as? HideBottomViewOnScrollBehavior<ExtendedFloatingActionButton>)?.also {
            it.slideUp(this)
        }
    }

    fun slideDown() {
        ((layoutParams as CoordinatorLayout.LayoutParams).behavior as? HideBottomViewOnScrollBehavior<ExtendedFloatingActionButton>)?.also {
            it.slideDown(this)
        }
    }
}


fun FloatingActionButton.slideUp() {
    ((layoutParams as CoordinatorLayout.LayoutParams).behavior as? HideBottomViewOnScrollBehavior<FloatingActionButton>)?.apply() {
        this.slideUp(this@slideUp)
    }
}