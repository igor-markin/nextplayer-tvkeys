package dev.anilbeesetti.nextplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayBackKeyActionTest {
    @Test
    fun backDownIsConsumedSoFocusDoesNotLeaveTheMenu() {
        assertEquals(OverlayBackKeyAction.CONSUME, overlayBackKeyAction(isBack = true, isKeyUp = false))
    }

    @Test
    fun backUpClosesTheMenuInOnePress() {
        assertEquals(OverlayBackKeyAction.CLOSE, overlayBackKeyAction(isBack = true, isKeyUp = true))
    }

    @Test
    fun otherKeysReachTheMenu() {
        assertEquals(OverlayBackKeyAction.PASS, overlayBackKeyAction(isBack = false, isKeyUp = false))
        assertEquals(OverlayBackKeyAction.PASS, overlayBackKeyAction(isBack = false, isKeyUp = true))
    }
}
