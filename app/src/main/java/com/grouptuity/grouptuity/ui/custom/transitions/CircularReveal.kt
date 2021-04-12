package com.grouptuity.grouptuity.ui.custom.transitions

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Parcel
import android.os.Parcelable
import android.view.Gravity
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.Transition
import androidx.transition.TransitionValues


class CircularRevealTransition(private val fadeView: View,
                               private val revealedView: View,
                               originParams: OriginParams,
                               private val revealDuration: Long,
                               private val opening: Boolean): Transition() {
    private val x = originParams.x
    private val y = originParams.y
    private val r = originParams.r

    override fun captureStartValues(transitionValues: TransitionValues) {
        transitionValues.values[PROP_FAB_X] = x
        transitionValues.values[PROP_FAB_Y] = y
        transitionValues.values[PROP_FAB_R] = r
    }
    override fun captureEndValues(transitionValues: TransitionValues) {
        transitionValues.values[PROP_FAB_X] = -1f
        transitionValues.values[PROP_FAB_Y] = -1f
        transitionValues.values[PROP_FAB_R] = -1f
    }
    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        if(startValues == null) {
            return null
        }

        val startRadius = startValues.values[PROP_FAB_R] as Float
        val originX = (startValues.values[PROP_FAB_X] as Float) + startRadius
        val originY = (startValues.values[PROP_FAB_Y] as Float) + startRadius

        val endRadius = kotlin.math.hypot(originX, originY)

        val animatorSet = AnimatorSet().apply {
            duration = revealDuration
            interpolator = FastOutSlowInInterpolator()
        }

        if(opening) {
            animatorSet.play(ValueAnimator.ofFloat(1f, 0f).apply {
                addListener(object: Animator.AnimatorListener{
                    override fun onAnimationStart(animation: Animator?) {
                        fadeView.alpha = 1f
                        fadeView.visibility = View.VISIBLE
                    }
                    override fun onAnimationEnd(animation: Animator?) { }
                    override fun onAnimationCancel(animation: Animator?) { }
                    override fun onAnimationRepeat(animation: Animator?) { }
                })
                addUpdateListener { fadeView.alpha = it.animatedValue as Float }
            }).with(ViewAnimationUtils.createCircularReveal(revealedView, originX.toInt(), originY.toInt(), startRadius, endRadius))
        }
        else {
            animatorSet.play(ValueAnimator.ofFloat(0f, 1f).apply {
                addListener(object: Animator.AnimatorListener{
                    override fun onAnimationStart(animation: Animator?) {
                        fadeView.alpha = 0f
                        fadeView.visibility = View.VISIBLE

                        sceneRoot.overlay.add(revealedView)

                        val concealAnimator = ViewAnimationUtils.createCircularReveal(revealedView, originX.toInt(), originY.toInt(), endRadius, startRadius).apply {
                            duration = revealDuration
                            interpolator = FastOutSlowInInterpolator()
                        }
                        concealAnimator.addListener(object: Animator.AnimatorListener{
                            override fun onAnimationStart(p0: Animator?) { }
                            override fun onAnimationEnd(p0: Animator?) { sceneRoot.overlay.remove(revealedView) }
                            override fun onAnimationCancel(p0: Animator?) { }
                            override fun onAnimationRepeat(p0: Animator?) { }
                        })
                        concealAnimator.start()
                    }
                    override fun onAnimationEnd(animation: Animator?) { }
                    override fun onAnimationCancel(animation: Animator?) { }
                    override fun onAnimationRepeat(animation: Animator?) { }
                })

                addUpdateListener { fadeView.alpha = it.animatedValue as Float }
            })
        }

        return animatorSet
    }

    class OriginParams: Parcelable {
        val x: Float
        val y: Float
        val r: Float
        private val gravity: Int
        private val leftInset: Int
        private val topInset: Int
        private val rightInset: Int
        private val bottomInset: Int

        private constructor(x: Float,
                            y: Float,
                            r: Float,
                            gravity: Int,
                            leftInset: Int,
                            topInset: Int,
                            rightInset: Int,
                            bottomInset: Int) {
            this.x = x
            this.y = y
            this.r = r
            this.gravity = gravity
            this.leftInset = leftInset
            this.topInset = topInset
            this.rightInset = rightInset
            this.bottomInset = bottomInset
        }

        constructor(view: View) {
            this.x = view.x
            this.y = view.y
            this.r = view.width/2f

            this.gravity = Gravity.getAbsoluteGravity((view.layoutParams as? CoordinatorLayout.LayoutParams)?.gravity ?: Gravity.NO_GRAVITY, view.layoutDirection)

            val insets = ViewCompat.getRootWindowInsets(view)!!.getInsets(WindowInsetsCompat.Type.systemBars() and WindowInsetsCompat.Type.statusBars().inv())
            leftInset = insets.left
            topInset = insets.top
            rightInset = insets.right
            bottomInset = insets.bottom
        }

        @SuppressLint("RtlHardcoded")
        fun withInsetsOn(view: View): OriginParams {

            val newInsets = ViewCompat.getRootWindowInsets(view)!!.getInsets(WindowInsetsCompat.Type.systemBars() and WindowInsetsCompat.Type.statusBars().inv())

            val newX = this.x + when(this.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                Gravity.LEFT -> { 0f }
                Gravity.RIGHT -> { (this.rightInset - newInsets.right).toFloat() }
                Gravity.CENTER_HORIZONTAL -> { (this.leftInset + this.rightInset - newInsets.left - newInsets.right)/2f }
                else -> { 0f }
            }

            val newY = this.y + when(this.gravity and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.TOP -> { 0f }
                Gravity.BOTTOM -> { (this.bottomInset - newInsets.bottom).toFloat() }
                Gravity.CENTER_VERTICAL -> { (this.topInset + this.bottomInset - newInsets.top - newInsets.bottom)/2f }
                else -> { 0f }
            }

            return OriginParams(newX, newY, this.r, this.gravity, newInsets.left, newInsets.top, newInsets.right, newInsets.bottom)
        }

        override fun describeContents() = 0

        override fun writeToParcel(dest: Parcel?, flags: Int) {
            dest?.apply {
                this.writeFloat(x)
                this.writeFloat(y)
                this.writeFloat(r)
                this.writeInt(gravity)
                this.writeInt(leftInset)
                this.writeInt(topInset)
                this.writeInt(rightInset)
                this.writeInt(bottomInset)
            }
        }

        companion object CREATOR : Parcelable.Creator<OriginParams> {
            override fun createFromParcel(parcel: Parcel): OriginParams {
                return OriginParams(
                        parcel.readFloat(),
                        parcel.readFloat(),
                        parcel.readFloat(),
                        parcel.readInt(),
                        parcel.readInt(),
                        parcel.readInt(),
                        parcel.readInt(),
                        parcel.readInt())
            }

            override fun newArray(size: Int): Array<OriginParams?> = arrayOfNulls(size)
        }

    }

    companion object {
        private const val PROP_FAB_X = "com.grouptuity.grouptuity:CircularRevealFABTransition:fab_x"
        private const val PROP_FAB_Y = "com.grouptuity.grouptuity:CircularRevealFABTransition:fab_y"
        private const val PROP_FAB_R = "com.grouptuity.grouptuity:CircularRevealFABTransition:fab_r"
    }
}