package com.example.foz.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.foz.R

/**
 * Read-only accessibility service used for on-screen context: when the user
 * asks about what is on their screen, the active window's node tree is
 * compacted into a short text snapshot. Snapshots are transient — never
 * stored or logged — and the feature is opt-in via Assistant settings.
 */
class FozAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        showTransparencyNotice()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

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

        @Volatile
        private var pendingSnapshot: String? = null

        private fun captureFromWindows(): String? {
            val service = instance ?: return null
            val appName = try {
                val info = service.applicationInfo
                service.packageManager.getApplicationLabel(info)
            } catch (_: PackageManager.NameNotFoundException) {
                "app"
            }
            val active = service.rootInActiveWindow
            val root = if (active != null && active.packageName != service.packageName) {
                active
            } else {
                service.windows
                    .asSequence()
                    .mapNotNull { it.root }
                    .firstOrNull { it.packageName != service.packageName }
            } ?: return null
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
            return "Visible content of $appName (current screen: $source):\n$body"
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
