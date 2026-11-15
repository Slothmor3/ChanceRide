package com.example.chanceride.conversation

/** A final STT result associated with a question and its offset in the session. */
data class ConversationLine(
    val questionIndex: Int,
    val text: String,
    val speaker: Seat?,
    val offsetMs: Long,
)
