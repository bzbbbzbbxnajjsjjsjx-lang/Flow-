package com.akis.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.akis.app.domain.SpeechChunks
import java.util.Locale

/**
 * Reads paragraphs aloud with the phone's own speech engine. Akış itself makes no network request
 * for this; one utterance per paragraph lets it pause, resume and change speed at a paragraph.
 */
@Suppress("TooManyFunctions")
class SpeechController(context: Context) {
    var playing by mutableStateOf(false)
        private set
    var index by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var rate by mutableFloatStateOf(1f)
        private set

    /** 0..1: how much of the text has been read, by characters, so it starts at 0 and grows evenly. */
    var progress by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Minutes until the reading pauses itself; 0 when no timer runs. */
    var sleepMinutes by mutableIntStateOf(0)
        private set

    private var ready = false
    private var queued: Runnable? = null
    private var parts: List<String> = emptyList()
    private var generation = 0
    private var failures = 0
    private var lastCode = 0
    private var offsets = IntArray(0)
    private var totalChars = 1
    private val main = Handler(Looper.getMainLooper())
    private val engine: TextToSpeech = TextToSpeech(context.applicationContext) { status -> main.post { onReady(status) } }

    init {
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    main.post { if (current(utteranceId) != null) failures = 0 }
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    main.post { current(utteranceId)?.let { progress = ratio(offsets[it] + start) } }
                }

                override fun onDone(utteranceId: String?) {
                    main.post { current(utteranceId)?.let { speakParagraph(it + 1) } }
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) = onError(utteranceId, -1)

                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post {
                        lastCode = errorCode
                        current(utteranceId)?.let(::skipFailed)
                    }
                }

                /** Someone else (another app, the system) stopped the engine: pause here so the user can resume. */
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    main.post { if (interrupted && current(utteranceId) != null) playing = false }
                }
            },
        )
    }

    /** The paragraph an utterance id belongs to, or null when it comes from an earlier run (stopped, restarted). */
    private fun current(utteranceId: String?): Int? {
        val match = utteranceId?.let { id.matchEntire(it) } ?: return null
        return match.groupValues[2].toIntOrNull()?.takeIf { playing && match.groupValues[1].toIntOrNull() == generation }
    }

    fun start(paragraphs: List<String>) {
        parts = SpeechChunks.split(paragraphs.filter { it.isNotBlank() })
        total = parts.size
        offsets = IntArray(parts.size).also { o -> var sum = 0; parts.forEachIndexed { i, p -> o[i] = sum; sum += p.length } }
        totalChars = parts.sumOf { it.length }.coerceAtLeast(1)
        progress = 0f
        speakFrom(0)
    }

    /** Cycles the sleep timer: off, 15, 30 minutes. */
    fun cycleSleep() {
        main.removeCallbacks(sleepStop)
        sleepMinutes = when (sleepMinutes) {
            0 -> SLEEP_SHORT
            SLEEP_SHORT -> SLEEP_LONG
            else -> 0
        }
        if (sleepMinutes > 0) main.postDelayed(sleepStop, sleepMinutes * MINUTE_MS)
    }

    private val sleepStop = Runnable {
        sleepMinutes = 0
        pause()
    }

    fun pause() {
        generation++
        engine.stop()
        playing = false
    }

    fun resume() = speakFrom(index)

    fun stop() {
        generation++
        engine.stop()
        playing = false
        index = 0
        progress = 0f
    }

    fun changeRate(value: Float) {
        rate = value
        if (playing) speakFrom(index)
    }

    fun shutdown() {
        main.removeCallbacks(sleepStop)
        generation++
        engine.stop()
        engine.shutdown()
    }

    private fun speakFrom(from: Int) {
        generation++
        failures = 0
        playing = error == null && parts.isNotEmpty()
        if (!ready) {
            queued = Runnable { speakFrom(from) }
            return
        }
        speakParagraph(from)
    }

    /** One paragraph at a time: the next one starts only when the engine reports this one done. */
    private fun speakParagraph(i: Int) {
        if (i >= parts.size) {
            playing = false
            index = 0
            progress = 0f
            return
        }
        index = i
        progress = ratio(offsets[i])
        engine.setSpeechRate(rate)
        if (engine.speak(parts[i], TextToSpeech.QUEUE_FLUSH, null, "g${generation}p$i") == TextToSpeech.ERROR) skipFailed(i)
    }

    private fun ratio(chars: Int): Float = (chars.toFloat() / totalChars).coerceIn(0f, 1f)

    /** A paragraph the engine could not read is skipped; several in a row mean the engine is broken. */
    private fun skipFailed(i: Int) {
        failures++
        if (failures >= MAX_FAILURES) {
            generation++
            playing = false
            error = "Sesli okuma yarıda kesildi (hata kodu $lastCode)"
        } else {
            speakParagraph(i + 1)
        }
    }

    private fun onReady(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            error = "Sesli okuyucu başlatılamadı"
            playing = false
            return
        }
        val result = engine.setLanguage(Locale.forLanguageTag("tr-TR"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            error = "Telefonda Türkçe ses verisi yok"
            playing = false
        } else {
            engine.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            ready = true
            queued?.run()
            queued = null
        }
    }

    private companion object {
        const val MAX_FAILURES = 3
        const val SLEEP_SHORT = 15
        const val SLEEP_LONG = 30
        const val MINUTE_MS = 60_000L
        val id = Regex("g(\\d+)p(\\d+)")
    }
}
