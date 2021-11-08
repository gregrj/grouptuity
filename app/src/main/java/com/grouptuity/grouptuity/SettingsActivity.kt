package com.grouptuity.grouptuity

import android.app.Application
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.transition.Hold
import com.grouptuity.grouptuity.data.CalculationType
import com.grouptuity.grouptuity.data.Repository
import com.grouptuity.grouptuity.data.UIViewModel
import com.grouptuity.grouptuity.databinding.ActivityMainBinding
import com.grouptuity.grouptuity.databinding.FragSettingsHolderBinding
import com.grouptuity.grouptuity.ui.calculator.CALCULATOR_RETURN_KEY
import com.grouptuity.grouptuity.ui.custom.CustomNavigator
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy
import java.text.NumberFormat


class SettingsViewModel(app: Application): UIViewModel(app) {
    val preferenceDataStore = object: PreferenceDataStore() {
        override fun getBoolean(key: String?, defValue: Boolean) = repository.keyStoredPreferenceMap[key]?.value as? Boolean ?: defValue
        override fun getFloat(key: String?, defValue: Float) = repository.keyStoredPreferenceMap[key]?.value as? Float ?: defValue
        override fun getInt(key: String?, defValue: Int) = repository.keyStoredPreferenceMap[key]?.value as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = repository.keyStoredPreferenceMap[key]?.value as? Long ?: defValue
        override fun getString(key: String?, defValue: String?) = repository.keyStoredPreferenceMap[key]?.value as? String ?: defValue

        override fun putBoolean(key: String?, value: Boolean) { (repository.keyStoredPreferenceMap[key] as? Repository.StoredPreference<Boolean>)?.value = value }
        override fun putFloat(key: String?, value: Float) { (repository.keyStoredPreferenceMap[key] as? Repository.StoredPreference<Float>)?.value = value }
        override fun putInt(key: String?, value: Int) { (repository.keyStoredPreferenceMap[key] as? Repository.StoredPreference<Int>)?.value = value }
        override fun putLong(key: String?, value: Long) { (repository.keyStoredPreferenceMap[key] as? Repository.StoredPreference<Long>)?.value = value }
        override fun putString(key: String?, value: String?) { (repository.keyStoredPreferenceMap[key] as? Repository.StoredPreference<String>)?.value = value!! }
    }

    val defaultTaxPercent = repository.defaultTaxPercent.stateFlow.asLiveData()
    val defaultTipPercent = repository.defaultTipPercent.stateFlow.asLiveData()

    fun setDefaultTaxPercent(value: String) { repository.defaultTaxPercent.value = value }
    fun setDefaultTipPercent(value: String) { repository.defaultTipPercent.value = value }
}


class SettingsActivity: AppCompatActivity() {
    private lateinit var appViewModel: AppViewModel
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appViewModel = ViewModelProvider(this).get(AppViewModel::class.java)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /* Some fragments and transitions may draw content or animations where the status bar is
        located. Setting decorFitsSystemWindows to false allows the app to draw under the status
        bar. However, the margins for any non-status bar decorations (for example, the navigation
        bar at the bottom of the screen) are still required and have to be added via a window inset
        listener. */
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() and WindowInsetsCompat.Type.statusBars().inv())

            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                updateMargins(systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom)
            }

            insets
        }

        configureNavigation()

        // Hack: Setting the navigation bar color with xml style had issues during testing. Setting
        // the color programmatically works.
        appViewModel.darkThemeActive.observe(this) {
            val typedValue = TypedValue()
            this.theme.resolveAttribute(R.attr.colorBackground, typedValue, true)
            window.navigationBarColor = typedValue.data
        }
    }

    private fun configureNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container_view) as NavHostFragment

        val navController = navHostFragment.navController
        navController.navigatorProvider.addNavigator(CustomNavigator(this, navHostFragment.childFragmentManager, R.id.fragment_container_view))
        navController.setGraph(R.navigation.nav_graph_settings)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when(destination.id) {
                R.id.settingsHolderFragment -> {
                    // TODO
                    Log.e("now in ", "settingsHolderFragment")
                }
                R.id.calculator -> {
                    // TODO
                    Log.e("now in ", "calculator")
                }
                else -> {
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
            }
        }
    }
}

