package com.joshua.autohotspot

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HotspotAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_TOGGLE_HOTSPOT = "com.joshua.autohotspot.TOGGLE_HOTSPOT"
    }

    private var armed = false

    private val armReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            armed = true
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(armReceiver, IntentFilter(ACTION_TOGGLE_HOTSPOT))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!armed) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val root = rootInActiveWindow ?: return
        val toggle = findHotspotToggle(root)
        if (toggle != null && !toggle.isChecked) {
            toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            armed = false
        }
    }

    private fun findHotspotToggle(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Look for a switch-like node whose text/description mentions hotspot or tethering
        if (node.className?.contains("Switch") == true ||
            node.className?.contains("ToggleButton") == true
        ) {
            val text = (node.text?.toString() ?: "") + (node.contentDescription?.toString() ?: "")
            if (node.isCheckable) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findHotspotToggle(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    override fun onInterrupt() { }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(armReceiver) } catch (_: Exception) { }
    }
}
