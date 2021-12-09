package com.grouptuity.grouptuity.ui.billsplit.dinerdetails

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnPreDraw
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionValues
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import com.grouptuity.grouptuity.databinding.*
import com.grouptuity.grouptuity.ui.billsplit.qrcodescanner.QRCodeScannerActivity
import com.grouptuity.grouptuity.ui.util.CustomNavigator
import com.grouptuity.grouptuity.ui.util.transitions.CardViewExpandTransition
import com.grouptuity.grouptuity.ui.util.views.setNullOnDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// private const val DELETE_BACKGROUND_COLOR = R.attr.colorTertiary

// TODO remove itemanimation on debt recyclerview causes abrupt changing to scroll position
// TODO from collapsed state, diner image does not return to exact position of icon in diner list item

// Note: Bug exists with programmatic changes to endIconMode so custom mode is used with a

class DinerDetailsFragment: Fragment() {
    private var binding by setNullOnDestroy<FragDinerDetailsBinding>()
    private val args: DinerDetailsFragmentArgs by navArgs()
    private lateinit var viewModel: DinerDetailsViewModel
    private lateinit var backPressedCallback: OnBackPressedCallback
    private lateinit var qrCodeScannerLauncher: ActivityResultLauncher<Intent>
    private var isToolBarCollapsed = false
    private var itemsExpanded = false
    private var discountsExpanded = false
    private var reimbursementsExpanded = false
    private var startTransitionPending = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(requireActivity())[DinerDetailsViewModel::class.java]
        binding = FragDinerDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()

        // Manually lock before AppBarLayout.OnOffsetChangedListener is called
        // TODO replace with dedicated lock? Should everything just be handled in the onResume method?
        viewModel.notifyTransitionStarted()

        // Intercept user interactions while fragment transitions are running
        binding.rootLayout.attachLock(viewModel.isInputLocked)

