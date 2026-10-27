package dev.anilbeesetti.nextplayer.feature.player.state

import android.os.Bundle
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import dev.anilbeesetti.nextplayer.feature.player.model.TvBoostStatus
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TvBoostStartupFeedbackTest {
    @Test
    fun foregroundStartupAndRestoredStatusDoNotDisplayAutomaticToast() {
        ShadowToast.reset()
        val context = RuntimeEnvironment.getApplication()
        val player = ExoPlayer.Builder(context).build()
        player.setMediaItem(MediaItem.Builder().setMediaId("movie").setUri("file:///unit/silent.mp4").build())
        val session = MediaSession.Builder(context, player).build()
        session.sessionExtras = Bundle().apply {
            putLong(CustomCommands.TV_BOOST_GENERATION_KEY, 1)
            putString(CustomCommands.TV_BOOST_MEDIA_ID_KEY, "movie")
        }
        val future = MediaController.Builder(context, session.token).buildAsync()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!future.isDone && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(1)
        }
        val controller = future.get(1, TimeUnit.SECONDS)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val state = TvBoostState(controller, context, scope)
        try {
            assertNotNull(controller.currentMediaItem)
            state.setActive(true)
            state.onStatus(TvBoostStatus(1, "movie", 1100, 1100, true, true))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(ShadowToast.getTextOfLatestToast())
        } finally {
            state.close()
            scope.cancel()
            controller.release()
            session.release()
            player.release()
        }
    }
}
