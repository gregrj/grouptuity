package com.grouptuity.grouptuity.ui.custom.views

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.grouptuity.grouptuity.R


fun extendFABToCenter(fab: ExtendedFloatingActionButton, iconId: Int) {
    // Extend FAB and replace icon
    fab.extend()
    fab.icon = ResourcesCompat.getDrawable(fab.resources, iconId, fab.context.theme)

    // HACK: Use small delay to give the illusion of animated translation. Real layout animation
    // using TransitionManager.beginDelayedTransition() causes delays in other animations
    Handler(Looper.getMainLooper()).postDelayed({
        // Move FAB to middle of screen
        val layoutParams  = fab.layoutParams as CoordinatorLayout.LayoutParams
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM

        // Restore FAB hide on scroll behavior
        val behavior = HideBottomViewOnScrollBehavior<ExtendedFloatingActionButton>()
        layoutParams.behavior = behavior

        fab.layoutParams = layoutParams
    }, 100)
}

fun shrinkFABToCorner(fab: ExtendedFloatingActionButton, iconId: Int) {
    // Shrink FAB and replace arrow icon
    fab.shrink()
    fab.icon = ResourcesCompat.getDrawable(fab.resources, iconId, fab.context.theme)

    // HACK: Use small delay to give the illusion of animated translation. Real layout animation
    // using TransitionManager.beginDelayedTransition() causes delays in other animations
    Handler(Looper.getMainLooper()).postDelayed({
        // Move FAB to end of screen
        val layoutParams  = fab.layoutParams as CoordinatorLayout.LayoutParams
        layoutParams.gravity = Gravity.END or Gravity.BOTTOM
        fab.layoutParams = layoutParams

        // Slide FAB back into raised position if hidden and then disable hide on scroll behavior
        val behavior = layoutParams.behavior as? HideBottomViewOnScrollBehavior<ExtendedFloatingActionButton> ?: HideBottomViewOnScrollBehavior<ExtendedFloatingActionButton>()
        behavior.slideUp(fab)
        layoutParams.behavior = null
    }, 50)
}

fun hideExtendedFAB(fab: ExtendedFloatingActionButton): Animation? {
    return if (fab.visibility != View.VISIBLE) {
        null
    } else {
        val animation = AnimationUtils.loadAnimation(fab.context, R.anim.scale_down).also {
            it.setAnimationListener(object: Animation.AnimationListener {
                override fun onAnimationStart(p0: Animation?) {
                    fab.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(p0: Animation?) {
                    fab.visibility = View.INVISIBLE
                }

                override fun onAnimationRepeat(p0: Animation?) { }
            })
        }

        fab.startAnimation(animation)
        animation
    }
}

fun showExtendedFAB(fab: ExtendedFloatingActionButton): Animation? {
    return if (fab.visibility == View.VISIBLE) {
        null
    } else {
        val animation = AnimationUtils.loadAnimation(fab.context, R.anim.scale_up).also {
            it.setAnimationListener(object: Animation.AnimationListener {
                override fun onAnimationStart(p0: Animation?) {
                    fab.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(p0: Animation?) { }

                override fun onAnimationRepeat(p0: Animation?) { }
            })
        }

        fab.startAnimation(animation)
        return animation
    }
}

fun slideUpFAB(fab: ExtendedFloatingActionButton) {
    ((fab.layoutParams as CoordinatorLayout.LayoutParams).behavior as? HideBottomViewOnScrollBehavior<ExtendedFloatingActionButton>)?.apply() {
        this.slideUp(fab)
    }
}

fun slideDownFAB(fab: ExtendedFloatingActionButton) {
    ((fab.layoutParams as CoordinatorLayout.LayoutParams).behavior as? HideBottomViewOnScrollBehavior<ExtendedFloatingActionButton>)?.apply() {
        this.slideDown(fab)
    }
}

fun slideUpFAB(fab: FloatingActionButton) {
    ((fab.layoutParams as CoordinatorLayout.LayoutParams).behavior as? HideBottomViewOnScrollBehavior<FloatingActionButton>)?.apply() {
        this.slideUp(fab)
    }
}