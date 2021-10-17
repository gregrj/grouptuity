package com.grouptuity.grouptuity

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.grouptuity.grouptuity.databinding.ActivityMainBinding
import com.grouptuity.grouptuity.ui.custom.CustomNavigator


class MainActivity: AppCompatActivity() {
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

        // TODO figure out what to do about menu item clickability
        // TODO Retain setting and allow reversion to system default
        val switchWidget = binding.drawerNavView.menu.findItem(R.id.dark_mode_switch).actionView.findViewById<SwitchMaterial>(R.id.switch_widget)
        switchWidget.setOnClickListener {
            if(switchWidget.isChecked) {
                appViewModel.switchToDarkTheme()
            } else {
                appViewModel.switchToLightTheme()
            }
        }

        // Hack: Setting the navigation bar color with xml style had issues during testing. Setting
        // the color programmatically works.
        appViewModel.darkThemeActive.observe(this) {
            switchWidget.isChecked = it
            val typedValue = TypedValue()
            this.theme.resolveAttribute(R.attr.colorBackground, typedValue, true)
            window.navigationBarColor = typedValue.data
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if(Intent.ACTION_SEARCH == intent.action) {
            intent.getStringExtra(SearchManager.QUERY)?.also {
                appViewModel.receiveVoiceInput(it)
            }
        }
    }

    private fun configureNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container_view) as NavHostFragment

        val navController = navHostFragment.navController
        navController.navigatorProvider.addNavigator(CustomNavigator(this, navHostFragment.childFragmentManager, R.id.fragment_container_view))
        navController.setGraph(R.navigation.nav_graph_main)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when(destination.id) {
                R.id.billSplitFragment -> {
                    binding.drawerNavView.menu.findItem(R.id.nav_group_bill_splitter).isChecked = true
                    binding.drawerLayout.closeDrawers()
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
                R.id.basicTipCalcFragment -> {
                    binding.drawerNavView.menu.findItem(R.id.nav_simple_calculator).isChecked = true
                    binding.drawerLayout.closeDrawers()
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
                else -> {
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
            }
        }

        binding.drawerNavView.setNavigationItemSelectedListener { menuItem ->
            when (navController.currentDestination?.id) {
                R.id.billSplitFragment -> {
                    when (menuItem.itemId) {
                        R.id.nav_group_bill_splitter -> { true }
                        R.id.nav_simple_calculator -> {
                            navController.navigate(R.id.switch_to_basic_tip_calc)
//                            binding.drawerLayout.closeDrawers()
                            true
                        }
                        R.id.nav_settings -> {
                            startActivity(Intent(this, SettingsActivity::class.java))
                            binding.drawerLayout.closeDrawers()
                            false
                        }
                        else -> {
                            false
                        }
                    }
                }
                R.id.basicTipCalcFragment -> {
                    when (menuItem.itemId) {
                        R.id.nav_group_bill_splitter -> {
                            navController.popBackStack()
                            true
                        }
                        R.id.nav_simple_calculator -> {
                            false
                        }
                        R.id.nav_settings -> {
                            startActivity(Intent(this, SettingsActivity::class.java))
                            binding.drawerLayout.closeDrawers()
                            false
                        }
                        else -> {
                            false
                        }
                    }
                }
                else -> {
                    false
                }
            }
        }
    }

    fun openNavViewDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }
}