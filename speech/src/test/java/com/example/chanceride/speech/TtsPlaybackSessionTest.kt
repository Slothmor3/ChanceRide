package com.example.chanceride.speech

import org.junit.Assert.*
import org.junit.Test

class TtsPlaybackSessionTest {
    private class Harness {
        var granted = true
        var requests = 0
        var abandons = 0
        var stops = 0
        var interruptions = 0
        val lossCallbacks = mutableListOf<() -> Unit>()
        val session = TtsPlaybackSession(
            requestFocus = {
                requests++
                lossCallbacks += it
                granted
            },
            abandonFocus = { abandons++ },
            stopAudio = { stops++ },
            onInterrupted = { interruptions++ }
        )
    }

    @Test fun deniedFocusNeverAuthorizesPlayback() {
        val h = Harness()
        h.session.setForeground(true)
        h.granted = false
        assertNull(h.session.begin())
        assertEquals(1, h.requests)
        assertEquals(1, h.abandons)
        assertEquals(0, h.stops)
    }

    @Test fun completionReturnsFocusExactlyOnce() {
        val h = Harness()
        h.session.setForeground(true)
        val token = h.session.begin()!!
        h.session.complete(token)
        h.session.complete(token)
        h.session.stop()
        assertEquals(1, h.abandons)
        assertEquals(0, h.stops)
        assertFalse(h.session.isCurrent(token))
    }

    @Test fun lostFocusStopsBeforeAbandonAndDoesNotReplay() {
        val events = mutableListOf<String>()
        var loss: (() -> Unit)? = null
        val session = TtsPlaybackSession(
            requestFocus = { loss = it; events += "request"; true },
            abandonFocus = { events += "abandon" },
            stopAudio = { events += "stop" },
            onInterrupted = { events += "interrupted" }
        )
        session.setForeground(true)
        val token = session.begin()!!
        loss!!()
        assertEquals(listOf("request", "stop", "abandon", "interrupted"), events)
        assertFalse(session.isCurrent(token))
        assertEquals(1, events.count { it == "request" })
    }

    @Test fun backgroundAndSuspendRejectNewRequestsAndResumeNeedsUserAction() {
        val h = Harness()
        assertNull(h.session.begin())
        assertEquals(0, h.requests)
        h.session.setForeground(true)
        h.session.begin()
        h.session.enterSuspend()
        assertEquals(1, h.stops)
        assertEquals(1, h.abandons)
        assertNull(h.session.begin())
        h.session.setForeground(false)
        h.session.resumeFromSuspend()
        assertNull(h.session.begin())
        h.session.setForeground(true)
        assertEquals(1, h.requests) // No restore request from either resume path.
        assertNotNull(h.session.begin())
        h.session.setForeground(false)
        assertEquals(2, h.stops)
        assertEquals(2, h.abandons)
    }

    @Test fun oldCompletionAndLossCannotAffectANewerUtterance() {
        val h = Harness()
        h.session.setForeground(true)
        val first = h.session.begin()!!
        val oldLoss = h.lossCallbacks.last()
        val second = h.session.begin()!!
        h.session.complete(first)
        oldLoss()
        assertTrue(h.session.isCurrent(second))
        assertEquals(1, h.stops)
        assertEquals(1, h.abandons)
        assertEquals(0, h.interruptions)
        h.session.stop()
        assertEquals(2, h.abandons)
    }

    @Test fun cancellationReturnsFocusEvenIfStoppingAudioThrows() {
        var abandons = 0
        val session = TtsPlaybackSession(
            requestFocus = { true },
            abandonFocus = { abandons++ },
            stopAudio = { throw IllegalStateException("SDK disconnected") },
            onInterrupted = {}
        )
        session.setForeground(true)
        val token = session.begin()!!
        assertThrows(IllegalStateException::class.java) { session.stop() }
        assertEquals(1, abandons)
        assertFalse(session.isCurrent(token))
    }
}
