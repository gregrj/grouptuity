package com.grouptuity.grouptuity

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.switchmaterial.SwitchMaterial
import com.grouptuity.grouptuity.databinding.ActivityMainBinding
import com.grouptuity.grouptuity.ui.custom.CustomNavigator


class MainActivity: AppCompatActivity() {
    private lateinit var appViewModel: AppViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var navigator: CustomNavigator
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController


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

    override fun onSupportNavigateUp() = navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

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

    // TODO Remove this after retrofitting simple calc?
    fun attachToolbar(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        appBarConfiguration = AppBarConfiguration(setOf(R.id.billSplitFragment), binding.drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    private fun configureNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container_view) as NavHostFragment

        navigator = CustomNavigator(this, navHostFragment.childFragmentManager, R.id.fragment_container_view)

        val updateDrawerForDestination: (NavDestination) -> Unit = { destination ->
            when(destination.id) {
                R.id.billSplitFragment -> {
                    binding.drawerNavView.menu.findItem(R.id.nav_group_bill_splitter).isChecked = true
                    binding.drawerNavView.menu.findItem(R.id.nav_simple_calculator).isChecked = false
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
                R.id.simpleCalcFragment -> {
                    binding.drawerNavView.menu.findItem(R.id.nav_group_bill_splitter).isChecked = false
                    binding.drawerNavView.menu.findItem(R.id.nav_simple_calculator).isChecked = true
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
                else -> {
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
            }
        }

        navController = navHostFragment.navController
        navController.navigatorProvider.addNavigator(navigator)
        navController.setGraph(R.navigation.nav_graph)
        navController.addOnDestinationChangedListener { _, destination, _ -> updateDrawerForDestination(destination) }

        binding.drawerNavView.setupWithNavController(navController)
        binding.drawerNavView.setNavigationItemSelectedListener { menuItem ->
            when (navController.currentDestination?.id) {
                R.id.billSplitFragment -> {
                    when (menuItem.itemId) {
                        R.id.nav_group_bill_splitter -> { }
                        R.id.nav_simple_calculator -> { navController.navigate(R.id.action_billSplitFragment_to_simpleCalcFragment) }
                        R.id.nav_settings -> { navController.navigate(R.id.action_global_settingsFragment) }
                    }
                }
                R.id.simpleCalcFragment -> {
                    when (menuItem.itemId) {
                        R.id.nav_group_bill_splitter -> { navController.popBackStack() }
                        R.id.nav_simple_calculator -> { }
                        R.id.nav_settings -> { navController.navigate(R.id.action_global_settingsFragment) }
                    }
                }
            }
            binding.drawerLayout.closeDrawers()
            true
        }

        // HACK: Checkable menu items in navigation drawer reset state when navigating back. This
        // listener ensures the correct state is shown.
        binding.drawerNavView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            navController.currentDestination?.apply { updateDrawerForDestination(this) }
        }
    }

    fun openNavViewDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun getNavigator() = navigator
}