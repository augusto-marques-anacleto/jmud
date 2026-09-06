package br.com.augusto.jmud.util

import kotlin.math.abs

enum class TiltZone {
    NEUTRAL,
    FORWARD,
    BACKWARD,
    RIGHT,
    LEFT
}

object TiltZones {

    const val DEFAULT_TRIGGER_DEGREES = 25
    const val DEFAULT_CONFIRM_MS = 400

    val TRIGGER_OPTIONS = listOf(35, 25, 15)
    val CONFIRM_OPTIONS = listOf(200, 400, 800, 1200)

    val MAPPABLE_ZONES = listOf(TiltZone.FORWARD, TiltZone.BACKWARD, TiltZone.RIGHT, TiltZone.LEFT)

    fun releaseDegreesFor(triggerDegrees: Int): Float =
        (triggerDegrees * RELEASE_RATIO).coerceAtMost(triggerDegrees - 1.0f).coerceAtLeast(1.0f)

    fun resolve(
        pitchDelta: Float,
        rollDelta: Float,
        current: TiltZone,
        triggerDegrees: Int
    ): TiltZone {
        val trigger = triggerDegrees.toFloat()
        val release = releaseDegreesFor(triggerDegrees)
        val absPitch = abs(pitchDelta)
        val absRoll = abs(rollDelta)

        if (current != TiltZone.NEUTRAL) {
            return if (absPitch < release && absRoll < release) TiltZone.NEUTRAL else current
        }

        if (absPitch < trigger && absRoll < trigger) return TiltZone.NEUTRAL

        return if (absPitch >= absRoll) {
            if (pitchDelta > 0f) TiltZone.FORWARD else TiltZone.BACKWARD
        } else {
            if (rollDelta > 0f) TiltZone.RIGHT else TiltZone.LEFT
        }
    }

    private const val RELEASE_RATIO = 0.4f
}
