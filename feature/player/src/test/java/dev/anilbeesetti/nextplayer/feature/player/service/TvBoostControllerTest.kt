package dev.anilbeesetti.nextplayer.feature.player.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvBoostControllerTest {
    private class Effect : TvBoostEffect {
        override var hasControl = true
        var gain = 0
        var released = false
        var fail = false
        override fun applyGain(gainMillibels: Int): Int {
            check(!fail)
            gain = gainMillibels
            return gain
        }
        override fun release() { released = true }
    }

    @Test
    fun startsDisabledAndOnlyForegroundCanIncrease() {
        val effect = Effect()
        val boost = TvBoostController { effect }
        boost.updateAudioSession(7, true)
        assertEquals(0, effect.gain)
        assertFalse(boost.adjust(50))
        boost.setActive(true)
        assertTrue(boost.adjust(50))
        assertEquals(50, effect.gain)
        boost.setActive(false)
        assertEquals(0, effect.gain)
        assertFalse(boost.adjust(50))
    }

    @Test
    fun halfDecibelStepsAreBoundedAtTwentyAndResetToZero() {
        val boost = TvBoostController { Effect() }
        boost.updateAudioSession(7, true)
        boost.setActive(true)
        repeat(45) { assertTrue(boost.adjust(50)) }
        assertEquals(2000, boost.gainMillibels)
        boost.adjust(-50)
        assertEquals(1950, boost.gainMillibels)
        boost.adjust(9999)
        assertEquals(2000, boost.gainMillibels)
        boost.adjust(0, reset = true)
        assertEquals(0, boost.gainMillibels)
        repeat(3) { boost.adjust(-50) }
        assertEquals(0, boost.gainMillibels)
    }

    @Test
    fun sessionChangesDisableAndReleasePreviousEffect() {
        val effects = mutableListOf<Effect>()
        val boost = TvBoostController { Effect().also { effects.add(it) } }
        boost.setActive(true)
        boost.updateAudioSession(7, true)
        boost.adjust(50)
        boost.updateAudioSession(8, true)
        assertTrue(effects[0].released)
        assertEquals(0, effects[0].gain)
        assertEquals(0, effects[1].gain)
        assertEquals(0, boost.gainMillibels)
        boost.adjust(50)
        boost.close()
        assertEquals(0, effects[1].gain)
        assertTrue(effects[1].released)
        assertFalse(boost.active)
    }

    @Test
    fun missingControlOrFailedEffectNeverReportsPositiveGain() {
        val effect = Effect()
        val boost = TvBoostController { effect }
        boost.setActive(true)
        boost.updateAudioSession(7, true)
        effect.hasControl = false
        assertFalse(boost.adjust(50))
        effect.hasControl = true
        effect.fail = true
        assertFalse(boost.adjust(50))
        assertEquals(0, boost.gainMillibels)
        assertTrue(effect.released)
        assertFalse(boost.isSupported)
    }
}
