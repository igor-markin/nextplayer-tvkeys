package dev.anilbeesetti.nextplayer.feature.player.service

import android.os.Bundle
import android.os.Looper
import android.os.Process
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TvBoostCommandsTest {
    @Test
    fun serviceRejectsDelayedCommandAfterResetAndAcceptsFreshGeneration() = withSession { _, session, callback, boost ->
        val delayed = args(boost.generation, "movie-a")
        boost.reset()
        assertEquals(SessionResult.RESULT_ERROR_INVALID_STATE, send(session, callback, delayed).resultCode)
        assertEquals(0, boost.gainMillibels)
        val result = send(session, callback, args(boost.generation, "movie-a"))
        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
        assertEquals(50, result.extras.getInt(CustomCommands.LOUDNESS_GAIN_KEY))
        assertEquals(boost.generation, result.extras.getLong(CustomCommands.TV_BOOST_GENERATION_KEY))
        assertEquals("movie-a", result.extras.getString(CustomCommands.TV_BOOST_MEDIA_ID_KEY))
    }

    @Test
    fun serviceRejectsOldMovieEvenIfGenerationIsCurrentAndRejectsMissingToken() = withSession { player, session, callback, boost ->
        player.setMediaItem(MediaItem.Builder().setMediaId("movie-b").setUri("file:///unit/movie-b.mp4").build())
        assertEquals(SessionResult.RESULT_ERROR_INVALID_STATE, send(session, callback, args(boost.generation, "movie-a")).resultCode)
        val missingGeneration = args(boost.generation, "movie-b").apply { remove(CustomCommands.TV_BOOST_GENERATION_KEY) }
        assertEquals(SessionResult.RESULT_ERROR_INVALID_STATE, send(session, callback, missingGeneration).resultCode)
        assertEquals(0, boost.gainMillibels)
        assertEquals(SessionResult.RESULT_SUCCESS, send(session, callback, args(boost.generation, "movie-b")).resultCode)
    }

    @Test
    fun serviceRejectsAnotherUidAndAnEmptyPlayer() = withSession { player, session, callback, boost ->
        val request = args(boost.generation, "movie-a")
        assertEquals(SessionResult.RESULT_ERROR_PERMISSION_DENIED, send(session, callback, request, Process.myUid() + 1).resultCode)
        player.clearMediaItems()
        assertEquals(SessionResult.RESULT_ERROR_INVALID_STATE, send(session, callback, request).resultCode)
        assertEquals(0, boost.gainMillibels)
    }

    private fun args(generation: Long, mediaId: String) = Bundle().apply {
        putLong(CustomCommands.TV_BOOST_GENERATION_KEY, generation)
        putString(CustomCommands.TV_BOOST_MEDIA_ID_KEY, mediaId)
        putInt(CustomCommands.TV_BOOST_DELTA_KEY, 50)
    }

    private fun send(session: MediaSession, callback: MediaSession.Callback, args: Bundle, uid: Int = Process.myUid()): SessionResult {
        val controller = MediaSession.ControllerInfo.createTestOnlyControllerInfo("test", 0, uid, 0, 0, true, Bundle.EMPTY, true)
        val future = callback.onCustomCommand(session, controller, CustomCommands.ADJUST_TV_BOOST.sessionCommand, args)
        shadowOf(Looper.getMainLooper()).idle()
        return future.get()
    }

    private fun withSession(check: (ExoPlayer, MediaSession, MediaSession.Callback, TvBoostController) -> Unit) {
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        player.setMediaItem(MediaItem.Builder().setMediaId("movie-a").setUri("file:///unit/movie-a.mp4").build())
        val session = MediaSession.Builder(RuntimeEnvironment.getApplication(), player).build()
        val boost = TvBoostController {
            object : TvBoostEffect {
                override val hasControl = true
                override fun applyGain(gainMillibels: Int) = gainMillibels
                override fun release() = Unit
            }
        }.apply {
            updateAudioSession(7, true)
            setActive(true)
        }
        val service = PlayerService()
        PlayerService::class.java.getDeclaredField("mediaSession").apply { isAccessible = true }.set(service, session)
        PlayerService::class.java.getDeclaredField("tvBoost").apply { isAccessible = true }.set(service, boost)
        val callback = PlayerService::class.java.getDeclaredField("mediaSessionCallback").apply { isAccessible = true }.get(service) as MediaSession.Callback
        try {
            check(player, session, callback, boost)
        } finally {
            boost.close()
            session.release()
            player.release()
        }
    }
}
