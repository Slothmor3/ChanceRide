package com.example.chanceride.data

import android.content.Context
import com.example.chanceride.conversation.ConversationLine
import com.example.chanceride.conversation.PlaySession
import com.example.chanceride.conversation.Seat
import org.json.JSONArray
import org.json.JSONObject

/** App-private text storage with a versioned format and validated reads. */
class SessionStore(context: Context, storageName: String = "conversation_sessions") {
    private val preferences = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)

    fun active(): PlaySession? = read(ACTIVE)?.takeUnless { it.completed }

    fun result(): PlaySession? = read(RESULT)?.takeIf { it.completed }

    fun save(value: PlaySession) {
        require(PlaySession.isValid(value))
        val editor = preferences.edit()
        if (value.completed) {
            editor.putString(RESULT, encode(value)).remove(ACTIVE)
        } else {
            editor.putString(ACTIVE, encode(value))
        }
        editor.apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun read(key: String): PlaySession? {
        val raw = preferences.getString(key, null) ?: return null
        val value = runCatching {
            val json = JSONObject(raw)
            require(json.getInt("version") == FORMAT_VERSION)
            val seats = json.getJSONArray("seats")
            val visited = json.getJSONArray("visited")
            val lines = json.optJSONArray("lines") ?: JSONArray()
            require(lines.length() <= PlaySession.MAX_LINES)

            PlaySession(
                id = json.getString("id"),
                themeId = json.getString("theme"),
                seats = (0 until seats.length())
                    .map { Seat.valueOf(seats.getString(it)) }.toSet(),
                questionIndex = json.getInt("index"),
                elapsedMs = json.getLong("elapsed"),
                visited = (0 until visited.length()).map { visited.getInt(it) }.toSet(),
                lines = (0 until lines.length()).map { index ->
                    val item = lines.getJSONObject(index)
                    ConversationLine(
                        questionIndex = item.getInt("question"),
                        text = item.getString("text"),
                        speaker = item.optString("speaker")
                            .takeIf { it.isNotEmpty() }?.let(Seat::valueOf),
                        offsetMs = item.getLong("offset"),
                    )
                },
                completed = json.getBoolean("completed"),
            ).also { require(PlaySession.isValid(it)) }
        }.getOrNull()

        // Drop only the invalid entry. Never feed malformed snapshots into the UI.
        if (value == null) preferences.edit().remove(key).apply()
        return value
    }

    private fun encode(value: PlaySession): String = JSONObject().apply {
        put("version", FORMAT_VERSION)
        put("id", value.id)
        put("theme", value.themeId)
        put("seats", JSONArray(value.seats.map { it.name }))
        put("index", value.questionIndex)
        put("elapsed", value.elapsedMs)
        put("visited", JSONArray(value.visited.toList()))
        put("completed", value.completed)
        put("lines", JSONArray().apply {
            value.lines.forEach { line ->
                put(JSONObject().apply {
                    put("question", line.questionIndex)
                    put("text", line.text)
                    put("speaker", line.speaker?.name.orEmpty())
                    put("offset", line.offsetMs)
                })
            }
        })
    }.toString()

    private companion object {
        const val FORMAT_VERSION = 1
        const val ACTIVE = "active"
        const val RESULT = "result"
    }
}
