package dev.anilbeesetti.nextplayer.feature.player.state

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@Composable
@OptIn(UnstableApi::class)
internal fun rememberTvBoostState(player: Player): TvBoostState? {
    val controller = player as? MediaController ?: return null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(controller) { TvBoostState(controller, context, scope) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.setActive(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { state.setActive(false) }
    DisposableEffect(state) { onDispose { state.close() } }
    return state
}

/** Only sends app-session gain commands; deliberately never reads or changes STREAM_MUSIC. */
@OptIn(UnstableApi::class)
internal class TvBoostState(
    private val controller: MediaController,
    private val context: Context,
    scope: CoroutineScope,
) {
    private val keys = TvColorKeyHandler()
    private val pending = Channel<Pair<Int, TvColorAction>>(Channel.UNLIMITED)
    private var active = false
    private var epoch = 0
    private var feedback: Toast? = null

    init {
        scope.launch {
            for ((requestEpoch, action) in pending) {
                if (!active || requestEpoch != epoch) continue
                val args = Bundle().apply {
                    putInt(CustomCommands.TV_BOOST_DELTA_KEY, if (action == TvColorAction.INCREASE) 50 else -50)
                    putBoolean(CustomCommands.TV_BOOST_RESET_KEY, action == TvColorAction.RESET)
                }
                try {
                    val result = controller.sendCustomCommand(CustomCommands.ADJUST_TV_BOOST.sessionCommand, args).await()
                    if (!active || requestEpoch != epoch) continue
                    val text = if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                        context.getString(R.string.tv_boost_unavailable)
                    } else {
                        val gain = result.extras.getInt(CustomCommands.LOUDNESS_GAIN_KEY, 0)
                        Log.i("TvKeys", "Applied gain millibels=$gain")
                        if (gain == 0) context.getString(R.string.tv_boost_zero) else context.getString(R.string.tv_boost_gain, gain / 100.0f)
                    }
                    showFeedback(text)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (active && requestEpoch == epoch) showFeedback(context.getString(R.string.tv_boost_unavailable))
                }
            }
        }
    }

    fun handleKey(event: KeyEvent): TvColorKeyResult {
        if (!active || controller.currentMediaItem == null) return TvColorKeyResult(false)
        val keyCode = TvColorKeyHandler.normalize(event.keyCode, event.scanCode, event.device?.vendorId ?: 0, event.device?.productId ?: 0)
        val result = keys.handle(keyCode, event.action, event.repeatCount, event.eventTime - event.downTime)
        if (result.consumed) Log.i("TvKeys", "Key code=${event.keyCode} scan=${event.scanCode} action=${event.action} repeat=${event.repeatCount} mapped=$keyCode")
        result.action?.takeIf { it == TvColorAction.INCREASE || it == TvColorAction.DECREASE || it == TvColorAction.RESET }?.let {
            pending.trySend(epoch to it)
        }
        return result
    }

    fun setActive(value: Boolean) {
        epoch++
        active = value
        keys.clear()
        if (!value) {
            feedback?.cancel()
            feedback = null
        }
        if (controller.isConnected) {
            controller.sendCustomCommand(
                CustomCommands.SET_TV_BOOST_ACTIVE.sessionCommand,
                Bundle().apply { putBoolean(CustomCommands.TV_BOOST_ACTIVE_KEY, value) },
            )
        }
    }

    fun close() {
        setActive(false)
        pending.close()
    }

    private fun showFeedback(text: String) {
        feedback?.cancel()
        feedback = Toast.makeText(context, text, Toast.LENGTH_SHORT).also { it.show() }
    }
}
