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
        override fun applyGain(gainMillibels: Int): Int { check(!fail); gain = gainMillibels; return gain }
        override fun release() { released = true }
    }

    private fun ready(boost: TvBoostController) {
        boost.updateAudioSession(7, true)
        boost.setReady(true)
        boost.setActive(true)
    }

    @Test
    fun noSavedTargetStartsAtZeroAndNoEffectExistsOutsideReadyForeground() {
        val effects = mutableListOf<Effect>()
        val boost = TvBoostController { Effect().also(effects::add) }
        boost.updateAudioSession(7, true)
        assertTrue(effects.isEmpty())
        assertFalse(boost.adjust(boost.generation, 50))
        boost.setReady(true)
        assertTrue(effects.isEmpty())
        boost.setActive(true)
        assertEquals(0, boost.gainMillibels)
        assertTrue(boost.adjust(boost.generation, 50))
        assertEquals(50, boost.gainMillibels)
        boost.setActive(false)
        assertEquals(0, boost.gainMillibels)
        assertEquals(50, boost.targetMillibels)
        assertTrue(effects.last().released)
    }

    @Test
    fun halfDecibelStepsClampAtTwentyAndZero() {
        val boost = TvBoostController { Effect() }
        ready(boost)
        repeat(45) { assertTrue(boost.adjust(boost.generation, 50)) }
        assertEquals(2000, boost.targetMillibels)
        boost.adjust(boost.generation, -50)
        assertEquals(1950, boost.gainMillibels)
        boost.adjust(boost.generation, 9999)
        assertEquals(2000, boost.gainMillibels)
        repeat(45) { boost.adjust(boost.generation, -50) }
        assertEquals(0, boost.gainMillibels)
        assertEquals(0, boost.targetMillibels)
    }

    @Test
    fun sessionReplacementReleasesOldEffectAndRestoresChosenTarget() {
        val effects = mutableListOf<Effect>()
        val boost = TvBoostController { Effect().also(effects::add) }
        ready(boost)
        boost.adjust(boost.generation, 50)
        val old = boost.generation
        boost.updateAudioSession(8, true)
        assertTrue(effects[0].released)
        assertEquals(0, effects[0].gain)
        assertEquals(50, effects[1].gain)
        assertFalse(boost.adjust(old, 50))
        assertEquals(50, boost.targetMillibels)
        boost.close()
        assertEquals(0, effects[1].gain)
        assertTrue(effects[1].released)
        assertEquals(50, boost.targetMillibels)
    }

    @Test
    fun movieInvalidationPreservesTargetButRejectsStaleQueuedPress() {
        val boost = TvBoostController { Effect() }
        ready(boost)
        boost.adjust(boost.generation, 50)
        val old = boost.generation
        boost.invalidatePlayback()
        assertEquals(0, boost.gainMillibels)
        assertEquals(50, boost.targetMillibels)
        assertFalse(boost.adjust(old, 50))
        boost.setReady(true)
        assertEquals(50, boost.gainMillibels)
        assertFalse(boost.adjust(old, 50))
        assertTrue(boost.adjust(boost.generation, 50))
        assertEquals(100, boost.targetMillibels)
    }

    @Test
    fun backgroundReleasesEffectAndResumeRestoresWithoutChangingTarget() {
        val effects = mutableListOf<Effect>()
        val boost = TvBoostController { Effect().also(effects::add) }
        boost.restoreTarget(1250)
        assertTrue(effects.isEmpty())
        ready(boost)
        val old = boost.generation
        boost.setActive(false)
        assertEquals(0, boost.gainMillibels)
        assertEquals(1250, boost.targetMillibels)
        assertTrue(effects.last().released)
        boost.setActive(true)
        assertEquals(1250, boost.gainMillibels)
        assertFalse(boost.adjust(old, 50))
    }

    @Test
    fun missingControlOrFailedEffectKeepsSelectedTargetButReportsNoAppliedGain() {
        val effect = Effect()
        val boost = TvBoostController { effect }
        ready(boost)
        effect.hasControl = false
        assertTrue(boost.adjust(boost.generation, 50))
        assertEquals(50, boost.targetMillibels)
        assertEquals(0, boost.gainMillibels)
        assertFalse(boost.isSupported)
        effect.hasControl = true
        effect.fail = true
        boost.adjust(boost.generation, 50)
        assertEquals(100, boost.targetMillibels)
        assertEquals(0, boost.gainMillibels)
        assertTrue(effect.released)
    }

    @Test
    fun savedValuesAreClampedAndAppliedOnlyWhenAnActiveSessionIsReady() {
        val boost = TvBoostController { Effect() }
        boost.restoreTarget(Int.MAX_VALUE)
        assertEquals(2000, boost.targetMillibels)
        assertEquals(0, boost.gainMillibels)
        ready(boost)
        assertEquals(2000, boost.gainMillibels)
        boost.restoreTarget(Int.MIN_VALUE)
        assertEquals(0, boost.targetMillibels)
        assertEquals(0, boost.gainMillibels)
    }

    @Test
    fun legacyExplicitResetInvalidatesOlderRequestsButIsNotBoundToAColorHold() {
        val boost = TvBoostController { Effect() }
        ready(boost)
        boost.adjust(boost.generation, 50)
        val old = boost.generation
        boost.reset()
        assertEquals(0, boost.targetMillibels)
        assertFalse(boost.adjust(old, 50))
    }
}