class SettingsHolderFragment: Fragment() {
    private var binding by setNullOnDestroy<FragSettingsHolderBinding>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragSettingsHolderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewModel = ViewModelProvider(requireActivity()).get(SettingsViewModel::class.java)

        binding.toolbar.apply {
            setNavigationIcon(R.drawable.ic_arrow_back_dark)
            setNavigationOnClickListener { requireActivity().onBackPressed() }

            navigationIcon?.setTint(TypedValue().also {
                requireContext().theme.resolveAttribute(R.attr.colorOnSurface, it, true)
            }.data)
        }

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Pair<CalculationType, Double>>(CALCULATOR_RETURN_KEY)?.observe(viewLifecycleOwner) { pair ->
            when (pair.first) {
                CalculationType.TAX_PERCENT -> { viewModel.setDefaultTaxPercent(pair.second.toString()) }
                CalculationType.TIP_PERCENT -> { viewModel.setDefaultTipPercent(pair.second.toString()) }
                else -> { /* Other CalculationTypes should not be received */ }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WindowInsetsControllerCompat(requireActivity().window, requireView()).isAppearanceLightStatusBars = true
    }

    override fun onPause() {
        super.onPause()
        WindowInsetsControllerCompat(requireActivity().window, requireView()).isAppearanceLightStatusBars = false
    }
}

class SettingsFragment: PreferenceFragmentCompat() {
    lateinit var viewModel: SettingsViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        viewModel = ViewModelProvider(requireActivity()).get(SettingsViewModel::class.java)
        preferenceManager.preferenceDataStore = viewModel.preferenceDataStore
        setPreferencesFromResource(R.xml.preferences, rootKey)

        (findPreference<Preference>(requireContext().getString(R.string.preference_key_user_email)) as EditTextPreference)?.apply {
            setOnBindEditTextListener {
                it.hint = requireContext().getString(R.string.settings_account_email_hint)
            }
        }

        findPreference<Preference>(requireContext().getString(R.string.preference_key_default_tax_percent))?.apply {
            this.setOnPreferenceClickListener {
                // Exit transition is needed to prevent next fragment from appearing immediately
                exitTransition = Hold().apply {
                    duration = 0L
                    addTarget(requireView())
                }

                findNavController().navigate(
                    SettingsHolderFragmentDirections.editNumericalPreference(
                        title = resources.getString(R.string.settings_bill_tax_percent),
                        previousValue = viewModel.defaultTaxPercent.value.toString(),
                        calculationType = CalculationType.TAX_PERCENT)
                )
                true
            }
        }

        findPreference<Preference>(requireContext().getString(R.string.preference_key_default_tip_percent))?.apply {
            this.setOnPreferenceClickListener {
                // Exit transition is needed to prevent next fragment from appearing immediately
                exitTransition = Hold().apply {
                    duration = 0L
                    addTarget(requireView())
                }

                findNavController().navigate(
                    SettingsHolderFragmentDirections.editNumericalPreference(
                        title = resources.getString(R.string.settings_bill_tip_percent),
                        previousValue = viewModel.defaultTipPercent.value.toString(),
                        calculationType = CalculationType.TIP_PERCENT)
                )
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.defaultTaxPercent.observe(viewLifecycleOwner) {
            findPreference<Preference>(requireContext().getString(R.string.preference_key_default_tax_percent))?.apply {
                this.summary = NumberFormat.getPercentInstance().format(it.toDouble() * 0.01)
            }
        }

        viewModel.defaultTipPercent.observe(viewLifecycleOwner) {
            findPreference<Preference>(requireContext().getString(R.string.preference_key_default_tip_percent))?.apply {
                this.summary = NumberFormat.getPercentInstance().format(it.toDouble() * 0.01)
            }
        }
    }
}