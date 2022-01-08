package com.grouptuity.grouptuity.ui.custom.views

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.view.setPadding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.entities.Contact
import com.grouptuity.grouptuity.databinding.ContactIconBinding
import kotlin.math.roundToInt


class ContactIcon @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0): LinearLayout(context, attrs, defStyleAttr) {
    private var _binding: ContactIconBinding? = ContactIconBinding.inflate(LayoutInflater.from(context), this, true)
    private val binding get() = _binding!!
    val image get() = _binding!!.image
    private val backgroundColor: Int
    private val strokeWidth: Float
    private val useInitialsPlaceholder: Boolean
    private val desaturationColorFilter = ColorMatrixColorFilter(ColorMatrix().also { it.setSaturation(0f) })
    private val iconFlipAnimationDuration = resources.getInteger(R.integer.card_flip_time_full).toLong()
    private val paddingScalingFactor = 1f/(240f*resources.displayMetrics.density) // Make padding 20% of view when height is 48dp; 10% when height is 24dp
    private var selectable: Boolean
    private var loadFinishedCallback: ((Boolean) -> Unit)? = null

    private var displayedContact: Contact? = null
    var isPhotoVisible = false
        private set

    private var selectionAnimation = AnimatorSet()
    private var selectionState = DESELECTED

    init {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.ContactIcon)
        backgroundColor = attributes.getColor(R.styleable.ContactIcon_backgroundColor,
            TypedValue().also { context.theme.resolveAttribute(R.attr.colorTertiary, it, true) }.data)
        binding.image.strokeColor = attributes.getColorStateList(R.styleable.ContactIcon_borderColor) ?:
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_enabled)),
                intArrayOf(TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnSurfaceLowEmphasis, it, true) }.data))
        strokeWidth = attributes.getDimensionPixelSize(R.styleable.ContactIcon_borderWidth, resources.displayMetrics.density.toInt()).toFloat() // default = 1dp
        binding.text.setTextColor(attributes.getColor(R.styleable.ContactIcon_textColor,
            TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnTertiary, it, true) }.data))
        useInitialsPlaceholder = attributes.getBoolean(R.styleable.ContactIcon_useInitialsPlaceholder, true)
        selectable = attributes.getBoolean(R.styleable.ContactIcon_selectable, true)

        // Adjustment to padding to avoid cutting off the stroke
        binding.image.setPadding((strokeWidth / 2).roundToInt())
        binding.checkmark.setPadding((strokeWidth / 4).roundToInt())

        buildAnimation()

        attributes.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        layoutParams?.apply {
            binding.text.setPadding((measuredHeight * measuredHeight * paddingScalingFactor).toInt())
        }
    }

    fun setSelectable(enabled: Boolean) { selectable = enabled }

    fun setContact(contact: Contact, selected: Boolean = false) {
        applyContactToView(contact.getInitials(), contact.photoUri, contact.visibility)

        if(selectable) {
            if(contact.lookupKey == displayedContact?.lookupKey) {
                when(selectionState) {
                    SELECTED, ANIMATING_SELECTION -> {
                        if (selected) return else animateDeselection()
                    }
                    DESELECTED, ANIMATING_DESELECTION -> {
                        if (selected) animateSelection() else return
                    }
                }
            } else {
                setSelectionState(selected)
            }
        } else {
            setSelectionState(false)
        }

        displayedContact = contact
    }

    private fun applyContactToView(initials: String, photoUri: String?, visibility: Int) {
        if(photoUri.isNullOrBlank()) {
            Glide.with(this).clear(binding.image)
            isPhotoVisible = false

            binding.image.strokeWidth = 0f

            if(useInitialsPlaceholder) {

                binding.text.text = initials
                binding.image.setColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY)
                binding.image.setImageResource(R.drawable.person_blank)
            }
            else {
                binding.text.text = ""

                if(visibility == Contact.HIDDEN) {
                    binding.image.setImageResource(R.drawable.person_hidden)
                    binding.image.colorFilter = desaturationColorFilter
                } else {
                    binding.image.setImageResource(R.drawable.person)
                    binding.image.clearColorFilter()
                }
            }
        }
        else {
            if(photoUri == displayedContact?.photoUri) {
                if(visibility == Contact.HIDDEN) {
                    binding.image.colorFilter = desaturationColorFilter
                } else {
                    binding.image.clearColorFilter()
                }
                return // photo already loaded
            }

            binding.text.text = initials
            binding.image.strokeWidth = 0f
            binding.image.setColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY)

            Glide.with(this)
                    .load(Uri.parse(photoUri))
                    .placeholder(R.drawable.person_blank)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            loadFinishedCallback?.let { it(false) }
                            return true
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            binding.image.strokeWidth = strokeWidth
                            binding.text.text = ""

                            if (visibility == Contact.HIDDEN) {
                                binding.image.colorFilter = desaturationColorFilter
                            } else {
                                binding.image.clearColorFilter()
                            }

                            isPhotoVisible = true
                            loadFinishedCallback?.let { it(true) }
                            return false
                        }
                    })
                    .into(binding.image)
        }
    }

    private fun setSelectionState(selected: Boolean) {
        selectionAnimation.cancel()

        if(selected) {
            binding.image.rotationY = -180f
            binding.image.alpha = 0f
            binding.text.rotationY = -180f
            binding.text.alpha = 0f
            binding.checkmark.rotationY = 0f
            binding.checkmark.alpha = 1f
            selectionState = SELECTED
        }
        else {
            binding.image.rotationY = 0f
            binding.image.alpha = 1f
            binding.text.rotationY = 0f
            binding.text.alpha = 1f
            binding.checkmark.rotationY = 180f
            binding.checkmark.alpha = 0f
            selectionState = DESELECTED
        }
    }

    private fun buildAnimation() {
        val imageRotateOut = ObjectAnimator.ofFloat(binding.image, "rotationY", 0f, -180f)
        imageRotateOut.duration = iconFlipAnimationDuration

        val textRotateOut = ObjectAnimator.ofFloat(binding.text, "rotationY", 0f, -180f)
        textRotateOut.duration = iconFlipAnimationDuration

        val checkmarkImageRotateIn = ObjectAnimator.ofFloat(binding.checkmark, "rotationY", 180f, 0f)
        checkmarkImageRotateIn.duration = iconFlipAnimationDuration

        /* HACK: duration needs to run for the whole animation set or the property resets to its
         * initial value when reverse() is called. To simulate an instantaneous switch in alpha
         * without using keyframes, multiple values are used compress the transition into the middle
         * of the animation set.
         */
        val imageAlphaOut = ObjectAnimator.ofFloat(binding.image, "alpha", 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f)
        imageAlphaOut.duration = iconFlipAnimationDuration

        val textAlphaOut = ObjectAnimator.ofFloat(binding.text, "alpha", 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f)
        textAlphaOut.duration = iconFlipAnimationDuration

        val checkmarkImageAlphaIn = ObjectAnimator.ofFloat(binding.checkmark, "alpha", 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)
        checkmarkImageAlphaIn.duration = iconFlipAnimationDuration

        selectionAnimation = AnimatorSet()
        selectionAnimation.playTogether(imageRotateOut,
                imageAlphaOut,
                textRotateOut,
                textAlphaOut,
                checkmarkImageAlphaIn,
                checkmarkImageRotateIn)
        selectionAnimation.interpolator = AccelerateDecelerateInterpolator()

        selectionAnimation.addListener(object : Animator.AnimatorListener {
            var active = false

            override fun onAnimationRepeat(p0: Animator?) {}
            override fun onAnimationEnd(p0: Animator?) {
                if (active) {
                    selectionState = when (selectionState) {
                        ANIMATING_SELECTION -> SELECTED
                        ANIMATING_DESELECTION -> DESELECTED
                        else -> SELECTED // should never be called
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

    private fun animateSelection() {
        when (selectionState) {
            SELECTED, ANIMATING_SELECTION -> {
                return
            }
            ANIMATING_DESELECTION -> {
                selectionState = ANIMATING_SELECTION
                selectionAnimation.currentPlayTime = selectionAnimation.currentPlayTime
                selectionAnimation.start()
            }
            DESELECTED -> {
                selectionState = ANIMATING_SELECTION
                selectionAnimation.start()
            }
        }
    }

    private fun animateDeselection(){
        when (selectionState) {
            DESELECTED, ANIMATING_DESELECTION -> {
                return
            }
            ANIMATING_SELECTION -> {
                selectionState = ANIMATING_DESELECTION
                selectionAnimation.currentPlayTime = selectionAnimation.currentPlayTime
                selectionAnimation.reverse()
            }
            SELECTED -> {
                selectionState = ANIMATING_DESELECTION
                selectionAnimation.reverse()
            }
        }
    }

    companion object {
        private const val DESELECTED = 0
        private const val SELECTED = 1
        private const val ANIMATING_SELECTION = 2
        private const val ANIMATING_DESELECTION = 3
    }
}