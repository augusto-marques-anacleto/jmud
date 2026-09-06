package br.com.augusto.jmud.util

import android.media.AudioManager
import android.media.ToneGenerator

class ToneFeedback {
    private var generator: ToneGenerator? = null

    fun beep() {
        try {
            val tone = generator ?: ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME).also { generator = it }
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, DURATION_MS)
        } catch (e: Exception) {
            generator = null
        }
    }

    fun release() {
        try {
            generator?.release()
        } catch (e: Exception) {
        }
        generator = null
    }

    private companion object {
        const val VOLUME = 70
        const val DURATION_MS = 90
    }
}
