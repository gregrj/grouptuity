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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
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
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.Payment
import com.grouptuity.grouptuity.data.PaymentMethod
import com.grouptuity.grouptuity.databinding.FragPaymentsBinding
import com.grouptuity.grouptuity.databinding.FragPaymentsListitemBinding
import com.grouptuity.grouptuity.ui.billsplit.PaymentsViewModel.Companion.CANDIDATE_STATE
import com.grouptuity.grouptuity.ui.billsplit.PaymentsViewModel.Companion.DEFAULT_STATE
import com.grouptuity.grouptuity.ui.billsplit.PaymentsViewModel.Companion.INELIGIBLE_STATE
import com.grouptuity.grouptuity.ui.billsplit.PaymentsViewModel.Companion.SELECTING_METHOD_STATE
import com.grouptuity.grouptuity.ui.billsplit.PaymentsViewModel.Companion.SHOWING_INSTRUCTIONS_STATE
import com.grouptuity.grouptuity.ui.custom.views.RecyclerViewBottomOffset
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


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
            object: PaymentRecyclerViewAdapter.PaymentRecyclerViewListener {
                override fun onClickActivePaymentMethodIcon(payment: Payment) {
                    paymentsViewModel.setActivePayment(payment)
                }

                override fun onClickCloseButton(payment: Payment) {
                    paymentsViewModel.setActivePayment(null)
                }

                override fun onClickPaymentMethod(payment: Payment, paymentMethod: PaymentMethod) {
                    paymentsViewModel.setPaymentMethodPreference(payment, paymentMethod)
                }

                override fun onClickSurrogate(diner: Diner) {
                    paymentsViewModel.setSurrogate(diner)
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
                                         val listener: PaymentRecyclerViewListener): RecyclerView.Adapter<PaymentRecyclerViewAdapter.ViewHolder>() {
    var mPaymentDataSet = emptyList<PaymentData>()
    var mActivePaymentIdAndState = PaymentsViewModel.INITIAL_STABLE_ID_AND_STATE
    var animationStartFlag = false
    var listAnimationStartTime = 0L

    private val activePaymentMethodIconStrokeColor = ColorStateList.valueOf(
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnBackgroundLowEmphasis, it, true) }.data)
    private val iconColorTint = ColorStateList.valueOf(
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnTertiary, it, true) }.data)
    private val iconColorOnBackgroundTint = ColorStateList.valueOf(
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnBackgroundMediumEmphasis, it, true) }.data)

    // TODO move hardcoded values to xml and also use in layout
    private val surfaceBackground = TypedValue().also { context.theme.resolveAttribute(R.attr.colorSurface, it, true) }.data
    private val tertiaryBackground = TypedValue().also { context.theme.resolveAttribute(R.attr.colorTertiary, it, true) }.data
    private val selectableBackground = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true) }.data
    private val payInstructionsVerticalPx = (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14F, context.resources.displayMetrics) +
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8F, context.resources.displayMetrics)).toInt()
    private val paymentMethodFlipButtonHorizontalPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56F, context.resources.displayMetrics).toInt()
    private val paymentMethodIconPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36F, context.resources.displayMetrics).toInt()
    private val enlargedPayerTextSizeSpFrac = 1.22f
    private val animationDuration = context.resources.getInteger(R.integer.card_flip_time_full).toLong()

    init { setHasStableIds(true) }

    interface PaymentRecyclerViewListener {
        fun onClickActivePaymentMethodIcon(payment: Payment)
        fun onClickCloseButton(payment: Payment)
        fun onClickPaymentMethod(payment: Payment, paymentMethod: PaymentMethod)
        fun onClickSurrogate(diner: Diner)
    }

    inner class ViewHolder(val viewBinding: FragPaymentsListitemBinding): RecyclerView.ViewHolder(viewBinding.root) {
        val paymentMethodButtonsAcceptedByPeers = mutableListOf<Triple<Int, Int, Int>>()
        val paymentMethodButtonsNotAcceptedByPeers = mutableListOf<Triple<Int, Int, Int>>()
        val paymentMethodButtonsAcceptedByRestaurant = mutableListOf<Triple<Int, Int, Int>>()
        val paymentMethodButtonsNeedsSurrogate = mutableListOf<Triple<Int, Int, Int>>()
        var viewHolderPaymentStableId: Long? = null
        var viewHolderState: Int = DEFAULT_STATE
        var viewHolderAnimationStartTime = 0L

        lateinit var backgroundAnimator: ObjectAnimator
        lateinit var fadeOutExpandingLayoutAnimator: ObjectAnimator
        lateinit var fadeOutFlipIconAnimator: ValueAnimator
        lateinit var fadeOutPaymentDetailsAnimator: ValueAnimator
        lateinit var fadeInSelectionInstructionAnimator: ObjectAnimator
        lateinit var fadeOutContactAnimator: ValueAnimator
        lateinit var enlargeContactAnimator: ValueAnimator

        fun setEnlargedPlaceholders() {
            viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> { height = 1 }
            viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> { width = 1 }
            viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> { width = 1 }

            viewBinding.payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                marginStart = 2 * viewBinding.payerReference.marginStart
            }
            viewBinding.payer.scaleX = enlargedPayerTextSizeSpFrac
            viewBinding.payer.scaleY = enlargedPayerTextSizeSpFrac
        }

        fun setShrunkenPlaceholders() {
            itemView.doOnPreDraw {
                viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    width = viewBinding.amount.width
                }
            }

            viewBinding.payInstructionsPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = payInstructionsVerticalPx
            }

            viewBinding.amountPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                width = viewBinding.amount.width
            }

            viewBinding.paymentMethodButtonPlaceholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                width = paymentMethodFlipButtonHorizontalPx
            }

            viewBinding.payer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                marginStart = viewBinding.payerReference.marginStart
            }
            viewBinding.payer.scaleX = 1f
            viewBinding.payer.scaleY = 1f
        }

        fun applyState(newState: Int) {
            backgroundAnimator.cancel()
            fadeOutExpandingLayoutAnimator.cancel()
            fadeOutFlipIconAnimator.cancel()
            fadeOutPaymentDetailsAnimator.cancel()
            fadeInSelectionInstructionAnimator.cancel()
            fadeOutContactAnimator.cancel()
            enlargeContactAnimator.cancel()

            when (newState) {
                DEFAULT_STATE -> {
                    viewBinding.surrogateSelection.visibility = View.GONE
                    viewBinding.backgroundView.setBackgroundColor(surfaceBackground)
                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                    viewBinding.paymentMethodFlipButton.setDeselected()
                    viewBinding.paymentMethodFlipButton.alpha = 1f
                    viewBinding.payInstructions.alpha = 1f
                    viewBinding.amount.alpha = 1f
                    viewBinding.selectionInstruction.alpha = 0f
                    viewBinding.payerContactIcon.alpha = 1f
                    viewBinding.payer.alpha = 1f

                    setShrunkenPlaceholders()
                }
                SELECTING_METHOD_STATE -> {
                    viewBinding.surrogateSelection.visibility = View.GONE
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

                    setShrunkenPlaceholders()
                }
                SHOWING_INSTRUCTIONS_STATE -> {
                    viewBinding.surrogateSelection.visibility = View.GONE
                    viewBinding.backgroundView.setBackgroundColor(tertiaryBackground)
                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                    viewBinding.paymentMethodFlipButton.setSelected()
                    viewBinding.paymentMethodFlipButton.alpha = 1f
                    viewBinding.payInstructions.alpha = 0f
                    viewBinding.amount.alpha = 0f
                    viewBinding.selectionInstruction.alpha = 1f
                    viewBinding.payerContactIcon.alpha = 0f
                    viewBinding.payer.alpha = 0f

                    setShrunkenPlaceholders()
                }
                CANDIDATE_STATE -> {
                    viewBinding.surrogateSelection.visibility = View.VISIBLE
                    viewBinding.backgroundView.setBackgroundColor(surfaceBackground)
                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                    viewBinding.paymentMethodFlipButton.setDeselected()
                    viewBinding.paymentMethodFlipButton.alpha = 0f
                    viewBinding.payInstructions.alpha = 0f
                    viewBinding.amount.alpha = 0f
                    viewBinding.selectionInstruction.alpha = 0f
                    viewBinding.payerContactIcon.alpha = 1f
                    viewBinding.payer.alpha = 1f

                    setEnlargedPlaceholders()
                }
                INELIGIBLE_STATE -> {
                    viewBinding.surrogateSelection.visibility = View.GONE
                    viewBinding.backgroundView.setBackgroundColor(surfaceBackground)
                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                    viewBinding.paymentMethodFlipButton.setDeselected()
                    viewBinding.paymentMethodFlipButton.alpha = 0f
                    viewBinding.payInstructions.alpha = 0f
                    viewBinding.amount.alpha = 0f
                    viewBinding.selectionInstruction.alpha = 0f
                    viewBinding.payerContactIcon.alpha = 1f
                    viewBinding.payer.alpha = 1f

                    setEnlargedPlaceholders()
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
                DEFAULT_STATE -> {
                    viewBinding.surrogateSelection.visibility = View.GONE
                    when (oldState) {
                        SELECTING_METHOD_STATE -> {
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

                            setShrunkenPlaceholders()

                            viewBinding.selectionInstruction.alpha = 0f
                        }
                        SHOWING_INSTRUCTIONS_STATE -> {
                            backgroundAnimator.setCurrentFraction(1f - initialProgress)
                            backgroundAnimator.reverse()

                            viewBinding.paymentMethodsExpandingLayout.setCollapsed()

                            viewBinding.paymentMethodFlipButton.alpha = 1f
                            viewBinding.paymentMethodFlipButton.animateDeselection(initialProgress)

                            fadeOutPaymentDetailsAnimator.setCurrentFraction(1f - initialProgress)
                            fadeOutPaymentDetailsAnimator.reverse()

                            fadeOutContactAnimator.setCurrentFraction(1f - initialProgress)
                            fadeOutContactAnimator.reverse()

                            setShrunkenPlaceholders()

                            fadeInSelectionInstructionAnimator.setCurrentFraction(1f - initialProgress)
                            fadeInSelectionInstructionAnimator.reverse()
                        }
                        CANDIDATE_STATE -> {
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
                        INELIGIBLE_STATE -> {
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
                SELECTING_METHOD_STATE -> {
                    viewBinding.surrogateSelection.visibility = View.GONE
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

                    setShrunkenPlaceholders()

                    viewBinding.selectionInstruction.alpha = 0f
                }
                SHOWING_INSTRUCTIONS_STATE -> {
                    viewBinding.surrogateSelection.visibility = View.GONE
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

                    setShrunkenPlaceholders()

                    fadeInSelectionInstructionAnimator.setCurrentFraction(initialProgress)
                    fadeInSelectionInstructionAnimator.start()
                }
                CANDIDATE_STATE -> {
                    viewBinding.surrogateSelection.visibility = View.VISIBLE
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
                INELIGIBLE_STATE -> {
                    viewBinding.surrogateSelection.visibility = View.GONE
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

    override fun getItemCount() = mPaymentDataSet.size

    override fun getItemId(position: Int) = mPaymentDataSet[position].stableId ?: NO_ID

    @SuppressLint("Recycle")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = ViewHolder(FragPaymentsListitemBinding.inflate(LayoutInflater.from(context), parent, false))

        viewHolder.viewBinding.apply {
            surrogateSelection.setOnClickListener {
                if (viewHolder.viewHolderState == CANDIDATE_STATE) {
                    listener.onClickSurrogate((viewHolder.itemView.tag as PaymentData).payment.payer)
                }
            }

            activePaymentMethodIcon.setOnClickListener {
                if (viewHolder.viewHolderState == DEFAULT_STATE) {
                    listener.onClickActivePaymentMethodIcon((viewHolder.itemView.tag as PaymentData).payment)
                }
            }

            closePaymentMethodListButton.setOnClickListener {
                if (viewHolder.viewHolderState == SELECTING_METHOD_STATE ||
                    viewHolder.viewHolderState == SHOWING_INSTRUCTIONS_STATE) {
                    listener.onClickCloseButton((viewHolder.itemView.tag as PaymentData).payment)
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
                        listener.onClickPaymentMethod((viewHolder.itemView.tag as PaymentData).payment, method)
                    }
                })

                val postSpacerId = View.generateViewId()
                paymentMethodsExpandingLayout.addView(Space(context).also {
                    it.id = postSpacerId
                    it.layoutParams = LinearLayout.LayoutParams(0, 1, 0.5F)
                })

                if (method.acceptedByPeer) {
                    viewHolder.paymentMethodButtonsAcceptedByPeers.add(Triple(preSpacerId, methodButtonId, postSpacerId))

                    if (method.acceptedByRestaurant) {
                        viewHolder.paymentMethodButtonsAcceptedByRestaurant.add(Triple(preSpacerId, methodButtonId, postSpacerId))
                    } else {
                        viewHolder.paymentMethodButtonsNeedsSurrogate.add(Triple(preSpacerId, methodButtonId, postSpacerId))
                    }
                } else {
                    // PaymentMethod must be accepted by restaurant if it cannot be accepted by peers
                    viewHolder.paymentMethodButtonsNotAcceptedByPeers.add(Triple(preSpacerId, methodButtonId, postSpacerId))
                    viewHolder.paymentMethodButtonsAcceptedByRestaurant.add(Triple(preSpacerId, methodButtonId, postSpacerId))
                }
            }
        }

        viewHolder.setShrunkenPlaceholders()

        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.apply {
            val newPaymentData = mPaymentDataSet[position]
            itemView.tag = newPaymentData

            viewBinding.paymentMethodsExpandingLayout.setExpandedSize(
                viewBinding.paymentCardView.width
                        - viewBinding.paymentMethodFlipButton.width
                        - viewBinding.amount.marginEnd
                        - viewBinding.payInstructions.marginStart)

            viewBinding.payer.text = newPaymentData.payerString
            viewBinding.payInstructions.text = newPaymentData.payInstructionsString
            viewBinding.amount.text = newPaymentData.amountString
            viewBinding.amountPlaceholder.text = newPaymentData.amountString

            viewBinding.activePaymentMethodIcon.icon = ContextCompat.getDrawable(context, newPaymentData.payment.method.paymentIconId)
            viewBinding.activePaymentMethodIcon.iconTint = if (newPaymentData.payment.method.isIconColorless) iconColorOnBackgroundTint else null

            viewBinding.payerContactIcon.setContact(newPaymentData.iconContact, false)

            if (newPaymentData.isPaymentIconClickable) {
                viewBinding.activePaymentMethodIcon.isClickable = true
                viewBinding.activePaymentMethodIcon.strokeColor = activePaymentMethodIconStrokeColor
                viewBinding.closePaymentMethodListButton.isClickable = true

                if (newPaymentData.payment.payee.isRestaurant()) {
                    for (viewIds in paymentMethodButtonsAcceptedByRestaurant) {
                        val (preSpacerId, methodButtonId, postSpacerId) = viewIds
                        viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(preSpacerId).visibility = View.VISIBLE
                        viewBinding.paymentMethodsExpandingLayout.findViewById<ImageView>(methodButtonId).visibility = View.VISIBLE
                        viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(postSpacerId).visibility = View.VISIBLE
                    }

                    if (newPaymentData.allowSurrogatePaymentMethods) {
                        for (viewIds in paymentMethodButtonsNeedsSurrogate) {
                            val (preSpacerId, methodButtonId, postSpacerId) = viewIds
                            viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(preSpacerId).visibility = View.VISIBLE
                            viewBinding.paymentMethodsExpandingLayout.findViewById<ImageView>(methodButtonId).visibility = View.VISIBLE
                            viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(postSpacerId).visibility = View.VISIBLE
                        }
                    } else {
                        for (viewIds in paymentMethodButtonsNeedsSurrogate) {
                            val (preSpacerId, methodButtonId, postSpacerId) = viewIds
                            viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(preSpacerId).visibility = View.GONE
                            viewBinding.paymentMethodsExpandingLayout.findViewById<ImageView>(methodButtonId).visibility = View.GONE
                            viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(postSpacerId).visibility = View.GONE
                        }
                    }
                } else {
                    for (viewIds in paymentMethodButtonsAcceptedByPeers) {
                        val (preSpacerId, methodButtonId, postSpacerId) = viewIds
                        viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(preSpacerId).visibility = View.VISIBLE
                        viewBinding.paymentMethodsExpandingLayout.findViewById<ImageView>(methodButtonId).visibility = View.VISIBLE
                        viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(postSpacerId).visibility = View.VISIBLE
                    }

                    for (viewIds in paymentMethodButtonsNotAcceptedByPeers) {
                        val (preSpacerId, methodButtonId, postSpacerId) = viewIds
                        viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(preSpacerId).visibility = View.GONE
                        viewBinding.paymentMethodsExpandingLayout.findViewById<ImageView>(methodButtonId).visibility = View.GONE
                        viewBinding.paymentMethodsExpandingLayout.findViewById<Space>(postSpacerId).visibility = View.GONE
                    }
                }
            } else {
                viewBinding.activePaymentMethodIcon.isClickable = false
                viewBinding.activePaymentMethodIcon.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
                viewBinding.closePaymentMethodListButton.isClickable = false
            }

            val timeNow = System.currentTimeMillis()
            if (animationStartFlag) {
                // Record start time for new list animation
                animationStartFlag = false
                listAnimationStartTime = timeNow
            }

            if (viewHolderPaymentStableId == newPaymentData.stableId) {
                if (viewHolderState == newPaymentData.displayState) {
                    // ViewHolder is bound to the same payment and state is unchanged
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
                        applyState(newPaymentData.displayState)
                    } else {
                        animateStateChange(viewHolderState, newPaymentData.displayState, animationProgress)
                    }
                    viewHolderState = newPaymentData.displayState
                }
            } else {
                /* ViewHolder is showing a different payment */
                viewHolderPaymentStableId = newPaymentData.stableId

                if (newPaymentData.displayState == DEFAULT_STATE) {
                    viewHolderAnimationStartTime = 0L
                    applyState(DEFAULT_STATE)
                } else {
                    val animationProgress = ((System.currentTimeMillis() - listAnimationStartTime)
                        .toFloat()/animationDuration).coerceIn(0f, 1f)
                    viewHolderAnimationStartTime = listAnimationStartTime

                    if (animationProgress == 1f) {

                        if(((itemView.tag as PaymentData).payment).payer.name == "Drew Regitsky") {
                            Log.e("applying "+newPaymentData.displayState, "now")
                        }



                        applyState(newPaymentData.displayState)
                    } else {

                        if(((itemView.tag as PaymentData).payment).payer.name == "Drew Regitsky") {
                            Log.e("animating "+newPaymentData.displayState, "now")
                        }

                        when (newPaymentData.displayState) {
                            SELECTING_METHOD_STATE -> {
                                animateStateChange(DEFAULT_STATE, SELECTING_METHOD_STATE, animationProgress)
                            }
                            SHOWING_INSTRUCTIONS_STATE -> {
                                animateStateChange(SELECTING_METHOD_STATE, SHOWING_INSTRUCTIONS_STATE, animationProgress)
                            }
                            CANDIDATE_STATE -> {
                                animateStateChange(DEFAULT_STATE, CANDIDATE_STATE, animationProgress)
                            }
                            INELIGIBLE_STATE -> {
                                animateStateChange(DEFAULT_STATE, INELIGIBLE_STATE, animationProgress)
                            }
                        }
                    }
                }

                viewHolderState = newPaymentData.displayState
            }
        }
    }

    suspend fun updateDataSet(paymentsData: Pair<List<PaymentData>, Pair<Long?, Int>>) {
        val adapter = this

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = mPaymentDataSet.size

            override fun getNewListSize() = paymentsData.first.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val oldPaymentId = mPaymentDataSet[oldPosition].stableId
                return oldPaymentId != null && oldPaymentId == paymentsData.first[newPosition].stableId
            }

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val oldPaymentData = mPaymentDataSet[oldPosition]
                val newPaymentData = paymentsData.first[newPosition]

                return newPaymentData.displayState == oldPaymentData.displayState &&
                        newPaymentData.payment.method == oldPaymentData.payment.method &&
                        newPaymentData.payerString == oldPaymentData.payerString &&
                        newPaymentData.payInstructionsString == oldPaymentData.payInstructionsString &&
                        newPaymentData.amountString == oldPaymentData.amountString &&
                        newPaymentData.iconContact == oldPaymentData.iconContact &&
                        newPaymentData.isPaymentIconClickable == oldPaymentData.isPaymentIconClickable &&
                        newPaymentData.allowSurrogatePaymentMethods == oldPaymentData.allowSurrogatePaymentMethods
            }
        })

        withContext(Dispatchers.Main) {
            adapter.notifyItemChanged(mPaymentDataSet.size - 1) // clears BottomOffset from old last item

            mPaymentDataSet = paymentsData.first
            if (paymentsData.second != mActivePaymentIdAndState) {
                // Active payment and/or state change that will trigger a new animation
                mActivePaymentIdAndState = paymentsData.second
                animationStartFlag = true
            }
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}