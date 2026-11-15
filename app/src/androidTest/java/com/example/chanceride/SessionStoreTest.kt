package com.example.chanceride

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chanceride.conversation.*
import com.example.chanceride.data.SessionStore
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Uses an isolated preference file; never deletes the developer's real conversations. */
@RunWith(AndroidJUnit4::class)
class SessionStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "session_store_instrumentation"
    private lateinit var store: SessionStore
    @Before fun setUp() { store = SessionStore(context, name); store.clear() }
    @After fun tearDown() { store.clear() }
    private fun session() = PlaySession("test", "romantic", setOf(Seat.DRIVER, Seat.PASSENGER))

    @Test fun progressAndUnicodeConversationSurviveANewStoreInstance() {
        val value = session().addElapsed(12_345).record("조수석 오늘도 행복해요 😀").next()
        store.save(value)
        val restored = SessionStore(context, name).active()
        assertEquals(value, restored)
        assertEquals(Seat.PASSENGER, restored!!.lines.first().speaker)
    }
    @Test fun completionAtomicallyMovesActiveSessionToResult() {
        store.save(session())
        val completed = session().addElapsed(5000).copy(completed = true)
        store.save(completed)
        assertNull(store.active())
        assertEquals(completed, store.result())
    }
    @Test fun corruptOrUnsupportedDataIsDiscarded() {
        val preferences = context.getSharedPreferences(name, 0)
        preferences.edit().putString("active", "{broken").commit()
        assertNull(store.active())
        assertFalse(preferences.contains("active"))
        preferences.edit().putString("active", """{"version":999}""").commit()
        assertNull(store.active())
    }
    @Test fun savingANewSessionKeepsPreviousCompletedConversation() {
        val completed = session().copy(completed = true)
        store.save(completed)
        val next = session().copy(id = "second", themeId = "humor")
        store.save(next)
        assertEquals(next, store.active())
        assertEquals(completed, store.result())
    }
}
