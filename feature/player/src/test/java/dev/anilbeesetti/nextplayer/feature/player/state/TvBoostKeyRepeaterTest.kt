package dev.anilbeesetti.nextplayer.feature.player.state

import dev.anilbeesetti.nextplayer.feature.player.service.TvBoostController
import dev.anilbeesetti.nextplayer.feature.player.service.TvBoostEffect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvBoostKeyRepeaterTest {
    @Test
    fun holdWaitsThenStepsAtControlledIntervalsAndStopsOnRelease() = runTest {
        val steps = mutableListOf<TvColorAction>()
        val repeater = TvBoostKeyRepeater(backgroundScope, { TvBoostContext(1, "movie") }, steps::add)
        repeater.start(183, TvColorAction.INCREASE)
        runCurrent()
        advanceTimeBy(399)
        runCurrent()
        assertEquals(0, steps.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, steps.size)
        advanceTimeBy(600)
        runCurrent()
        assertEquals(4, steps.size)
        repeater.release(183)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(4, steps.size)
    }

    @Test
    fun mediaGenerationChangeStopsHeldKeyWithoutRebindingToTheNewMovie() = runTest {
        var context = TvBoostContext(1, "movie-a")
        var count = 0
        val repeater = TvBoostKeyRepeater(backgroundScope, { context }) { count++ }
        repeater.start(183, TvColorAction.INCREASE)
        runCurrent()
        context = TvBoostContext(2, "movie-b")
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(0, count)
    }

    @Test
    fun cancelOrLifecycleStopPreventsFutureSteps() = runTest {
        var count = 0
        val repeater = TvBoostKeyRepeater(backgroundScope, { TvBoostContext(1, "movie") }) { count++ }
        repeater.start(184, TvColorAction.DECREASE)
        runCurrent()
        repeater.stop()
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(0, count)
    }

    @Test
    fun heldPlusAndMinusReachBoundsWithoutOvershoot() = runTest {
        val boost = TvBoostController {
            object : TvBoostEffect {
                override val hasControl = true
                override fun applyGain(gainMillibels: Int) = gainMillibels
                override fun release() = Unit
            }
        }.apply { updateAudioSession(7, true); setReady(true); setActive(true) }
        val repeater = TvBoostKeyRepeater(backgroundScope, { TvBoostContext(1, "movie") }) {
            boost.adjust(boost.generation, if (it == TvColorAction.INCREASE) 50 else -50)
        }
        repeater.start(183, TvColorAction.INCREASE)
        runCurrent()
        advanceTimeBy(10000)
        runCurrent()
        assertEquals(2000, boost.targetMillibels)
        repeater.release(183)
        repeater.start(184, TvColorAction.DECREASE)
        runCurrent()
        advanceTimeBy(10000)
        runCurrent()
        assertEquals(0, boost.targetMillibels)
    }
}
