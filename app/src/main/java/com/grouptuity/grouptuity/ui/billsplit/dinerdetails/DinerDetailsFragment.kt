package com.grouptuity.grouptuity.ui.billsplit.dinerdetails

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnPreDraw
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import com.grouptuity.grouptuity.databinding.*
import com.grouptuity.grouptuity.ui.calculator.CALCULATOR_RETURN_KEY
import com.grouptuity.grouptuity.ui.custom.CustomNavigator
import com.grouptuity.grouptuity.ui.custom.transitions.CardViewExpandTransition
import com.grouptuity.grouptuity.ui.custom.transitions.Revealable
import com.grouptuity.grouptuity.ui.custom.transitions.RevealableImpl
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// private const val DELETE_BACKGROUND_COLOR = R.attr.colorTertiary

class DinerDetailsFragment: Fragment(), Revealable by RevealableImpl() {
    private var binding by setNullOnDestroy<FragDinerDetailsBinding>()
    private val args: DinerDetailsFragmentArgs by navArgs()
    private lateinit var viewModel: DinerDetailsViewModel
    private lateinit var backPressedCallback: OnBackPressedCallback
    private var isToolBarCollapsed = false
    private var itemsExpanded = false
    private var discountsExpanded = false
    private var reimbursementsExpanded = false
    private var startTransitionPending = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(requireActivity()).get(DinerDetailsViewModel::class.java)
        binding = FragDinerDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Intercept user interactions while while fragment transitions are running
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

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener { viewModel.handleOnBackPressed() }
        (view.findViewById(R.id.appbar_layout) as AppBarLayout).addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
                isToolBarCollapsed = when (appBarLayout.totalScrollRange) {
                    abs(verticalOffset) -> {
                        true
                    }
                    else -> {
                        false
                    }
                }
            })

        val loadedDiner = args.diner

        when {
            loadedDiner == null -> {
                // Creating a Diner using a new Contact
                viewModel.initializeForDiner(null)
                binding.toolbar.title = getString(R.string.dinerdetails_toolbar_title_new_diner)

                // TODO
                postponeEnterTransition()
                view.doOnPreDraw { startPostponedEnterTransition() }
            }
            findNavController().navigatorProvider.getNavigator(CustomNavigator::class.java).lastNavigationWasBackward -> {
                // Navigating back from DebtEntryFragment
                viewModel.initializeForDiner(loadedDiner)
                binding.toolbar.title = loadedDiner.name
                binding.appbarLayout.setExpanded(false, false)

                if (loadedDiner.photoUri == null) {
                    binding.nestedScrollView.isNestedScrollingEnabled = false
                } else {
                    Glide.with(this)
                        .load(Uri.parse(loadedDiner.photoUri))
                        .apply(RequestOptions().dontTransform())
                        .into(binding.dinerImage)
                }

                postponeEnterTransition()
                view.doOnPreDraw { startPostponedEnterTransition() }

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
                // Inspecting details for existing diner
                viewModel.initializeForDiner(loadedDiner)
                binding.toolbar.title = loadedDiner.name

                if (loadedDiner.photoUri == null) {
                    setupCollapsedEnterTransition(loadedDiner)
                } else {
                    setupExpandedEnterTransition(loadedDiner)
                }
            }
        }

        setupSubtotalSection()
        setupDiscountSection()
        setupTaxTipSection()
        setupReimbursementsSection()
        setupDebtSection()
    }

    override fun onResume() {
        super.onResume()

        // Reset UI input/output locks leftover from aborted transitions/animations
        viewModel.unFreezeOutput()
    }

    private fun closeFragment() {
        // Prevent callback from intercepting back pressed events
        backPressedCallback.isEnabled = false

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

        val loadedDiner = viewModel.loadedDiner.value
        when {
            loadedDiner == null -> {
                // TODO
            }
            isToolBarCollapsed -> {
                setupCollapsedReturnTransition(loadedDiner)
            } else -> {
                setupExpandedReturnTransition(loadedDiner)
            }
        }

        // Close fragment using default onBackPressed behavior
        requireActivity().onBackPressed()
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
        // TODO
    }

    private fun setupExpandedEnterTransition(diner: Diner) {
        postponeEnterTransition()
        binding.coveredFragment.setImageBitmap(coveredFragmentBitmap)
        binding.nestedScrollView.isNestedScrollingEnabled = true
        binding.appbarLayout.setExpanded(true, false)

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

        binding.container.transitionName = "container" + diner.lookupKey
        binding.dinerImage.transitionName = "image" + diner.lookupKey
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
        postponeEnterTransition()
        binding.coveredFragment.setImageBitmap(coveredFragmentBitmap)
        binding.nestedScrollView.isNestedScrollingEnabled = false
        binding.appbarLayout.setExpanded(false, false)

        binding.container.transitionName = "container" + diner.lookupKey

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


        requireView().doOnPreDraw { startPostponedEnterTransition() }
    }

    private fun setupNewExitTransition() {
        // TODO
    }

    private fun setupExpandedReturnTransition(diner: Diner) {
        binding.container.transitionName = "container" + diner.lookupKey
        binding.dinerImage.transitionName = "image" + diner.lookupKey

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
        binding.container.transitionName = "container" + diner.lookupKey

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

    private fun setupNewReturnTransition() {
        // TODO
    }

//    private fun setupEditTransitions(view: View, diner: Diner, isToolbarLocked: Boolean) {
//        binding.coveredFragment.setImageBitmap(coveredFragmentBitmap)
//
//        binding.container.transitionName = "container" + diner.lookupKey
//        binding.dinerImage.transitionName = "image" + diner.lookupKey
//
//        val PROP_IMAGE_HEIGHT = "com.grouptuity.grouptuity:CardViewExpandTransition:image_height"
//        val PROP_IMAGE_WIDTH = "com.grouptuity.grouptuity:CardViewExpandTransition:image_width"
//        val PROP_IMAGE_MARGIN = "com.grouptuity.grouptuity:CardViewExpandTransition:image_margin"
//
//        sharedElementEnterTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, true)
//            .addListener(object : Transition.TransitionListener {
//                override fun onTransitionStart(transition: Transition) { viewModel.notifyTransitionStarted() }
//                override fun onTransitionEnd(transition: Transition) { viewModel.notifyTransitionFinished() }
//                override fun onTransitionCancel(transition: Transition) {}
//                override fun onTransitionPause(transition: Transition) {}
//                override fun onTransitionResume(transition: Transition) {}
//            })
//
//        sharedElementReturnTransition = CardViewExpandTransition(binding.container.transitionName, binding.coordinatorLayout.id, false)
//
//        if (isToolbarLocked) {
//            binding.nestedScrollView.isNestedScrollingEnabled = false
//            binding.appbarLayout.setExpanded(false, false)
//
//            (sharedElementEnterTransition as CardViewExpandTransition).setOnTransitionStartCallback { transition: Transition, sceneRoot: ViewGroup, _: View, _: View ->
//                sceneRoot.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.apply {
//                    this.setCollapsedTitleTextColor(Color.TRANSPARENT)
//                    this.setContentScrimColor(Color.TRANSPARENT)
//                    this.setStatusBarScrimColor(Color.TRANSPARENT)
//                    binding.nestedScrollView.alpha = 0f
//
//                    this.animate()
//                        .setDuration(transition.duration)
//                        .setUpdateListener {
//                            // Fade in CardView content and toolbar during card expansion
//                            val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)
//                            binding.nestedScrollView.alpha = progress
//                            this.setContentScrimColor(
//                                ColorUtils.setAlphaComponent(
//                                    TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorPrimary, it, true) }.data,
//                                    (progress*255).toInt()
//                                ))
//                            this.setStatusBarScrimColor(
//                                ColorUtils.setAlphaComponent(
//                                    TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorPrimaryVariant, it, true) }.data,
//                                    (progress*255).toInt()
//                                ))
//
//                            // Fade in navigation icon and toolbar title at the end of the animation
//                            val eightyTo100Progress = (5f*progress - 4f).coerceIn(0f, 1f)
//                            sceneRoot.findViewById<Toolbar>(R.id.toolbar)?.alpha = eightyTo100Progress
//                            this.setCollapsedTitleTextColor(
//                                ColorUtils.setAlphaComponent(
//                                    TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorOnPrimary, it, true) }.data,
//                                    (eightyTo100Progress*255).toInt()
//                                ))
//                        }
//                        .start()
//                }
//            }
//
//
//        } else {
//            binding.nestedScrollView.isNestedScrollingEnabled = true
//            binding.appbarLayout.setExpanded(true, false)
//
//            (sharedElementEnterTransition as CardViewExpandTransition).addElement(
//                binding.dinerImage.transitionName,
//                object: CardViewExpandTransition.Element {
//                    override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
//                        transitionValues.values[PROP_IMAGE_HEIGHT] = transitionValues.view.height
//                        transitionValues.values[PROP_IMAGE_WIDTH] = transitionValues.view.width
//
//                        // Assume all padding is the same so only query one value. Referenced parent view is
//                        // the ConstraintLayout in the DinerFragment list item (frag_diners_listitem.xml).
//                        transitionValues.values[PROP_IMAGE_MARGIN] =
//                            (transitionValues.view.parent.parent.parent as View).paddingStart
//                    }
//
//                    override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
//                        transitionValues.values[PROP_IMAGE_HEIGHT] = transitionValues.view.height
//                        transitionValues.values[PROP_IMAGE_WIDTH] = transitionValues.view.width
//                        transitionValues.values[PROP_IMAGE_MARGIN] = 0
//                    }
//
//                    override fun createAnimator(
//                        transition: Transition,
//                        sceneRoot: ViewGroup,
//                        startValues: TransitionValues,
//                        endValues: TransitionValues): Animator {
//
//                        val startWidth = startValues.values[PROP_IMAGE_WIDTH] as Int
//                        val startHeight = startValues.values[PROP_IMAGE_HEIGHT] as Int
//                        val startMargin = startValues.values[PROP_IMAGE_MARGIN] as Int
//                        val startCornerSize = 0.5f * startWidth // Shortcut for view starting as a circle
//
//                        val endView = endValues.view
//                        val endWidth = endValues.values[PROP_IMAGE_WIDTH] as Int
//                        val endHeight = endValues.values[PROP_IMAGE_HEIGHT] as Int
//                        val endMargin = endValues.values[PROP_IMAGE_MARGIN] as Int
//                        val endCornerSize = 0f
//
//                        val animator = ValueAnimator.ofFloat(0f, 1f)
//                        animator.addUpdateListener {
//                            val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)
//
//                            val cornerSize = (startCornerSize + (endCornerSize - startCornerSize) * progress)
//                            (endView as ShapeableImageView).shapeAppearanceModel =
//                                endView.shapeAppearanceModel
//                                    .toBuilder()
//                                    .setAllCorners(CornerFamily.ROUNDED, cornerSize)
//                                    .build()
//
//                            val width = (startWidth + (endWidth - startWidth) * progress).toInt()
//                            val height = (startHeight + (endHeight - startHeight) * progress).toInt()
//                            endView.layoutParams = CollapsingToolbarLayout.LayoutParams(width, height).apply {
//                                val baseMargin = (startMargin + (endMargin - startMargin) * progress).toInt()
//                                setMargins(baseMargin, baseMargin, baseMargin, baseMargin)
//                            }
//                        }
//                        return animator
//                    }
//                }
//            )
//            .setOnTransitionStartCallback { transition: Transition, sceneRoot: ViewGroup, _: View, _: View ->
//                sceneRoot.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.apply {
//                    this.setExpandedTitleColor(Color.TRANSPARENT)
//                    binding.nestedScrollView.alpha = 0f
//
//                    this.animate()
//                        .setDuration(transition.duration)
//                        .setUpdateListener {
//                            // Fade in CardView content during card expansion
//                            val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)
//                            binding.nestedScrollView.alpha = progress
//
//                            // Fade in toolbar and navigation icon at the end of the animation
//                            val eightyTo100Progress = (5f*progress - 4f).coerceIn(0f, 1f)
//                            this.setExpandedTitleColor(
//                                ColorUtils.setAlphaComponent(
//                                    TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorOnPrimary, it, true) }.data,
//                                    (eightyTo100Progress*255).toInt()
//                                ))
//                            sceneRoot.findViewById<Toolbar>(R.id.toolbar)?.alpha = eightyTo100Progress
//                        }
//                        .start()
//                }
//            }
//
//            (sharedElementReturnTransition as CardViewExpandTransition).addElement(
//                binding.dinerImage.transitionName,
//                object: CardViewExpandTransition.Element {
//
//                override fun captureStartValues(transition: Transition, transitionValues: TransitionValues) {
//                    transitionValues.values[PROP_IMAGE_HEIGHT] = transitionValues.view.height
//                    transitionValues.values[PROP_IMAGE_WIDTH] = transitionValues.view.width
//                    transitionValues.values[PROP_IMAGE_MARGIN] = 0
//                }
//
//                override fun captureEndValues(transition: Transition, transitionValues: TransitionValues) {
//                    transitionValues.values[PROP_IMAGE_HEIGHT] = transitionValues.view.height
//                    transitionValues.values[PROP_IMAGE_WIDTH] = transitionValues.view.width
//
//                    // Assume all padding is the same so only query one value. Referenced parent view is
//                    // the ConstraintLayout in the DinerFragment list item (frag_diners_listitem.xml).
//                    transitionValues.values[PROP_IMAGE_MARGIN] = (transitionValues.view.parent.parent.parent as View).paddingStart
//                }
//
//                override fun createAnimator(
//                    transition: Transition,
//                    sceneRoot: ViewGroup,
//                    startValues: TransitionValues,
//                    endValues: TransitionValues): Animator? {
//
//                    val startView = startValues.view
//                    val startWidth = startValues.values[PROP_IMAGE_WIDTH] as Int
//                    val startHeight = startValues.values[PROP_IMAGE_HEIGHT] as Int
//                    val startMargin = startValues.values[PROP_IMAGE_MARGIN] as Int
//                    val startCornerSize = 0f
//
//                    val endWidth = endValues.values[PROP_IMAGE_WIDTH] as Int
//                    val endHeight = endValues.values[PROP_IMAGE_HEIGHT] as Int
//                    val endMargin = endValues.values[PROP_IMAGE_MARGIN] as Int
//                    val endCornerSize = 0.5f * endWidth // Shortcut for view starting as a circle
//
//                    val animator = ValueAnimator.ofFloat(0f, 1f)
//                    animator.addUpdateListener {
//                        val progress = AccelerateDecelerateInterpolator().getInterpolation(it.animatedFraction)
//
//                        val cornerSize = (startCornerSize + (endCornerSize - startCornerSize) * progress)
//                        (startView as ShapeableImageView).shapeAppearanceModel =
//                            startView.shapeAppearanceModel
//                                .toBuilder()
//                                .setAllCorners(CornerFamily.ROUNDED, cornerSize)
//                                .build()
//
//                        val height = (startHeight + (endHeight - startHeight) * progress).toInt()
//                        val width = (startWidth + (endWidth - startWidth) * progress).toInt()
//                        startView.layoutParams = CollapsingToolbarLayout.LayoutParams(width, height).apply {
//                            val margin = (startMargin + (endMargin - startMargin) * progress).toInt()
//                            setMargins(margin, margin, margin, margin)
//                        }
//                    }
//                    return animator
//                }
//            })
//            .setOnTransitionStartCallback { transition, _, startView, _ ->
//                val toolbar = startView.findViewById<Toolbar>(R.id.toolbar)
//                val appbarLayout = startView.findViewById<AppBarLayout>(R.id.appbar_layout)
//
//                if (isToolBarCollapsed) {
//                    binding.dinerImage.visibility = View.GONE
//                }
//
////                startView.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.apply {
////                    this.animate().setDuration(transition.duration).setUpdateListener { animator ->
////                        val progress = AccelerateDecelerateInterpolator().getInterpolation(animator.animatedFraction)
////
////                        // Fade out CardView content and toolbar during card contraction
//////                        binding.nestedScrollView.alpha = 1f - progress
////
//////                        this.setContentScrimColor(
//////                            ColorUtils.setAlphaComponent(
//////                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorPrimary, it, true) }.data,
//////                                ((1f-progress)*255).toInt()
//////                            ))
//////                        this.setStatusBarScrimColor(
//////                            ColorUtils.setAlphaComponent(
//////                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorPrimaryVariant, it, true) }.data,
//////                                ((1f-progress)*255).toInt()
//////                            ))
////
////                        // Fade out toolbar title and icons in the first 20% of transition
//////                        val zeroToTwentyProgress = (5f*progress).coerceIn(0f, 1f)
//////                        this.setExpandedTitleColor(
//////                            ColorUtils.setAlphaComponent(
//////                                TypedValue().also { this.context.theme.resolveAttribute(R.attr.colorOnPrimary, it, true) }.data,
//////                                ((1f - zeroToTwentyProgress)*255).toInt()
//////                            ))
////
//////                        toolbar.alpha = 1f - progress
////
////                        // Elevation
////                        appbarLayout.elevation = 0f
////                    }.start()
////                }
//            }
//        }
//
//        // Return transition is needed to prevent next fragment from appearing immediately
//        returnTransition = Hold().apply {
//            duration = 0L
//            addTarget(view)
//        }
//
//        postponeEnterTransition()
//    }
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
                //TODO handle orphan items / discounts from removed diner
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