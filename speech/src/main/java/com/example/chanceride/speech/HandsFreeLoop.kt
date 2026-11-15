package com.example.chanceride.speech

/**
 * Half-duplex voice loop: finish reading, listen, process one final utterance,
 * then listen again. Epoch checks discard scheduled work after navigation/stop.
 * No TTS/STT overlap, automatic focus reacquisition after interruptions, or timers in background.
 */
class HandsFreeLoop(
    private val schedule: (Long, () -> Unit) -> Unit,
    private val stopSpeech: () -> Unit,
    private val speak: (String) -> Unit,
    private val listen: () -> Unit,
    private val onUtterance: (String) -> Unit,
    private val onState: (String) -> Unit
) {
    enum class Phase { OFF, WAITING, READING, LISTENING, BETWEEN }
    var phase = Phase.OFF
        private set
    val enabled: Boolean get() = phase != Phase.OFF
    private var epoch = 0
    private var question = ""
    private var failures = 0

    fun start(text: String, ready: Boolean) {
        stop()
        question = text
        failures = 0
        phase = Phase.WAITING
        if (ready) ready() else {
            onState("음성 서비스를 준비하고 있어요")
            later(12_000) {
                if (phase == Phase.WAITING) pause("음성 연결을 확인한 뒤 다시 시작해 주세요")
            }
        }
    }

    fun ready() {
        if (phase != Phase.WAITING) return
        phase = Phase.READING
        onState("질문을 읽고 있어요")
        speak(question)
    }

    fun questionChanged(text: String, ready: Boolean) {
        if (enabled) start(text, ready)
    }

    fun playbackFinished() {
        if (phase != Phase.READING) return
        phase = Phase.BETWEEN
        queueListening(450)
    }

    fun finalResult(text: String) {
        if (phase != Phase.LISTENING) return
        failures = 0
        phase = Phase.BETWEEN
        val previous = epoch
        onUtterance(text)
        // onUtterance may have navigated, stopped, or started a new question.
        if (previous == epoch && phase == Phase.BETWEEN) queueListening(650)
    }

    fun recognitionFailed(retryable: Boolean, silence: Boolean) {
        if (phase != Phase.LISTENING) return
        if (!retryable) {
            pause("음성 서비스를 확인한 뒤 다시 시작해 주세요")
            return
        }
        if (!silence && ++failures >= 5) {
            pause("마이크 연결을 확인한 뒤 다시 시작해 주세요")
            return
        }
        phase = Phase.BETWEEN
        queueListening(if (silence) 1200 else (800L * (1 shl failures.coerceAtMost(3))))
    }

    fun pause(message: String) {
        stop()
        onState(message)
    }

    fun stop() {
        epoch++
        phase = Phase.OFF
        stopSpeech()
    }

    private fun queueListening(delay: Long) {
        later(delay) {
            if (phase == Phase.BETWEEN) {
                phase = Phase.LISTENING
                onState("듣고 있어요 · 다음 / 이전 / 나가기")
                listen()
            }
        }
    }

    private fun later(delay: Long, action: () -> Unit) {
        val token = epoch
        schedule(delay) { if (epoch == token && enabled) action() }
    }
}