        // Intercept back pressed events to allow fragment-specific behaviors
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(viewModel.handleOnBackPressed()) {
                    closeFragment()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        val loadedDiner = args.diner
        viewModel.initializeForDiner(loadedDiner)

        when {
            (findNavController().navigatorProvider.getNavigator(CustomNavigator::class.java).lastNavigationWasBackward) -> {
                // Returning from DebtEntryFragment
                binding.appbarLayout.setExpanded(false, false)

                if (loadedDiner.photoUri == null) {
                    binding.nestedScrollView.isNestedScrollingEnabled = false
                } else {
                    binding.nestedScrollView.isNestedScrollingEnabled = true

                    Glide.with(this)
                        .load(Uri.parse(loadedDiner.photoUri))
                        .apply(RequestOptions().dontTransform())
                        .into(binding.dinerImage)
                }

                findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>(
                    "DebtEntryNavBack")?.observe(viewLifecycleOwner) {

                    if (it) {
                        startTransitionPending = true
                    } else {
                        view.doOnPreDraw { startPostponedEnterTransition() }
                    }
                }
            }
            else -> {
                // Opening fragment from DinerFragment
                if (loadedDiner.photoUri == null) {
                    binding.nestedScrollView.isNestedScrollingEnabled = false
                    binding.appbarLayout.setExpanded(false, false)

                    setupCollapsedEnterTransition(loadedDiner)
                    view.doOnPreDraw { startPostponedEnterTransition() }
                } else {
                    binding.nestedScrollView.isNestedScrollingEnabled = true
                    binding.appbarLayout.setExpanded(true, false)
                    setupExpandedEnterTransition(loadedDiner)
                }
            }
        }

        qrCodeScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val paymentMethod = result.data?.extras?.get(requireContext().getString(R.string.intent_key_qrcode_payment_method))
                val address = result.data?.extras?.get(requireContext().getString(R.string.intent_key_qrcode_payment_method_address)) as String

                when (paymentMethod) {
                    PaymentMethod.PAYBACK_LATER -> { binding.emailBiographics.input.setText(address) }
                    PaymentMethod.VENMO -> { binding.venmoBiographics.input.setText(address) }
                    PaymentMethod.CASH_APP -> { binding.cashAppBiographics.input.setText(address) }
                    PaymentMethod.ALGO -> { binding.algorandBiographics.input.setText(address) }
                    else -> { }
                }
            } else {
                Log.e("false", result.toString())
                // TODO show snackbar
            }
        }

        setupToolbar()

        setupBiographics()

        setupSubtotalSection()

        setupDiscountSection()

        setupTaxTipSection()

        setupReimbursementsSection()

        setupDebtSection()

        binding.fab.setOnClickListener { viewModel.editBiographics() }
    }

    override fun onResume() {
        super.onResume()

        // Reset UI input/output locks leftover from aborted transitions/animations
        viewModel.unFreezeOutput()

        // Manually unlock so AppBarLayout.OnOffsetChangedListener can run
        viewModel.notifyTransitionFinished()
    }

    private fun closeFragment() {
        // Prevent callback from intercepting back pressed events
        backPressedCallback.isEnabled = false

        // Manually lock so AppBarLayout.OnOffsetChangedListener does not show fab
        viewModel.notifyTransitionStarted()
        binding.fab.hide()

        // Freeze UI in place as the fragment closes
        viewModel.freezeOutput()

        // Closing animation shrinking fragment into the FAB of the previous fragment.
        // Transition is defined here to incorporate dynamic changes to window insets.
//        if(args.editedItem == null) {
//            returnTransition = CircularRevealTransition(
//                binding.fadeView,
//                binding.revealedLayout,
//                args.originParams!!.withInsetsOn(binding.fab),
//                resources.getInteger(R.integer.frag_transition_duration).toLong(),
//                false)
//        } else {
//            // TODO
//        }

        viewModel.loadedDiner.value?.also {
            if (isToolBarCollapsed) {
                setupCollapsedReturnTransition(it)
            } else {
                setupExpandedReturnTransition(it)
            }
        }

        // Close fragment using default onBackPressed behavior
        requireActivity().onBackPressed()
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.toolbar_dinerdetails)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_light)
        binding.toolbar.setNavigationOnClickListener { viewModel.handleOnBackPressed() }

        viewModel.toolbarTitle.observe(viewLifecycleOwner) {
            binding.toolbar.title = it
        }

        (requireView().findViewById(R.id.appbar_layout) as AppBarLayout).addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                when {
                    viewModel.isInputLocked.value || viewModel.editingBiographics.value == true -> {
                        return@OnOffsetChangedListener
                    }
                    appBarLayout.totalScrollRange == 0 -> {
                        // View needs non-zero height to determine collapsed state
                        binding.toolbar.menu.setGroupVisible(R.id.group_editor, false)
                        binding.fab.hide()
                    }
                    verticalOffset == 0 -> {
                        // Fully expanded (hide toolbar icon and show fab)
                        binding.toolbar.menu.setGroupVisible(R.id.group_editor, false)

                        /* The FAB is rendered momentarily at the bottom of the screen if the user
                           drags the toolbar all the way to the expanded position. The anchor view
                           has the correct position and dimensions when this branch is called so the
                           root cause is unclear. A nominal delay before showing helps in some
                           cases. */
                        Handler(Looper.getMainLooper()).postDelayed({
                            binding.fab.show()
                        }, 100)
                    }
                    appBarLayout.totalScrollRange == -verticalOffset -> {
                        // Fully collapsed (show toolbar icon and hide fab)
                        binding.toolbar.menu.setGroupVisible(R.id.group_editor, true)
                        binding.fab.hide()
                    }
                    else -> {
                        // Partially collapsed (hide both toolbar icon and fab)
                        binding.toolbar.menu.setGroupVisible(R.id.group_editor, false)
                        binding.fab.hide()
                    }
                }
            })

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.edit_diner -> {
                    viewModel.editBiographics()
                    true
                }
                else -> {
                    false
                }
            }
        }
    }

    private fun setupBiographics() {
        viewModel.editingBiographics.observe(viewLifecycleOwner) {
            if (it) {
                binding.emailBiographics.container.transitionToEnd()
                binding.venmoBiographics.container.transitionToEnd()
                binding.cashAppBiographics.container.transitionToEnd()
                binding.algorandBiographics.container.transitionToEnd()

                binding.nestedScrollView.smoothScrollTo(0, 0)

                binding.fab.hide()
                binding.toolbar.menu.setGroupVisible(R.id.group_editor, false)
            } else {
                binding.emailBiographics.container.transitionToStart()
                binding.venmoBiographics.container.transitionToStart()
                binding.cashAppBiographics.container.transitionToStart()
                binding.algorandBiographics.container.transitionToStart()

                // Update edit controls unless fragment is transitioning
                if (!viewModel.isInputLocked.value) {
                    if(isToolBarCollapsed) {
                        binding.toolbar.menu.setGroupVisible(R.id.group_editor, true)
                    } else {
                        binding.fab.show()
                    }
                }
            }
        }

        setupBiographicsItem(PaymentMethod.PAYBACK_LATER, binding.emailBiographics, viewModel.email)

        setupBiographicsItem(PaymentMethod.VENMO, binding.venmoBiographics, viewModel.venmoAddress)

        setupBiographicsItem(PaymentMethod.CASH_APP, binding.cashAppBiographics, viewModel.cashtag)

        setupBiographicsItem(PaymentMethod.ALGO, binding.algorandBiographics, viewModel.algorandAddress)
    }

    private fun setupBiographicsItem(
        method: PaymentMethod,
        biographics: FragDinerDetailsBiographicItemBinding,
        addressLiveData: LiveData<Triple<Boolean, String, Boolean>>) {

        val addressName = requireContext().getString(method.addressNameStringId)
        val scanIconContentDescription = requireContext().getString(R.string.dinerdetails_biographics_scan_qr_code)
        val clearIconContentDescription = requireContext().getString(R.string.dinerdetails_biographics_clear_address)
        val addressSetColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnSurface, it, true) }.data
        val addressUnsetColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorOnSurfaceLowEmphasis, it, true) }.data
        val inactiveAlpha = 0.25f

        biographics.apply {
            icon.setImageResource(method.paymentIconId)
            inputLayout.hint = addressName

            if (method.addressCanBeEmail) {
                viewModel.dinerEmailAddresses.observe(viewLifecycleOwner) {
                    if (it.isEmpty()) {
                        input.setAdapter(null)
                    } else {
                        input.setAdapter(ArrayAdapter(
                            requireContext(),
                            R.layout.autocomplete_textview,
                            it.take(3)))
                    }
                }
            }

            input.onFocusChangeListener = View.OnFocusChangeListener { view, focused ->
                if (focused)
                    input.showDropDown()
            }

            input.addTextChangedListener { editable ->
                when {
                    (editable?.isNotEmpty() == true) -> {
                        inputLayout.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_cancel)
                        inputLayout.endIconContentDescription = clearIconContentDescription
                        inputLayout.setEndIconOnClickListener {
                            input.text = null
                        }
                    }
                    method.addressCodeScannable -> {
                        inputLayout.endIconDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_qr_code_24dp)
                        inputLayout.endIconContentDescription = scanIconContentDescription
                        inputLayout.setEndIconOnClickListener {
                            val intent = Intent(requireContext(), QRCodeScannerActivity::class.java)
                            intent.putExtra(getString(R.string.intent_key_qrcode_payment_method), method)
                            intent.putExtra(getString(R.string.intent_key_qrcode_diner_name), viewModel.toolbarTitle.value)
                            qrCodeScannerLauncher.launch(intent)
                        }
                    }
                    else -> {
                        // Ripple from clearing text gets stuck unless the icon removal is delayed
                        Handler(Looper.getMainLooper()).postDelayed({
                            inputLayout.endIconDrawable = null
                            inputLayout.setEndIconOnClickListener(null)
                        }, 100)
                    }
                }
            }
        }

        addressLiveData.observe(viewLifecycleOwner) {
            biographics.address.text = it.second
            if (it.first) {
                biographics.icon.alpha = 1.0f
                biographics.input.setText(it.second)
                biographics.address.setTextColor(addressSetColor)
            } else {
                biographics.icon.alpha = if (it.third) 1.0f else inactiveAlpha
                biographics.input.text = null
                biographics.address.setTextColor(addressUnsetColor)
            }
        }
    }

    private fun setupSubtotalSection() {
        val itemAdapter = ItemRecyclerViewAdapter(requireContext())
        binding.itemList.adapter = itemAdapter

        // Swipe to delete interaction is hard to discover and may be triggered accidentally
        /*
        ItemTouchHelper(object: ItemTouchHelper.SimpleCallback(0,ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete_outline)!!
            val background = ColorDrawable(TypedValue().also { requireContext().theme.resolveAttribute(DELETE_BACKGROUND_COLOR, it, true) }.data)

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewModel.removeItem(viewHolder.itemView.tag as Item)
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

                val itemView = viewHolder.itemView
                val backgroundCornerOffset = 20

                val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
                val iconBottom = iconTop + icon.intrinsicHeight

                when {
                    dX > 0 -> {
                        val iconLeft = itemView.left + iconMargin
                        val iconRight = itemView.left + iconMargin + icon.intrinsicWidth
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt() + backgroundCornerOffset, itemView.bottom)
                    }
                    dX < 0 -> {
                        val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                        val iconRight = itemView.right - iconMargin
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        background.setBounds(itemView.right + dX.toInt() - backgroundCornerOffset, itemView.top, itemView.right, itemView.bottom)
                    }
                    else -> {
                        background.setBounds(0, 0, 0 ,0)
                    }
                }

                background.draw(c)
                icon.draw(c)
            }

        }).attachToRecyclerView(binding.itemList)
        */

        binding.itemsSubtotalSection.setOnClickListener {
            binding.itemsExpandingLayout.toggleExpansion()
            itemsExpanded = if (itemsExpanded) {
                binding.itemsSubtotalExpandIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_chevron_expanded, requireActivity().theme))
                (binding.itemsSubtotalExpandIcon.drawable as Animatable).start()
                false
            } else {
                binding.itemsSubtotalExpandIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_chevron_collapsed, requireActivity().theme))
                (binding.itemsSubtotalExpandIcon.drawable as Animatable).start()
                true
            }
        }

        viewModel.subtotalString.observe(viewLifecycleOwner) { binding.itemsSubtotalAmount.text = it }

        viewModel.items.observe(viewLifecycleOwner) { items ->
            lifecycleScope.launch { itemAdapter.updateDataSet(items) }
        }

        viewModel.enableItemExpansion.observe(viewLifecycleOwner) {
            if(it) {
                binding.itemsSubtotalExpandIcon.visibility = View.VISIBLE
                binding.itemsSubtotalSection.isClickable = true
            } else {
                binding.itemsSubtotalExpandIcon.visibility = View.GONE
                binding.itemsSubtotalSection.isClickable = false
            }
        }
    }

    private fun setupDiscountSection() {
        val discountAdapter = DiscountRecyclerViewAdapter(requireContext())
        binding.discountList.adapter = discountAdapter

        // Swipe to delete interaction is hard to discover and may be triggered accidentally
        /*
        ItemTouchHelper(object: ItemTouchHelper.SimpleCallback(0,ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete_outline)!!
            val background = ColorDrawable(TypedValue().also { requireContext().theme.resolveAttribute(DELETE_BACKGROUND_COLOR, it, true) }.data)

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewModel.removeItem(viewHolder.itemView.tag as Item)
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

                val itemView = viewHolder.itemView
                val backgroundCornerOffset = 20

                val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
                val iconBottom = iconTop + icon.intrinsicHeight

                when {
                    dX > 0 -> {
                        val iconLeft = itemView.left + iconMargin
                        val iconRight = itemView.left + iconMargin + icon.intrinsicWidth
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt() + backgroundCornerOffset, itemView.bottom)
                    }
                    dX < 0 -> {
                        val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                        val iconRight = itemView.right - iconMargin
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        background.setBounds(itemView.right + dX.toInt() - backgroundCornerOffset, itemView.top, itemView.right, itemView.bottom)
                    }
                    else -> {
                        background.setBounds(0, 0, 0 ,0)
                    }
                }

                background.draw(c)
                icon.draw(c)
            }
        }).attachToRecyclerView(binding.discountList)
        */

        binding.discountsSection.setOnClickListener {
            binding.discountsExpandingLayout.toggleExpansion()
            discountsExpanded = if (discountsExpanded) {
                binding.discountsTotalExpandIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_chevron_expanded, requireActivity().theme))
                (binding.discountsTotalExpandIcon.drawable as Animatable).start()
                false
            } else {
                binding.discountsTotalExpandIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_chevron_collapsed, requireActivity().theme))
                (binding.discountsTotalExpandIcon.drawable as Animatable).start()
                true
            }
        }

        viewModel.enableDiscountExpansion.observe(viewLifecycleOwner) {
            if(it) {
                binding.discountsTotalExpandIcon.visibility = View.VISIBLE
                binding.discountsSection.isClickable = true
            } else {
                binding.discountsTotalExpandIcon.visibility = View.GONE
                binding.discountsSection.isClickable = false
            }
        }

        viewModel.discounts.observe(viewLifecycleOwner) { discounts ->
            lifecycleScope.launch { discountAdapter.updateDataSet(discounts) }
        }

        viewModel.unusedDiscountString.observe(viewLifecycleOwner) {
            if (it == null) {
                binding.discountUnusedRow.visibility = View.GONE
            } else {
                binding.discountsUnused.text = it
                binding.discountUnusedRow.visibility = View.VISIBLE
            }
        }

        viewModel.borrowedDiscountString.observe(viewLifecycleOwner) {
            if (it == null) {
                binding.discountBorrowedRow.visibility = View.GONE
            } else {
                binding.discountsBorrowed.text = it
                binding.discountBorrowedRow.visibility = View.VISIBLE
            }
        }

        viewModel.discountsTotalString.observe(viewLifecycleOwner) { binding.discountsTotalAmount.text = it }
    }

    private fun setupTaxTipSection() {
        viewModel.afterDiscountsTotalString.observe(viewLifecycleOwner) { binding.afterDiscountAmount.text = it }
        viewModel.taxPercentString.observe(viewLifecycleOwner) { binding.taxPercent.text = it }
        viewModel.taxAmountString.observe(viewLifecycleOwner) { binding.taxAmount.text = it }
        viewModel.afterTaxTotalString.observe(viewLifecycleOwner) { binding.afterTaxAmount.text = it }
        viewModel.tipPercentString.observe(viewLifecycleOwner) { binding.tipPercent.text = it }
        viewModel.tipAmountString.observe(viewLifecycleOwner) { binding.tipAmount.text = it }
        viewModel.totalString.observe(viewLifecycleOwner) { binding.totalAmount.text = it }
    }

    private fun setupReimbursementsSection() {
        val reimbursementAdapter = ReimbursementRecyclerViewAdapter(requireContext())
        binding.reimbursementsList.adapter = reimbursementAdapter

        viewModel.reimbursements.observe(viewLifecycleOwner) { reimbursements ->
            lifecycleScope.launch { reimbursementAdapter.updateDataSet(reimbursements) }
        }

        binding.reimbursementsSection.setOnClickListener {
            binding.reimbursementsExpandingLayout.toggleExpansion()
            reimbursementsExpanded = if (reimbursementsExpanded) {
                binding.reimbursementTotalExpandIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_chevron_expanded, requireActivity().theme))
                (binding.reimbursementTotalExpandIcon.drawable as Animatable).start()
                false
            } else {
                binding.reimbursementTotalExpandIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_chevron_collapsed, requireActivity().theme))
                (binding.reimbursementTotalExpandIcon.drawable as Animatable).start()
                true
            }
        }

        viewModel.enableReimbursementExpansion.observe(viewLifecycleOwner) {
            if(it) {
                binding.reimbursementTotalExpandIcon.visibility = View.VISIBLE
                binding.reimbursementsSection.isClickable = true
            } else {
                binding.reimbursementTotalExpandIcon.visibility = View.GONE
                binding.reimbursementsSection.isClickable = false
            }
        }

        viewModel.reimbursementsTotalString.observe(viewLifecycleOwner) { binding.reimbursementTotal.text = it }
    }

    private fun setupDebtSection() {
        val debtAdapter = DebtRecyclerViewAdapter(requireContext(), object: DebtRecyclerViewAdapter.DebtRecyclerListener {
            override fun removeDebt(debt: Debt) { viewModel.removeDebt(debt) }
        })
        binding.debtList.adapter = debtAdapter

        viewModel.debts.observe(viewLifecycleOwner) { debts ->
            lifecycleScope.launch { debtAdapter.updateDataSet(debts) }.invokeOnCompletion {
                if (startTransitionPending) {
                    startTransitionPending = false
                    (view?.parent as? ViewGroup)?.doOnPreDraw {
                        binding.debtTotal.requestFocus()
                        startPostponedEnterTransition()
                    }
                }
            }
        }

        viewModel.debtTotalTitleString.observe(viewLifecycleOwner) { binding.debtTotalTitle.text = it }
        viewModel.debtTotalString.observe(viewLifecycleOwner) { binding.debtTotal.text = it }

        binding.addDebtButton.setOnClickListener {
            // Exit transition is needed to prevent next fragment from appearing immediately
            exitTransition = Hold().apply {
                duration = 0L
                addTarget(requireView())
            }

            (requireActivity() as MainActivity).storeViewAsBitmap(requireView())

            findNavController().navigate(
                DinerDetailsFragmentDirections.addDebt(args.diner),
                FragmentNavigatorExtras(
                    binding.addDebtContainer to binding.addDebtContainer.transitionName,
                    binding.addDebtButton to binding.addDebtButton.transitionName
                )
            )
        }
    }

    private fun setupNewEnterTransition() {
        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)
        binding.newContactButton.visibility = View.VISIBLE
        binding.container.transitionName = "new_contact_container_transition_name"

        val propCornerRadius = "com.grouptuity.grouptuity:CardViewExpandTransition:button_corner_radius"

        sharedElementEnterTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, true)
            .addElement(binding.newContactButton.transitionName,
                object: CardViewExpandTransition.Element {
                    override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
                        transitionValues.values[propCornerRadius] = 0.5
                    }

                    override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
                        transitionValues.values[propCornerRadius] = 0
                    }

                    override fun createAnimator(transition: Transition, sceneRoot: ViewGroup, startValues: TransitionValues, endValues: TransitionValues): Animator? {
                        val button = endValues.view as com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

                        val animator = ValueAnimator.ofFloat(0f, 1f)
                        animator.doOnStart {
                            // Button is smaller than the container initially so start with the card background hidden
                            binding.container.setCardBackgroundColor(Color.TRANSPARENT)
                            binding.container.elevation = 0f

                            // Container content will be hidden initially and fade in later
                            binding.coordinatorLayout.alpha = 0f
                        }

                        val surfaceColor = TypedValue().also { requireContext().theme.resolveAttribute(R.attr.colorSurface, it, true) }.data

                        animator.addUpdateListener {
                            val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)

                            // Adjust shape of button to match background container
                            val zeroTo20Progress = (5f*progress).coerceIn(0f, 1f)
                            button.shapeAppearanceModel = button.shapeAppearanceModel.withCornerSize(RelativeCornerSize(0.5f * (1f - zeroTo20Progress)))

                            // Restore card background once the button starts fading out
                            if(progress >= 0.2) {
                                binding.container.setCardBackgroundColor(surfaceColor)

                                val twentyTo80Progress = (1.666667f*progress - 0.33333f).coerceIn(0f, 1f)
                                button.alpha = 1f - twentyTo80Progress
                                binding.coordinatorLayout.alpha = twentyTo80Progress
                            }
                        }
                        animator.doOnEnd {
                            button.visibility = View.GONE
                        }

                        return animator
                    }
                }
            )
            .addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) { viewModel.notifyTransitionStarted() }
                override fun onTransitionEnd(transition: Transition) { viewModel.notifyTransitionFinished() }
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            })
    }

    private fun setupExpandedEnterTransition(diner: Diner) {
        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)

        binding.toolbar.menu.setGroupVisible(R.id.group_editor, false)

        Glide.with(this)
            .load(Uri.parse(diner.photoUri))
            .apply(RequestOptions().dontTransform())
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    startPostponedEnterTransition()
                    return true
                }

                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    startPostponedEnterTransition()
                    return false
                }
            })
            .into(binding.dinerImage)

        binding.container.transitionName = "container" + diner.id
        binding.dinerImage.transitionName = "image" + diner.id
        val PROP_IMAGE_HEIGHT = "com.grouptuity.grouptuity:CardViewExpandTransition:image_height"
        val PROP_IMAGE_WIDTH = "com.grouptuity.grouptuity:CardViewExpandTransition:image_width"
        val PROP_IMAGE_MARGIN = "com.grouptuity.grouptuity:CardViewExpandTransition:image_margin"

        sharedElementEnterTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, true)
            .addElement(binding.dinerImage.transitionName, object: CardViewExpandTransition.Element {
                override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
                    transitionValues.values[PROP_IMAGE_HEIGHT] = transitionValues.view.height
                    transitionValues.values[PROP_IMAGE_WIDTH] = transitionValues.view.width

                    // Assume all padding is the same so only query one value. Referenced parent view is
                    // the ConstraintLayout in the DinerFragment list item (frag_diners_listitem.xml).
                    transitionValues.values[PROP_IMAGE_MARGIN] =
                        (transitionValues.view.parent.parent.parent as View).paddingStart
                }

                override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
                    transitionValues.values[PROP_IMAGE_HEIGHT] = transitionValues.view.height
                    transitionValues.values[PROP_IMAGE_WIDTH] = transitionValues.view.width
                    transitionValues.values[PROP_IMAGE_MARGIN] = 0
                }

                override fun createAnimator(
                    transition: Transition,
                    sceneRoot: ViewGroup,
                    startValues: TransitionValues,
                    endValues: TransitionValues): Animator {

                    val startWidth = startValues.values[PROP_IMAGE_WIDTH] as Int
                    val startHeight = startValues.values[PROP_IMAGE_HEIGHT] as Int
                    val startMargin = startValues.values[PROP_IMAGE_MARGIN] as Int
                    val startCornerSize = 0.5f * startWidth // Shortcut for view starting as a circle

                    val endView = endValues.view
                    val endWidth = endValues.values[PROP_IMAGE_WIDTH] as Int
                    val endHeight = endValues.values[PROP_IMAGE_HEIGHT] as Int
                    val endMargin = endValues.values[PROP_IMAGE_MARGIN] as Int
                    val endCornerSize = 0f

                    val animator = ValueAnimator.ofFloat(0f, 1f)
                    animator.addUpdateListener {
                        val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)

                        val cornerSize = (startCornerSize + (endCornerSize - startCornerSize) * progress)
                        (endView as ShapeableImageView).shapeAppearanceModel =
                            endView.shapeAppearanceModel
                                .toBuilder()
                                .setAllCorners(CornerFamily.ROUNDED, cornerSize)
                                .build()

                        val width = (startWidth + (endWidth - startWidth) * progress).toInt()
                        val height = (startHeight + (endHeight - startHeight) * progress).toInt()
                        endView.layoutParams = CollapsingToolbarLayout.LayoutParams(width, height).apply {
                            val baseMargin = (startMargin + (endMargin - startMargin) * progress).toInt()
                            setMargins(baseMargin, baseMargin, baseMargin, baseMargin)
                        }
                    }
                    return animator
                }
            })
            .setOnTransitionStartCallback { transition: Transition, sceneRoot: ViewGroup, _: View, _: View ->
                sceneRoot.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.apply {
                    this.setExpandedTitleColor(Color.TRANSPARENT)
                    binding.nestedScrollView.alpha = 0f

                    this.animate()
                        .setDuration(transition.duration)
                        .setUpdateListener {
                            // Fade in CardView content during card expansion
                            val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)
                            binding.nestedScrollView.alpha = progress

                            // Fade in toolbar and navigation icon at the end of the animation
                            val eightyTo100Progress = (5f*progress - 4f).coerceIn(0f, 1f)
                            this.setExpandedTitleColor(
                                ColorUtils.setAlphaComponent(
                                    TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorOnPrimary, it, true) }.data,
                                    (eightyTo100Progress*255).toInt()
                                ))
                            sceneRoot.findViewById<Toolbar>(R.id.toolbar)?.alpha = eightyTo100Progress
                        }
                        .start()
                }
            }
            .addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) { viewModel.notifyTransitionStarted() }
                override fun onTransitionEnd(transition: Transition) { viewModel.notifyTransitionFinished() }
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            })
    }

    private fun setupCollapsedEnterTransition(diner: Diner) {
        binding.coveredFragment.setImageBitmap(MainActivity.storedViewBitmap)
        binding.container.transitionName = "container" + diner.id

        binding.toolbar.menu.setGroupVisible(R.id.group_editor, true)

        sharedElementEnterTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, true)
            .setOnTransitionStartCallback { transition: Transition, sceneRoot: ViewGroup, _: View, _: View ->
                sceneRoot.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.apply {
                    this.setCollapsedTitleTextColor(Color.TRANSPARENT)
                    this.setContentScrimColor(Color.TRANSPARENT)
                    this.setStatusBarScrimColor(Color.TRANSPARENT)
                    binding.nestedScrollView.alpha = 0f

                    this.animate()
                    .setDuration(transition.duration)
                    .setUpdateListener {
                        // Fade in CardView content and toolbar during card expansion
                        val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)
                        binding.nestedScrollView.alpha = progress
                        this.setContentScrimColor(
                            ColorUtils.setAlphaComponent(
                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorPrimary, it, true) }.data,
                                (progress*255).toInt()
                            ))
                        this.setStatusBarScrimColor(
                            ColorUtils.setAlphaComponent(
                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorPrimaryVariant, it, true) }.data,
                                (progress*255).toInt()
                            ))

                        // Fade in navigation icon and toolbar title at the end of the animation
                        val eightyTo100Progress = (5f*progress - 4f).coerceIn(0f, 1f)
                        sceneRoot.findViewById<Toolbar>(R.id.toolbar)?.alpha = eightyTo100Progress
                        this.setCollapsedTitleTextColor(
                            ColorUtils.setAlphaComponent(
                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorOnPrimary, it, true) }.data,
                                (eightyTo100Progress*255).toInt()
                            ))
                    }.start()
                }
            }
            .addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) { viewModel.notifyTransitionStarted() }
                override fun onTransitionEnd(transition: Transition) { viewModel.notifyTransitionFinished() }
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            })
    }

    private fun setupExpandedReturnTransition(diner: Diner) {
        binding.container.transitionName = "container" + diner.id
        binding.dinerImage.transitionName = "image" + diner.id

        val PROP_IMAGE_HEIGHT = "com.grouptuity.grouptuity:CardViewExpandTransition:image_height"
        val PROP_IMAGE_WIDTH = "com.grouptuity.grouptuity:CardViewExpandTransition:image_width"
        val PROP_IMAGE_MARGIN = "com.grouptuity.grouptuity:CardViewExpandTransition:image_margin"

        sharedElementReturnTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, false).apply {
            this.addElement(binding.dinerImage.transitionName, object: CardViewExpandTransition.Element {
                override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
                    transitionValues.values[PROP_IMAGE_HEIGHT] = transitionValues.view.height
                    transitionValues.values[PROP_IMAGE_WIDTH] = transitionValues.view.width
                    transitionValues.values[PROP_IMAGE_MARGIN] = 0
                }

                override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
                    transitionValues.values[PROP_IMAGE_HEIGHT] = transitionValues.view.height
                    transitionValues.values[PROP_IMAGE_WIDTH] = transitionValues.view.width

                    // Assume all padding is the same so only query one value. Referenced parent view is
                    // the ConstraintLayout in the DinerFragment list item (frag_diners_listitem.xml).
                    transitionValues.values[PROP_IMAGE_MARGIN] = (transitionValues.view.parent.parent.parent as View).paddingStart
                }

                override fun createAnimator(
                    transition: Transition,
                    sceneRoot: ViewGroup,
                    startValues: TransitionValues,
                    endValues: TransitionValues): Animator? {

                    val startView = startValues.view
                    val startWidth = startValues.values[PROP_IMAGE_WIDTH] as Int
                    val startHeight = startValues.values[PROP_IMAGE_HEIGHT] as Int
                    val startMargin = startValues.values[PROP_IMAGE_MARGIN] as Int
                    val startCornerSize = 0f

                    val endWidth = endValues.values[PROP_IMAGE_WIDTH] as Int
                    val endHeight = endValues.values[PROP_IMAGE_HEIGHT] as Int
                    val endMargin = endValues.values[PROP_IMAGE_MARGIN] as Int
                    val endCornerSize = 0.5f * endWidth // Shortcut for view starting as a circle

                    val animator = ValueAnimator.ofFloat(0f, 1f)
                    animator.addUpdateListener {
                        val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)

                        val cornerSize = (startCornerSize + (endCornerSize - startCornerSize) * progress)
                        (startView as ShapeableImageView).shapeAppearanceModel =
                            startView.shapeAppearanceModel
                                .toBuilder()
                                .setAllCorners(CornerFamily.ROUNDED, cornerSize)
                                .build()

                        val height = (startHeight + (endHeight - startHeight) * progress).toInt()
                        val width = (startWidth + (endWidth - startWidth) * progress).toInt()
                        startView.layoutParams = CollapsingToolbarLayout.LayoutParams(width, height).apply {
                            val margin = (startMargin + (endMargin - startMargin) * progress).toInt()
                            setMargins(margin, margin, margin, margin)
                        }
                    }
                    return animator
                }
            })

            this.setOnTransitionStartCallback { transition, _, startView, _ ->
                val toolbar = startView.findViewById<Toolbar>(R.id.toolbar)
                val appbarLayout = startView.findViewById<AppBarLayout>(R.id.appbar_layout)

                startView.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.apply {
                    this.animate().setDuration(transition.duration).setUpdateListener { animator ->
                        // Fade out CardView content and toolbar during card contraction
                        val progress = AccelerateDecelerateInterpolator().getInterpolation(animator.animatedFraction)
                        binding.nestedScrollView.alpha = 1f - progress
                        this.setContentScrimColor(
                            ColorUtils.setAlphaComponent(
                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorPrimary, it, true) }.data,
                                ((1f-progress)*255).toInt()
                            ))
                        this.setStatusBarScrimColor(
                            ColorUtils.setAlphaComponent(
                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorPrimaryVariant, it, true) }.data,
                                ((1f-progress)*255).toInt()
                            ))

                        // Fade out toolbar title and icons in the first 20% of transition
                        val zeroToTwentyProgress = (5f*progress).coerceIn(0f, 1f)
                        toolbar.alpha = 1f - zeroToTwentyProgress
                        this.setExpandedTitleColor(
                            ColorUtils.setAlphaComponent(
                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorOnPrimary, it, true) }.data,
                                ((1f - zeroToTwentyProgress)*255).toInt()
                            ))

                        // Elevation TODO verify purpose
                        appbarLayout.elevation = 0f
                    }.start()
                }
            }
        }

        // Return transition is needed to prevent next fragment from appearing immediately
        returnTransition = Hold().apply {
            duration = 0L
            addTarget(requireView())
        }
    }

    private fun setupCollapsedReturnTransition(diner: Diner) {
        binding.container.transitionName = "container" + diner.id

        binding.dinerImage.visibility = View.INVISIBLE

        sharedElementReturnTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, false).apply {
            this.setOnTransitionStartCallback { transition, _, startView, _ ->
                val toolbar = startView.findViewById<Toolbar>(R.id.toolbar)
                val appbarLayout = startView.findViewById<AppBarLayout>(R.id.appbar_layout)

                startView.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.apply {
                    this.animate().setDuration(transition.duration).setUpdateListener { animator ->
                        // Fade out CardView content and toolbar during card contraction
                        val progress = AccelerateDecelerateInterpolator().getInterpolation(animator.animatedFraction)
                        binding.nestedScrollView.alpha = 1f - progress
                        toolbar.alpha = 1f - progress
                        this.setContentScrimColor(
                            ColorUtils.setAlphaComponent(
                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorPrimary, it, true) }.data,
                                ((1f-progress)*255).toInt()
                            ))
                        this.setStatusBarScrimColor(
                            ColorUtils.setAlphaComponent(
                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorPrimaryVariant, it, true) }.data,
                                ((1f-progress)*255).toInt()
                            ))
                        this.setCollapsedTitleTextColor(
                            ColorUtils.setAlphaComponent(
                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorOnPrimary, it, true) }.data,
                                ((1f - progress)*255).toInt()
                            ))

                        // Elevation
                        appbarLayout.elevation = 0f
                    }.start()
                }
            }
        }

        // Return transition is needed to prevent next fragment from appearing immediately
        returnTransition = Hold().apply {
            duration = 0L
            addTarget(requireView())
        }
    }
}


