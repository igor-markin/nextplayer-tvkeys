package dev.markin.tvhelper

import dev.markin.tvhelper.LongPressTracker.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongPressTrackerTest {

    private val tracker = LongPressTracker(thresholdMs = 600)

    @Test
    fun shortPressPassesThrough() {
        assertEquals(Action.PASS, tracker.onDown(repeatCount = 0, downTime = 0, eventTime = 0))
        assertFalse(tracker.onUp())
    }

    @Test
    fun repeatsBeforeThresholdAreSwallowed() {
        tracker.onDown(repeatCount = 0, downTime = 0, eventTime = 0)
        assertEquals(Action.CONSUME, tracker.onDown(repeatCount = 1, downTime = 0, eventTime = 500))
        assertFalse(tracker.onUp())
    }

    @Test
    fun repeatAfterThresholdFiresOnceAndSwallowsRelease() {
        tracker.onDown(repeatCount = 0, downTime = 0, eventTime = 0)
        assertEquals(Action.FIRE, tracker.onDown(repeatCount = 2, downTime = 0, eventTime = 650))
        assertEquals(Action.CONSUME, tracker.onDown(repeatCount = 3, downTime = 0, eventTime = 700))
        assertFalse(tracker.onTimeout())
        assertTrue(tracker.onUp())
    }

    @Test
    fun timeoutFiresWhileHeld() {
        tracker.onDown(repeatCount = 0, downTime = 0, eventTime = 0)
        assertTrue(tracker.onTimeout())
        assertEquals(Action.CONSUME, tracker.onDown(repeatCount = 1, downTime = 0, eventTime = 700))
        assertTrue(tracker.onUp())
    }

    @Test
    fun timeoutAfterReleaseDoesNothing() {
        tracker.onDown(repeatCount = 0, downTime = 0, eventTime = 0)
        tracker.onUp()
        assertFalse(tracker.onTimeout())
    }

    @Test
    fun nextPressStartsClean() {
        tracker.onDown(repeatCount = 0, downTime = 0, eventTime = 0)
        tracker.onTimeout()
        tracker.onUp()
        assertEquals(Action.PASS, tracker.onDown(repeatCount = 0, downTime = 1000, eventTime = 1000))
        assertFalse(tracker.onUp())
    }
}
