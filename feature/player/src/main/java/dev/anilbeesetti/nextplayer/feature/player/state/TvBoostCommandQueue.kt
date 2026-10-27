package dev.anilbeesetti.nextplayer.feature.player.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal data class TvBoostContext(val generation: Long, val mediaId: String)

internal data class TvBoostReply(val context: TvBoostContext, val gainMillibels: Int, val successful: Boolean)

/** Captures the session at physical press time; never rebinds old work to a new movie/session. */
internal class TvBoostCommandQueue(
    scope: CoroutineScope,
    private val currentContext: () -> TvBoostContext?,
    private val send: suspend (TvBoostContext, TvColorAction) -> TvBoostReply,
    private val onReply: (TvBoostReply) -> Unit,
) {
    private data class Request(val epoch: Long, val context: TvBoostContext, val action: TvColorAction)

    private val pending = Channel<Request>(Channel.UNLIMITED)
    private var epoch = 0L
    private var active = false

    init {
        scope.launch {
            for (request in pending) {
                if (!active || request.epoch != epoch || request.context != currentContext()) continue
                val reply = try {
                    send(request.context, request.action)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    TvBoostReply(request.context, 0, false)
                }
                if (active && request.epoch == epoch && reply.context == currentContext()) onReply(reply)
            }
        }
    }

    fun submit(action: TvColorAction) {
        if (!active) return
        // A deliberate reset also discards earlier unsent presses and stale in-flight feedback.
        if (action == TvColorAction.RESET) epoch++
        val context = currentContext() ?: return
        pending.trySend(Request(epoch, context, action))
    }

    fun setActive(value: Boolean) {
        epoch++
        active = value
    }

    fun close() {
        setActive(false)
        pending.close()
    }
}
