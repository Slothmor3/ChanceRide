package com.example.chanceride.speech

import org.junit.Assert.*
import org.junit.Test

class HandsFreeLoopTest {
    private class Harness {
        val tasks = mutableListOf<() -> Unit>()
        val spoken = mutableListOf<String>()
        var listens = 0
        val results = mutableListOf<String>()
        val loop = HandsFreeLoop(
            schedule = { _, task -> tasks += task },
            stopSpeech = {},
            speak = { spoken += it },
            listen = { listens++ },
            onUtterance = { results += it },
            onState = {}
        )
        fun drain() { val pending = tasks.toList(); tasks.clear(); pending.forEach { it() } }
    }
    @Test fun readingMustFinishBeforeOpeningMicrophone() {
        val h = Harness()
        h.loop.start("질문", true)
        assertEquals(listOf("질문"), h.spoken)
        assertEquals(0, h.listens)
        h.loop.playbackFinished()
        h.drain()
        assertEquals(1, h.listens)
        h.loop.finalResult("대화 내용")
        h.drain()
        assertEquals(listOf("대화 내용"), h.results)
        assertEquals(2, h.listens)
    }
    @Test fun backgroundStopCancelsRearmAndLateResults() {
        val h = Harness()
        h.loop.start("질문", true)
        h.loop.playbackFinished()
        h.loop.stop()
        h.drain()
        h.loop.finalResult("다음")
        assertEquals(0, h.listens)
        assertTrue(h.results.isEmpty())
    }
    @Test fun silenceRetriesButRepeatedHardwareErrorsStop() {
        val h = Harness()
        h.loop.start("질문", true)
        h.loop.playbackFinished()
        h.drain()
        repeat(7) { h.loop.recognitionFailed(true, true); h.drain() }
        assertTrue(h.loop.enabled)
        repeat(5) { h.loop.recognitionFailed(true, false); h.drain() }
        assertFalse(h.loop.enabled)
    }
    @Test fun focusInterruptionDoesNotAutomaticallyRestart() {
        val h = Harness()
        h.loop.start("질문", true)
        h.loop.pause("통화 중")
        h.loop.playbackFinished()
        h.loop.ready()
        h.drain()
        assertFalse(h.loop.enabled)
        assertEquals(0, h.listens)
        assertEquals(1, h.spoken.size)
    }
    @Test fun initializationTimeoutCannotStopANewerQuestion() {
        val h = Harness()
        h.loop.start("첫 질문", false)
        h.loop.start("두 번째 질문", true)
        h.drain()
        assertEquals(HandsFreeLoop.Phase.READING, h.loop.phase)
        assertEquals(listOf("두 번째 질문"), h.spoken)
    }
}
