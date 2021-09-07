package com.grouptuity.grouptuity.ui.billsplit

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
import androidx.core.content.ContextCompat
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
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
                    paymentsViewModel.setActivePayment(null)
                }

                override fun onClickPaymentMethod(payment: Payment, paymentMethod: PaymentMethod) {
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
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnBackgroundLowEmphasis, it, true) }.data
    )
    private val iconColorTint = ColorStateList.valueOf(
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnTertiary, it, true) }.data
    )
    private val iconColorOnBackgroundTint = ColorStateList.valueOf(
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnBackgroundMediumEmphasis, it, true) }.data
    )
    private val selectableBackground = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true) }.data
    private val paymentMethodIconPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36F, context.resources.displayMetrics).toInt()
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
        var viewHolderState: Int = PaymentsViewModel.DEFAULT
        var viewHolderAnimationStartTime = 0L

        fun animateToSelectingMethod(initialProgress: Float) {
            viewBinding.paymentMotionLayout.setTransition(R.id.default_to_selecting_method)
            viewBinding.paymentMotionLayout.progress = initialProgress
            viewBinding.paymentMotionLayout.transitionToEnd()

            if (initialProgress == 1f) {
                viewBinding.paymentMethodsExpandingLayout.setExpanded(
                    viewBinding.paymentCardView.width
                            - viewBinding.paymentMethodButtonPlaceholder.width
                            - viewBinding.amount.marginEnd
                            - viewBinding.payInstructions.marginStart)
            } else {
                viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                viewBinding.paymentMethodsExpandingLayout.expand(
                    viewBinding.paymentCardView.width
                            - viewBinding.paymentMethodButtonPlaceholder.width
                            - viewBinding.amount.marginEnd
                            - viewBinding.payInstructions.marginStart,
                    initialProgress)
            }
        }

        fun animateSelectingMethodToDefault(initialProgress: Float) {
            viewBinding.paymentMotionLayout.setTransition(R.id.selecting_method_to_default)
            viewBinding.paymentMotionLayout.progress = initialProgress
            viewBinding.paymentMotionLayout.transitionToEnd()

            if (initialProgress == 1f) {
                viewBinding.paymentMethodsExpandingLayout.setCollapsed()
            } else {
                viewBinding.paymentMethodsExpandingLayout.setExpanded(
                    viewBinding.paymentCardView.width
                        - viewBinding.paymentMethodButtonPlaceholder.width
                        - viewBinding.amount.marginEnd
                        - viewBinding.payInstructions.marginStart)
                viewBinding.paymentMethodsExpandingLayout.collapse(initialProgress)
            }
        }

        fun animateToShowingInstructions(initialProgress: Float) {
            viewBinding.paymentMotionLayout.setTransition(R.id.selecting_method_to_showing_instructions)
            viewBinding.paymentMotionLayout.progress = initialProgress
            viewBinding.paymentMotionLayout.transitionToEnd()

            viewBinding.paymentMethodsExpandingLayout.setExpanded(
                viewBinding.paymentCardView.width
                        - viewBinding.paymentMethodButtonPlaceholder.width
                        - viewBinding.amount.marginEnd
                        - viewBinding.payInstructions.marginStart)
        }

        fun animateShowingInstructionsToDefault(initialProgress: Float) {
            viewBinding.paymentMotionLayout.setTransition(R.id.showing_instructions_to_default)
            viewBinding.paymentMotionLayout.progress = initialProgress
            viewBinding.paymentMotionLayout.transitionToEnd()

            viewBinding.paymentMethodsExpandingLayout.setCollapsed()
        }

        fun animateToCandidate(initialProgress: Float) {
            viewBinding.paymentMotionLayout.setTransition(R.id.default_to_candidate)
            viewBinding.paymentMotionLayout.progress = initialProgress
            viewBinding.paymentMotionLayout.transitionToEnd()

            viewBinding.paymentMethodsExpandingLayout.setCollapsed()
        }

        fun animateCandidateToDefault(initialProgress: Float) {
            viewBinding.paymentMotionLayout.setTransition(R.id.candidate_to_default)
            viewBinding.paymentMotionLayout.progress = initialProgress
            viewBinding.paymentMotionLayout.transitionToEnd()

            viewBinding.paymentMethodsExpandingLayout.setCollapsed()
        }

        fun animateToIneligible(initialProgress: Float) {
            viewBinding.paymentMotionLayout.setTransition(R.id.default_to_ineligible)
            viewBinding.paymentMotionLayout.progress = initialProgress
            viewBinding.paymentMotionLayout.transitionToEnd()

            viewBinding.paymentMethodsExpandingLayout.setCollapsed()
        }

        fun animateIneligibleToDefault(initialProgress: Float) {
            viewBinding.paymentMotionLayout.setTransition(R.id.ineligible_to_default)
            viewBinding.paymentMotionLayout.progress = initialProgress
            viewBinding.paymentMotionLayout.transitionToEnd()

            viewBinding.paymentMethodsExpandingLayout.setCollapsed()
        }
    }

    override fun getItemCount() = mPaymentList.size

    override fun getItemId(position: Int) = paymentViewModel.getPaymentStableId(mPaymentList[position].first) ?: NO_ID

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = ViewHolder(FragPaymentsListitemBinding.inflate(LayoutInflater.from(context), parent, false))

        viewHolder.viewBinding.apply {
            paymentMethodsExpandingLayout.duration = animationDuration

            activePaymentMethodIcon.setOnClickListener {
                listener.onClickActivePaymentMethodIcon((viewHolder.itemView.tag as Triple<*, *, *>).first as Payment)
            }

            closePaymentMethodListButton.setOnClickListener {
                listener.onClickCloseButton((viewHolder.itemView.tag as Triple<*, *, *>).first as Payment)
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

        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.apply {
            val (newPayment, newStableId, newState) = mPaymentList[position]
            itemView.tag = mPaymentList[position] // store updated payment and state

            viewBinding.activePaymentMethodIcon.iconTint =
                if (newPayment.method.isIconColorless) { iconColorOnBackgroundTint } else { null }
            viewBinding.activePaymentMethodIcon.icon = ContextCompat.getDrawable(context, newPayment.method.paymentIconId)

            viewBinding.amount.text = NumberFormat.getCurrencyInstance().format(newPayment.amount)

            when {
                (newPayment.payer.isCashPool()) -> {
                    viewBinding.payer.text = newPayment.payee.name
                    viewBinding.payerContactIcon.setContact(newPayment.payee.contact, false)
                    viewBinding.payInstructions.text = context.resources.getString(R.string.payments_instruction_from_cash_pool)
                    viewBinding.activePaymentMethodIcon.isClickable = false
                    viewBinding.activePaymentMethodIcon.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
                }
                (newPayment.payee.isCashPool()) -> {
                    viewBinding.payer.text = newPayment.payer.name
                    viewBinding.payerContactIcon.setContact(newPayment.payer.contact, false)
                    viewBinding.payInstructions.text = context.resources.getString(R.string.payments_instruction_into_cash_pool)
                    viewBinding.activePaymentMethodIcon.isClickable = false
                    viewBinding.activePaymentMethodIcon.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
                }
                (newPayment.payee.isRestaurant()) -> {
                    viewBinding.payer.text = newPayment.payer.name
                    viewBinding.payerContactIcon.setContact(newPayment.payer.contact, false)
                    viewBinding.activePaymentMethodIcon.isClickable = true
                    viewBinding.activePaymentMethodIcon.strokeColor = activePaymentMethodIconStrokeColor

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

            if(newPayment.payer.name == "You" && newPayment.payee.isRestaurant()) {
                Log.e("binding "+newPayment.payer.name, ""+newState)
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

                    val animationProgress = ((timeNow - viewHolderAnimationStartTime).toFloat()/animationDuration).coerceIn(0f, 1f)

                    when (newState) {
                        PaymentsViewModel.DEFAULT -> {
                            when (viewHolderState) {
                                PaymentsViewModel.SELECTING_METHOD -> { animateSelectingMethodToDefault(animationProgress) }
                                PaymentsViewModel.SHOWING_SURROGATE_INSTRUCTIONS -> { animateShowingInstructionsToDefault(animationProgress) }
                                PaymentsViewModel.CANDIDATE_SURROGATE -> { animateCandidateToDefault(animationProgress) }
                                PaymentsViewModel.INELIGIBLE_SURROGATE -> { animateIneligibleToDefault(animationProgress) }
                            }
                        }
                        PaymentsViewModel.SELECTING_METHOD -> { animateToSelectingMethod(animationProgress) }
                        PaymentsViewModel.SHOWING_SURROGATE_INSTRUCTIONS -> { animateToShowingInstructions(animationProgress) }
                        PaymentsViewModel.CANDIDATE_SURROGATE -> { animateToCandidate(animationProgress) }
                        PaymentsViewModel.INELIGIBLE_SURROGATE -> { animateToIneligible(animationProgress) }
                    }
                    viewHolderState = newState
                }
            } else {
                /* ViewHolder is showing a different payment. Assume animation started from default
                   state when the list animation started. */
                viewHolderPaymentStableId = newStableId

                if (newState == PaymentsViewModel.DEFAULT) {
                    viewHolderAnimationStartTime = 0L
                    animateSelectingMethodToDefault(1f)
                } else {
                    val animationProgress = ((System.currentTimeMillis() - listAnimationStartTime).toFloat()/animationDuration).coerceIn(0f, 1f)
                    viewHolderAnimationStartTime = listAnimationStartTime
                    when (newState) {
                        PaymentsViewModel.SELECTING_METHOD -> { animateToSelectingMethod(animationProgress) }
                        PaymentsViewModel.SHOWING_SURROGATE_INSTRUCTIONS -> { animateToShowingInstructions(animationProgress) }
                        PaymentsViewModel.CANDIDATE_SURROGATE -> { animateToCandidate(animationProgress) }
                        PaymentsViewModel.INELIGIBLE_SURROGATE -> { animateToIneligible(animationProgress) }
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