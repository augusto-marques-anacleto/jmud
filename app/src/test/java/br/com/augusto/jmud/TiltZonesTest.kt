package br.com.augusto.jmud

import br.com.augusto.jmud.util.TiltZone
import br.com.augusto.jmud.util.TiltZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiltZonesTest {

    private val trigger = 25

    @Test
    fun smallMovementsStayNeutral() {
        assertEquals(TiltZone.NEUTRAL, TiltZones.resolve(5f, -8f, TiltZone.NEUTRAL, trigger))
        assertEquals(TiltZone.NEUTRAL, TiltZones.resolve(-24f, 24f, TiltZone.NEUTRAL, trigger))
    }

    @Test
    fun crossingTheThresholdPicksTheDirection() {
        assertEquals(TiltZone.FORWARD, TiltZones.resolve(30f, 2f, TiltZone.NEUTRAL, trigger))
        assertEquals(TiltZone.BACKWARD, TiltZones.resolve(-30f, 2f, TiltZone.NEUTRAL, trigger))
        assertEquals(TiltZone.RIGHT, TiltZones.resolve(2f, 30f, TiltZone.NEUTRAL, trigger))
        assertEquals(TiltZone.LEFT, TiltZones.resolve(2f, -30f, TiltZone.NEUTRAL, trigger))
    }

    @Test
    fun diagonalMovementPicksTheDominantAxis() {
        assertEquals(TiltZone.FORWARD, TiltZones.resolve(40f, 30f, TiltZone.NEUTRAL, trigger))
        assertEquals(TiltZone.RIGHT, TiltZones.resolve(30f, 40f, TiltZone.NEUTRAL, trigger))
    }

    @Test
    fun zoneHoldsWhileStillTiltedSoItDoesNotRepeat() {
        assertEquals(TiltZone.FORWARD, TiltZones.resolve(30f, 0f, TiltZone.FORWARD, trigger))
        assertEquals(TiltZone.FORWARD, TiltZones.resolve(15f, 0f, TiltZone.FORWARD, trigger))
    }

    @Test
    fun zoneOnlyRearmsNearTheRestingPosition() {
        val release = TiltZones.releaseDegreesFor(trigger)
        assertTrue(release < trigger)
        assertEquals(TiltZone.FORWARD, TiltZones.resolve(release + 1f, 0f, TiltZone.FORWARD, trigger))
        assertEquals(TiltZone.NEUTRAL, TiltZones.resolve(release - 1f, 0f, TiltZone.FORWARD, trigger))
    }

    @Test
    fun anotherDirectionOnlyStartsAfterGoingBackToNeutral() {
        assertEquals(TiltZone.FORWARD, TiltZones.resolve(0f, 40f, TiltZone.FORWARD, trigger))
        assertEquals(TiltZone.RIGHT, TiltZones.resolve(0f, 40f, TiltZone.NEUTRAL, trigger))
    }

    @Test
    fun higherSensitivityNeedsLessTilt() {
        assertEquals(TiltZone.NEUTRAL, TiltZones.resolve(20f, 0f, TiltZone.NEUTRAL, 35))
        assertEquals(TiltZone.FORWARD, TiltZones.resolve(20f, 0f, TiltZone.NEUTRAL, 15))
    }

    @Test
    fun releaseNeverReachesTheTriggerAngle() {
        for (degrees in TiltZones.TRIGGER_OPTIONS) {
            assertTrue(TiltZones.releaseDegreesFor(degrees) < degrees)
            assertTrue(TiltZones.releaseDegreesFor(degrees) >= 1f)
        }
    }

    @Test
    fun everyMappableZoneIsADirection() {
        assertEquals(4, TiltZones.MAPPABLE_ZONES.size)
        assertTrue(!TiltZones.MAPPABLE_ZONES.contains(TiltZone.NEUTRAL))
    }
}