private class ItemRecyclerViewAdapter(val context: Context): RecyclerView.Adapter<ItemRecyclerViewAdapter.ViewHolder>() {
    var mItemData = emptyList<Pair<Item, Triple<String, String, String?>>>()

    inner class ViewHolder(val viewBinding: FragDinerDetailsItemBinding): RecyclerView.ViewHolder(viewBinding.root)

    override fun getItemCount() = mItemData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(FragDinerDetailsItemBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val newItemDatum = mItemData[position]

        holder.apply {
            itemView.tag = newItemDatum.first
            val (name, price, splitString) = newItemDatum.second

            viewBinding.name.text = name
            viewBinding.itemPrice.text = price

            if (splitString == null) {
                viewBinding.splitSummary.visibility = View.GONE
            } else {
                viewBinding.splitSummary.visibility = View.VISIBLE
                viewBinding.splitSummary.text = splitString
            }
        }
    }

    suspend fun updateDataSet(newItemData: List<Pair<Item, Triple<String, String, String?>>>) {
        val adapter = this

        val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize() = mItemData.size

            override fun getNewListSize() = newItemData.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newItemData[newPosition].first.id == mItemData[oldPosition].first.id

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val newItemDatum = newItemData[newPosition]
                val oldItemDatum = mItemData[oldPosition]

                return newItemDatum.first.id == oldItemDatum.first.id &&
                        newItemDatum.second == oldItemDatum.second
            }
        })

        withContext(Dispatchers.Main) {
            mItemData = newItemData
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}


