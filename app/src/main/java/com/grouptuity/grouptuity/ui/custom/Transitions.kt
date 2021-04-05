package com.grouptuity.grouptuity.ui.custom

import android.graphics.Bitmap

interface Revealable {
    var coveredFragmentBitmap: Bitmap?
}

class RevealableImpl: Revealable {
    override var coveredFragmentBitmap: Bitmap? = null
}