package com.example.chanceride

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import android.graphics.Matrix
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chanceride.data.SessionStore
import org.hamcrest.Matchers.containsString
import org.junit.*
import org.junit.runner.RunWith

/** Real parked AAOS UI + real repository serialization; user conversations are isolated. */
@RunWith(AndroidJUnit4::class)
class ConversationFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as RideApplication
    private lateinit var original: SessionStore
    private lateinit var isolated: SessionStore
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUp() {
        original = app.sessions
        isolated = SessionStore(app, "ui_flow_instrumentation")
        isolated.clear()
        app.sessions = isolated
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${app.packageName} ${Manifest.permission.RECORD_AUDIO}"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
    @After fun tearDown() {
        instrumentation.runOnMainSync {
            val lifecycle = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            val activities = androidx.test.runner.lifecycle.Stage.entries
                .flatMap { lifecycle.getActivitiesInStage(it) }.distinct()
            activities.filter { it is MainActivity || it is PlayActivity }.forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
        scenario?.close()
        app.sessions = original
        isolated.clear()
    }

    /**
     * Map the local center through every ancestor transform. The reference artboard
     * is scaled by its parent, so an untransformed width/height can tap another view.
     */
    private fun click() = GeneralClickAction(
        Tap.SINGLE,
        { view ->
            val center = floatArrayOf(view.width / 2f, view.height / 2f)
            Matrix().apply { view.transformMatrixToGlobal(this) }.mapPoints(center)
            center
        },
        Press.FINGER,
        InputDevice.SOURCE_TOUCHSCREEN,
        MotionEvent.BUTTON_PRIMARY,
    )

    private fun awaitText(value: String) {
        val end = android.os.SystemClock.uptimeMillis() + 12_000
        var failure: Throwable? = null
        while (android.os.SystemClock.uptimeMillis() < end) {
            try { onView(withText(value)).check(matches(isDisplayed())); return }
            catch (error: AssertionError) { failure = error }
            catch (error: androidx.test.espresso.NoMatchingViewException) { failure = error }
            catch (error: RuntimeException) {
                // AAOS can briefly have no RESUMED Activity while switching native windows.
                // Retry only that transition condition; application crashes still fail the test.
                if (generateSequence<Throwable>(error) { it.cause }
                        .any { it is androidx.test.espresso.NoActivityResumedException }) failure = error
                else throw error
            }
            android.os.SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for $value", failure)
    }
    private fun begin() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText("대화 시작하기")
        onView(withText("이어하기")).check(doesNotExist())
        // CarUxRestrictions connects asynchronously; wait for the card to become enabled.
        val end = android.os.SystemClock.uptimeMillis() + 12_000
        while (!isReady() && android.os.SystemClock.uptimeMillis() < end) android.os.SystemClock.sleep(100)
        onView(withContentDescription("대화 시작하기")).perform(click())
        awaitText("테마를 골라주세요.")
        onView(withContentDescription("스몰 토크, 부담 없는 질문으로 가볍게 이야기를 시작")).perform(click())
        awaitText("바로 시작")
        onView(withText("바로 시작")).perform(click())
        awaitText("플레이 시작")
        onView(withText("플레이 시작")).perform(click())
        awaitText("다음 질문")
    }
    private fun isReady(): Boolean = runCatching {
        onView(withContentDescription("대화 시작하기")).check(matches(isEnabled()))
    }.isSuccess

    @Test fun exitAndContinueRestoreTheQuestion() {
        begin()
        onView(withText("다음 질문")).perform(click())
        onView(withText("2/10")).check(matches(isDisplayed()))
        onView(withText("나가기")).perform(click())
        awaitText("이어하기")
        onView(withText("이어하기")).perform(click())
        awaitText("다음 질문")
        onView(withText("2/10")).check(matches(isDisplayed()))
        onView(withText("나가기")).perform(click())
    }

    @Test fun newPlayReplacesActiveConversationWithoutConfirmation() {
        begin()
        onView(withText("다음 질문")).perform(click())
        val oldId = isolated.active()!!.id
        onView(withText("나가기")).perform(click())
        awaitText("이어하기")
        onView(withContentDescription("대화 시작하기")).perform(click())
        awaitText("테마를 골라주세요.")
        onView(withContentDescription("스몰 토크, 부담 없는 질문으로 가볍게 이야기를 시작")).perform(click())
        awaitText("바로 시작")
        onView(withText("바로 시작")).perform(click())
        awaitText("플레이 시작")
        onView(withText("플레이 시작")).perform(click())
        awaitText("다음 질문")
        onView(withText("새 대화를 시작할까요?")).check(doesNotExist())
        onView(withText("1/10")).check(matches(isDisplayed()))
        Assert.assertNotEquals(oldId, isolated.active()!!.id)
        Assert.assertEquals(0, isolated.active()!!.questionIndex)
        onView(withText("나가기")).perform(click())
    }

    @Test fun lastQuestionCompletesAndRemovesContinueCard() {
        begin()
        repeat(10) { onView(withText("다음 질문")).perform(click()) }
        onView(withText(containsString("즐거운 이야기"))).check(matches(isDisplayed()))
        onView(withText("꺼내본 질문 10개")).check(matches(isDisplayed()))
        onView(withText("홈으로 돌아가기")).perform(click())
        awaitText("대화 시작하기")
        onView(withText("이어하기")).check(doesNotExist())
        onView(withText("지난 대화 보기")).check(doesNotExist())
        Assert.assertNotNull(isolated.result())
    }
}
