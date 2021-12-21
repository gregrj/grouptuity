package com.grouptuity.grouptuity.ui.util.views

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.grouptuity.grouptuity.R


fun setupToolbarSecondaryTertiaryAnimation(
    context: Context,
    viewLifecycleOwner: LifecycleOwner,
    targetTertiaryState: LiveData<Boolean>,
    toolBarView:  View,
    statusBarBackgroundView: View,
    extraView: View?=null) {

    val theme = context.theme
    val secondaryColor = TypedValue().also { theme.resolveAttribute(R.attr.colorSecondary, it, true) }.data
    val secondaryDarkColor = TypedValue().also { theme.resolveAttribute(R.attr.colorSecondaryVariant, it, true) }.data
    val tertiaryColor = TypedValue().also { theme.resolveAttribute(R.attr.colorTertiary, it, true) }.data
    val tertiaryDarkColor = TypedValue().also { theme.resolveAttribute(R.attr.colorTertiaryVariant, it, true) }.data

    val animationDuration = context.resources.getInteger(R.integer.viewprop_animation_duration).toLong()

    var actualTertiaryState = false

    targetTertiaryState.observe(viewLifecycleOwner) {
        if(it != actualTertiaryState) {
            if(actualTertiaryState) {
                ValueAnimator.ofObject(ArgbEvaluator(), tertiaryColor, secondaryColor).apply {
                    duration = animationDuration
                    addUpdateListener { animator -> toolBarView.setBackgroundColor(animator.animatedValue as Int) }
                }.start()

                ValueAnimator.ofObject(ArgbEvaluator(), tertiaryDarkColor, secondaryDarkColor).apply {
                    duration = animationDuration
                    addUpdateListener { animator -> statusBarBackgroundView.setBackgroundColor(animator.animatedValue as Int) }
                }.start()

                if (extraView != null) {
                    ValueAnimator.ofObject(ArgbEvaluator(), tertiaryColor, secondaryColor).apply {
                        duration = animationDuration
                        addUpdateListener { animator -> extraView.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()
                }
            } else {
                ValueAnimator.ofObject(ArgbEvaluator(), secondaryColor, tertiaryColor).apply {
                    duration = animationDuration
                    addUpdateListener { animator -> toolBarView.setBackgroundColor(animator.animatedValue as Int) }
                }.start()

                ValueAnimator.ofObject(ArgbEvaluator(), secondaryDarkColor, tertiaryDarkColor).apply {
                    duration = animationDuration
                    addUpdateListener { animator -> statusBarBackgroundView.setBackgroundColor(animator.animatedValue as Int) }
                }.start()

                if (extraView != null) {
                    ValueAnimator.ofObject(ArgbEvaluator(), secondaryColor, tertiaryColor).apply {
                        duration = animationDuration
                        addUpdateListener { animator -> extraView.setBackgroundColor(animator.animatedValue as Int) }
                    }.start()
                }
            }

            actualTertiaryState = it
        }
    }
}


fun setupToolbarPrimarySecondaryAnimation(
    context: Context,
    viewLifecycleOwner: LifecycleOwner,
    toolBarView:  View,
    statusBarBackgroundView: View,
    targetTertiaryState: LiveData<Boolean>) {

    val theme = context.theme
    val secondaryColor = TypedValue().also { theme.resolveAttribute(R.attr.colorPrimary, it, true) }.data
    val secondaryDarkColor = TypedValue().also { theme.resolveAttribute(R.attr.colorPrimaryVariant, it, true) }.data
    val tertiaryColor = TypedValue().also { theme.resolveAttribute(R.attr.colorSecondary, it, true) }.data
    val tertiaryDarkColor = TypedValue().also { theme.resolveAttribute(R.attr.colorSecondaryVariant, it, true) }.data

    val animationDuration = context.resources.getInteger(R.integer.viewprop_animation_duration).toLong()

    var actualTertiaryState = false

    targetTertiaryState.observe(viewLifecycleOwner) {
        if(it != actualTertiaryState) {
            if(actualTertiaryState) {
                ValueAnimator.ofObject(ArgbEvaluator(), tertiaryColor, secondaryColor).apply {
                    duration = animationDuration
                    addUpdateListener { animator -> toolBarView.setBackgroundColor(animator.animatedValue as Int) }
                }.start()

                ValueAnimator.ofObject(ArgbEvaluator(), tertiaryDarkColor, secondaryDarkColor).apply {
                    duration = animationDuration
                    addUpdateListener { animator -> statusBarBackgroundView.setBackgroundColor(animator.animatedValue as Int) }
                }.start()
            } else {
                ValueAnimator.ofObject(ArgbEvaluator(), secondaryColor, tertiaryColor).apply {
                    duration = animationDuration
                    addUpdateListener { animator -> toolBarView.setBackgroundColor(animator.animatedValue as Int) }
                }.start()

                ValueAnimator.ofObject(ArgbEvaluator(), secondaryDarkColor, tertiaryDarkColor).apply {
                    duration = animationDuration
                    addUpdateListener { animator -> statusBarBackgroundView.setBackgroundColor(animator.animatedValue as Int) }
                }.start()
            }

            actualTertiaryState = it
        }
    }
}