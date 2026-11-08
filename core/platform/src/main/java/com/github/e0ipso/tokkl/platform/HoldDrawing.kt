package com.github.e0ipso.tokkl.platform

import android.view.View
import android.view.ViewTreeObserver

/**
 * Draws no frame of this view until [ready] says so, as the platform's splash screen guide keeps
 * the splash screen up with a pre-draw listener. Each skipped frame asks for another, so the view
 * draws on the first frame after [ready] turns true.
 */
fun View.holdDrawingUntil(ready: () -> Boolean) {
    viewTreeObserver.addOnPreDrawListener(
        object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!ready()) return false
                viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        },
    )
}
