package com.grouptuity.grouptuity

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.grouptuity.grouptuity.databinding.ActivityMainBinding
import com.grouptuity.grouptuity.ui.custom.CustomNavigator

class MainActivity: AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navigator: CustomNavigator
    private lateinit var appBarConfiguration: AppBarConfiguration

    private lateinit var navController: NavController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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