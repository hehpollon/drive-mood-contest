package io.github.hehpollon.drivemood.data

import android.content.Context
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

fun createVoiceGuide(context: Context): VoiceGuide = PleosVoiceGuide(context.applicationContext, AndroidVoiceGuide(context))

/**
 * Pleos TextToSpeech SDK in on-device mode: no network or app credentials are needed.
 * The vehicle speech service plays the audio, so this class does not hold audio focus.
 * Falls back to the device engine when Korean on-device speech is not available.
 */
private class PleosVoiceGuide(context: Context, private val fallback: VoiceGuide) : VoiceGuide {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var speaking = false
    override val available get() = ready || fallback.available
    override val description get() = if (ready) "Pleos TextToSpeech SDK로 안내해요." else fallback.description

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() { speaking = true }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { speaking = false }
        override fun onStop() { speaking = false }
        override fun onError(errorType: ErrorType) {
            speaking = false
            Log.w(TAG, "speech error: $errorType")
            if (errorType in engineErrors) ready = false
        }
    }

    init {
        // Any SDK failure leaves the device engine in charge; speech must never stop the app.
        runCatching {
            val engine = TextToSpeech(context, Mode.ON_DEVICE)
            tts = engine
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    // Listeners and locale are accepted only after initialization completes.
                    runCatching { engine.addEventListener(events); engine.setLocale(Locale.KOREA) }
                        .onSuccess { ready = true; Log.i(TAG, "Pleos speech ready") }
                        .onFailure { Log.w(TAG, "Pleos speech setup failed", it) }
                }
                override fun onFailure(errorType: ErrorType) { Log.w(TAG, "initialize failed: $errorType") }
            })
        }.onFailure { Log.w(TAG, "Pleos speech unavailable", it) }
    }

    override fun speak(message: String) {
        val engine = tts
        if (!ready || engine == null) { fallback.speak(message); return }
        // The newest guidance replaces an unfinished one, as with the device engine's flush.
        runCatching { if (speaking) engine.stop(); engine.speak(message, true) }
            .onFailure { Log.w(TAG, "speak failed", it) }
    }

    override fun stop() {
        if (ready) runCatching { tts?.stop() }
        fallback.stop()
    }

    private companion object {
        const val TAG = "DriveMoodVoice"
        // Errors that mean this vehicle cannot speak Korean on-device; other errors are per request.
        val engineErrors = setOf(ErrorType.UNAVAILABLE_LANGUAGE, ErrorType.ON_DEVICE_ENGINE_ERROR,
            ErrorType.PERMISSION_DENIED, ErrorType.NOT_SUPPORTED_API)
    }
}
