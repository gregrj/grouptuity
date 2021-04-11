package com.grouptuity.grouptuity

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
        bar. However, the margins for any non-status bar decorations (i.e., the navigation bar at
        the bottom of the screen) are still required and have to be added via a window inset
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

        appViewModel.darkThemeActive.observe(this) {
            switchWidget.isChecked = it
            window.navigationBarColor = if(it) {
                resources.getColor(R.color.black)
            } else {
                resources.getColor(R.color.white)
            }

//            val a = TypedValue()
//            theme.resolveAttribute(android.R.attr.colorBackground, a, true)
//            if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
//                // windowBackground is a color
//                val color = a.data
//            } else {
//                // windowBackground is not a color, probably a drawable
//                val d: Drawable = activity.getResources().getDrawable(a.resourceId)
//            }
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

    fun attachToolbar(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        appBarConfiguration = AppBarConfiguration(setOf(R.id.billSplitFragment), binding.drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    private fun configureNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container_view) as NavHostFragment

        navigator = CustomNavigator(this, navHostFragment.childFragmentManager, R.id.fragment_container_view)

        navController = navHostFragment.navController
        navController.navigatorProvider.addNavigator(navigator)
        navController.setGraph(R.navigation.nav_graph)
        navController.addOnDestinationChangedListener { _, destination, _ -> updateForNavDestination(destination) }

        binding.drawerNavView.setupWithNavController(navController)
        binding.drawerNavView.setNavigationItemSelectedListener { menuItem ->
            when (navController.currentDestination?.id) {
                R.id.billSplitFragment -> {
                    when (menuItem.itemId) {
                        R.id.nav_group_bill_splitter -> { binding.drawerLayout.closeDrawers() }
                        R.id.nav_simple_calculator -> {
                            navController.navigate(R.id.action_billSplitFragment_to_simpleCalcFragment)
                            binding.drawerLayout.closeDrawers()
                        }
                    }
                }
                R.id.simpleCalcFragment -> {
                    when (menuItem.itemId) {
                        R.id.nav_group_bill_splitter -> {
                            navController.popBackStack()
                            binding.drawerLayout.closeDrawers()
                        }
                        R.id.nav_simple_calculator -> { binding.drawerLayout.closeDrawers() }
                    }
                }
            }
            true
        }

        // HACK: Checkable menu items in navigation drawer reset state when navigating back. This
        // listener ensures the correct state is shown.
        binding.drawerNavView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            navController.currentDestination?.apply { updateForNavDestination(this) }
        }
    }

    private fun updateForNavDestination(destination: NavDestination) {
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

    fun getNavigator() = navigator
}