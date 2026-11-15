package com.example.chanceride.speech

/**
 * Owns a single short utterance and its focus lease. Main-thread only.
 * Gain callbacks never restart an interrupted utterance.
 */
internal class TtsPlaybackSession(
    private val requestFocus: (onLoss: () -> Unit) -> Boolean,
    private val abandonFocus: () -> Unit,
    private val stopAudio: () -> Unit,
    private val onInterrupted: () -> Unit
) {
    private var foreground = false
    private var suspended = false
    private var generation = 0L
    private var active = false

    val canStart: Boolean get() = foreground && !suspended
    val isSuspended: Boolean get() = suspended

    fun begin(): Long? {
        stop()
        if (!canStart) return null
        val token = ++generation
        active = true
        val granted = requestFocus {
            if (isCurrent(token)) {
                stop()
                onInterrupted()
            }
        }
        if (!granted || !isCurrent(token)) {
            complete(token)
            return null
        }
        return token
    }

    fun isCurrent(token: Long): Boolean = active && generation == token

    fun complete(token: Long) {
        if (!isCurrent(token)) return
        active = false
        generation++
        abandonFocus()
    }

    fun stop() {
        val wasActive = active
        active = false
        generation++
        if (wasActive) {
            try {
                stopAudio()
            } finally {
                abandonFocus()
            }
        }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) stop()
    }

    fun enterSuspend() {
        suspended = true
        stop()
    }

    fun resumeFromSuspend() {
        suspended = false
        // Neither focus nor playback is restored; the next user action starts a new lease.
    }
}
