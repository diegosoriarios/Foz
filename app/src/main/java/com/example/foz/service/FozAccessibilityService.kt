package com.example.foz.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.foz.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Read-only accessibility service used for on-screen context: when the user
 * asks about what is on their screen, the active window's node tree is
 * compacted into a numbered list of elements ("[3] button \"Enviar\" (990,2200)").
 * Snapshots are transient — never stored or logged — and the feature is
 * opt-in via Assistant settings.
 *
 * When screen control is also enabled in settings, the assistant may
 * additionally tap/scroll/navigate through [performAction]; tap_element aims
 * at a numbered element's exact bounds. When the node tree is too sparse
 * (canvas-rendered apps), an on-device ML Kit OCR pass over a screenshot
 * provides the same numbered format (Android 11+).
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

    private fun screenRect(): Rect {
        val dm = resources.displayMetrics
        return Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun tap(target: String): String {
        val query = target.trim()
        if (query.isEmpty()) return "No target text given for tap."
        val root = nonFozRoot() ?: return "No screen available to interact with."
        val match = findNode(root, query)
            ?: return "Element \"$query\" not found on screen."
        val bounds = Rect().also { match.getBoundsInScreen(it) }
        if (bounds.isEmpty || !Rect.intersects(bounds, screenRect())) {
            return "Found \"$query\" but it is not visible on screen; scroll and try again."
        }
        if (match.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "Tapped \"$query\"."
        }
        return if (dispatchTap(bounds.exactCenterX(), bounds.exactCenterY())) {
            "Tapped \"$query\"."
        } else {
            "Could not tap \"$query\"."
        }
    }

    private fun tapElement(raw: String): String {
        val index = raw.trim().toIntOrNull()
            ?: return "tap_element needs the numeric index of an element from the last screen read."
        if (index < 1) return "Element index starts at 1."
        val bounds = elementBounds[index]
            ?: return "Element $index is not in the last screen read; call read_screen again."
        if (bounds.isEmpty || !Rect.intersects(bounds, screenRect())) {
            return "Element $index is not visible on screen; scroll and read the screen again."
        }
        return if (dispatchTap(bounds.exactCenterX(), bounds.exactCenterY())) {
            "Tapped element $index."
        } else {
            "Could not tap element $index."
        }
    }

    private fun scroll(direction: String): String {
        val dir = direction.trim().lowercase()
        if (dir !in listOf("up", "down", "left", "right")) {
            return "Unknown scroll direction \"$direction\". Use up, down, left or right."
        }
        val root = nonFozRoot()
        if (root != null) {
            val scrollable = findScrollable(root)
            if (scrollable != null) {
                val action = when (dir) {
                    "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    "left" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
                    else -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
                }
                if (scrollable.performAction(action)) return "Scrolled $dir."
            }
        }
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f
        val distY = dm.heightPixels * 0.35f
        val distX = dm.widthPixels * 0.35f
        val (startX, startY, endX, endY) = when (dir) {
            "up" -> listOf(cx, cy - distY, cx, cy + distY)
            "down" -> listOf(cx, cy + distY, cx, cy - distY)
            "left" -> listOf(cx + distX, cy, cx - distX, cy)
            else -> listOf(cx - distX, cy, cx + distX, cy)
        }
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return if (dispatchGesture(gesture, null, null)) {
            "Scrolled $dir."
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

    /** First visible scrollable node, or null. */
    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screen = screenRect()
        val bounds = Rect()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            if (node.isScrollable) {
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty && Rect.intersects(bounds, screen)) return node
            }
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { stack.add(it) }
                } catch (_: Throwable) {
                }
            }
        }
        return null
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
        /** Tree captures shorter than this trigger the OCR fallback. */
        private const val SPARSE_BODY_CHARS = 40

        @Volatile
        private var instance: FozAccessibilityService? = null

        /** Bounds by element index for the most recent snapshot (tree or OCR). */
        private val elementBounds = ConcurrentHashMap<Int, Rect>()

        /** True only when the user enabled Foz in system Accessibility settings. */
        fun isEnabled(): Boolean = instance != null

        /**
         * Stashes a tree-only snapshot of the current screen BEFORE Foz's own
         * overlay covers it (bubble tap). Consumed by the next [snapshot] call.
         */
        fun stashScreen() {
            pendingSnapshot = captureTree()
        }

        /**
         * Current screen as numbered elements. Returns the pre-overlay stash
         * from a bubble tap if present, otherwise captures visible windows
         * (skipping Foz's own windows so the overlay never describes itself),
         * falling back to on-device OCR when the tree is too sparse.
         */
        suspend fun snapshot(): String? {
            pendingSnapshot?.let {
                pendingSnapshot = null
                return it
            }
            return captureWithOcrFallback()
        }

        /**
         * Fresh (never stashed) capture — used as feedback after screen
         * actions, where stale pre-overlay content would mislead the model.
         */
        suspend fun freshSnapshot(): String? = captureWithOcrFallback()

        /**
         * Executes one screen-control action. Returns a short
         * human/model-readable result, or null when the service is disabled.
         */
        fun performAction(action: String, arg: String): String? {
            val service = instance ?: return null
            return when (action) {
                "tap" -> service.tap(arg)
                "tap_element" -> service.tapElement(arg)
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

        private fun sourceName(root: AccessibilityNodeInfo, service: FozAccessibilityService): String {
            return try {
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
        }

        /** Tree-only capture, safe to call synchronously (bubble tap path). */
        private fun captureTree(): String? {
            val service = instance ?: return null
            val root = service.nonFozRoot() ?: return null
            val body = walk(root)
            if (body.isNullOrEmpty()) return null
            return header(sourceName(root, service)) + body
        }

        /** Tree capture with the OCR fallback when the tree body is too sparse. */
        private suspend fun captureWithOcrFallback(): String? {
            val service = instance ?: return null
            val root = service.nonFozRoot() ?: return null
            val name = sourceName(root, service)
            val body = walk(root)
            if (!body.isNullOrEmpty() && body.length >= SPARSE_BODY_CHARS) {
                return header(name) + body
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ocrBody(name)?.let { return it }
            }
            if (body.isNullOrEmpty()) return null
            return header(name) + body
        }

        private fun header(source: String): String =
            "Screen of $source. Elements are numbered; press one with tap_element:\n"

        /**
         * Depth-first walk that renders every labeled node as a numbered
         * element with its on-screen center, registering bounds so
         * tap_element can aim without re-matching text.
         */
        private fun walk(root: AccessibilityNodeInfo): String? {
            val service = instance ?: return null
            elementBounds.clear()
            val sb = StringBuilder()
            val bounds = Rect()
            val stack = ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)
            var visited = 0
            var index = 0
            while (stack.isNotEmpty() && sb.length < MAX_CHARS && visited < MAX_NODES) {
                val node = stack.removeLast()
                visited++
                val label = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                if (label != null) {
                    index++
                    node.getBoundsInScreen(bounds)
                    elementBounds[index] = Rect(bounds)
                    val kind = when {
                        node.isEditable -> "input"
                        node.isClickable -> "button"
                        else -> null
                    }
                    val cx = (bounds.left + bounds.right) / 2
                    val cy = (bounds.top + bounds.bottom) / 2
                    sb.append('[').append(index).append("] ")
                    if (kind != null) sb.append(kind).append(' ')
                    sb.append('"')
                        .append(label.take(MAX_LABEL_CHARS).replace("\"", "'"))
                        .append("\" (")
                        .append(cx).append(',').append(cy)
                        .append(")\n")
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

        /**
         * On-device OCR pass (Android 11+): screenshots the display through
         * the accessibility service, runs ML Kit text recognition locally and
         * renders recognized lines in the same numbered-element format.
         */
        private suspend fun ocrBody(source: String): String? {
            val service = instance ?: return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            val bitmap = takeScreenshotBitmap(service) ?: return null
            val text = try {
                recognizeText(bitmap)
            } catch (_: Throwable) {
                null
            }
            bitmap.recycle()
            text ?: return null
            elementBounds.clear()
            val sb = StringBuilder()
            var index = 0
            outer@ for (block in text.textBlocks) {
                for (line in block.lines) {
                    val value = line.text.trim()
                    if (value.isEmpty()) continue
                    index++
                    line.boundingBox?.let { elementBounds[index] = Rect(it) }
                    val cx = line.boundingBox?.let { (it.left + it.right) / 2 } ?: 0
                    val cy = line.boundingBox?.let { (it.top + it.bottom) / 2 } ?: 0
                    sb.append('[').append(index).append("] \"")
                        .append(value.take(MAX_LABEL_CHARS).replace("\"", "'"))
                        .append("\" (").append(cx).append(',').append(cy).append(")\n")
                    if (sb.length >= MAX_CHARS || index >= MAX_NODES) break@outer
                }
            }
            val body = sb.toString().trim()
            if (body.isEmpty()) return null
            return "Screen of $source (recognized with OCR). Elements are numbered; press one with tap_element:\n$body"
        }

        @Suppress("NewApi")
        private suspend fun takeScreenshotBitmap(
            service: FozAccessibilityService
        ): Bitmap? {
            return suspendCancellableCoroutine { cont ->
                val callback = object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val soft = try {
                            Bitmap.wrapHardwareBuffer(
                                result.hardwareBuffer,
                                result.colorSpace
                            )?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Throwable) {
                            null
                        } finally {
                            try {
                                result.hardwareBuffer.close()
                            } catch (_: Throwable) {
                            }
                        }
                        if (cont.isActive) cont.resume(soft)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                val executor = service.mainExecutor
                try {
                    // API 34+: takeScreenshot(int displayId, executor, callback)
                    AccessibilityService::class.java
                        .getMethod(
                            "takeScreenshot",
                            Int::class.javaPrimitiveType,
                            java.util.concurrent.Executor::class.java,
                            AccessibilityService.TakeScreenshotCallback::class.java
                        )
                        .invoke(service, 0, executor, callback)
                } catch (_: NoSuchMethodException) {
                    try {
                        // API 30-33: takeScreenshot(Display, executor, callback)
                        AccessibilityService::class.java
                            .getMethod(
                                "takeScreenshot",
                                android.view.Display::class.java,
                                java.util.concurrent.Executor::class.java,
                                AccessibilityService.TakeScreenshotCallback::class.java
                            )
                            .invoke(service, service.display, executor, callback)
                    } catch (_: Throwable) {
                        if (cont.isActive) cont.resume(null)
                    }
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

        private suspend fun recognizeText(bitmap: Bitmap): com.google.mlkit.vision.text.Text? {
            return suspendCancellableCoroutine { cont ->
                try {
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        .process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }
}
