package com.example.chanceride.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitializeListener
import java.util.Locale

/**
 * Pleos Connect 2.1.0 / speech SDK 3.3.1, ON_DEVICE mode.
 * Public methods run on the main thread. Stop on backgrounding, close on destruction.
 */
class KoreanSpeechController(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val canShowVoiceTranscription: () -> Boolean = { true },
    private val onEvent: (SpeechEvent) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null
    private var ttsReady = false
    private var sttReady = false
    private var ttsState = "미연결"
    private var sttState = "미연결"
    private var ttsInitializing = false
    private var sttInitializing = false
    private var listening = false
    private var recognitionGeneration = 0
    private var playbackGeneration = 0
    private var resultListener: ResultListener? = null
    private var eventListener: EventListener? = null
    private val audioFocus = TtsAudioFocus(appContext, main)
    private val playbackSession = TtsPlaybackSession(
        requestFocus = audioFocus::request,
        abandonFocus = audioFocus::abandon,
        stopAudio = { if (ttsReady) sdkCall("TTS 중지") { tts?.stop() }; Unit },
        onInterrupted = {
            stopPlayback()
            onStatus("오디오 포커스 상실 · TTS를 중단했습니다. 다시 들으려면 재생 버튼을 눌러 주세요.")
            onEvent(SpeechEvent.PlaybackInterrupted)
        }
    )
    private val playbackTimeout = Runnable {
        stopPlayback()
        if (!closed) {
            onStatus("TTS 응답 시간 초과 · 재생을 중지하고 오디오 포커스를 반환했습니다.")
            onEvent(SpeechEvent.PlaybackFailed("TIMEOUT"))
        }
    }

    private val recognitionTimeout = Runnable {
        if (listening) {
            stopRecognition()
            onStatus("음성 인식 시간 초과 · 에뮬레이터 마이크 입력을 확인하고 다시 눌러 주세요.")
            onEvent(SpeechEvent.RecognitionFailed("TIMEOUT", retryable = true, silence = true))
        }
    }

    init { initialize() }

    /** Retry failed connections; already connected engines are reused. */
    fun initialize() {
        if (closed || playbackSession.isSuspended) return
        if (!ttsReady && !ttsInitializing) initializeTts()
        if (!sttReady && !sttInitializing) initializeStt()
        // Post so an Activity can finish assigning its controller before callbacks arrive.
        dispatch { showConnectionStatus() }
    }

    private fun initializeTts() {
        if (!hasPermission(TTS_PERMISSION)) {
            ttsState = "TTS_SERVICE 권한 없음 · Pleos 실행 환경을 확인하세요."
            return
        }
        ttsInitializing = true
        ttsState = "연결 중"
        if (!sdkCall("TTS 초기화") {
            tts?.release()
            val engine = TextToSpeech(appContext, TtsMode.ON_DEVICE)
            tts = engine
            engine.initialize(object : TtsInitializeListener {
                override fun onSuccess() = dispatch {
                    if (tts !== engine) return@dispatch
                    ttsInitializing = false
                    if (sdkCall("TTS 한국어/오디오 설정") {
                        engine.setLocale(Locale.KOREA)
                        engine.setAudioAttributes(audioFocus.attributes)
                    }) {
                        ttsReady = true
                        ttsState = "준비 완료"
                        showConnectionStatus()
                    }
                }
                override fun onFailure(errorType: TtsError) = dispatch {
                    if (tts !== engine) return@dispatch
                    ttsInitializing = false
                    ttsReady = false
                    ttsState = errorMessage(errorType.name)
                    showConnectionStatus()
                }
            })
        }) {
            ttsInitializing = false
            ttsState = "초기화 실패"
        }
    }

    private fun initializeStt() {
        if (!hasPermission(STT_PERMISSION)) {
            sttState = "STT_SERVICE 권한 없음 · Pleos 실행 환경을 확인하세요."
            return
        }
        sttInitializing = true
        sttState = "연결 중"
        if (!sdkCall("STT 초기화") {
            stt?.release()
            val engine = SpeechToText(appContext, SttMode.ON_DEVICE)
            stt = engine
            engine.initialize(object : SttInitializeListener {
                override fun onSuccess() = dispatch {
                    if (stt !== engine) return@dispatch
                    sttInitializing = false
                    if (sdkCall("STT 한국어 설정") { engine.setLocale(Locale.KOREA) }) {
                        sttReady = true
                        sttState = "준비 완료"
                        showConnectionStatus()
                    }
                }
                override fun onFailure(errorType: SttError) = dispatch {
                    if (stt !== engine) return@dispatch
                    sttInitializing = false
                    sttReady = false
                    sttState = errorMessage(errorType.name)
                    showConnectionStatus()
                }
            })
        }) {
            sttInitializing = false
            sttState = "초기화 실패"
        }
    }

    val isReady: Boolean get() = ttsReady && sttReady && !closed

    private fun showConnectionStatus() {
        onEvent(SpeechEvent.Availability(ttsReady, sttReady))
        onStatus("Pleos SDK 3.3.1 · ON_DEVICE · ko-KR\nTTS: $ttsState\nSTT: $sttState")
    }

    fun speak(text: String) {
        if (closed) return
        if (!playbackSession.canStart) {
            onStatus("백그라운드 또는 Suspend 상태에서는 TTS를 재생하지 않습니다.")
            return
        }
        stopRecognition()
        stopPlayback()
        val engine = tts
        if (!ttsReady || engine == null) {
            initialize()
            onStatus("Pleos TTS가 준비되지 않았습니다.\nTTS: $ttsState\n연결 완료 후 다시 눌러 주세요.")
            return
        }
        val token = playbackSession.begin()
        if (token == null) {
            onStatus("오디오 포커스 요청이 거부되어 TTS를 재생하지 않았습니다.")
            onEvent(SpeechEvent.PlaybackFailed("FOCUS_DENIED"))
            return
        }
        val generation = playbackGeneration
        var started = false
        val listener = object : EventListener {
            private fun current(action: () -> Unit) = dispatch {
                if (generation == playbackGeneration && playbackSession.isCurrent(token)) action()
            }
            override fun onReady() = current { onStatus("오디오 포커스 확보 · Pleos TTS 재생 준비 중") }
            override fun onStart() = current {
                started = true
                onStatus("오디오 포커스 확보 · Pleos TTS 한국어 안내 재생 중")
            }
            override fun onDone() = current {
                // Discard an old completion arriving before this utterance's start.
                if (!started) return@current
                playbackSession.complete(token)
                detachPlaybackListener()
                onStatus("Pleos TTS · 한국어 재생 완료 · 오디오 포커스 반환")
                onEvent(SpeechEvent.PlaybackFinished)
            }
            override fun onStop() = current {
                // A stop acknowledgement from a preceding request can precede this start.
                if (started) {
                    playbackSession.complete(token)
                    detachPlaybackListener()
                    onStatus("Pleos TTS 중지 · 오디오 포커스 반환")
                    onEvent(SpeechEvent.PlaybackInterrupted)
                }
            }
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onError(errorType: TtsError) = current {
                stopPlayback()
                onStatus("Pleos TTS · ${errorMessage(errorType.name)} · 오디오 포커스 반환")
                onEvent(SpeechEvent.PlaybackFailed(errorType.name))
            }
        }
        eventListener = listener
        // This is a short-utterance demo. Do not retain a focus lease forever if the
        // remote service disconnects without delivering a terminal callback.
        main.postDelayed(playbackTimeout, 60_000)
        if (!sdkCall("TTS 재생") {
            engine.setLocale(Locale.KOREA)
            engine.setAudioAttributes(audioFocus.attributes)
            engine.addEventListener(listener)
            engine.speak(text)
        }) {
            stopPlayback()
            onEvent(SpeechEvent.PlaybackFailed("REQUEST_FAILED"))
        }
    }

    fun listen() {
        if (closed || listening) return
        if (!playbackSession.canStart) {
            onStatus("백그라운드 또는 Suspend 상태에서는 음성 인식을 시작하지 않습니다.")
            return
        }
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            onStatus("마이크 권한이 필요합니다.")
            onEvent(SpeechEvent.RecognitionFailed("PERMISSION_DENIED", retryable = false))
            return
        }
        val engine = stt
        if (!sttReady || engine == null) {
            initialize()
            onStatus("Pleos STT가 준비되지 않았습니다.\nSTT: $sttState\n연결 완료 후 다시 눌러 주세요.")
            return
        }
        stopPlayback()
        stopRecognition()
        val generation = recognitionGeneration
        val listener = object : ResultListener {
            private fun current(action: () -> Unit) = dispatch {
                if (listening && generation == recognitionGeneration) action()
            }
            override fun onReady() = current { onStatus("Pleos STT 준비 완료 · ‘다음’이라고 말해 주세요.") }
            override fun onStartedRecognition() = current { onStatus("Pleos STT 듣는 중 · ‘다음’이라고 말해 주세요.") }
            override fun onUpdated(stt: String, completed: Boolean) = current {
                if (completed) {
                    stopRecognition()
                    onFinalResult(stt)
                } else {
                    if (canShowVoiceTranscription()) {
                        onStatus("중간 인식: ‘$stt’ · 최종 결과를 기다립니다.")
                    } else {
                        onStatus("음성 명령 처리 중 · 주행 규제로 인식 텍스트를 표시하지 않습니다.")
                    }
                }
            }
            // Some engines deliver the final result after the end event. Keep the request
            // active until completed=true, an error, cancellation, or the overall timeout.
            override fun onEndedRecognition() = current { onStatus("Pleos STT · 최종 인식 결과를 기다리는 중…") }
            override fun onError(errorType: SttError) = current {
                stopRecognition()
                onStatus("Pleos STT · ${errorMessage(errorType.name)}")
                onEvent(SpeechEvent.RecognitionFailed(errorType.name,
                    retryable = errorType.name !in setOf("PERMISSION_DENIED", "UNAVAILABLE_LANGUAGE", "NOT_SUPPORTED_API", "UNAUTHENTICATED", "ON_DEVICE_ENGINE_ERROR"),
                    silence = errorType.name == "TIMEOUT"))
            }
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
        }
        resultListener = listener
        listening = true
        onListeningChanged(true)
        onStatus("Pleos STT · 마이크 입력 요청 중…")
        main.postDelayed(recognitionTimeout, 15_000)
        if (!sdkCall("STT 인식 요청") {
            engine.setLocale(Locale.KOREA)
            engine.addListener(listener)
            engine.request()
        }) {
            stopRecognition()
            onEvent(SpeechEvent.RecognitionFailed("REQUEST_FAILED", retryable = false))
        }
    }

    private fun stopRecognition() {
        recognitionGeneration++
        main.removeCallbacks(recognitionTimeout)
        val wasListening = listening
        listening = false
        onListeningChanged(false)
        resultListener?.let { listener -> sdkCall("STT 리스너 해제") { stt?.removeListener(listener) } }
        resultListener = null
        if (wasListening) sdkCall("STT 중지") { stt?.stop() }
    }

    private fun detachPlaybackListener() {
        playbackGeneration++
        main.removeCallbacks(playbackTimeout)
        eventListener?.let { listener -> sdkCall("TTS 리스너 해제") { tts?.removeEventListener(listener) } }
        eventListener = null
    }

    private fun stopPlayback() {
        // Stop audio before returning focus. Invalidate callbacks before SDK stop().
        detachPlaybackListener()
        playbackSession.stop()
    }

    fun setForeground(foreground: Boolean) {
        playbackSession.setForeground(foreground)
        if (!foreground) stop()
    }

    /** Connect an OEM pre-suspend power event here, before the system sleeps. */
    fun onSuspendEntering() {
        playbackSession.enterSuspend()
        stop()
        releaseEngines()
        if (!closed) onStatus("Suspend · 음성 중지, 오디오 포커스 반환, SDK 연결 해제")
    }

    /** Reconnect only; a fresh user action is required to request focus/play audio. */
    fun onResumeFromSuspend() {
        if (closed) return
        playbackSession.resumeFromSuspend()
        if (playbackSession.canStart) initialize()
    }

    private fun releaseEngines() {
        val oldStt = stt
        val oldTts = tts
        stt = null
        tts = null
        sttReady = false
        ttsReady = false
        sttInitializing = false
        ttsInitializing = false
        sttState = "미연결"
        ttsState = "미연결"
        sdkCall("STT 연결 해제") { oldStt?.release() }
        sdkCall("TTS 연결 해제") { oldTts?.release() }
    }

    fun stop() {
        stopRecognition()
        stopPlayback()
    }

    fun close() {
        if (closed) return
        closed = true
        stop()
        releaseEngines()
        main.removeCallbacksAndMessages(null)
    }

    private fun dispatch(action: () -> Unit) {
        main.post { if (!closed) action() }
    }

    private fun hasPermission(permission: String): Boolean =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Permission can be revoked between the grant check and a Binder call. */
    private fun sdkCall(operation: String, action: () -> Unit): Boolean = try {
        action()
        true
    } catch (error: SecurityException) {
        if (!closed) onStatus("$operation · 권한이 없습니다. Pleos 서비스 권한과 앱 권한을 확인해 주세요.")
        false
    } catch (error: RuntimeException) {
        if (!closed) onStatus("$operation 실패 · ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
        false
    }

    private fun errorMessage(code: String): String = when (code) {
        "PERMISSION_DENIED" -> "$code · Pleos 서비스 권한을 확인해 주세요."
        "ON_DEVICE_ENGINE_ERROR" -> "$code · Pleos 온디바이스 엔진을 확인해 주세요."
        "UNAVAILABLE_LANGUAGE" -> "$code · 한국어 음성 모델을 사용할 수 없습니다."
        "MIC_ERROR", "AUDIO_ERROR" -> "$code · 에뮬레이터 마이크 입력과 오디오 설정을 확인해 주세요."
        "TIMEOUT" -> "$code · 입력 시간이 초과되었습니다. 다시 눌러 주세요."
        "UNAUTHENTICATED" -> "$code · Pleos 서비스 인증 상태를 확인해 주세요."
        "NETWORK_ERROR" -> "$code · Pleos 서비스의 연결 상태를 확인해 주세요."
        "NOT_SUPPORTED_API" -> "$code · Pleos 이미지와 SDK 버전의 호환성을 확인해 주세요."
        else -> "$code · Pleos 음성 서비스 요청에 실패했습니다."
    }

    private companion object {
        const val TTS_PERMISSION = "pleos.car.permission.TTS_SERVICE"
        const val STT_PERMISSION = "pleos.car.permission.STT_SERVICE"
    }
}
