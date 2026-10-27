package dev.anilbeesetti.nextplayer.feature.player.service

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TvBoostPreferencesTest {
    @Test
    fun noSavedValueStartsAtZeroAndChosenValueSurvivesANewStoreInstance() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("boost-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        assertEquals(0, TvBoostPreferences(preferences).load())
        assertTrue(TvBoostPreferences(preferences).save(650))
        assertEquals(650, TvBoostPreferences(preferences).load())
    }

    @Test
    fun damagedOrOutOfRangeValuesCannotRestoreAnUnboundedGain() {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("boost-corrupt", Context.MODE_PRIVATE)
        val store = TvBoostPreferences(preferences)
        preferences.edit().putString(TvBoostPreferences.KEY, "unknown").commit()
        assertEquals(0, store.load())
        preferences.edit().putInt(TvBoostPreferences.KEY, -50).commit()
        assertEquals(0, store.load())
        preferences.edit().putLong(TvBoostPreferences.KEY, Long.MAX_VALUE).commit()
        assertEquals(2000, store.load())
        assertTrue(store.save(Int.MAX_VALUE))
        assertEquals(2000, store.load())
    }

    @Test
    fun releasingRuntimeEffectDoesNotErasePersistedSelection() {
        val context = RuntimeEnvironment.getApplication()
        val store = TvBoostPreferences(context)
        assertTrue(store.save(1100))
        val boost = TvBoostController {
            object : TvBoostEffect {
                override val hasControl = true
                override fun applyGain(gainMillibels: Int) = gainMillibels
                override fun release() = Unit
            }
        }
        boost.restoreTarget(store.load())
        boost.updateAudioSession(7, true)
        boost.setReady(true)
        boost.setActive(true)
        assertEquals(1100, boost.gainMillibels)
        boost.setActive(false)
        boost.close()
        assertEquals(0, boost.gainMillibels)
        assertEquals(1100, TvBoostPreferences(context).load())
    }
}
