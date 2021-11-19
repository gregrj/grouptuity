package com.grouptuity.grouptuity.ui.custom

import android.content.Context
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.FragmentNavigator


@Navigator.Name("nav_fragment")
class CustomNavigator(private val context: Context, private val manager: FragmentManager, private val containerId: Int): FragmentNavigator(context, manager, containerId) {
    private val mBackStack = ArrayDeque<Int>()
    var lastNavigationWasBackward = false
        private set

    override fun popBackStack(): Boolean {
        if (mBackStack.isEmpty() || manager.isStateSaved) {
            return false
        }
        lastNavigationWasBackward = true

        manager.popBackStack(generateBackStackName(mBackStack.size, mBackStack.last()), FragmentManager.POP_BACK_STACK_INCLUSIVE)
        mBackStack.removeLast()
        return true
    }

    override fun navigate(destination: Destination, args: Bundle?, navOptions: NavOptions?, navigatorExtras: Navigator.Extras?): NavDestination? {
        if (manager.isStateSaved) {
            return null
        }
        lastNavigationWasBackward = false

        var className = destination.className
        if (className[0] == '.') {
            className = context.packageName + className
        }

        val frag = manager.fragmentFactory.instantiate(context.classLoader, className)
        frag.arguments = args

        val ft = manager.beginTransaction()

        var enterAnim = navOptions?.enterAnim ?: -1
        var exitAnim = navOptions?.exitAnim ?: -1
        var popEnterAnim = navOptions?.popEnterAnim ?: -1
        var popExitAnim = navOptions?.popExitAnim ?: -1
        if (enterAnim != -1 || exitAnim != -1 || popEnterAnim != -1 || popExitAnim != -1) {
            enterAnim = if (enterAnim != -1) enterAnim else 0
            exitAnim = if (exitAnim != -1) exitAnim else 0
            popEnterAnim = if (popEnterAnim != -1) popEnterAnim else 0
            popExitAnim = if (popExitAnim != -1) popExitAnim else 0
            ft.setCustomAnimations(enterAnim, exitAnim, popEnterAnim, popExitAnim)
        }

        ft.replace(containerId, frag)
        ft.setPrimaryNavigationFragment(frag)

        @IdRes val destId = destination.id
        val initialNavigation = mBackStack.isEmpty()
        val isSingleTopReplacement = (navOptions != null && !initialNavigation && navOptions.shouldLaunchSingleTop() && mBackStack.last() == destId)

        val isAdded: Boolean = when {
            initialNavigation -> true
            isSingleTopReplacement -> {
                // Single Top means we only want one instance on the back stack
                if (mBackStack.size > 1) {
                    // If the Fragment to be replaced is on the FragmentManager's
                    // back stack, a simple replace() isn't enough so we
                    // remove it from the back stack and put our replacement
                    // on the back stack in its place
                    manager.popBackStack(
                        generateBackStackName(mBackStack.size, mBackStack.last()),
                        FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                    ft.addToBackStack(generateBackStackName(mBackStack.size, destId))
                }
                false
            }
            else -> {
                ft.addToBackStack(generateBackStackName(mBackStack.size + 1, destId))
                true
            }
        }

        if (navigatorExtras is Extras) {
            for ((key, value) in navigatorExtras.sharedElements) {
                ft.addSharedElement(key, value)
            }
        }
        ft.setReorderingAllowed(true)
        ft.commit()

        return if (isAdded) {
            mBackStack.add(destId)
            destination
        } else {
            null
        }
    }

    override fun onSaveState(): Bundle {
        val b = Bundle()
        val backStack = IntArray(mBackStack.size)
        var index = 0
        for (id in mBackStack) {
            backStack[index++] = id
        }
        b.putIntArray(KEY_BACK_STACK_IDS, backStack)
        return b
    }

    override fun onRestoreState(savedState: Bundle) {
        val backStack = savedState.getIntArray(KEY_BACK_STACK_IDS)
        if (backStack != null) {
            mBackStack.clear()
            for (destId in backStack) {
                mBackStack.add(destId)
            }
        }
    }

    private fun generateBackStackName(backStackIndex: Int, destId: Int) = "$backStackIndex-$destId"

    companion object {
        private const val KEY_BACK_STACK_IDS = "androidx-nav-fragment:navigator:backStackIds"
    }
}