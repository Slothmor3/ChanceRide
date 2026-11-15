package com.example.chanceride

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.chanceride.conversation.PlaySession
import com.example.chanceride.conversation.Seat
import com.example.chanceride.conversation.ThemeCatalog
import com.example.chanceride.conversation.ThemeGroup
import com.example.chanceride.drivingrestrictions.CarDrivingRestrictionsMonitor
import com.example.chanceride.drivingrestrictions.DrivingRestrictionsState
import com.example.chanceride.ui.Palette
import com.example.chanceride.ui.RideActivity
import com.example.chanceride.ui.dp
import com.example.chanceride.ui.formatTime
import com.example.chanceride.ui.rounded
import java.util.UUID

/** Parked setup flow. Only PlayActivity is marked distraction optimized. */
class MainActivity : RideActivity() {
    private enum class Screen { SPLASH, HOME, THEMES, DETAIL, SEATS }
    private var screen = Screen.SPLASH
    private var group = ThemeGroup.GENERAL
    private var themeId = "smalltalk"
    private var seats = setOf(Seat.DRIVER, Seat.PASSENGER)
    private val store by lazy { (application as RideApplication).sessions }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var restrictions: CarDrivingRestrictionsMonitor
    private var ux = DrivingRestrictionsState()
    private var notice: TextView? = null
    private val setupViews = mutableListOf<Pair<View, () -> Boolean>>()
    private var pendingNew = false

