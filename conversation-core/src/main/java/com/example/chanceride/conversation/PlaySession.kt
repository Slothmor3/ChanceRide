package com.example.chanceride.conversation

/**
 * Immutable conversation state shared by touch controls, voice commands and persistence.
 * [visited] tracks unique questions; [elapsedMs] counts foreground play time.
 */
data class PlaySession(
    val id: String,
    val themeId: String,
    val seats: Set<Seat>,
    val questionIndex: Int = 0,
    val elapsedMs: Long = 0,
    val visited: Set<Int> = setOf(0),
    val lines: List<ConversationLine> = emptyList(),
    val completed: Boolean = false,
) {
    val theme: ConversationTheme
        get() = requireNotNull(ThemeCatalog.find(themeId))

    val question: String
        get() = theme.questions[questionIndex]

    fun next(): PlaySession = when {
        completed -> this
        questionIndex == theme.questions.lastIndex -> copy(completed = true)
        else -> copy(
            questionIndex = questionIndex + 1,
            visited = visited + (questionIndex + 1),
        )
    }

    fun previous(): PlaySession {
        if (completed) return this
        val previous = (questionIndex - 1).coerceAtLeast(0)
        return copy(questionIndex = previous, visited = visited + previous)
    }

    fun addElapsed(deltaMs: Long): PlaySession = if (completed) {
        this
    } else {
        copy(elapsedMs = (elapsedMs + deltaMs.coerceIn(0, MAX_ELAPSED)).coerceAtMost(MAX_ELAPSED))
    }

    fun record(text: String): PlaySession {
        val clean = text.trim().take(MAX_LINE_LENGTH)
        if (clean.isBlank() || completed) return this

        // Match longer seat names first: "운전석 뒤" must never be attributed to "운전석".
        val mentionedSeat = Seat.entries
            .sortedByDescending { it.label.length }
            .firstOrNull { clean == it.label || clean.startsWith(it.label + " ") }
        val speaker = mentionedSeat?.takeIf { it in seats }
        val spoken = if (speaker == null) clean else clean.removePrefix(speaker.label).trim()
        if (spoken.isBlank()) return this

        val line = ConversationLine(questionIndex, spoken, speaker, elapsedMs)
        return copy(lines = (lines + line).takeLast(MAX_LINES))
    }

    companion object {
        const val MAX_LINES = 500
        const val MAX_LINE_LENGTH = 2_000
        const val MAX_ELAPSED = 7L * 24 * 60 * 60 * 1_000

        /** Validate stored data before it reaches the UI or a question lookup. */
        fun isValid(value: PlaySession): Boolean {
            val theme = ThemeCatalog.find(value.themeId) ?: return false
            return value.id.isNotBlank() &&
                value.seats.size in 2..4 &&
                value.questionIndex in theme.questions.indices &&
                value.elapsedMs in 0..MAX_ELAPSED &&
                value.visited.isNotEmpty() &&
                value.questionIndex in value.visited &&
                value.visited.all { it in theme.questions.indices } &&
                value.lines.size <= MAX_LINES &&
                value.lines.all { line ->
                    line.questionIndex in theme.questions.indices &&
                        line.text.isNotBlank() &&
                        line.text.length <= MAX_LINE_LENGTH &&
                        (line.speaker == null || line.speaker in value.seats) &&
                        line.offsetMs in 0..value.elapsedMs
                }
        }
    }
}
