package com.grouptuity.grouptuity.ui.billsplit

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.updateLayoutParams
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

        paymentsViewModel.payments.observe(viewLifecycleOwner) { payments ->
            lifecycleScope.launch { recyclerAdapter.updateDataSet(payments) }

            binding.noPaymentsHint.visibility = if (payments.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}


private class PaymentRecyclerViewAdapter(val context: Context,
                                         val paymentViewModel: PaymentsViewModel,
                                         val listener: PaymentRecyclerViewListener): RecyclerView.Adapter<PaymentRecyclerViewAdapter.ViewHolder>() {
    var mPaymentList = emptyList<Pair<Payment, Int>>()

    private val activePaymentMethodIconStrokeColor = ColorStateList.valueOf(
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnBackgroundLowEmphasis, it, true) }.data
    )
    private val iconColorTint = ColorStateList.valueOf(
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnTertiary, it, true) }.data
    )
    private val iconColorOnBackgroundTint = ColorStateList.valueOf(
        TypedValue().also { context.theme.resolveAttribute(R.attr.colorOnBackgroundMediumEmphasis, it, true) }.data
    )
    private val backgroundColor = TypedValue().also { context.theme.resolveAttribute(R.attr.colorBackground, it, true) }.data
    private val backgroundColorActive = TypedValue().also { context.theme.resolveAttribute(R.attr.colorTertiary, it, true) }.data
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
        var state: Int = PaymentsViewModel.DEFAULT

        fun animateToSelectingMethod() {
            viewBinding.paymentMotionLayout.setTransition(R.id.default_to_selecting_method)
            viewBinding.paymentMotionLayout.transitionToEnd()

            // Expand payment method list (first ensure it is reset to collapsed state)
            viewBinding.paymentMethodsExpandingLayout.setCollapsed()
            viewBinding.paymentMethodsExpandingLayout.duration = animationDuration
            viewBinding.paymentMethodsExpandingLayout.expand(
                viewBinding.paymentCardView.width
                        - viewBinding.paymentMethodFlipButton.width
                        - viewBinding.amount.marginEnd
                        - viewBinding.payInstructions.marginStart
            )

            // Flip icon button
            viewBinding.paymentMethodFlipButton.animateSelection()
        }

        fun animateSelectingMethodToDefault() {
            viewBinding.paymentMotionLayout.setTransition(R.id.selecting_method_to_default)
            viewBinding.paymentMotionLayout.transitionToEnd()

            // Collapse payment method list
            viewBinding.paymentMethodsExpandingLayout.collapse()

            // Flip icon button back
            viewBinding.paymentMethodFlipButton.animateDeselection()
        }

        fun animateToShowingInstructions() {
            viewBinding.paymentMotionLayout.setTransition(R.id.selecting_method_to_showing_instructions)
            viewBinding.paymentMotionLayout.transitionToEnd()

            viewBinding.paymentMotionLayout.addTransitionListener(object: MotionLayout.TransitionListener {
                override fun onTransitionStarted(motionLayout: MotionLayout?, startId: Int, endId: Int) { }

                override fun onTransitionChange(motionLayout: MotionLayout?, startId: Int, endId: Int, progress: Float) { }

                override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                    if (currentId == R.id.showing_instructions_state) {
                        viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                        viewBinding.paymentMotionLayout.removeTransitionListener(this)
                    }
                }

                override fun onTransitionTrigger(motionLayout: MotionLayout?, triggerId: Int, positive: Boolean, progress: Float ) { }
            })
        }

        fun animateShowingInstructionsToDefault() {
            viewBinding.paymentMotionLayout.setTransition(R.id.showing_instructions_to_default)
            viewBinding.paymentMotionLayout.transitionToEnd()

            // Flip icon button back
            viewBinding.paymentMethodFlipButton.animateDeselection()
        }

        fun animateToCandidate() {
            viewBinding.paymentMotionLayout.setTransition(R.id.default_to_candidate)
            viewBinding.paymentMotionLayout.transitionToEnd()
        }

        fun animateCandidateToDefault() {
            viewBinding.paymentMotionLayout.setTransition(R.id.candidate_to_default)
            viewBinding.paymentMotionLayout.transitionToEnd()
        }

        fun animateIneligibleToDefault() {

        }

        fun animateToIneligible() {

        }
    }

    override fun getItemCount() = mPaymentList.size

    override fun getItemId(position: Int) = paymentViewModel.getPaymentStableId(mPaymentList[position].first) ?: NO_ID

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = ViewHolder(FragPaymentsListitemBinding.inflate(LayoutInflater.from(context), parent, false))

        viewHolder.viewBinding.apply {
            paymentMethodsExpandingLayout.duration = animationDuration
            paymentMethodsExpandingLayout.parallaxMultiplier = 1f

            activePaymentMethodIcon.setOnClickListener {
                listener.onClickActivePaymentMethodIcon((viewHolder.itemView.tag as Pair<*, *>).first as Payment)
            }

            closePaymentMethodListButton.setOnClickListener {
                listener.onClickCloseButton((viewHolder.itemView.tag as Pair<*, *>).first as Payment)
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
                        listener.onClickPaymentMethod((viewHolder.itemView.tag as Pair<*, *>).first as Payment, method)
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if(payloads.size == 0) {
            onBindViewHolder(holder, position)
            return
        }

        holder.apply {
            val (newPayment, newState) = mPaymentList[position]
            itemView.tag = mPaymentList[position] // store updated payment and state

            Log.e("onbind", mPaymentList[position].second.toString())

            viewBinding.activePaymentMethodIcon.iconTint = if (newPayment.method.isIconColorless) {
                iconColorOnBackgroundTint
            } else {
                null
            }
            viewBinding.activePaymentMethodIcon.icon = ContextCompat.getDrawable(context, newPayment.method.paymentIconId)

            viewBinding.payInstructions.text = if (newPayment.payee.isRestaurant()) {
                val surrogate = newPayment.surrogate
                when {
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
            } else {
                context.resources.getString(
                    newPayment.method.paymentInstructionStringId,
                    newPayment.payee.name
                )
            }

            viewBinding.amount.text = NumberFormat.getCurrencyInstance().format(newPayment.amount)

            when (newState) {
                PaymentsViewModel.DEFAULT -> {
                    when (state) {
                        PaymentsViewModel.DEFAULT -> { }
                        PaymentsViewModel.SELECTING_METHOD -> { animateSelectingMethodToDefault() }
                        PaymentsViewModel.SHOWING_SURROGATE_INSTRUCTIONS -> { animateShowingInstructionsToDefault() }
                        PaymentsViewModel.CANDIDATE_SURROGATE -> { animateCandidateToDefault() }
                        PaymentsViewModel.INELIGIBLE_SURROGATE -> { animateIneligibleToDefault() }
                    }
                }
                PaymentsViewModel.SELECTING_METHOD -> { animateToSelectingMethod() }
                PaymentsViewModel.SHOWING_SURROGATE_INSTRUCTIONS -> { animateToShowingInstructions() }
                PaymentsViewModel.CANDIDATE_SURROGATE -> { animateToCandidate() }
                PaymentsViewModel.INELIGIBLE_SURROGATE -> { animateToIneligible() }
            }
            state = newState
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.apply {
            val (newPayment, newState) = mPaymentList[position]
            itemView.tag = mPaymentList[position] // store updated payment and state

            viewBinding.amount.text = NumberFormat.getCurrencyInstance().format(newPayment.amount)

            viewBinding.activePaymentMethodIcon.iconTint = if (newPayment.method.isIconColorless) {
                iconColorOnBackgroundTint
            } else {
                null
            }
            viewBinding.activePaymentMethodIcon.icon = ContextCompat.getDrawable(context, newPayment.method.paymentIconId)

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

                    when {
                        (newPayment.surrogate == null) -> {
                            viewBinding.payInstructions.text = context.resources.getString(newPayment.method.paymentInstructionStringId,
                                context.resources.getString(R.string.payments_instruction_restaurant_payee))
                        }
                        newPayment.surrogate!!.isCashPool() -> {
                            viewBinding.payInstructions.text = context.resources.getString(R.string.payments_instruction_into_cash_pool)
                        }
                        else -> {
                            viewBinding.payInstructions.text = context.resources.getString(newPayment.method.paymentInstructionStringId, newPayment.surrogate!!.name)
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

            when(newState) {
                PaymentsViewModel.DEFAULT -> {
                    itemView.setBackgroundColor(backgroundColor)
                    viewBinding.paymentMethodFlipButton.setSelectionState(false)
                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                }
                PaymentsViewModel.CANDIDATE_SURROGATE -> {
                    itemView.setBackgroundColor(backgroundColor)
                    viewBinding.paymentMethodFlipButton.setSelectionState(false)
                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                    //TODO
                }
                PaymentsViewModel.INELIGIBLE_SURROGATE -> {
                    //TODO
                }
                PaymentsViewModel.SELECTING_METHOD -> {
                    itemView.setBackgroundColor(backgroundColorActive)
                    viewBinding.paymentMethodFlipButton.setSelectionState(true)
                    viewBinding.paymentMotionLayout.alpha = 0f
                    viewBinding.paymentMethodsExpandingLayout.setExpanded(
                        viewBinding.paymentCardView.width
                                - viewBinding.paymentMethodFlipButton.width
                                - viewBinding.paymentMotionLayout.paddingEnd
                                - viewBinding.paymentMotionLayout.paddingStart
                    )
                }
                PaymentsViewModel.SHOWING_SURROGATE_INSTRUCTIONS -> {
                    //TODO
                    itemView.setBackgroundColor(backgroundColorActive)
                    viewBinding.paymentMethodFlipButton.setSelectionState(true)
                    viewBinding.paymentMotionLayout.alpha = 0f
                    viewBinding.paymentMethodsExpandingLayout.setCollapsed()
                }
            }
        }
    }

    suspend fun updateDataSet(newData: List<Pair<Payment, Int>>) {
        val adapter = this
        val oldData = mPaymentList

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldData.size

            override fun getNewListSize() = newData.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val oldPaymentId = paymentViewModel.getPaymentStableId(oldData[oldPosition].first)
                val newPaymentId = paymentViewModel.getPaymentStableId(newData[newPosition].first)

                return oldPaymentId != null && oldPaymentId == newPaymentId
            }

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val (oldPayment, oldState) = oldData[oldPosition]
                val (newPayment, newState) = newData[newPosition]

                return newState == oldState &&
                        newPayment.amount == oldPayment.amount &&
                        newPayment.committed == oldPayment.committed &&
                        newPayment.method == oldPayment.method &&
                        newPayment.surrogateId == oldPayment.surrogateId
            }

            override fun getChangePayload(oldPosition: Int, newPosition: Int): Any? {
                val (oldPayment, _) = oldData[oldPosition]
                val (newPayment, _) = newData[newPosition]

                val oldStableId = paymentViewModel.getPaymentStableId(oldPayment)
                val newStableId = paymentViewModel.getPaymentStableId(newPayment)

                return if (oldStableId != newStableId || oldStableId == null) {
                    null
                } else {
                    Pair(oldPayment, newPayment)
                }
            }
        })

        withContext(Dispatchers.Main) {
            adapter.notifyItemChanged(mPaymentList.size - 1) // clears BottomOffset from old last item
            mPaymentList = newData
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}