package com.example.chanceride.conversation

import org.junit.Assert.*
import org.junit.Test

class PlaySessionTest {
    private fun session() = PlaySession("test", "smalltalk", setOf(Seat.DRIVER, Seat.PASSENGER))

    @Test fun strictCommandsDoNotMistakeOrdinaryConversationForNavigation() {
        assertEquals(PlayCommand.NEXT, PlayCommandParser.parse(" 다음 질문! "))
        assertEquals(PlayCommand.PREVIOUS, PlayCommandParser.parse("찬스라이드 이전"))
        assertEquals(PlayCommand.EXIT, PlayCommandParser.parse("나가기"))
        assertEquals(PlayCommand.COMPLETE, PlayCommandParser.parse("대화 완료"))
        listOf("다음에 또 만나자", "이전 여행이 좋았어", "나가기 싫어", "그만 생각하자", "").forEach {
            assertNull(it, PlayCommandParser.parse(it))
        }
    }
    @Test fun nextPreviousAndCompletionKeepVisitedQuestionCountAccurate() {
        var current = session()
        assertEquals(0, current.previous().questionIndex)
        current = current.next().next().previous()
        assertEquals(1, current.questionIndex)
        assertEquals(setOf(0,1,2), current.visited)
        while (!current.completed) current = current.next()
        assertEquals(current.theme.questions.lastIndex, current.questionIndex)
        assertEquals(current.theme.questions.size, current.visited.size)
        assertTrue(PlaySession.isValid(current))
    }
    @Test fun speakerIsOnlyAssignedWhenAnExplicitSelectedSeatPrefixExists() {
        val current = session().record("조수석 오늘 날씨가 좋네요").record("저도 그렇게 생각해요").record("운전석 뒤 안녕하세요")
        assertEquals(Seat.PASSENGER, current.lines[0].speaker)
        assertEquals("오늘 날씨가 좋네요", current.lines[0].text)
        assertNull(current.lines[1].speaker)
        assertNull(current.lines[2].speaker) // Seat not selected; never invent a speaker.
        assertNull(session().record("운전석 뒤").lines.single().speaker)
        assertTrue(session().record("운전석").lines.isEmpty())
    }
    @Test fun transcriptsBelongToTheirQuestionAndAreBounded() {
        var current = session().record("첫 질문 답변").next().record("둘째 질문 답변")
        assertEquals(listOf(0,1), current.lines.map { it.questionIndex })
        repeat(600) { current = current.record("발화 $it") }
        assertEquals(PlaySession.MAX_LINES, current.lines.size)
        assertEquals("발화 599", current.lines.last().text)
    }
    @Test fun invalidOrStalePersistedSessionsAreRejected() {
        val original = session()
        assertTrue(PlaySession.isValid(original))
        assertFalse(PlaySession.isValid(original.copy(themeId = "removed")))
        assertFalse(PlaySession.isValid(original.copy(questionIndex = 999)))
        assertFalse(PlaySession.isValid(original.copy(seats = setOf(Seat.DRIVER))))
        assertFalse(PlaySession.isValid(original.copy(elapsedMs = -1)))
        assertFalse(PlaySession.isValid(original.copy(visited = emptySet())))
        assertFalse(PlaySession.isValid(original.copy(lines = listOf(ConversationLine(0,"text",Seat.REAR_DRIVER,0)))))
    }
    @Test fun timeCountsOnlyPositiveForegroundDeltas() {
        assertEquals(1250L, session().addElapsed(1250).addElapsed(-200).elapsedMs)
        assertEquals(PlaySession.MAX_ELAPSED, session().addElapsed(Long.MAX_VALUE).elapsedMs)
    }
    @Test fun completedSessionsStayFrozenAndPartialCheckpointsCanGoBackSafely() {
        val complete = session().copy(completed = true, elapsedMs = 1000)
        assertEquals(complete, complete.next().previous().addElapsed(5000).record("대화"))
        val restored = session().copy(questionIndex = 4, visited = setOf(4)).previous()
        assertEquals(setOf(3,4), restored.visited)
        assertTrue(PlaySession.isValid(restored))
        assertEquals(PlaySession.MAX_ELAPSED, session().addElapsed(PlaySession.MAX_ELAPSED - 1).addElapsed(Long.MAX_VALUE).elapsedMs)
    }
    @Test fun allBundledThemesHaveUniqueIdsAndTenOriginalQuestions() {
        assertEquals(ThemeCatalog.themes.size, ThemeCatalog.themes.map { it.id }.toSet().size)
        ThemeCatalog.themes.forEach {
            assertEquals(10, it.questions.size)
            assertEquals(10, it.questions.toSet().size)
            assertFalse(it.instructions.isBlank())
        }
    }
}