private class DiscountRecyclerViewAdapter(val context: Context): RecyclerView.Adapter<DiscountRecyclerViewAdapter.ViewHolder>() {
    var mDiscountData = emptyList<Pair<Discount, Triple<String, String, String?>>>()

    inner class ViewHolder(val viewBinding: FragDinerDetailsDiscountBinding): RecyclerView.ViewHolder(viewBinding.root)

    override fun getItemCount() = mDiscountData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(FragDinerDetailsDiscountBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val newDiscountDatum = mDiscountData[position]

        holder.apply {
            itemView.tag = newDiscountDatum.first
            val (description, share, splitString) = newDiscountDatum.second

            viewBinding.description.text = description
            viewBinding.discountShare.text = share

            if (splitString == null) {
                viewBinding.splitSummary.visibility = View.GONE
            } else {
                viewBinding.splitSummary.visibility = View.VISIBLE
                viewBinding.splitSummary.text = splitString
            }
        }
    }

    suspend fun updateDataSet(newDiscountData: List<Pair<Discount, Triple<String, String, String?>>>) {
        val adapter = this

        val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize() = mDiscountData.size

            override fun getNewListSize() = newDiscountData.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newDiscountData[newPosition].first.id == mDiscountData[oldPosition].first.id

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val newItemDatum = newDiscountData[newPosition]
                val oldItemDatum = mDiscountData[oldPosition]

                return newItemDatum.first.id == oldItemDatum.first.id &&
                        newItemDatum.second == oldItemDatum.second
            }
        })

        withContext(Dispatchers.Main) {
            mDiscountData = newDiscountData
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}


