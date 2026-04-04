
package com.example.moonlightwebcam

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MoonlightAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MoonlightAccessibility", "Service Connected")
        InputInjector.registerService(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to listen to events, we only inject them
    }

    override fun onInterrupt() {
        Log.d("MoonlightAccessibility", "Service Interrupted")
        InputInjector.unregisterService()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MoonlightAccessibility", "Service Destroyed")
        InputInjector.unregisterService()
    }
}
