package com.grouptuity.grouptuity.ui.billsplit

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_ID
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Payment
import com.grouptuity.grouptuity.data.PaymentMethod
import com.grouptuity.grouptuity.databinding.FragPaymentsBinding
import com.grouptuity.grouptuity.databinding.FragPaymentsListitemBinding
import com.grouptuity.grouptuity.ui.custom.views.RecyclerViewBottomOffset
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat


class PaymentsFragment: Fragment() {
    companion object {
        @JvmStatic
        fun newInstance() = PaymentsFragment()
    }

    private var binding by setNullOnDestroy<FragPaymentsBinding>()
    private lateinit var paymentsViewModel: PaymentsViewModel
    private lateinit var backPressedCallback: OnBackPressedCallback

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        paymentsViewModel = ViewModelProvider(requireActivity()).get(PaymentsViewModel::class.java)
        binding = FragPaymentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Intercept back pressed events to allow fragment-specific behaviors
        backPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (paymentsViewModel.handleOnBackPressed()) {
                    backPressedCallback.isEnabled = false
                    requireActivity().onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)


        val recyclerAdapter = PaymentRecyclerViewAdapter(
            requireContext(),
            paymentsViewModel,
            object: PaymentRecyclerViewAdapter.PaymentRecyclerViewListener {
                override fun onClickActivePaymentMethodIcon(payment: Payment) {
                    Log.e("onClickActivePaymentMethodIcon", payment.payer.name +" "+ payment.payee.name)
                    paymentsViewModel.setActivePayment(payment)
                }

                override fun onClickCloseButton(payment: Payment) {
                    Log.e("onClickCloseButton", payment.payer.name +" "+ payment.payee.name)
                    paymentsViewModel.setActivePayment(null)
                }

                override fun onClickPaymentMethod(payment: Payment, paymentMethod: PaymentMethod) {
                    Log.e("onClickPaymentMethod", payment.payer.name +" "+ payment.payee.name)
                    paymentsViewModel.setPaymentMethodPreference(payment, paymentMethod)
                }
            }
        )