private class ReimbursementRecyclerViewAdapter(val context: Context): RecyclerView.Adapter<ReimbursementRecyclerViewAdapter.ViewHolder>() {
    var mReimbursementData = emptyList<Triple<String, String, String>>()

    inner class ViewHolder(val viewBinding: FragDinerDetailsReimbursementBinding): RecyclerView.ViewHolder(viewBinding.root)

    override fun getItemCount() = mReimbursementData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(FragDinerDetailsReimbursementBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val newReimbursementDatum = mReimbursementData[position]

        holder.apply {
            val (description, share, splitString) = newReimbursementDatum
            viewBinding.reimbursementDescription.text = description
            viewBinding.discountShare.text = share
            viewBinding.discountDescription.text = splitString
        }
    }

    suspend fun updateDataSet(newReimbursementData: List<Triple<String, String, String>>) {
        val adapter = this

        val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize() = mReimbursementData.size

            override fun getNewListSize() = newReimbursementData.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newReimbursementData[newPosition] == mReimbursementData[oldPosition]

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                return newReimbursementData[newPosition] == mReimbursementData[oldPosition]
            }
        })

        withContext(Dispatchers.Main) {
            mReimbursementData = newReimbursementData
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}


private class DebtRecyclerViewAdapter(val context: Context, val listener: DebtRecyclerListener): RecyclerView.Adapter<DebtRecyclerViewAdapter.ViewHolder>() {
    var mDebtData = emptyList<Pair<Debt,Triple<String, String, String>>>()

    interface DebtRecyclerListener {
        fun removeDebt(debt: Debt)
    }

    inner class ViewHolder(val viewBinding: FragDinerDetailsDebtBinding): RecyclerView.ViewHolder(viewBinding.root)

    override fun getItemCount() = mDebtData.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(FragDinerDetailsDebtBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val newDebtDatum = mDebtData[position]

        holder.apply {
            itemView.tag = newDebtDatum.first
            val (name, share, description) = newDebtDatum.second

            viewBinding.debtName.text = name
            viewBinding.debtShare.text = share
            viewBinding.debtDescription.text = description

            viewBinding.remove.setOnClickListener {
                listener.removeDebt(newDebtDatum.first)
            }
        }
    }

    suspend fun updateDataSet(newDebtData: List<Pair<Debt, Triple<String, String, String>>>) {
        val adapter = this

        val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize() = mDebtData.size

            override fun getNewListSize() = newDebtData.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
                newDebtData[newPosition].first.id == mDebtData[oldPosition].first.id

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int) =
                newDebtData[newPosition].second == mDebtData[oldPosition].second
        })

        withContext(Dispatchers.Main) {
            mDebtData = newDebtData
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}