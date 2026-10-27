package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.FileProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.ui.R as uiR
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run only on the disposable TV emulator: silent media, no system volume writes. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TvKeysPlaybackTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun <T> main(block: () -> T): T {
        val value = AtomicReference<T>()
        instrumentation.runOnMainSync { value.set(block()) }
        return value.get()
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 20_000
        while (!condition() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
        assertTrue(condition())
    }

    private fun playbackIntent(name: String): Intent {
        val file = File(context.cacheDir, name)
        instrumentation.context.assets.open("tvkeys-silence.mp4").use { input -> file.outputStream().use(input::copyTo) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return Intent(Intent.ACTION_VIEW, uri, context, PlayerActivity::class.java)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun command(controller: MediaController, command: CustomCommands, args: Bundle = Bundle.EMPTY): SessionResult {
        val future = main { controller.sendCustomCommand(command.sessionCommand, args) }
        return future.get(10, TimeUnit.SECONDS)
    }

    private fun gain(controller: MediaController): Int = command(controller, CustomCommands.GET_LOUDNESS_GAIN)
        .extras.getInt(CustomCommands.LOUDNESS_GAIN_KEY)

    @Test
    fun colorsControlSessionGainAndSelectorsWithoutChangingSystemVolume() {
        assumeTrue(context.isTelevision)
        val audio = context.getSystemService(AudioManager::class.java)
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val scenario = ActivityScenario.launch<PlayerActivity>(playbackIntent("tvkeys-a.mp4"))
        var future: ListenableFuture<MediaController>? = null
        main {
            future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlayerService::class.java))).buildAsync()
        }
        val controller = future!!.get(20, TimeUnit.SECONDS)
        try {
            awaitCondition { main { controller.playbackState == Player.STATE_READY && controller.currentTracks.groups.size >= 3 } }
            awaitCondition {
                command(controller, CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED).extras
                    .getBoolean(CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED_KEY)
            }
            assertEquals(0, gain(controller))
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_PROG_GREEN)
            awaitCondition { gain(controller) == 50 }
            val greenDown = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(KeyEvent(greenDown, greenDown, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_GREEN, 0))
            repeat(4) { index ->
                instrumentation.sendKeySync(KeyEvent(greenDown, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_GREEN, index + 1))
            }
            instrumentation.sendKeySync(KeyEvent(greenDown, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PROG_GREEN, 0))
            awaitCondition { gain(controller) == 100 }
            assertEquals(originalVolume, audio.getStreamVolume(AudioManager.STREAM_MUSIC))

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_PROG_YELLOW)
            compose.waitForIdle()
            compose.onNodeWithText(context.getString(uiR.string.audio_track)).assertIsDisplayed()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_PROG_BLUE)
            compose.waitForIdle()
            compose.onNodeWithText(context.getString(uiR.string.subtitle_track)).assertIsDisplayed()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

            val redDown = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(KeyEvent(redDown, redDown, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_RED, 0))
            SystemClock.sleep(700)
            instrumentation.sendKeySync(KeyEvent(redDown, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PROG_RED, 0))
            awaitCondition { gain(controller) == 0 }

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_PROG_GREEN)
            awaitCondition { gain(controller) == 50 }
            scenario.onActivity { it.startActivity(playbackIntent("tvkeys-b.mp4")) }
            awaitCondition { main { controller.currentMediaItem?.localConfiguration?.uri.toString().contains("tvkeys-b.mp4") } }
            awaitCondition { gain(controller) == 0 }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_PROG_GREEN)
            awaitCondition { gain(controller) == 50 }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            awaitCondition { gain(controller) == 0 }
            assertEquals(originalVolume, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        } finally {
            scenario.close()
            main { controller.release() }
        }
    }
}
