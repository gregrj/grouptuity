package com.grouptuity.grouptuity.ui.custom

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.FragmentNavigator

@Navigator.Name("nav_fragment")
class CustomNavigator(private val context: Context, private val manager: FragmentManager, private val containerId: Int): FragmentNavigator(context, manager, containerId) {
    private val mBackStack = ArrayDeque<Int>()
    var previousDestination: Int = -1
        private set

    override fun popBackStack(): Boolean {
        if (mBackStack.isEmpty() || manager.isStateSaved) {
            return false
        }

        previousDestination = mBackStack.last()

        manager.popBackStack(generateBackStackName(mBackStack.size, mBackStack.last()), FragmentManager.POP_BACK_STACK_INCLUSIVE)
        mBackStack.removeLast()
        return true
    }


    override fun navigate(destination: Destination, args: Bundle?, navOptions: NavOptions?, navigatorExtras: Navigator.Extras?): NavDestination? {
        if (manager.isStateSaved) {
            return null
        }

        previousDestination = mBackStack.lastOrNull() ?: -1

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

        if(frag is Revealable) {
            (manager.primaryNavigationFragment)?.apply {
                view?.apply {
                    copyViewToBitmap(requireActivity(), this) {
                        (frag as Revealable).coveredFragmentBitmap = it
                    }
                }
            }
        }

        ft.replace(containerId, frag)

        ft.setPrimaryNavigationFragment(frag)

        @IdRes val destId = destination.id
        val initialNavigation = mBackStack.isEmpty()

        val isSingleTopReplacement = (navOptions != null && !initialNavigation && navOptions.shouldLaunchSingleTop() && mBackStack.last() == destId)
        val isAdded: Boolean = if (initialNavigation) { true } else if (isSingleTopReplacement) {
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
        } else {
            ft.addToBackStack(generateBackStackName(mBackStack.size + 1, destId))
            true
        }

        if (navigatorExtras is Extras) {
            for ((key, value) in navigatorExtras.sharedElements) {
                ft.addSharedElement(key!!, value!!)
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

    override fun onRestoreState(savedState: Bundle?) {
        if (savedState != null) {
            val backStack = savedState.getIntArray(KEY_BACK_STACK_IDS)
            if (backStack != null) {
                mBackStack.clear()
                for (destId in backStack) {
                    mBackStack.add(destId)
                }
            }
        }
    }

    private fun generateBackStackName(backStackIndex: Int, destId: Int) = "$backStackIndex-$destId"

    private fun copyViewToBitmap(activity: Activity, view: View, callback: (Bitmap) -> Unit) {
        activity.window?.let { window ->
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val locationOfViewInWindow = IntArray(2)
            view.getLocationInWindow(locationOfViewInWindow)
            try {
                PixelCopy.request(
                        window,
                        Rect(locationOfViewInWindow[0], locationOfViewInWindow[1], locationOfViewInWindow[0] + view.width, locationOfViewInWindow[1] + view.height),
                        bitmap,
                        { callback(bitmap) },
                        Handler(Looper.getMainLooper())
                )
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                callback(bitmap)
            }
        }
    }

    companion object {
        private const val KEY_BACK_STACK_IDS = "androidx-nav-fragment:navigator:backStackIds"
    }
}