        binding.list.apply {
            adapter = recyclerAdapter

            //TODO starttransition getting called on all 4 tab fragments?
            requireParentFragment().apply {
                postponeEnterTransition()
                viewTreeObserver.addOnPreDrawListener {
                    startPostponedEnterTransition()
                    true
                }
            }

            // Add a spacer to the last item in the list to ensure it is not cut off when the toolbar
            // and floating action button are visible
            addItemDecoration(RecyclerViewBottomOffset(resources.getDimension(R.dimen.recyclerview_bottom_offset).toInt()))

            itemAnimator = object: DefaultItemAnimator() {
                override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder, payloads: MutableList<Any>): Boolean { return true }
            }
        }

        paymentsViewModel.paymentsData.observe(viewLifecycleOwner) { paymentsData ->
            lifecycleScope.launch { recyclerAdapter.updateDataSet(paymentsData) }

            // TODO FAb toggle based on state and payments

            binding.noPaymentsHint.visibility = if (paymentsData.first.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}


private class PaymentRecyclerViewAdapter(val context: Context,
                                         val paymentViewModel: PaymentsViewModel,
                                         val listener: PaymentRecyclerViewListener): RecyclerView.Adapter<PaymentRecyclerViewAdapter.ViewHolder>() {
    var mPaymentList = emptyList<Triple<Payment, Long?, Int>>()
    var mActivePaymentIdAndState = PaymentsViewModel.INITIAL_STABLE_ID_AND_STATE
    var animationStartFlag = false
    var listAnimationStartTime = 0L

    private val activePaymentMethodIconStrokeColor = ColorStateList.valueOf(
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnBackgroundLowEmphasis, it, true) }.data)
    private val iconColorTint = ColorStateList.valueOf(
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnTertiary, it, true) }.data)
    private val iconColorOnBackgroundTint = ColorStateList.valueOf(
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnBackgroundMediumEmphasis, it, true) }.data)

    private val surfaceBackground = TypedValue().also { context.theme.resolveAttribute(R.attr.colorSurface, it, true) }.data
    private val tertiaryBackground = TypedValue().also { context.theme.resolveAttribute(R.attr.colorTertiary, it, true) }.data
    private val selectableBackground = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true) }.data
    private val paymentMethodIconPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36F, context.resources.displayMetrics).toInt()
    private val enlargedPayerTextSizeSpFrac = 1.22f
    private val animationDuration = context.resources.getInteger(R.integer.card_flip_time_full).toLong()

    init { setHasStableIds(true) }

    interface PaymentRecyclerViewListener {
        fun onClickActivePaymentMethodIcon(payment: Payment)
        fun onClickCloseButton(payment: Payment)
        fun onClickPaymentMethod(payment: Payment, paymentMethod: PaymentMethod)
    }

    inner class ViewHolder(val viewBinding: FragPaymentsListitemBinding): RecyclerView.ViewHolder(viewBinding.root) {
        val peerToPeerMethodButtons = mutableListOf<Triple<Int, Int, Int>>()
        val nonPeerToPeerMethodButtons = mutableListOf<Triple<Int, Int, Int>>()
        var viewHolderPaymentStableId: Long? = null
        var viewHolderState: Int = R.id.default_state
        var viewHolderAnimationStartTime = 0L

        lateinit var backgroundAnimator: ObjectAnimator
        lateinit var fadeOutExpandingLayoutAnimator: ObjectAnimator
        lateinit var fadeOutFlipIconAnimator: ValueAnimator
        lateinit var fadeOutPaymentDetailsAnimator: ValueAnimator
        lateinit var fadeInSelectionInstructionAnimator: ObjectAnimator
        lateinit var fadeOutContactAnimator: ValueAnimator
        lateinit var enlargeContactAnimator: ValueAnimator

        fun applyState(newState: Int) {
            backgroundAnimator.cancel()
            fadeOutExpandingLayoutAnimator.cancel()
            fadeOutFlipIconAnimator.cancel()
            fadeOutPaymentDetailsAnimator.cancel()
            fadeInSelectionInstructionAnimator.cancel()
            fadeOutContactAnimator.cancel()
            enlargeContactAnimator.cancel()

            when (newState) {
                R.id.default_state -> {
                    viewBinding.backgroundView.setBackgroundColor(surfaceBackground)
                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                    viewBinding.paymentMethodFlipButton.setDeselected()
                    viewBinding.paymentMethodFlipButton.alpha = 1f
                    viewBinding.payInstructions.alpha = 1f
                    viewBinding.amount.alpha = 1f
                    viewBinding.selectionInstruction.alpha = 0f
                    viewBinding.payerContactIcon.alpha = 1f
                    viewBinding.payer.alpha = 1f

                    viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = viewBinding.paymentMethodFlipButton.width + viewBinding.paymentMethodFlipButton.marginStart
                    }

                    viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        height = viewBinding.payInstructions.height + viewBinding.payInstructions.marginBottom
                    }

                    viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = viewBinding.amount.width
                    }

                    viewBinding.payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        marginStart = viewBinding.payerReference.marginStart
                    }
                    viewBinding.payer.scaleX = 1f
                    viewBinding.payer.scaleY = 1f
                }
                R.id.selecting_method_state -> {
                    viewBinding.backgroundView.setBackgroundColor(tertiaryBackground)
                    viewBinding.paymentMethodsExpandingLayout.setExpanded()
                    viewBinding.paymentMethodsExpandingLayout.alpha = 1f
                    viewBinding.paymentMethodFlipButton.setSelected()
                    viewBinding.paymentMethodFlipButton.alpha = 1f
                    viewBinding.payInstructions.alpha = 0f
                    viewBinding.amount.alpha = 0f
                    viewBinding.selectionInstruction.alpha = 0f
                    viewBinding.payerContactIcon.alpha = 0f
                    viewBinding.payer.alpha = 0f

                    viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = viewBinding.paymentMethodFlipButton.width + viewBinding.paymentMethodFlipButton.marginStart
                    }

                    viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        height = viewBinding.payInstructions.height + viewBinding.payInstructions.marginBottom
                    }

                    viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = viewBinding.amount.width
                    }

                    viewBinding.payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        marginStart = viewBinding.payerReference.marginStart
                    }
                    viewBinding.payer.scaleX = 1f
                    viewBinding.payer.scaleY = 1f
                }
                R.id.showing_instructions_state -> {
                    viewBinding.backgroundView.setBackgroundColor(tertiaryBackground)
                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                    viewBinding.paymentMethodFlipButton.setSelected()
                    viewBinding.paymentMethodFlipButton.alpha = 1f
                    viewBinding.payInstructions.alpha = 0f
                    viewBinding.amount.alpha = 0f
                    viewBinding.selectionInstruction.alpha = 1f
                    viewBinding.payerContactIcon.alpha = 0f
                    viewBinding.payer.alpha = 0f
                }
                R.id.candidate_state -> {
                    viewBinding.backgroundView.setBackgroundColor(surfaceBackground)
                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                    viewBinding.paymentMethodFlipButton.setDeselected()
                    viewBinding.paymentMethodFlipButton.alpha = 0f
                    viewBinding.payInstructions.alpha = 0f
                    viewBinding.amount.alpha = 0f
                    viewBinding.selectionInstruction.alpha = 0f
                    viewBinding.payerContactIcon.alpha = 1f
                    viewBinding.payer.alpha = 1f

                    viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = 1
                    }

                    viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        height = 1
                    }

                    viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = 1
                    }

                    viewBinding.payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        marginStart = 2 * viewBinding.payerReference.marginStart
                    }
                    viewBinding.payer.scaleX = enlargedPayerTextSizeSpFrac
                    viewBinding.payer.scaleY = enlargedPayerTextSizeSpFrac
                }
                R.id.ineligible_state -> {
                    viewBinding.backgroundView.setBackgroundColor(surfaceBackground)
                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                    viewBinding.paymentMethodFlipButton.setDeselected()
                    viewBinding.paymentMethodFlipButton.alpha = 0f
                    viewBinding.payInstructions.alpha = 0f
                    viewBinding.amount.alpha = 0f
                    viewBinding.selectionInstruction.alpha = 0f
                    viewBinding.payerContactIcon.alpha = 1f
                    viewBinding.payer.alpha = 1f

                    viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = 1
                    }

                    viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        height = 1
                    }

                    viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = 1
                    }

                    viewBinding.payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        marginStart = 2 * viewBinding.payerReference.marginStart
                    }
                    viewBinding.payer.scaleX = enlargedPayerTextSizeSpFrac
                    viewBinding.payer.scaleY = enlargedPayerTextSizeSpFrac
                }
            }
        }

        fun animateStateChange(oldState: Int, newState: Int, initialProgress: Float) {
            backgroundAnimator.cancel()
            fadeOutExpandingLayoutAnimator.cancel()
            fadeOutFlipIconAnimator.cancel()
            fadeOutPaymentDetailsAnimator.cancel()
            fadeInSelectionInstructionAnimator.cancel()
            fadeOutContactAnimator.cancel()
            enlargeContactAnimator.cancel()

            when (newState) {
                R.id.default_state -> {
                    when (oldState) {
                        R.id.selecting_method_state -> {
                            backgroundAnimator.setCurrentFraction(1f - initialProgress)
                            backgroundAnimator.reverse()

                            viewBinding.paymentMethodsExpandingLayout.alpha = 1f
                            viewBinding.paymentMethodsExpandingLayout.collapse(initialProgress)

                            viewBinding.paymentMethodFlipButton.alpha = 1f
                            viewBinding.paymentMethodFlipButton.animateDeselection(initialProgress)

                            fadeOutPaymentDetailsAnimator.setCurrentFraction(1f - initialProgress)
                            fadeOutPaymentDetailsAnimator.reverse()

                            fadeOutContactAnimator.setCurrentFraction(1f - initialProgress)
                            fadeOutContactAnimator.reverse()

                            viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                width = viewBinding.paymentMethodFlipButton.width + viewBinding.paymentMethodFlipButton.marginStart
                            }

                            viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                height = viewBinding.payInstructions.height + viewBinding.payInstructions.marginBottom
                            }

                            viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                width = viewBinding.amount.width
                            }

                            viewBinding.payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                marginStart = viewBinding.payerReference.marginStart
                            }
                            viewBinding.payer.scaleX = 1f
                            viewBinding.payer.scaleY = 1f

                            viewBinding.selectionInstruction.alpha = 0f
                        }
                        R.id.showing_instructions_state -> {
                            backgroundAnimator.setCurrentFraction(1f - initialProgress)
                            backgroundAnimator.reverse()

                            viewBinding.paymentMethodsExpandingLayout.setCollapsed()

                            viewBinding.paymentMethodFlipButton.alpha = 1f
                            viewBinding.paymentMethodFlipButton.animateDeselection(initialProgress)

                            fadeOutPaymentDetailsAnimator.setCurrentFraction(1f - initialProgress)
                            fadeOutPaymentDetailsAnimator.reverse()

                            fadeOutContactAnimator.setCurrentFraction(1f - initialProgress)
                            fadeOutContactAnimator.reverse()

                            viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                width = viewBinding.paymentMethodFlipButton.width + viewBinding.paymentMethodFlipButton.marginStart
                            }

                            viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                height = viewBinding.payInstructions.height + viewBinding.payInstructions.marginBottom
                            }

                            viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                width = viewBinding.amount.width
                            }

                            viewBinding.payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                marginStart = viewBinding.payerReference.marginStart
                            }
                            viewBinding.payer.scaleX = 1f
                            viewBinding.payer.scaleY = 1f

                            fadeInSelectionInstructionAnimator.setCurrentFraction(1f - initialProgress)
                            fadeInSelectionInstructionAnimator.reverse()
                        }
                        R.id.candidate_state -> {
                            viewBinding.backgroundView.setBackgroundColor(surfaceBackground)

                            viewBinding.paymentMethodsExpandingLayout.setCollapsed()

                            viewBinding.paymentMethodFlipButton.setDeselected()
                            fadeOutFlipIconAnimator.setCurrentFraction(1f - initialProgress)
                            fadeOutFlipIconAnimator.reverse()

                            fadeOutPaymentDetailsAnimator.setCurrentFraction(1f - initialProgress)
                            fadeOutPaymentDetailsAnimator.reverse()

                            viewBinding.payerContactIcon.alpha = 1f
                            viewBinding.payer.alpha = 1f

                            enlargeContactAnimator.setCurrentFraction(1f - initialProgress)
                            enlargeContactAnimator.reverse()

                            viewBinding.selectionInstruction.alpha = 0f
                        }
                        R.id.ineligible_state -> {
                            viewBinding.backgroundView.setBackgroundColor(surfaceBackground)

                            viewBinding.paymentMethodsExpandingLayout.setCollapsed()

                            viewBinding.paymentMethodFlipButton.setDeselected()
                            fadeOutFlipIconAnimator.setCurrentFraction(1f - initialProgress)
                            fadeOutFlipIconAnimator.reverse()

                            fadeOutPaymentDetailsAnimator.setCurrentFraction(1f - initialProgress)
                            fadeOutPaymentDetailsAnimator.reverse()

                            viewBinding.payerContactIcon.alpha = 1f
                            viewBinding.payer.alpha = 1f

                            enlargeContactAnimator.setCurrentFraction(1f - initialProgress)
                            enlargeContactAnimator.reverse()

                            viewBinding.selectionInstruction.alpha = 0f
                        }
                    }
                }
                R.id.selecting_method_state -> {
                    backgroundAnimator.setCurrentFraction(initialProgress)
                    backgroundAnimator.start()

                    viewBinding.paymentMethodsExpandingLayout.alpha = 1f
                    viewBinding.paymentMethodsExpandingLayout.expand(initialProgress)

                    viewBinding.paymentMethodFlipButton.alpha = 1f
                    viewBinding.paymentMethodFlipButton.animateSelection(initialProgress)

                    fadeOutPaymentDetailsAnimator.setCurrentFraction(initialProgress)
                    fadeOutPaymentDetailsAnimator.start()

                    fadeOutContactAnimator.setCurrentFraction(initialProgress)
                    fadeOutContactAnimator.start()

                    viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = viewBinding.paymentMethodFlipButton.width + viewBinding.paymentMethodFlipButton.marginStart
                    }

                    viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        height = viewBinding.payInstructions.height + viewBinding.payInstructions.marginBottom
                    }

                    viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        width = viewBinding.amount.width
                    }

                    viewBinding.payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        marginStart = viewBinding.payerReference.marginStart
                    }
                    viewBinding.payer.scaleX = 1f
                    viewBinding.payer.scaleY = 1f

                    viewBinding.selectionInstruction.alpha = 0f
                }
                R.id.showing_instructions_state -> {
                    viewBinding.backgroundView.setBackgroundColor(tertiaryBackground)

                    viewBinding.paymentMethodsExpandingLayout.setExpanded()
                    fadeOutExpandingLayoutAnimator.setCurrentFraction(initialProgress)
                    fadeOutExpandingLayoutAnimator.start()

                    viewBinding.paymentMethodFlipButton.alpha = 1f
                    viewBinding.paymentMethodFlipButton.setSelected()

                    viewBinding.payInstructions.alpha = 0f
                    viewBinding.amount.alpha = 0f

                    viewBinding.payerContactIcon.alpha = 0f
                    viewBinding.payer.alpha = 0f

                    fadeInSelectionInstructionAnimator.setCurrentFraction(initialProgress)
                    fadeInSelectionInstructionAnimator.start()
                }
                R.id.candidate_state -> {
                    viewBinding.backgroundView.setBackgroundColor(surfaceBackground)

                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()

                    viewBinding.paymentMethodFlipButton.setDeselected()
                    fadeOutFlipIconAnimator.setCurrentFraction(initialProgress)
                    fadeOutFlipIconAnimator.start()

                    fadeOutPaymentDetailsAnimator.setCurrentFraction(initialProgress)
                    fadeOutPaymentDetailsAnimator.start()

                    viewBinding.payerContactIcon.alpha = 1f
                    viewBinding.payer.alpha = 1f

                    enlargeContactAnimator.setCurrentFraction(initialProgress)
                    enlargeContactAnimator.start()

                    viewBinding.selectionInstruction.alpha = 0f
                }
                R.id.ineligible_state -> {
                    viewBinding.backgroundView.setBackgroundColor(surfaceBackground)

                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()

                    viewBinding.paymentMethodFlipButton.setDeselected()
                    fadeOutFlipIconAnimator.setCurrentFraction(initialProgress)
                    fadeOutFlipIconAnimator.start()

                    fadeOutPaymentDetailsAnimator.setCurrentFraction(initialProgress)
                    fadeOutPaymentDetailsAnimator.start()

                    viewBinding.payerContactIcon.alpha = 1f
                    viewBinding.payer.alpha = 1f

                    enlargeContactAnimator.setCurrentFraction(initialProgress)
                    enlargeContactAnimator.start()

                    viewBinding.selectionInstruction.alpha = 0f
                }
            }
        }
    }

    override fun getItemCount() = mPaymentList.size

    override fun getItemId(position: Int) = paymentViewModel.getPaymentStableId(mPaymentList[position].first) ?: NO_ID

    @SuppressLint("Recycle")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = ViewHolder(FragPaymentsListitemBinding.inflate(LayoutInflater.from(context), parent, false))

        viewHolder.viewBinding.apply {
            activePaymentMethodIcon.setOnClickListener {
                if (viewHolder.viewHolderState == R.id.default_state) {
                    listener.onClickActivePaymentMethodIcon((viewHolder.itemView.tag as Triple<*, *, *>).first as Payment)
                }
            }

            closePaymentMethodListButton.setOnClickListener {
                if (viewHolder.viewHolderState == R.id.selecting_method_state ||
                    viewHolder.viewHolderState == R.id.showing_instructions_state) {
                    listener.onClickCloseButton((viewHolder.itemView.tag as Triple<*, *, *>).first as Payment)
                }
            }

            viewHolder.backgroundAnimator = ObjectAnimator.ofArgb(
                backgroundView,
                "backgroundColor",
                surfaceBackground,
                tertiaryBackground).also { it.duration = animationDuration }

            viewHolder.fadeOutExpandingLayoutAnimator = ObjectAnimator.ofFloat(
                paymentMethodsExpandingLayout,
                "alpha",
                1f,
                0f).also { it.duration = animationDuration }

            viewHolder.fadeOutFlipIconAnimator = ValueAnimator.ofFloat(1f, 0f).also {
                it.duration = animationDuration
                it.addUpdateListener { animator ->
                    viewHolder.viewBinding.paymentMethodFlipButton.alpha =
                        (1f - 5f*animator.animatedFraction).coerceIn(0f, 1f)
                }
            }

            viewHolder.fadeOutPaymentDetailsAnimator = ValueAnimator.ofFloat(1f, 0f).also {
                it.duration = animationDuration
                it.addUpdateListener { animator ->
                    val progress = (1f - 5f*animator.animatedFraction).coerceIn(0f, 1f)
                    viewHolder.viewBinding.payInstructions.alpha = progress
                    viewHolder.viewBinding.amount.alpha = progress
                }
            }

            viewHolder.fadeInSelectionInstructionAnimator = ObjectAnimator.ofFloat(
                selectionInstruction,
                "alpha",
                0f,
                1f).also { it.duration = animationDuration }

            viewHolder.fadeOutContactAnimator = ValueAnimator.ofFloat(1f, 0f).also {
                it.duration = animationDuration
                it.addUpdateListener { animator ->
                    val progress = (1f - 5f*animator.animatedFraction).coerceIn(0f, 1f)
                    viewHolder.viewBinding.payerContactIcon.alpha = progress
                    viewHolder.viewBinding.payer.alpha = progress
                }
            }

            viewHolder.enlargeContactAnimator = ValueAnimator.ofFloat(1f, 0f).also {
                it.duration = animationDuration
                it.addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float

                    viewHolder.viewBinding.apply {
                        paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            width = ((paymentMethodFlipButton.width + paymentMethodFlipButton.marginStart)
                                    * (progress)).toInt().coerceAtLeast(1)
                        }

                        payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            height = ((payInstructions.height + payInstructions.marginBottom) * (progress)).toInt().coerceAtLeast(1)
                        }

                        amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            width = (amount.width * (progress)).toInt().coerceAtLeast(1)
                        }

                        payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            marginStart = (payerReference.marginStart * (2f - progress)).toInt()
                        }

                        payer.scaleX = (1f - progress)*(enlargedPayerTextSizeSpFrac - 1f) + 1f
                        payer.scaleY = (1f - progress)*(enlargedPayerTextSizeSpFrac - 1f) + 1f
                    }
                }
            }

            for (method in PaymentMethod.values()) {
                val preSpacerId = View.generateViewId()
                paymentMethodsExpandingLayout.addView(Space(context).also {
                    it.id = preSpacerId
                    it.layoutParams = LinearLayout.LayoutParams(0, 1, 0.5F)
                })

                val methodButtonId = View.generateViewId()
                paymentMethodsExpandingLayout.addView(ImageView(context).also {
                    it.id = methodButtonId
                    it.layoutParams = LinearLayout.LayoutParams(paymentMethodIconPx, paymentMethodIconPx)
                    it.setBackgroundColor(selectableBackground)
                    if (method.isIconColorless) {
                        it.imageTintList = iconColorTint
                    }
                    it.setImageResource(method.paymentIconId)
                    it.setOnClickListener {
                        listener.onClickPaymentMethod((viewHolder.itemView.tag as Triple<*, *, *>).first as Payment, method)
                    }
                })

                val postSpacerId = View.generateViewId()
                paymentMethodsExpandingLayout.addView(Space(context).also {
                    it.id = postSpacerId
                    it.layoutParams = LinearLayout.LayoutParams(0, 1, 0.5F)
                })

                if (method.isPeerToPeer) {
                    viewHolder.peerToPeerMethodButtons.add(Triple(preSpacerId, methodButtonId, postSpacerId))
                } else {
                    viewHolder.nonPeerToPeerMethodButtons.add(Triple(preSpacerId, methodButtonId, postSpacerId))
                }
            }
        }

        viewHolder.itemView.doOnPreDraw {
            viewHolder.viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = viewHolder.viewBinding.payInstructions.height + viewHolder.viewBinding.payInstructions.marginBottom
            }

            viewHolder.viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                width = viewHolder.viewBinding.amount.width
            }

            viewHolder.viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                width = viewHolder.viewBinding.paymentMethodFlipButton.width + viewHolder.viewBinding.paymentMethodFlipButton.marginStart
            }
        }

        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.apply {
            val (newPayment, newStableId, newState) = mPaymentList[position]
            itemView.tag = mPaymentList[position] // store updated payment and state

            viewBinding.paymentMethodsExpandingLayout.setExpandedSize(
                viewBinding.paymentCardView.width
                        - viewBinding.paymentMethodFlipButton.width
                        - viewBinding.amount.marginEnd
                        - viewBinding.payInstructions.marginStart)

            viewBinding.paymentMethodsExpandingLayout.df = newPayment.payer.name == "Alan Leung" || newPayment.payer.name == "Geoffrey Wing"


            viewBinding.activePaymentMethodIcon.iconTint =
                if (newPayment.method.isIconColorless) { iconColorOnBackgroundTint } else { null }
            viewBinding.activePaymentMethodIcon.icon =
                ContextCompat.getDrawable(context, newPayment.method.paymentIconId)

            viewBinding.amount.text = NumberFormat.getCurrencyInstance().format(newPayment.amount)
            viewBinding.amountPlaceholder.text = NumberFormat.getCurrencyInstance().format(newPayment.amount)

            when {
                (newPayment.payer.isCashPool()) -> {
                    viewBinding.payer.text = newPayment.payee.name
                    viewBinding.payerContactIcon.setContact(newPayment.payee.contact, false)
                    viewBinding.payInstructions.text = context.resources.getString(R.string.payments_instruction_from_cash_pool)
                    viewBinding.activePaymentMethodIcon.isClickable = false
                    viewBinding.activePaymentMethodIcon.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
                    viewBinding.closePaymentMethodListButton.isClickable = false
                }
                (newPayment.payee.isCashPool()) -> {
                    viewBinding.payer.text = newPayment.payer.name
                    viewBinding.payerContactIcon.setContact(newPayment.payer.contact, false)
                    viewBinding.payInstructions.text = context.resources.getString(R.string.payments_instruction_into_cash_pool)
                    viewBinding.activePaymentMethodIcon.isClickable = false
                    viewBinding.activePaymentMethodIcon.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
                    viewBinding.closePaymentMethodListButton.isClickable = false
                }
                (newPayment.payee.isRestaurant()) -> {
                    viewBinding.payer.text = newPayment.payer.name
                    viewBinding.payerContactIcon.setContact(newPayment.payer.contact, false)
                    viewBinding.activePaymentMethodIcon.isClickable = true
                    viewBinding.activePaymentMethodIcon.strokeColor = activePaymentMethodIconStrokeColor
                    viewBinding.closePaymentMethodListButton.isClickable = true

                    val surrogate = newPayment.surrogate
                    viewBinding.payInstructions.text = when {
                        surrogate == null -> {
                            context.resources.getString(
                                newPayment.method.paymentInstructionStringId,
                                context.resources.getString(
                                    R.string.payments_instruction_restaurant_payee
                                )
                            )
                        }
                        surrogate.isCashPool() -> {
                            context.resources.getString(R.string.payments_instruction_into_cash_pool)
                        }
                        else -> {
                            context.resources.getString(
                                newPayment.method.paymentInstructionStringId,
                                surrogate.name
                            )
                        }
                    }

                    for (viewIds in nonPeerToPeerMethodButtons) {
                        val (preSpacerId, methodButtonId, postSpacerId) = viewIds
                        viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(preSpacerId).visibility = View.VISIBLE
                        viewBinding.paymentMethodsExpandingLayout.findViewById<ImageView>(methodButtonId).visibility = View.VISIBLE
                        viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(postSpacerId).visibility = View.VISIBLE
                    }
                }
                else -> {
                    viewBinding.payer.text = newPayment.payer.name
                    viewBinding.payerContactIcon.setContact(newPayment.payer.contact, false)
                    viewBinding.payInstructions.text = context.resources.getString(newPayment.method.paymentInstructionStringId, newPayment.payee.name)

                    viewBinding.activePaymentMethodIcon.isClickable = true
                    viewBinding.activePaymentMethodIcon.strokeColor = activePaymentMethodIconStrokeColor
                    viewBinding.closePaymentMethodListButton.isClickable = true

                    for (viewIds in nonPeerToPeerMethodButtons) {
                        val (preSpacerId, methodButtonId, postSpacerId) = viewIds
                        viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(preSpacerId).visibility = View.GONE
                        viewBinding.paymentMethodsExpandingLayout.findViewById<ImageView>(methodButtonId).visibility = View.GONE
                        viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(postSpacerId).visibility = View.GONE
                    }
                }
            }

            val timeNow = System.currentTimeMillis()
            if (animationStartFlag) {
                // Record start time for new list animation
                animationStartFlag = false
                listAnimationStartTime = timeNow
            }

            if (viewHolderPaymentStableId == newStableId) {
                if (viewHolderState == newState) {
                    // ViewHolder is already in or transitioning to the desired state
                } else {
                    // ViewHolder is bound to the same payment, but state has changed
                    val viewHolderElapsedTime = timeNow - viewHolderAnimationStartTime

                    viewHolderAnimationStartTime = if(viewHolderElapsedTime >= animationDuration) {
                        listAnimationStartTime
                    } else {
                        listAnimationStartTime - animationDuration + viewHolderElapsedTime
                    }

                    val animationProgress = ((timeNow - viewHolderAnimationStartTime)
                        .toFloat()/animationDuration).coerceIn(0f, 1f)

                    if (animationProgress == 1f) {
                        applyState(newState)
                    } else {
                        animateStateChange(viewHolderState, newState, animationProgress)
                    }
                    viewHolderState = newState
                }
            } else {
                /* ViewHolder is showing a different payment */
                viewHolderPaymentStableId = newStableId

                if (newState == R.id.default_state) {
                    viewHolderAnimationStartTime = 0L
                    applyState(R.id.default_state)
                } else {
                    val animationProgress = ((System.currentTimeMillis() - listAnimationStartTime)
                        .toFloat()/animationDuration).coerceIn(0f, 1f)
                    viewHolderAnimationStartTime = listAnimationStartTime

                    if (animationProgress == 1f) {
                        applyState(newState)
                    } else {
                        when (newState) {
                            R.id.selecting_method_state -> {
                                animateStateChange(R.id.default_state, R.id.selecting_method_state, animationProgress)
                            }
                            R.id.showing_instructions_state -> {
                                animateStateChange(R.id.selecting_method_state, R.id.showing_instructions_state, animationProgress)
                            }
                            R.id.candidate_state -> {
                                animateStateChange(R.id.default_state, R.id.candidate_state, animationProgress)
                            }
                            R.id.ineligible_state -> {
                                animateStateChange(R.id.default_state, R.id.ineligible_state, animationProgress)
                            }
                        }
                    }
                }

                viewHolderState = newState
            }
        }
    }

    suspend fun updateDataSet(paymentsData: Pair<List<Triple<Payment, Long?, Int>>, Pair<Long?, Int>>) {
        val adapter = this
        val oldPayments = mPaymentList
        val newPayments = paymentsData.first

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldPayments.size

            override fun getNewListSize() = newPayments.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val oldPaymentId = oldPayments[oldPosition].second
                val newPaymentId = newPayments[newPosition].second

                return oldPaymentId != null && oldPaymentId == newPaymentId
            }

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val (oldPayment, _, oldState) = oldPayments[oldPosition]
                val (newPayment, _, newState) = newPayments[newPosition]

                return newState == oldState &&
                        newPayment.amount == oldPayment.amount &&
                        newPayment.committed == oldPayment.committed &&
                        newPayment.method == oldPayment.method &&
                        newPayment.surrogate?.name == oldPayment.surrogate?.name &&
                        newPayment.payer.name == oldPayment.payer.name &&
                        newPayment.payee.name == oldPayment.payee.name
            }
        })

        withContext(Dispatchers.Main) {
            adapter.notifyItemChanged(mPaymentList.size - 1) // clears BottomOffset from old last item

            mPaymentList = newPayments
            if (paymentsData.second != mActivePaymentIdAndState) {
                // Active payment and/or state change that will trigger a new animation
                mActivePaymentIdAndState = paymentsData.second
                animationStartFlag = true
            }
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}