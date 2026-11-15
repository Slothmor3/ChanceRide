package com.example.chanceride.conversation

/** Exact utterance matching keeps ordinary conversation from triggering navigation. */
enum class PlayCommand { NEXT, PREVIOUS, EXIT, REPEAT, COMPLETE }
object PlayCommandParser {
    fun parse(value: String): PlayCommand? {
        val text = value.trim().trimEnd('.', '!', '?', '。').trim()
            .removePrefix("어쩌다 동승 ").removePrefix("찬스라이드 ").trim()
        return when (text) {
            "다음", "다음 질문", "다음으로" -> PlayCommand.NEXT
            "이전", "이전 질문", "뒤로" -> PlayCommand.PREVIOUS
            "나가기", "그만", "그만하기", "종료", "홈으로", "홈으로 돌아가기" -> PlayCommand.EXIT
            "다시", "다시 읽어줘", "다시 읽기" -> PlayCommand.REPEAT
            "완료", "플레이 완료", "대화 완료" -> PlayCommand.COMPLETE
            else -> null
        }
    }
}
