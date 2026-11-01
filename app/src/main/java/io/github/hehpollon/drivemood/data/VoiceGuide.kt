package io.github.hehpollon.drivemood.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceGuide(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var ready = false
    private var engine: TextToSpeech? = null
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { change -> if (change <= AudioManager.AUDIOFOCUS_LOSS) stop() }.build()
    var available = false
        private set

    init {
        engine = TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val status = engine?.setLanguage(Locale.KOREAN)
                ready = status != null && status >= TextToSpeech.LANG_AVAILABLE
                available = ready
            }
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { if (utteranceId == "drive-mood-voice") audio.abandonAudioFocusRequest(focus) }
            @Deprecated("Platform callback")
            override fun onError(utteranceId: String?) { audio.abandonAudioFocusRequest(focus) }
        })
    }

    fun speak(message: String) {
        if (!ready || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        engine?.playSilentUtterance(300, TextToSpeech.QUEUE_FLUSH, "lead-silence")
        engine?.speak(message, TextToSpeech.QUEUE_ADD, Bundle(), "drive-mood-voice")
    }

    fun stop() { engine?.stop(); audio.abandonAudioFocusRequest(focus) }
}
