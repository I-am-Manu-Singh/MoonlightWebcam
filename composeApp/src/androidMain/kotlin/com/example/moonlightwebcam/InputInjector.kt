package com.example.moonlightwebcam

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

object InputInjector {
    private var service: AccessibilityService? = null

    fun registerService(s: AccessibilityService) {
        service = s
        Log.d("InputInjector", "Service Registered")
    }

    fun unregisterService() {
        service = null
        Log.d("InputInjector", "Service Unregistered")
    }

    fun perfomTap(x: Float, y: Float) {
        val s = service
        if (s == null) {
            Log.e("InputInjector", "Service is NULL. Is Accessibility Service enabled?")
            return
        }

        // Convert normalized (0..1) to screen coords
        val displayMetrics = s.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        val actualX = x * screenW
        val actualY = y * screenH

        Log.d("InputInjector", "Injecting Tap at $actualX, $actualY")

        val path = Path()
        path.moveTo(actualX, actualY)
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val builder = GestureDescription.Builder()
        builder.addStroke(stroke)
        s.dispatchGesture(builder.build(), null, null)
    }

    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300) {
        val s = service ?: return
        val displayMetrics = s.resources.displayMetrics
        val w = displayMetrics.widthPixels
        val h = displayMetrics.heightPixels

        val path = Path()
        path.moveTo(x1 * w, y1 * h)
        path.lineTo(x2 * w, y2 * h)

        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val builder = GestureDescription.Builder()
        builder.addStroke(stroke)
        s.dispatchGesture(builder.build(), null, null)
    }

    fun injectText(text: String) {
        val s = service
        if (s == null) {
            Log.e("InputInjector", "Service is NULL during injectText")
            return
        }
        val root = s.rootInActiveWindow ?: return
        val focus = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)

        if (focus != null && focus.isEditable) {
            val currentText = focus.text?.toString() ?: ""
            val newText =
                    if (text == "\b") {
                        if (currentText.isNotEmpty()) currentText.dropLast(1) else currentText
                    } else {
                        currentText + text
                    }

            val arguments = android.os.Bundle()
            arguments.putCharSequence(
                    android.view.accessibility.AccessibilityNodeInfo
                            .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    newText
            )
            focus.performAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                    arguments
            )
            Log.d("InputInjector", "Updated text from '$currentText' to '$newText'")
        } else {
            Log.w("InputInjector", "No editable focus found")
        }
    }
}
