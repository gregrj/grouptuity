package com.grouptuity.grouptuity.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceFragmentCompat
import com.grouptuity.grouptuity.AppViewModel
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.databinding.FragSettingsHolderBinding
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy

class SettingsHolderFragment: Fragment() {
    private var binding by setNullOnDestroy<FragSettingsHolderBinding>()
    private lateinit var appViewModel: AppViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragSettingsHolderBinding.inflate(inflater, container, false)

        appViewModel = ViewModelProvider(this).get(AppViewModel::class.java)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.apply {
            setNavigationIcon(R.drawable.ic_arrow_back)

            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.colorOnSurface, typedValue, true)
            navigationIcon?.setTint(typedValue.data)

            setNavigationOnClickListener { requireActivity().onBackPressed() }
        }
    }
}

class SettingsFragment: PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}