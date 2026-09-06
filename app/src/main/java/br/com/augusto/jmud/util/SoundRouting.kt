package br.com.augusto.jmud.util

object SoundRouting {

    const val MAX_SOUND_POOL_PCM_BYTES = 900_000L

    private const val BYTES_PER_SAMPLE = 2
    private const val MICROSECONDS_PER_SECOND = 1_000_000.0

    fun pcmBytes(durationUs: Long, sampleRate: Int, channels: Int): Long {
        if (durationUs <= 0L || sampleRate <= 0 || channels <= 0) return Long.MAX_VALUE
        val seconds = durationUs / MICROSECONDS_PER_SECOND
        val bytes = seconds * sampleRate * channels * BYTES_PER_SAMPLE
        return if (bytes >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else bytes.toLong()
    }

    fun needsLongPlayer(durationUs: Long, sampleRate: Int, channels: Int): Boolean =
        pcmBytes(durationUs, sampleRate, channels) >= MAX_SOUND_POOL_PCM_BYTES

    fun needsLongPlayerForFileSize(fileBytes: Long): Boolean =
        fileBytes >= MAX_SOUND_POOL_PCM_BYTES
}