    private val player = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        screen = if (result.data?.getBooleanExtra("themes", false) == true) Screen.THEMES else Screen.HOME
        render()
    }
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchPending()
        else {
            pendingNew = false
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                AlertDialog.Builder(this).setTitle("마이크 권한이 필요해요")
                    .setMessage("음성으로 플레이하고 대화를 기록하려면 앱 설정에서 마이크 권한을 허용해 주세요.")
                    .setPositiveButton("앱 설정") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                    .setNegativeButton("닫기", null).show()
            } else toast("마이크를 허용하면 음성으로 플레이할 수 있어요.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = savedInstanceState?.getString("screen")?.let { runCatching { Screen.valueOf(it) }.getOrNull() } ?: Screen.SPLASH
        themeId = savedInstanceState?.getString("theme")?.takeIf { ThemeCatalog.find(it) != null } ?: "smalltalk"
        group = savedInstanceState?.getString("group")?.let { runCatching { ThemeGroup.valueOf(it) }.getOrNull() } ?: ThemeGroup.GENERAL
        seats = savedInstanceState?.getStringArrayList("seats")?.mapNotNull { name -> Seat.entries.firstOrNull { it.name == name } }?.toSet() ?: seats
        pendingNew = savedInstanceState?.getBoolean("pendingNew") ?: false
        restrictions = CarDrivingRestrictionsMonitor(this) { state -> ux = state; updateRestrictions() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    Screen.SPLASH, Screen.HOME -> finish()
                    Screen.THEMES -> go(Screen.HOME)
                    Screen.DETAIL -> go(Screen.THEMES)
                    Screen.SEATS -> go(Screen.DETAIL)
                }
            }
        })
        render()
        if (screen == Screen.SPLASH) main.postDelayed({ if (!isFinishing) go(Screen.HOME) }, 650)
    }

    private fun go(value: Screen) { screen = value; render() }
    private fun requireParked(): Boolean {
        if (!ux.canSelectCategory) { toast("테마와 참여자 선택은 정차 후 이용해 주세요."); return false }
        return true
    }
    private fun setup(view: View, valid: () -> Boolean = { true }): View {
        setupViews += view to valid
        return view
    }
    private fun guarded(action: () -> Unit): () -> Unit = { if (requireParked()) action() }
    private fun updateRestrictions() {
        setupViews.forEach { (view, valid) ->
            view.isEnabled = ux.canSelectCategory && valid()
            view.alpha = if (view.isEnabled) 1f else .42f
        }
        notice?.text = when {
            !ux.hasCurrentRestrictions -> "차량 상태를 확인하고 있어요"
            !ux.canSelectCategory -> "테마와 참여자 선택은 정차 후 이용해 주세요"
            else -> ""
        }
        notice?.visibility = if (notice?.text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun render() {
        setupViews.clear()
        notice = null
        val root = design.page()
        val theme = ThemeCatalog.find(themeId)!!
        if (screen == Screen.SPLASH) {
            design.place(root, design.glyph("logo", Palette.dark, 100), 370, 484, 100, 100)
            design.label(root, "어쩌다 동승", 200, 638, 440, 64, 44f, true, centered = true)
            present(root)
            return
        }
        if (screen == Screen.SEATS) design.pageHeader(root, theme.title,
            color = Color.parseColor(theme.color), icon = theme.icon, exit = { go(Screen.HOME) })
        else design.pageHeader(root, subtitle = "현재 탑승 인원 ${seats.size}명",
            exit = if (screen == Screen.THEMES) ({ go(Screen.HOME) }) else null)
        when (screen) {
            Screen.HOME -> home(root)
            Screen.THEMES -> themes(root)
            Screen.DETAIL -> {
                design.label(root, theme.title, 40, 141, 760, 64, 44f, true)
                design.place(root, design.glyph(theme.icon, Color.parseColor(theme.color), 200), 317, 245, 200, 200)
                design.label(root, "하는 법", 40, 548, 760, 48, 34f, true)
                val instructions = theme.instructions
                design.label(root, instructions, 40, 612, 760, 180, 32f)
                design.label(root, "예시 질문", 40, 821, 760, 48, 34f, true)
                design.label(root, "“${theme.questions.first()}”", 40, 886, 760, 120, 32f)
                val buttons = design.bottomActions(root, "뒤로 가기", "바로 시작",
                    { go(Screen.THEMES) }, guarded { go(Screen.SEATS) })
                setup(buttons.second)
            }
            Screen.SEATS -> participants(root)
            else -> Unit
        }
        // Restriction feedback is contextual; it never shifts the reference layout.
        notice = design.label(root, "", 40, 1008, 760, 36, 24f, color = Palette.muted)
        present(root)
        updateRestrictions()
    }

    private fun home(root: FrameLayout) {
        design.label(root, "대화도 게임도,\n말로 함께 즐겨요.", 40, 147, 760, 132, 44f, true)
        val start = design.tile(color = Palette.conversation)
        design.place(start, design.glyph("brand", Palette.conversation, 100), 44, 34, 100, 100)
        design.label(start, "대화 시작하기", 163, 62, 490, 52, 36f, true, Color.WHITE)
        design.place(start, design.glyph("arrow", Color.TRANSPARENT, 56), 690, 55, 56, 56)
        design.clickable(start, "대화 시작하기", guarded { go(Screen.THEMES) })
        setup(start)
        design.place(root, start, 24, 305, 792, 166)
        val active = store.active()
        if (active != null) {
            val resume = design.tile(color = Palette.pink)
            val title = design.label(resume, active.theme.title, 36, 39, 350, 48, 34f, true)
            val metadataX = maxOf(176, 52 + (title.paint.measureText(active.theme.title) / resources.displayMetrics.density).toInt())
            design.label(resume, "${active.seats.size}명  |  ${formatTime(active.elapsedMs)}", metadataX, 46, 600 - metadataX, 38, 26f, true, Palette.muted)
            design.place(resume, design.progress(active.questionIndex + 1, active.theme.questions.size,
                Color.parseColor(active.theme.color)), 36, 105, 536, 12)
            design.place(resume, design.button("이어하기", true) { requestStart(false) }.apply {
                textSize = 26f
            }, 619, 46, 138, 64)
            design.place(root, resume, 24, 496, 792, 156)
        }
        val headingY = if (active == null) 566 else 733
        design.label(root, "추천 테마", 40, headingY, 400, 56, 36f, true)
        val all = design.page()
        design.label(all, "전체 테마", 0, 0, 112, 56, 26f, true).apply { gravity = Gravity.CENTER_VERTICAL }
        all.addView(design.glyph("chevron", Color.TRANSPARENT, 36),
            FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER_VERTICAL).apply { leftMargin = dp(104) })
        design.clickable(all, "전체 테마", guarded { go(Screen.THEMES) })
        setup(all)
        design.place(root, all, 650, headingY, 150, 56)
        ThemeCatalog.themes.take(3).forEachIndexed { index, theme ->
            val card = design.tile()
            design.label(card, theme.title, 34, 43, 210, 62, 38f, true)
            design.place(card, design.glyph(theme.icon, Color.parseColor(theme.color), 100), 116, 184, 100, 100)
            design.clickable(card, theme.title, guarded { themeId = theme.id; go(Screen.DETAIL) })
            setup(card)
            design.place(root, card, 29 + index * 269, if (active == null) 642 else 809, 248, 316)
        }
        // Data management remains available without adding controls absent from the design.
        root.setOnLongClickListener {
            if (requireParked()) AlertDialog.Builder(this).setItems(arrayOf("지난 대화 보기", "저장된 기록 삭제")) { _, which ->
                if (which == 0 && store.result() != null)
                    player.launch(Intent(this, PlayActivity::class.java).putExtra("result", true))
                else if (which == 1) AlertDialog.Builder(this).setTitle("기록을 삭제할까요?")
                    .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                        if (requireParked()) { store.clear(); render() }
                    }.show()
            }.show()
            true
        }
    }

    private fun themes(root: FrameLayout) {
        design.label(root, "테마를 골라주세요.", 40, 156, 760, 64, 44f, true)
        design.place(root, View(this).apply { setBackgroundColor(Palette.line) }, 20, 343, 800, 1)
        val positions = intArrayOf(48, 168, 331)
        ThemeGroup.entries.forEachIndexed { index, item ->
            val tab = design.text(item.label, 32f, true).apply { gravity = Gravity.CENTER }
            design.clickable(tab, item.label, guarded { group = item; render() })
            setup(tab)
            design.place(root, tab, positions[index], 264, if (index == 1) 132 else 83, 72)
            if (group == item) design.place(root, View(this).apply { setBackgroundColor(Palette.conversation) },
                positions[index], 340, if (index == 1) 132 else 83, 4)
        }
        val content = design.column()
        ThemeCatalog.themes.filter { it.group == group }.forEach { theme ->
            val card = design.tile()
            design.place(card, design.glyph(theme.icon, Color.parseColor(theme.color), 100), 49, 48, 100, 100)
            design.label(card, theme.title, 181, 50, 530, 54, 36f, true)
            design.label(card, theme.subtitle, 181, 113, 530, 72, 28f, true, Palette.muted)
            design.clickable(card, "${theme.title}, ${theme.subtitle}", guarded { themeId = theme.id; go(Screen.DETAIL) })
            setup(card)
            content.addView(card, LinearLayout.LayoutParams(dp(744), dp(197)))
            design.space(content, 24)
        }
        val scroll = ScrollView(this).apply {
            addView(content); isVerticalScrollBarEnabled = true
            scrollBarSize = dp(12); isScrollbarFadingEnabled = false
            setPadding(0, 0, dp(48), 0); clipToPadding = false
        }
        design.place(root, scroll, 24, 385, 800, 795)
    }

    private fun participants(root: FrameLayout) {
        design.label(root, "참여자의 좌석을 모두 선택해주세요.", 40, 148, 760, 66, 42f, true)
        design.label(root, "${seats.size}명 선택됨", 40, 220, 760, 48, 32f)
        Seat.entries.forEachIndexed { index, seat ->
            val selected = seat in seats
            val selectedColor = Color.rgb(137,139,141)
            val card = design.tile(color = if (selected) selectedColor else Color.WHITE)
            if (selected) design.place(card, design.text("✓", 24f, color = Color.WHITE).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(0,197,115), dp(32))
            }, 180, 28, 32, 32)
            if (seat == Seat.DRIVER) design.place(card,
                design.glyph("wheel", if (selected) selectedColor else Color.WHITE, 100), 70, 41, 100, 100)
            design.label(card, seat.label, 12, 126, 216, 48, 28f,
                color = if (selected) Color.WHITE else Palette.ink, centered = true)
            design.clickable(card, seat.label + if (selected) " 선택됨" else " 선택 안 됨", guarded {
                seats = if (seat in seats) seats - seat else seats + seat
                render()
            })
            setup(card)
            design.place(root, card, 176 + index % 2 * 268, 347 + index / 2 * 312, 240, 288)
        }
        val (_, play) = design.bottomActions(root, "뒤로 가기", "플레이 시작", { go(Screen.DETAIL) }, guarded {
            requestStart(true)
        })
        setup(play) { seats.size >= 2 }
    }

    private fun requestStart(new: Boolean) {
        if (new && !requireParked()) return
        if (!ux.hasCurrentRestrictions) { toast("차량 상태를 확인한 뒤 다시 눌러 주세요."); return }
        pendingNew = new
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) launchPending()
        else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(this).setTitle("말로 함께 플레이해요")
                .setMessage("질문을 들은 뒤 음성으로 다음·이전·나가기를 선택하고, 대화를 기록하려면 마이크 권한이 필요해요.")
                .setPositiveButton("권한 허용") { _, _ -> microphone.launch(Manifest.permission.RECORD_AUDIO) }
                .setNegativeButton("취소", null).show()
        } else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun launchPending() {
        if (!ux.hasCurrentRestrictions || (pendingNew && !requireParked())) return
        if (pendingNew) {
            if (seats.size < 2) return
            store.save(PlaySession(UUID.randomUUID().toString(), themeId, seats))
        }
        pendingNew = false
        if (store.active() != null) player.launch(Intent(this, PlayActivity::class.java))
    }

    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show() }
    override fun onStart() { super.onStart(); restrictions.start() }
    override fun onStop() { restrictions.stop(); super.onStop() }
    override fun onDestroy() { main.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen.name); outState.putString("theme", themeId)
        outState.putString("group", group.name); outState.putStringArrayList("seats", ArrayList(seats.map { it.name }))
        outState.putBoolean("pendingNew", pendingNew)
        super.onSaveInstanceState(outState)
    }
}
