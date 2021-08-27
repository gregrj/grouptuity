package com.grouptuity.grouptuity.ui.custom.transitions

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionValues
import com.grouptuity.grouptuity.R


class CardViewExpandTransition(private val containerTransitionName: String,
                               private val contentId: Int,
                               private val expanding: Boolean): Transition() {
    private val elements = mutableMapOf<String, Element>()
    private var initialContentLayoutParamWidth: Int = ViewGroup.LayoutParams.MATCH_PARENT
    private var initialContentLayoutParamHeight: Int = ViewGroup.LayoutParams.MATCH_PARENT
    private var onTransitionEndCallback: (Transition, ViewGroup, View, View) -> Unit = { _, _, _, _ -> }
    private var onTransitionProgressCallback: (Transition, ViewGroup, View, ValueAnimator) -> Unit = { _, _, _, _ -> }
    private var onTransitionStartCallback: (Transition, ViewGroup, View, View) -> Unit = { _, _, _, _ -> }

    interface Element {
        fun captureStartValues(transition: Transition, transitionValues: TransitionValues)
        fun captureEndValues(transition: Transition, transitionValues: TransitionValues)
        fun createAnimator(transition: Transition, sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator?
    }

    fun addElement(transitionName: String, element: Element) = this.apply { elements[transitionName] = element }

    fun setOnTransitionStartCallback(callback: (Transition, ViewGroup, View, View) -> Unit) = this.apply { onTransitionStartCallback = callback }

    fun setOnTransitionProgressCallback(callback: (Transition, ViewGroup, View, ValueAnimator) -> Unit) = this.apply { onTransitionProgressCallback = callback }

    fun setOnTransitionEndCallback(callback: (Transition, ViewGroup, View, View) -> Unit) = this.apply { onTransitionEndCallback = callback }

    override fun captureStartValues(transitionValues: TransitionValues) {
        when(transitionValues.view.transitionName) {
            containerTransitionName -> {
                duration = transitionValues.view.resources.getInteger(R.integer.frag_transition_duration).toLong()
                transitionValues.values[PROP_CONTAINER_HEIGHT] = transitionValues.view.height
                transitionValues.values[PROP_CONTAINER_WIDTH] = transitionValues.view.width

                val location = intArrayOf(0, 0)
                transitionValues.view.getLocationOnScreen(location)
                transitionValues.values[PROP_CONTAINER_X] = location[0]
                transitionValues.values[PROP_CONTAINER_Y] = location[1]


            }
            in elements -> {
                elements[transitionValues.view.transitionName]?.captureStartValues(this, transitionValues)
            }
        }
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        when(transitionValues.view.transitionName) {
            containerTransitionName -> {
                transitionValues.values[PROP_CONTAINER_HEIGHT] = transitionValues.view.height
                transitionValues.values[PROP_CONTAINER_WIDTH] = transitionValues.view.width

                val location = intArrayOf(0, 0)
                transitionValues.view.getLocationOnScreen(location)
                transitionValues.values[PROP_CONTAINER_X] = location[0]
                transitionValues.values[PROP_CONTAINER_Y] = location[1]
            }
            in elements -> {
                elements[transitionValues.view.transitionName]?.captureEndValues(this, transitionValues)
            }
        }
    }

    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        if (startValues == null || endValues == null) {
            return null
        }

        val startView = startValues.view
        val endView = endValues.view

        return when (endView.transitionName) {
            containerTransitionName -> {
                if(expanding) {
                    expandEnter(sceneRoot, startValues, startView, endValues, endView)
                } else {
                    contractReturn(sceneRoot, startValues, startView, endValues, endView)
                }
            }
            in elements -> {
                elements[endView.transitionName]?.createAnimator(this, sceneRoot, startValues, endValues)
            }
            else -> { return null }
        }
    }

    private fun expandEnter(sceneRoot: ViewGroup, startValues: TransitionValues, startView: View, endValues: TransitionValues, endView: View): Animator {
        val scrim = sceneRoot.findViewById<View>(R.id.scrim)
        val startScrimAlpha = 0f
        val endScrimAlpha = 0.33f

        val startX = startValues.values[PROP_CONTAINER_X] as Int
        val startY = startValues.values[PROP_CONTAINER_Y] as Int
        val startWidth = startValues.values[PROP_CONTAINER_WIDTH] as Int
        val startHeight = startValues.values[PROP_CONTAINER_HEIGHT] as Int

        val endX = endValues.values[PROP_CONTAINER_X] as Int
        val endY = endValues.values[PROP_CONTAINER_Y] as Int
        val endWidth = endValues.values[PROP_CONTAINER_WIDTH] as Int
        val endHeight = endValues.values[PROP_CONTAINER_HEIGHT] as Int

        endView.translationX = (startX - endX) * (1.0f)
        endView.translationY = (startY - endY) * (1.0f)
        endView.layoutParams = endView.layoutParams.apply {
            this.height = startHeight
            this.width = startWidth
        }

        addListener(object : TransitionListenerAdapter() {
            override fun onTransitionStart(transition: Transition) {
                sceneRoot.findViewById<View>(contentId)?.apply {
                    initialContentLayoutParamWidth = layoutParams.width
                    initialContentLayoutParamHeight = layoutParams.height

                    layoutParams = layoutParams.also {
                        it.height = this.measuredHeight
                        it.width = this.measuredWidth
                    }
                }

                onTransitionStartCallback(transition, sceneRoot, startView, endView)
            }

            override fun onTransitionEnd(transition: Transition) {
                // Restore specified layout params for content within container
                sceneRoot.findViewById<View>(contentId)?.apply {
                    layoutParams = layoutParams.also {
                        it.height = layoutParams.height
                        it.width = layoutParams.width
                    }
                }

                onTransitionEndCallback(transition, sceneRoot, startView, endView)
            }
        })

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.addUpdateListener {
            val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)
            val zeroToTwentyProgress = (5f*progress).coerceIn(0f, 1f)

            scrim.alpha = startScrimAlpha + (endScrimAlpha - startScrimAlpha) * zeroToTwentyProgress

            endView.translationX = (startX - endX) * (1.0f - progress)
            endView.translationY = (startY - endY) * (1.0f - progress)
            endView.layoutParams = endView.layoutParams.apply {
                height = (startHeight + (endHeight - startHeight) * progress).toInt()
                width = (startWidth + (endWidth - startWidth) * progress).toInt()
            }

            onTransitionProgressCallback(this, sceneRoot, endView, it)
        }

        return animator
    }

    private fun contractReturn(sceneRoot: ViewGroup, startValues: TransitionValues, startView: View, endValues: TransitionValues, endView: View): Animator {
        val scrim = View(sceneRoot.context)
        val startScrimAlpha = 0.33f
        val endScrimAlpha = 0f
        scrim.setBackgroundColor(Color.BLACK)
        scrim.alpha = startScrimAlpha
        scrim.layout(0, 0, sceneRoot.width, sceneRoot.height)

        val startX = startValues.values[PROP_CONTAINER_X] as Int
        val startY = startValues.values[PROP_CONTAINER_Y] as Int
        val startWidth = startValues.values[PROP_CONTAINER_WIDTH] as Int
        val startHeight = startValues.values[PROP_CONTAINER_HEIGHT] as Int

        val endX = endValues.values[PROP_CONTAINER_X] as Int
        val endY = endValues.values[PROP_CONTAINER_Y] as Int
        val endWidth = endValues.values[PROP_CONTAINER_WIDTH] as Int
        val endHeight = endValues.values[PROP_CONTAINER_HEIGHT] as Int

        addListener(object : TransitionListenerAdapter() {
            override fun onTransitionStart(transition: Transition) {
                // Freeze layout of the content within the container
                startView.findViewById<View>(contentId)?.apply {
                    initialContentLayoutParamWidth = layoutParams.width
                    initialContentLayoutParamHeight = layoutParams.height

                    layoutParams = layoutParams.also {
                        it.height = this.measuredHeight
                        it.width = this.measuredWidth
                    }
                }

                sceneRoot.overlay.add(scrim)
                sceneRoot.overlay.add(startView)
                endView.alpha = 0f

                onTransitionStartCallback(transition, sceneRoot, startView, endView)
            }

            override fun onTransitionEnd(transition: Transition) {
                sceneRoot.overlay.remove(scrim)
                sceneRoot.overlay.remove(startView)
                endView.alpha = 1f

                // Restore specified layout params for content within the container
                sceneRoot.findViewById<View>(contentId)?.apply {
                    layoutParams = layoutParams.also {
                        it.height = layoutParams.height
                        it.width = layoutParams.width
                    }
                }

                onTransitionEndCallback(transition, sceneRoot, startView, endView)
            }
        })

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.addUpdateListener {
            val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)
            val eightyTo100Progress = (5f*progress - 4f).coerceIn(0f, 1f)

            // Fade out shaded overlay behind detailed diner view
            scrim.alpha = startScrimAlpha + (endScrimAlpha - startScrimAlpha) * eightyTo100Progress

            startView.translationX = (endX - startX) * progress
            startView.translationY = (endY - startY) * progress

            val width = (startWidth + (endWidth - startWidth) * progress).toInt()
            val height = (startHeight + (endHeight - startHeight) * progress).toInt()
            startView.layoutParams = FrameLayout.LayoutParams(width, height)
            startView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            startView.layout(0, 0, width, height)

            onTransitionProgressCallback(this, sceneRoot, endView, it)
        }
        return animator
    }

    companion object {
        private const val PROP_CONTAINER_HEIGHT = "com.grouptuity.grouptuity:CardViewExpandTransition:container_height"
        private const val PROP_CONTAINER_WIDTH = "com.grouptuity.grouptuity:CardViewExpandTransition:container_width"
        private const val PROP_CONTAINER_X = "com.grouptuity.grouptuity:CardViewExpandTransition:container_x"
        private const val PROP_CONTAINER_Y = "com.grouptuity.grouptuity:CardViewExpandTransition:container_y"
    }
}