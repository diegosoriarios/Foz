package com.example.foz.manager

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import org.json.JSONObject

/**
 * Process-level crash safety net for the launcher.
 *
 * [install] hooks the default uncaught exception handler and records the
 * crash to `filesDir/crash_guard.json` before delegating to the system
 * handler. Only Java/Kotlin crashes land here; a native SIGSEGV kills the
 * process uncatchably and is instead caught indirectly by
 * [LoadCrashPolicy]'s load-death streak.
 *
 * On the next start [consumeStartupState] classifies the previous crash:
 *  - [Recovery.SAFE_MODE]: crash less than [SAFE_MODE_WINDOW_MS] ago — the
 *    session refuses automatic model loads (and bubble startup) so a
 *    crashing build can never boot-loop the launcher into Android's
 *    home-picker fallback.
 *  - [Recovery.WARNING]: crash within [WARNING_WINDOW_MS] — surfaces a
 *    one-time recovery notice.
 *
 * All file access is defensive: the handler itself must never throw.
 */
object CrashGuard {

    enum class Recovery { NONE, WARNING, SAFE_MODE }

    const val SAFE_MODE_WINDOW_MS = 60_000L
    const val WARNING_WINDOW_MS = 10 * 60_000L

    private const val FILE_NAME = "crash_guard.json"
    private const val FILE_SYSTEM_RESTART = "system_restart"
    private const val MAX_STACK_CHARS = 500
    /** How deep the cause chain is walked when detecting system restarts. */
    private const val MAX_CAUSE_DEPTH = 8

    @Volatile
    private var installed = false

    @Volatile
    private var consumed: Recovery? = null

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isSystemRestartCause(throwable)) {
                // Android itself is restarting (system_server died): Foz is
                // collateral damage, not the culprit. Keep diagnostics
                // honest — no crash record, and the breaker must not count
                // this as a Foz death on the next start.
                try {
                    com.example.foz.ui.common.ErrorHub.record(
                        "Android restarted; Foz was killed with the system (not a Foz crash)"
                    )
                } catch (_: Throwable) {
                }
                try {
                    systemRestartFile(appContext)
                        .writeText(System.currentTimeMillis().toString())
                } catch (_: Throwable) {
                }
            } else {
                try {
                    record(appContext, thread, throwable)
                } catch (_: Throwable) {
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * True when the throwable chain points at Android's own death rather
     * than a Foz bug (system_server restart). Matched by class name so no
     * API-level class loading can fail on older devices.
     */
    fun isSystemRestartCause(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        repeat(MAX_CAUSE_DEPTH) {
            val current = cause ?: return false
            when (current.javaClass.name) {
                "android.os.DeadSystemException",
                "android.os.DeadSystemRuntimeException",
                "android.os.DeadObjectException" -> return true
            }
            cause = current.cause
        }
        return false
    }

    /** Marker file written when Android itself restarts. */
    private fun systemRestartFile(context: Context): File =
        File(context.applicationContext.filesDir, FILE_SYSTEM_RESTART)

    /**
     * True when the previous process died because Android restarted
     * (not a Foz crash); consumes the marker.
     */
    fun consumeSystemRestart(context: Context): Boolean = try {
        val file = systemRestartFile(context)
        val wasRestart = file.exists()
        if (wasRestart) file.delete()
        wasRestart
    } catch (_: Throwable) {
        false
    }

    /** Evaluated once per process; later calls return the same value. */
    fun consumeStartupState(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Recovery {
        consumed?.let { return it }
        var crashTimestampMs = 0L
        try {
            crashFile(context).let { file ->
                if (file.exists()) {
                    crashTimestampMs = JSONObject(file.readText()).optLong("timestamp", 0L)
                }
            }
        } catch (_: Throwable) {
        }
        // The file is intentionally kept (overwritten by the next crash) so
        // the Diagnostics screen can always show the most recent crash.
        val recovery = classify(nowMs, crashTimestampMs)
        consumed = recovery
        return recovery
    }

    data class LastCrash(
        val timestampMs: Long,
        val thread: String,
        val exception: String,
        val stack: String,
        val versionCode: Long
    )

    /** Why the previous process exited, from ApplicationExitInfo (API 30+). */
    enum class ExitKind { CRASHED, LOW_MEMORY, ANR, SIGNALED, USER, OTHER }

    data class ExitRecord(
        val timestampMs: Long,
        val kind: ExitKind,
        val reasonCode: Int,
        val description: String
    )

    fun exitReasonKind(reasonCode: Int): ExitKind = when (reasonCode) {
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE -> ExitKind.CRASHED
        ApplicationExitInfo.REASON_LOW_MEMORY -> ExitKind.LOW_MEMORY
        ApplicationExitInfo.REASON_ANR -> ExitKind.ANR
        ApplicationExitInfo.REASON_SIGNALED -> ExitKind.SIGNALED
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED -> ExitKind.USER
        else -> ExitKind.OTHER
    }

    /**
     * Most recent exit of this process (API 30+; null below that or when no
     * record exists). This is how the Diagnostics screen distinguishes a
     * native crash from an lmkd kill without adb.
     */
    fun readLastExitReason(context: Context): ExitRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val am = context.applicationContext
                .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return null
            am.getHistoricalProcessExitReasons(context.packageName, 0, 1)
                .firstOrNull()
                ?.let { info ->
                    ExitRecord(
                        timestampMs = info.timestamp,
                        kind = exitReasonKind(info.reason),
                        reasonCode = info.reason,
                        description = info.description?.toString().orEmpty()
                    )
                }
        } catch (_: Throwable) {
            null
        }
    }

    /** Parsed last-crash record for the Diagnostics screen, if any. */
    fun readLastCrash(context: Context): LastCrash? = try {
        val file = crashFile(context)
        if (!file.exists()) {
            null
        } else {
            val json = JSONObject(file.readText())
            LastCrash(
                timestampMs = json.optLong("timestamp", 0L),
                thread = json.optString("thread"),
                exception = json.optString("exception"),
                stack = json.optString("stack"),
                versionCode = json.optLong("versionCode", 0L)
            )
        }
    } catch (_: Throwable) {
        null
    }

    /** True when this session started in safe mode (consume already ran). */
    val safeModeActive: Boolean
        get() = consumed == Recovery.SAFE_MODE

    fun classify(nowMs: Long, crashTimestampMs: Long): Recovery = when {
        crashTimestampMs <= 0L -> Recovery.NONE
        nowMs - crashTimestampMs <= SAFE_MODE_WINDOW_MS -> Recovery.SAFE_MODE
        nowMs - crashTimestampMs <= WARNING_WINDOW_MS -> Recovery.WARNING
        else -> Recovery.NONE
    }

    private fun crashFile(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NAME)

    private fun record(appContext: Context, thread: Thread, throwable: Throwable) {
        val json = JSONObject()
        json.put("timestamp", System.currentTimeMillis())
        json.put("thread", thread.name)
        json.put("versionCode", versionCode(appContext))
        json.put("exception", throwable.javaClass.name)
        json.put("stack", throwable.stackTraceToString().take(MAX_STACK_CHARS))
        crashFile(appContext).writeText(json.toString())
    }

    private fun versionCode(appContext: Context): Long = try {
        appContext.packageManager
            .getPackageInfo(appContext.packageName, 0)
            .longVersionCode
    } catch (_: Throwable) {
        0L
    }
}
