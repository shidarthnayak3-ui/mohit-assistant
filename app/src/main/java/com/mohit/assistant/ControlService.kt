package com.mohit.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class ControlService : AccessibilityService() {
    companion object { var inst: ControlService? = null }
    override fun onServiceConnected() { inst = this }
    override fun onUnbind(i: android.content.Intent?): Boolean { inst = null; return super.onUnbind(i) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 300)).build(), null, null)
    }
    fun scroll(down: Boolean) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        if (down) swipe(w / 2, h * 0.7f, w / 2, h * 0.3f) else swipe(w / 2, h * 0.3f, w / 2, h * 0.7f)
    }
    fun tapCenter() {
        val w = resources.displayMetrics.widthPixels / 2f
        val h = resources.displayMetrics.heightPixels / 2f
        swipe(w, h, w, h)
    }
    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
}
