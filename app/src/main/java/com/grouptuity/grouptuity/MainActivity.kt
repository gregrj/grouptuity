package com.grouptuity.grouptuity

import android.os.Bundle
import android.view.Menu
import android.view.ViewGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.*
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.grouptuity.grouptuity.databinding.ActivityMainBinding
import com.grouptuity.grouptuity.ui.custom.CustomNavigator

class MainActivity: AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private lateinit var navigator: CustomNavigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        appBarConfiguration = AppBarConfiguration(setOf(R.id.billSplitFragment), binding.drawerLayout)

        configureNavigation()
    }

    override fun onSupportNavigateUp() = navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

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
            else -> {
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
        }
    }

    fun getNavigator() = navigator
}