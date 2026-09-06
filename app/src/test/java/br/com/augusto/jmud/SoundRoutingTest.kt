package br.com.augusto.jmud

import br.com.augusto.jmud.util.SoundRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundRoutingTest {

    @Test
    fun shortSoundStaysOnSoundPool() {
        assertFalse(SoundRouting.needsLongPlayer(1_500_000L, 44100, 2))
    }

    @Test
    fun eightSecondStereoSoundNeedsLongPlayer() {
        assertTrue(SoundRouting.needsLongPlayer(8_000_000L, 44100, 2))
    }

    @Test
    fun fifteenSecondMonoSoundNeedsLongPlayer() {
        assertTrue(SoundRouting.needsLongPlayer(15_000_000L, 44100, 1))
    }

    @Test
    fun shortMonoSoundAtLowRateStaysOnSoundPool() {
        assertFalse(SoundRouting.needsLongPlayer(8_000_000L, 22050, 1))
    }

    @Test
    fun pcmBytesFollowsDurationRateAndChannels() {
        assertEquals(44100L * 2 * 2, SoundRouting.pcmBytes(1_000_000L, 44100, 2))
    }

    @Test
    fun unknownDurationFallsBackToLongPlayer() {
        assertTrue(SoundRouting.needsLongPlayer(0L, 44100, 2))
        assertTrue(SoundRouting.needsLongPlayer(-1L, 44100, 2))
    }

    @Test
    fun invalidFormatValuesFallBackToLongPlayer() {
        assertTrue(SoundRouting.needsLongPlayer(5_000_000L, 0, 2))
        assertTrue(SoundRouting.needsLongPlayer(5_000_000L, 44100, 0))
    }

    @Test
    fun fileSizeFallbackUsesSameLimit() {
        assertFalse(SoundRouting.needsLongPlayerForFileSize(120_000L))
        assertTrue(SoundRouting.needsLongPlayerForFileSize(1_400_000L))
    }
}
