package com.example.chanceride.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.util.Log

/** Uses the same attributes for the app focus request and Pleos SDK playback. */
internal class TtsAudioFocus(context: Context, private val handler: Handler) {
    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val manager = context.getSystemService(AudioManager::class.java)
    private var current: AudioFocusRequest? = null

    fun request(onLoss: () -> Unit): Boolean {
        abandon()
        lateinit var request: AudioFocusRequest
        request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            // Speech must remain intelligible. Pleos SDK has no pause/volume API;
            // the owner stops the utterance instead of trying to duck system volume.
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (current === request) {
                    Log.i(TAG, "client=${contextName} change=$change")
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onLoss()
                        AudioManager.AUDIOFOCUS_GAIN -> Unit // No automatic replay.
                    }
                }
            }, handler)
            .build()
        current = request
        val result = try {
            manager.requestAudioFocus(request)
        } catch (error: RuntimeException) {
            Log.w(TAG, "request failed", error)
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        Log.i(TAG, "client=$contextName usage=ASSISTANT gain=TRANSIENT_MAY_DUCK result=$result")
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            abandon()
            return false
        }
        return true
    }

    private val contextName = context.packageName

    fun abandon() {
        val request = current ?: return
        current = null // Ignore already queued callbacks for this lease.
        try {
            val result = manager.abandonAudioFocusRequest(request)
            Log.i(TAG, "client=$contextName abandon=$result")
        } catch (error: RuntimeException) {
            Log.w(TAG, "abandon failed", error)
        }
    }

    private companion object { const val TAG = "ChanceRideAudioFocus" }
}
