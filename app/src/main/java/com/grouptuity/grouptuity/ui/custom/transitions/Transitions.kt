package com.grouptuity.grouptuity.ui.custom.transitions

import android.util.Log
import android.view.View
import androidx.core.app.SharedElementCallback
import androidx.core.view.ViewCompat


class MutableSharedElementCallback: SharedElementCallback() {
    private val mSharedElementViews = mutableSetOf<View>()

    fun setSharedElementViews(vararg sharedElementViews: View) {
        mSharedElementViews.clear()
        mSharedElementViews.addAll(listOf(*sharedElementViews))
    }

    override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
        names.clear()
        sharedElements.clear()

        Log.e("d", "d")

        for (sharedElementView in mSharedElementViews) {
            val transitionName = ViewCompat.getTransitionName(sharedElementView)
            names.add(transitionName!!)
            sharedElements[transitionName] = sharedElementView

            Log.e("adding onMapSharedElements", transitionName + " " + sharedElementView)
        }
    }

//    override fun onSharedElementEnd(sharedElementNames: List<String>, sharedElements: List<View>, sharedElementSnapshots: List<View>) {
//        for (sharedElementView in mSharedElementViews) {
//            forceSharedElementLayout(sharedElementView)
//        }
//    }
//
//    private fun forceSharedElementLayout(view: View) {
//        val widthSpec = View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY)
//        val heightSpec = View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY)
//        view.measure(widthSpec, heightSpec)
//        view.layout(view.left, view.top, view.right, view.bottom)
//    }
}