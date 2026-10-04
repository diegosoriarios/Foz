package com.example.foz.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.foz.R

/**
 * Read-only accessibility service used for on-screen context: when the user
 * asks about what is on their screen, the active window's node tree is
 * compacted into a short text snapshot. Snapshots are transient — never
 * stored or logged — and the feature is opt-in via Assistant settings.
 *
 * When screen control is also enabled in settings, the assistant may
 * additionally tap/scroll/navigate (Phase C tools) through [performAction].
 */
class FozAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        showTransparencyNotice()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Root of the most relevant non-Foz window, or null. */
    private fun nonFozRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && active.packageName != packageName) return active
        return windows.asSequence()
            .mapNotNull { it.root }
            .firstOrNull { it.packageName != packageName }
    }

    private fun tap(target: String): String {
        val query = target.trim()
        if (query.isEmpty()) return "No target text given for tap."
        val root = nonFozRoot() ?: return "No screen available to interact with."
        val match = findNode(root, query)
            ?: return "Element \"$query\" not found on screen."
        if (match.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "Tapped \"$query\"."
        }
        val bounds = Rect().also { match.getBoundsInScreen(it) }
        if (bounds.isEmpty) return "Found \"$query\" but it cannot be tapped."
        return if (dispatchTap(bounds.exactCenterX(), bounds.exactCenterY())) {
            "Tapped \"$query\"."
        } else {
            "Could not tap \"$query\"."
        }
    }

    private fun scroll(direction: String): String {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f
        val distY = dm.heightPixels * 0.35f
        val distX = dm.widthPixels * 0.35f
        val (startX, startY, endX, endY) = when (direction.trim().lowercase()) {
            "up" -> listOf(cx, cy - distY, cx, cy + distY)
            "down" -> listOf(cx, cy + distY, cx, cy - distY)
            "left" -> listOf(cx + distX, cy, cx - distX, cy)
            "right" -> listOf(cx - distX, cy, cx + distX, cy)
            else -> return "Unknown scroll direction \"$direction\". Use up, down, left or right."
        }
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return if (dispatchGesture(gesture, null, null)) {
            "Scrolled $direction."
        } else {
            "Could not scroll."
        }
    }

    private fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /** Depth-first search for a node whose text/contentDescription contains [query]. */
    private fun findNode(
        root: AccessibilityNodeInfo,
        query: String
    ): AccessibilityNodeInfo? {
        val needle = query.lowercase()
        var fallback: AccessibilityNodeInfo? = null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            val label = node.text?.toString()
                ?: node.contentDescription?.toString()
            if (label?.contains(needle, ignoreCase = true) == true) {
                if (node.isClickable || node.isEditable) return node
                if (fallback == null) fallback = node
            }
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { stack.add(it) }
                } catch (_: Throwable) {
                }
            }
        }
        return fallback
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        hideTransparencyNotice()
        super.onDestroy()
    }

    private fun showTransparencyNotice() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    SCREEN_CHANNEL_ID,
                    getString(R.string.accessibility_service_description),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        val notification = NotificationCompat.Builder(this, SCREEN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_assistant_bubble)
            .setContentTitle(getString(R.string.screen_notice_title))
            .setContentText(getString(R.string.screen_notice_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        try {
            manager.notify(SCREEN_NOTICE_ID, notification)
        } catch (_: Throwable) {
        }
    }

    private fun hideTransparencyNotice() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(SCREEN_NOTICE_ID)
    }

    companion object {
        private const val SCREEN_CHANNEL_ID = "assistant_screen"
        private const val SCREEN_NOTICE_ID = 4001
        private const val MAX_CHARS = 2500
        private const val MAX_NODES = 250
        private const val MAX_LABEL_CHARS = 120

        @Volatile
        private var instance: FozAccessibilityService? = null

        /** True only when the user enabled Foz in system Accessibility settings. */
        fun isEnabled(): Boolean = instance != null

        /**
         * Stashes a snapshot of the current screen BEFORE Foz's own overlay
         * covers it (bubble tap). Consumed by the next [snapshot] call.
         */
        fun stashScreen() {
            pendingSnapshot = captureFromWindows()
        }

        /**
         * Current screen as compact text. Returns the pre-overlay stash from
         * a bubble tap if present, otherwise walks visible windows (skipping
         * Foz's own windows so the overlay never describes itself). Null when
         * the accessibility service is disabled or nothing readable is found.
         */
        fun snapshot(): String? {
            pendingSnapshot?.let {
                pendingSnapshot = null
                return it
            }
            return captureFromWindows()
        }

        /**
         * Fresh (never stashed) capture — used as feedback after screen
         * actions, where stale pre-overlay content would mislead the model.
         */
        fun freshSnapshot(): String? = captureFromWindows()

        /**
         * Executes one screen-control action (Phase C). Returns a short
         * human/model-readable result, or null when the service is disabled.
         */
        fun performAction(action: String, arg: String): String? {
            val service = instance ?: return null
            return when (action) {
                "tap" -> service.tap(arg)
                "scroll" -> service.scroll(arg)
                "back" ->
                    if (service.performGlobalAction(GLOBAL_ACTION_BACK)) "Pressed back."
                    else "Could not press back."
                "home" ->
                    if (service.performGlobalAction(GLOBAL_ACTION_HOME)) "Pressed home."
                    else "Could not press home."
                else -> "Unknown screen action."
            }
        }

        @Volatile
        private var pendingSnapshot: String? = null

        private fun captureFromWindows(): String? {
            val service = instance ?: return null
            val root = service.nonFozRoot() ?: return null
            val source = try {
                service.packageManager
                    .getApplicationLabel(
                        service.packageManager.getApplicationInfo(
                            root.packageName.toString(), 0
                        )
                    )
                    .toString()
            } catch (_: Throwable) {
                root.packageName.toString()
            }
            val body = walk(root)
            if (body.isNullOrEmpty()) return null
            return "Visible content of $source:\n$body"
        }

        private fun walk(root: AccessibilityNodeInfo): String? {
            val sb = StringBuilder()
            val stack = ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)
            var visited = 0
            while (stack.isNotEmpty() && sb.length < MAX_CHARS && visited < MAX_NODES) {
                val node = stack.removeLast()
                visited++
                val label = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                if (label != null) {
                    val prefix = when {
                        node.isEditable -> "[input] "
                        node.isClickable -> "[button] "
                        else -> ""
                    }
                    sb.append(prefix)
                        .append(label.take(MAX_LABEL_CHARS))
                        .append('\n')
                }
                for (i in 0 until node.childCount) {
                    try {
                        node.getChild(i)?.let { stack.add(it) }
                    } catch (_: Throwable) {
                    }
                }
                if (sb.length >= MAX_CHARS) break
            }
            val text = sb.toString().trim()
            return text.ifEmpty { null }
        }
    }
}
