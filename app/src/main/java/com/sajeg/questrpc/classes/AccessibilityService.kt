package com.sajeg.questrpc.classes

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class AccessibilityService : AccessibilityService() {

    private val screenStateReceiver = ScreenStateReceiver()

    override fun onServiceConnected() {
        super.onServiceConnected()
        ActivityManager.start(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // Android 13+ (API 33) requires RECEIVER_EXPORTED/RECEIVER_NOT_EXPORTED to be
        // specified, otherwise registerReceiver throws a SecurityException at runtime.
        // SCREEN_ON/OFF are system-protected broadcasts, so NOT_EXPORTED is safe here.
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenStateReceiver)
        ActivityManager.stop(this)
    }

    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
        if (p0 == null) {
            return
        }
        if (p0.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = p0.packageName?.toString()
            ActivityManager.appChanged(packageName.toString(), this)
        }
    }

    override fun onInterrupt() {
    }
}