package com.grouptuity.grouptuity.ui.custom.transitions

import android.graphics.Bitmap

interface Revealable {
    var coveredFragmentBitmap: Bitmap?
}

class RevealableImpl: Revealable {
    override var coveredFragmentBitmap: Bitmap? = null
}