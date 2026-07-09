package br.com.augusto.jmud.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var speechRate = 1.0f
    private var pitch = 1.0f
    private var volume = 1.0f
    private var voiceName = ""

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()
            val result = tts?.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
                applyConfig()
            }
        }
    }

    private fun applyConfig() {
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)
        if (voiceName.isNotBlank()) {
            val voice = try {
                tts?.voices?.firstOrNull { it.name == voiceName }
            } catch (e: Exception) {
                null
            }
            if (voice != null) {
                tts?.voice = voice
            }
        }
    }

    fun configure(rate: Float, pitchValue: Float, volumeValue: Float, voice: String) {
        speechRate = rate
        pitch = pitchValue
        volume = volumeValue
        voiceName = voice
        if (isInitialized) {
            applyConfig()
        }
    }

    fun setEngine(engine: String) {
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
        tts = if (engine.isBlank()) {
            TextToSpeech(context, this)
        } else {
            TextToSpeech(context, this, engine)
        }
    }

    fun getEngines(): List<Pair<String, String>> =
        tts?.engines?.map { it.name to it.label } ?: emptyList()

    fun getVoices(): List<String> {
        val all = try {
            tts?.voices?.toList()
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val portuguese = all.filter { it.locale.language == "pt" }
        val chosen = if (portuguese.isNotEmpty()) portuguese else all
        return chosen.map { it.name }.distinct().sorted()
    }

    fun speak(text: String, flush: Boolean) {
        if (!isInitialized) return
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        tts?.speak(text, queueMode, params, "cmud_tts")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
