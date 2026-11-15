package com.example.chanceride.speech

sealed interface SpeechEvent {
    data class Availability(val ttsReady: Boolean, val sttReady: Boolean) : SpeechEvent
    data object PlaybackFinished : SpeechEvent
    data object PlaybackInterrupted : SpeechEvent
    data class PlaybackFailed(val reason: String) : SpeechEvent
    data class RecognitionFailed(val code: String, val retryable: Boolean, val silence: Boolean = false) : SpeechEvent
}
