package com.example.foz.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.example.foz.R
import com.example.foz.ui.assistant.AssistantOverlayActivity

/**
 * Floating assistant bubble drawn over other apps (SYSTEM_ALERT_WINDOW).
 * Tap opens [AssistantOverlayActivity]; drag repositions. Lifecycle is
 * driven by the assistant-bubble preference in LauncherViewModel.
 */
class FozOverlayService : Service() {

    private var bubbleView: View? = null
    private var windowManager: WindowManager? = null

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val bubble = ImageView(this).apply {
            setImageResource(R.drawable.ic_assistant_bubble)
            setBackgroundResource(R.drawable.bg_assistant_bubble)
            val pad = (14 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = getString(R.string.acc_assistant_bubble)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels -
                (64 * resources.displayMetrics.density).toInt()
            y = (resources.displayMetrics.heightPixels * 0.35f).toInt()
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop

        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (moved || dx * dx + dy * dy > touchSlop * touchSlop) {
                        moved = true
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        windowManager?.updateViewLayout(bubble, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) openAssistant()
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(bubble, params)
            bubbleView = bubble
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun openAssistant() {
        // Capture what's on screen before our own overlay covers it.
        FozAccessibilityService.stashScreen()
        val intent = Intent(this, AssistantOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        bubbleView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Throwable) {
            }
        }
        bubbleView = null
        super.onDestroy()
    }

    companion object {
        private var running = false

        fun start(context: android.content.Context) {
            if (running || !Settings.canDrawOverlays(context)) return
            running = true
            context.startService(Intent(context, FozOverlayService::class.java))
        }

        fun stop(context: android.content.Context) {
            running = false
            context.stopService(Intent(context, FozOverlayService::class.java))
        }
    }
}
