package com.pranav.ai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PranavAccessibilityService : AccessibilityService() {
    companion object { var instance: PranavAccessibilityService? = null }
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun dumpScreenText(): String {
        val root = rootInActiveWindow ?: return "Screen empty"
        val sb = StringBuilder()
        traverse(root, sb)
        return sb.toString()
    }
    private fun traverse(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        val t = node.text?.toString() ?: node.contentDescription?.toString()
        if (!t.isNullOrBlank()) sb.append("[").append(t).append("] ")
        for (i in 0 until node.childCount) traverse(node.getChild(i), sb)
    }
    fun clickByText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (node in root.findAccessibilityNodeInfosByText(target)) {
            var c: AccessibilityNodeInfo? = node
            while (c != null) {
                if (c.isClickable) return c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                c = c.parent
            }
        }
        return false
    }
    fun typeText(text: String): Boolean {
        val focus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 280)).build(), null, null)
    }
    fun openAppByName(appName: String): Boolean {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString()
            if (label.contains(appName, ignoreCase = true)) {
                val launch = pm.getLaunchIntentForPackage(app.packageName) ?: continue
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
                return true
            }
        }
        return false
    }
    override fun onDestroy() { super.onDestroy(); instance = null }
}
