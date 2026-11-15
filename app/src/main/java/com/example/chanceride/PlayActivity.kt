package com.example.chanceride

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.chanceride.conversation.PlayCommand
import com.example.chanceride.conversation.PlayCommandParser
import com.example.chanceride.conversation.PlaySession
import com.example.chanceride.conversation.Seat
import com.example.chanceride.drivingrestrictions.CarDrivingRestrictionsMonitor
import com.example.chanceride.drivingrestrictions.DrivingRestrictionsState
import com.example.chanceride.drivingrestrictions.UxRestriction
import com.example.chanceride.speech.HandsFreeLoop
import com.example.chanceride.speech.KoreanSpeechController
import com.example.chanceride.speech.SpeechEvent
import com.example.chanceride.ui.Palette
import com.example.chanceride.ui.RideActivity
import com.example.chanceride.ui.dp
import com.example.chanceride.ui.formatTime
import com.example.chanceride.ui.rounded
import java.util.UUID

class PlayRetainedState : ViewModel() {
    var rearmAfterConfiguration = false
}

/** Voice-first playback is isolated from parked theme/participant configuration. */
class PlayActivity : RideActivity() {
    private val store by lazy { (application as RideApplication).sessions }
    private lateinit var session: PlaySession
    private lateinit var speech: KoreanSpeechController
    private lateinit var loop: HandsFreeLoop
    private lateinit var monitor: CarDrivingRestrictionsMonitor
    private lateinit var retained: PlayRetainedState
    private var ux = DrivingRestrictionsState()
    private val main = Handler(Looper.getMainLooper())
    private var foreground = false
    private var freshLaunch = false
    private var lastTick = 0L
    private var ticks = 0
    private var voiceMessage = "음성을 준비하고 있어요"
    private var clock: TextView? = null
    private var closing = false
    private var suspended = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                suspended = true
                loop.pause("화면이 꺼져 음성을 멈췄어요")
                speech.onSuspendEntering()
                persist()
            } else if (intent.action == Intent.ACTION_SCREEN_ON && foreground) {
                suspended = false
                speech.onResumeFromSuspend() // Reconnect only. User must explicitly restart.
            }
        }
    }
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed && foreground) startVoice()
        else if (!allowed && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(this).setTitle("마이크 권한 설정")
                .setMessage("음성 플레이와 대화 기록을 다시 사용하려면 마이크 권한을 허용해 주세요.")
                .setPositiveButton("앱 설정") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                .setNegativeButton("닫기", null).show()
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!foreground || closing) return
            updateElapsed()
            clock?.text = limited(headerText())
            if (++ticks % 10 == 0) persist()
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loaded = if (intent.getBooleanExtra("result", false)) store.result() else store.active() ?: store.result()
        if (loaded == null) { finish(); return }
        session = loaded
        retained = ViewModelProvider(this)[PlayRetainedState::class.java]
        freshLaunch = savedInstanceState == null && !loaded.completed
        monitor = CarDrivingRestrictionsMonitor(this) { state ->
            ux = state
            if (!closing && foreground) render()
        }
        render()
        speech = KoreanSpeechController(this,
            onStatus = {}, // SDK diagnostic text never enters the conversation history.
            onListeningChanged = {},
            onFinalResult = { if (!closing) loop.finalResult(it) },
            canShowVoiceTranscription = { mayShowConversation() },
            onEvent = { event ->
                if (::loop.isInitialized && !closing) when (event) {
                    is SpeechEvent.Availability -> if (event.ttsReady && event.sttReady) loop.ready()
                    SpeechEvent.PlaybackFinished -> loop.playbackFinished()
                    SpeechEvent.PlaybackInterrupted -> if (loop.enabled) loop.pause("다른 소리에 양보했어요 · 음성 다시 시작")
                    is SpeechEvent.PlaybackFailed -> if (loop.enabled) loop.pause("음성을 재생하지 못했어요 · 다시 시작해 주세요")
                    is SpeechEvent.RecognitionFailed -> loop.recognitionFailed(event.retryable, event.silence)
                }
            }
        )
        loop = HandsFreeLoop(
            schedule = { delay, action -> main.postDelayed({ action() }, delay); Unit },
            stopSpeech = { speech.stop() },
            speak = { speech.speak(it) },
            listen = { speech.listen() },
            onUtterance = ::handleUtterance,
            onState = { message -> voiceMessage = message }
        )
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        }, RECEIVER_NOT_EXPORTED)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { exitPlay() }
        })
    }

    private fun headerText(): String = "${session.theme.title}  |  ${session.seats.size}명  |  ${formatTime(session.elapsedMs)}"
    private fun isRestrictedView(): Boolean = !ux.hasCurrentRestrictions || ux.restrictions?.requiresDistractionOptimization != false
    private fun mayShowConversation(): Boolean = !isRestrictedView() &&
        ux.canShowVoiceTranscription && ux.allows(UxRestriction.NO_TEXT_MESSAGE)
    private fun limited(value: String): String {
        val rules = ux.restrictions ?: return value
        if (!rules.isRestricted(UxRestriction.LIMIT_STRING_LENGTH)) return value
        val max = rules.maxRestrictedStringLength.coerceAtLeast(0)
        if (value.codePointCount(0, value.length) <= max) return value
        if (max == 0) return ""
        return value.substring(0, value.offsetByCodePoints(0, (max - 1).coerceAtLeast(0))) + "…"
    }
    private fun applyTextLimits(view: View) {
        if (view is TextView) view.text = limited(view.text.toString())
        if (view is ViewGroup) for (index in 0 until view.childCount) applyTextLimits(view.getChildAt(index))
    }

    private fun render() {
        if (!::session.isInitialized) return
        val theme = session.theme
        val color = Color.parseColor(theme.color)
        val root = design.page()
        clock = design.pageHeader(root, headerText(), color = color, icon = theme.icon,
            exit = if (session.completed) null else ::exitPlay)
        // The header keeps its reference appearance; long press exposes voice recovery
        // without adding a permanent status row or an extra button to the artwork.
        clock?.apply {
            contentDescription = "음성 제어 메뉴"
            setOnLongClickListener {
                AlertDialog.Builder(this@PlayActivity).setTitle("음성 제어")
                    .setMessage(voiceMessage)
                    .setPositiveButton(if (::loop.isInitialized && loop.enabled) "음성 일시정지" else "음성 다시 시작") { _, _ -> toggleVoice() }
                    .setNegativeButton("닫기", null).show()
                true
            }
        }
        if (session.completed) {
            design.label(root, "즐거운 이야기 나누셨나요?", 40, 158, 760, 68, 44f, true)
            design.label(root, "꺼내본 질문 ${session.visited.size}개", 40, 332, 760, 48, 28f, true, centered = true)
            design.label(root, "함께한 시간 ${session.elapsedMs / 60000}분 ${session.elapsedMs / 1000 % 60}초",
                40, 382, 760, 48, 28f, true, centered = true)
            if (!isRestrictedView()) {
                val other = design.tile()
                design.label(other, "다른 테마 고르기", 12, 160, 322, 80, 36f, true, centered = true)
                design.clickable(other, "다른 테마 고르기") { if (ux.canSelectCategory) returnToThemes() }
                design.place(root, other, 60, 486, 346, 400)
                val again = design.tile()
                design.place(again, design.glyph("restart", Color.TRANSPARENT, 64), 141, 104, 64, 64)
                design.label(again, "같은 테마로\n한번 더 하기", 12, 202, 322, 108, 36f, true, centered = true)
                design.clickable(again, "같은 테마로 한번 더 하기") { restart() }
                design.place(root, again, 441, 486, 346, 400)
            }
            design.place(root, design.footerButton(design.button("홈으로 돌아가기", true, ::exitPlay)),
                20, 1062, 800, 100)
        } else {
            design.place(root, design.progress(session.questionIndex + 1, theme.questions.size, color),
                40, 149, 672, 12)
            design.label(root, "${session.questionIndex + 1}/${theme.questions.size}", 751, 140, 70, 44, 26f)
            design.label(root, if (isRestrictedView()) "질문은 음성으로 들려드릴게요." else session.question,
                40, 205, 760, 150, 44f, true)
            design.place(root, View(this).apply { setBackgroundColor(Palette.line) }, 0, 359, 840, 1)
            if (mayShowConversation()) {
                val conversation = design.column()
                addConversation(conversation)
                val scroll = ScrollView(this).apply {
                    addView(conversation); isVerticalScrollBarEnabled = true
                    scrollBarSize = dp(12); isScrollbarFadingEnabled = false
                    setPadding(0, 0, dp(24), 0)
                }
                design.place(root, scroll, 24, 392, 800, 634)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
            val (previous, next) = design.bottomActions(root, "이전 질문", "다음 질문",
                { execute(PlayCommand.PREVIOUS) }, { execute(PlayCommand.NEXT) })
            previous.isEnabled = !isRestrictedView() && session.questionIndex > 0
            next.isEnabled = !isRestrictedView()
            previous.alpha = if (previous.isEnabled) 1f else .4f
            next.alpha = if (next.isEnabled) 1f else .4f
        }
        applyTextLimits(root)
        present(root)
    }

    private fun addConversation(parent: LinearLayout) {
        val lines = session.lines.filter { it.questionIndex == session.questionIndex }
        lines.takeLast(100).forEach { line ->
            val right = line.speaker == Seat.PASSENGER || line.speaker == Seat.REAR_PASSENGER
            val group = design.column().apply { gravity = if (right) Gravity.END else Gravity.START }
            group.addView(design.text(line.speaker?.label ?: "참여자", 20f).apply {
                setPadding(dp(22), 0, dp(22), 0)
            })
            design.space(group, 8)
            val bubble = design.text(line.text, 28f, true).apply {
                background = rounded(if (right) Palette.pink else Color.WHITE, dp(26))
                setPadding(dp(24), dp(12), dp(24), dp(12))
                maxWidth = dp(728)
            }
            group.addView(bubble)
            design.add(parent, group)
            design.space(parent, 18)
        }
    }

    private fun spokenPrompt(): String = if (session.completed)
        "즐거운 이야기 나누셨나요? ${session.visited.size}개의 질문을 함께했습니다. 나가기라고 말씀하시면 마칩니다."
        else session.question

    private fun startVoice() {
        if (!foreground || suspended || closing) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            loop.pause("마이크 권한을 허용해 주세요")
            return
        }
        speech.initialize()
        loop.start(spokenPrompt(), speech.isReady)
    }

    private fun toggleVoice() {
        if (!::loop.isInitialized) return
        if (loop.enabled) loop.pause("음성을 잠시 멈췄어요")
        else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
        else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(this).setTitle("마이크 권한 안내")
                .setMessage("음성 명령과 대화를 인식해 기기에 기록하려면 마이크 권한이 필요해요.")
                .setPositiveButton("허용") { _, _ -> microphone.launch(Manifest.permission.RECORD_AUDIO) }
                .setNegativeButton("취소", null).show()
        } else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun handleUtterance(text: String) {
        val command = PlayCommandParser.parse(text)
        if (command != null) execute(command)
        else if (!session.completed) {
            updateElapsed()
            session = session.record(text)
            persist()
            if (mayShowConversation()) render()
        }
    }

    private fun execute(command: PlayCommand) {
        if (closing) return
        if (command == PlayCommand.EXIT) { exitPlay(); return }
        if (command == PlayCommand.REPEAT) {
            if (loop.enabled) loop.questionChanged(spokenPrompt(), speech.isReady) else startVoice()
            return
        }
        if (session.completed) return
        updateElapsed()
        session = when (command) {
            PlayCommand.NEXT -> session.next()
            PlayCommand.PREVIOUS -> session.previous()
            PlayCommand.COMPLETE -> session.copy(completed = true)
            else -> session
        }
        persist()
        render()
        loop.questionChanged(spokenPrompt(), speech.isReady)
    }

    private fun restart() {
        if (!ux.canSelectCategory) return
        loop.stop()
        session = PlaySession(UUID.randomUUID().toString(), session.themeId, session.seats)
        lastTick = SystemClock.elapsedRealtime()
        store.save(session)
        render()
        startVoice()
    }
    private fun returnToThemes() {
        if (!ux.canSelectCategory) return
        setResult(RESULT_OK, Intent().putExtra("themes", true))
        exitPlay()
    }
    private fun exitPlay() {
        if (closing || !::session.isInitialized) return
        updateElapsed()
        persist()
        closing = true
        if (::loop.isInitialized) loop.stop()
        finish()
    }
    private fun updateElapsed() {
        val now = SystemClock.elapsedRealtime()
        if (foreground && lastTick > 0 && !session.completed) session = session.addElapsed(now - lastTick)
        lastTick = now
    }
    private fun persist() { if (::session.isInitialized) store.save(session) }

    override fun onStart() {
        super.onStart()
        if (!::speech.isInitialized) return
        foreground = true
        lastTick = SystemClock.elapsedRealtime()
        speech.setForeground(true)
        monitor.start()
        suspended = !getSystemService(PowerManager::class.java).isInteractive
        if (suspended) speech.onSuspendEntering() else speech.onResumeFromSuspend()
        main.post(tick)
        val auto = freshLaunch || retained.rearmAfterConfiguration
        freshLaunch = false
        retained.rearmAfterConfiguration = false
        if (auto && !suspended) startVoice()
        else { voiceMessage = "상단 제목을 길게 눌러 음성을 다시 시작할 수 있어요" }
    }
    override fun onStop() {
        if (::loop.isInitialized) {
            updateElapsed()
            persist()
            retained.rearmAfterConfiguration = isChangingConfigurations && loop.enabled
            foreground = false
            loop.stop()
            speech.setForeground(false)
            monitor.stop()
            main.removeCallbacks(tick)
        }
        super.onStop()
    }
    override fun onDestroy() {
        if (::speech.isInitialized) {
            unregisterReceiver(screenReceiver)
            loop.stop()
            speech.close()
        }
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
