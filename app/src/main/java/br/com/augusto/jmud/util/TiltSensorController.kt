package br.com.augusto.jmud.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.atan2
import kotlin.math.hypot

class TiltSensorController(
    context: Context,
    private val listener: Listener
) : SensorEventListener {

    interface Listener {
        fun onZoneEntered(zone: TiltZone)
        fun onZoneConfirmed(zone: TiltZone)
        fun onZoneRepeated(zone: TiltZone)
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val sensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val smoothed = FloatArray(3)
    private var hasSmoothed = false

    private var referencePitch = 0f
    private var referenceRoll = 0f
    private var hasReference = false

    private var currentZone = TiltZone.NEUTRAL
    private var zoneEnteredAt = 0L
    private var zoneConfirmed = false

    private var triggerDegrees = TiltZones.DEFAULT_TRIGGER_DEGREES
    private var confirmMs = TiltZones.DEFAULT_CONFIRM_MS
    private var repeatMs = 0
    private var lastRepeatAt = 0L

    private var running = false

    fun isAvailable(): Boolean = sensor != null

    fun start(triggerDegrees: Int, confirmMs: Int, repeatMs: Int): Boolean {
        val manager = sensorManager ?: return false
        val target = sensor ?: return false
        if (running) stop()

        this.triggerDegrees = triggerDegrees
        this.confirmMs = confirmMs
        this.repeatMs = repeatMs
        hasSmoothed = false
        hasReference = false
        currentZone = TiltZone.NEUTRAL
        zoneConfirmed = false

        running = manager.registerListener(this, target, SensorManager.SENSOR_DELAY_UI)
        return running
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager?.unregisterListener(this)
    }

    fun recalibrate() {
        hasReference = false
        currentZone = TiltZone.NEUTRAL
        zoneConfirmed = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return

        val values = if (event.sensor.type == Sensor.TYPE_GRAVITY) {
            event.values
        } else {
            applyLowPass(event.values)
        }

        val x = values[0]
        val y = values[1]
        val z = values[2]

        val pitch = Math.toDegrees(atan2(-y.toDouble(), hypot(x.toDouble(), z.toDouble()))).toFloat()
        val roll = Math.toDegrees(atan2(x.toDouble(), hypot(y.toDouble(), z.toDouble()))).toFloat()

        if (!hasReference) {
            referencePitch = pitch
            referenceRoll = roll
            hasReference = true
            return
        }

        val pitchDelta = pitch - referencePitch
        val rollDelta = roll - referenceRoll

        val zone = TiltZones.resolve(pitchDelta, rollDelta, currentZone, triggerDegrees)

        if (zone == TiltZone.NEUTRAL) {
            referencePitch += (pitch - referencePitch) * REFERENCE_DRIFT
            referenceRoll += (roll - referenceRoll) * REFERENCE_DRIFT
        }

        if (zone != currentZone) {
            currentZone = zone
            zoneConfirmed = false
            if (zone != TiltZone.NEUTRAL) {
                zoneEnteredAt = SystemClock.elapsedRealtime()
                listener.onZoneEntered(zone)
            }
            return
        }

        if (zone == TiltZone.NEUTRAL) return

        val now = SystemClock.elapsedRealtime()
        if (!zoneConfirmed && now - zoneEnteredAt >= confirmMs) {
            zoneConfirmed = true
            lastRepeatAt = now
            listener.onZoneConfirmed(zone)
            return
        }

        if (zoneConfirmed && repeatMs > 0 && now - lastRepeatAt >= repeatMs) {
            lastRepeatAt = now
            listener.onZoneRepeated(zone)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun applyLowPass(values: FloatArray): FloatArray {
        if (!hasSmoothed) {
            values.copyInto(smoothed, 0, 0, 3)
            hasSmoothed = true
            return smoothed
        }
        for (i in 0 until 3) {
            smoothed[i] = smoothed[i] * SMOOTHING + values[i] * (1f - SMOOTHING)
        }
        return smoothed
    }

    private companion object {
        const val SMOOTHING = 0.8f
        const val REFERENCE_DRIFT = 0.005f
    }
}
