package com.example.foz.ai

/**
 * Pure decision logic for the assistant model load crash-loop breaker.
 *
 * A load writes a start timestamp; success clears it. If a new process
 * starts and finds a leftover timestamp, the previous process died during
 * a load and the streak is bumped. While the streak is at or above
 * [AUTO_LOAD_BLOCK_THRESHOLD], automatic loads (bubble tap, panel open,
 * mic button, keep-loaded warm-up) are blocked with a user-visible notice;
 * only an explicit retry from settings is allowed, and it resets the
 * streak on success. This guarantees a bad model or native crash can
 * never turn the launcher into a load/crash loop.
 */
object LoadCrashPolicy {

    const val AUTO_LOAD_BLOCK_THRESHOLD = 1

    enum class LoadDecision { ALLOW, BLOCKED_BY_CRASH_STREAK }

    data class StartDecision(
        val newStreak: Int,
        val blockAutoLoad: Boolean
    )

    /**
     * Evaluated once at process start with the persisted values.
     * [pendingStartTimestampMs] is a leftover "load started" marker from a
     * previous process: its presence means that process died mid-load.
     */
    fun onProcessStart(
        pendingStartTimestampMs: Long,
        previousStreak: Int
    ): StartDecision {
        val diedDuringLoad = pendingStartTimestampMs > 0L
        val streak = if (diedDuringLoad) previousStreak + 1 else previousStreak
        return StartDecision(
            newStreak = streak,
            blockAutoLoad = streak >= AUTO_LOAD_BLOCK_THRESHOLD
        )
    }

    /** Whether a load request may proceed. Manual requests always pass. */
    fun decideLoad(streak: Int, auto: Boolean): LoadDecision =
        if (!auto || streak < AUTO_LOAD_BLOCK_THRESHOLD) {
            LoadDecision.ALLOW
        } else {
            LoadDecision.BLOCKED_BY_CRASH_STREAK
        }

    /** Streak after a successful manual load. */
    fun onSuccessManual(streak: Int): Int = 0
}
