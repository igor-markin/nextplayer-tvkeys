package dev.anilbeesetti.nextplayer.feature.player.state

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvBoostFeedbackTest {
    @Test
    fun startupOrRestoredLevelCannotShowWithoutUserInput() = runTest {
        var visible: String? = null
        val feedback = TvBoostFeedback(backgroundScope, { visible = it }, { visible = null })
        feedback.show("restored +11 dB")
        advanceTimeBy(5000)
        runCurrent()
        assertNull(visible)
    }

    @Test
    fun explicitInputShowsAndHidesAfterOneAndAHalfSeconds() = runTest {
        var visible: String? = null
        val feedback = TvBoostFeedback(backgroundScope, { visible = it }, { visible = null })
        feedback.onUserInteraction()
        feedback.show("+0.5 dB")
        advanceTimeBy(1499)
        runCurrent()
        assertEquals("+0.5 dB", visible)
        advanceTimeBy(1)
        runCurrent()
        assertNull(visible)
        feedback.show("late reply")
        assertNull(visible)
    }

    @Test
    fun lastPressOrKeyUpRenewsDeadline() = runTest {
        var visible: String? = null
        val feedback = TvBoostFeedback(backgroundScope, { visible = it }, { visible = null })
        feedback.onUserInteraction()
        feedback.show("+1 dB")
        advanceTimeBy(1000)
        feedback.onUserInteraction()
        advanceTimeBy(1499)
        runCurrent()
        assertEquals("+1 dB", visible)
        advanceTimeBy(1)
        runCurrent()
        assertNull(visible)
    }

    @Test
    fun holdKeepsFeedbackBrieflyUntilTheLastInteractionExpires() = runTest {
        var visible: String? = null
        val feedback = TvBoostFeedback(backgroundScope, { visible = it }, { visible = null })
        repeat(20) {
            feedback.onUserInteraction()
            feedback.show("step $it")
            advanceTimeBy(200)
            runCurrent()
        }
        feedback.onUserInteraction() // key-up
        advanceTimeBy(1500)
        runCurrent()
        assertNull(visible)
    }

    @Test
    fun openingMenuHidesImmediatelyAndClosingItDoesNotReviveOldFeedback() = runTest {
        var visible: String? = null
        val feedback = TvBoostFeedback(backgroundScope, { visible = it }, { visible = null })
        feedback.onUserInteraction()
        feedback.show("+2 dB")
        feedback.setBlocked(true)
        assertNull(visible)
        feedback.onUserInteraction()
        feedback.show("while menu open")
        assertNull(visible)
        feedback.setBlocked(false)
        feedback.show("late reply after closing menu")
        assertNull(visible)
        feedback.onUserInteraction()
        feedback.show("new explicit press")
        assertEquals("new explicit press", visible)
    }

    @Test
    fun lifecycleDismissAndLateRepliesCannotShowAgain() = runTest {
        var visible: String? = null
        val feedback = TvBoostFeedback(backgroundScope, { visible = it }, { visible = null })
        feedback.onUserInteraction()
        feedback.show("+3 dB")
        feedback.dismiss()
        feedback.show("old reply")
        advanceTimeBy(2000)
        runCurrent()
        assertNull(visible)
    }
}
