package dev.anilbeesetti.nextplayer.feature.player.state

import dev.anilbeesetti.nextplayer.feature.player.service.TvBoostController
import dev.anilbeesetti.nextplayer.feature.player.service.TvBoostEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvBoostCommandQueueTest {
    private class Session(scope: CoroutineScope) {
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
        var mediaId = "movie-a"
        val serverContext get() = TvBoostContext(boost.generation, mediaId)
        var clientContext: TvBoostContext? = serverContext
        var beforeExecution: (suspend () -> Unit)? = null
        val sent = mutableListOf<Pair<TvBoostContext, TvColorAction>>()
        val replies = mutableListOf<TvBoostReply>()
        val queue = TvBoostCommandQueue(
            scope,
            currentContext = { clientContext },
            send = { snapshot, action ->
                sent += snapshot to action
                beforeExecution?.invoke()
                val applied = snapshot.mediaId == mediaId && boost.adjust(
                    snapshot.generation,
                    if (action == TvColorAction.INCREASE) 50 else -50,
                    action == TvColorAction.RESET,
                )
                TvBoostReply(serverContext, boost.gainMillibels, applied)
            },
            onReply = replies::add,
        ).apply { setActive(true) }
    }

    @Test
    fun unsentPressesAreDroppedAfterMediaChangesAndFreshPressStillWorks() = runTest {
        val session = Session(backgroundScope)
        session.queue.submit(TvColorAction.INCREASE)
        session.mediaId = "movie-b"
        session.boost.reset()
        session.clientContext = session.serverContext
        runCurrent()
        assertTrue(session.sent.isEmpty())
        assertEquals(0, session.boost.gainMillibels)
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        assertEquals(50, session.boost.gainMillibels)
    }

    @Test
    fun inFlightOldMediaPressCannotRaiseNewMovieBeforeClientGetsExtras() = runTest {
        val session = Session(backgroundScope)
        val release = CompletableDeferred<Unit>()
        session.beforeExecution = { release.await() }
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        session.mediaId = "movie-b"
        session.boost.reset()
        // Deliberately retain stale controller extras while the transport delivers old work.
        release.complete(Unit)
        runCurrent()
        assertEquals(0, session.boost.gainMillibels)
        assertTrue(session.replies.isEmpty())
        session.clientContext = session.serverContext
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        assertEquals(50, session.boost.gainMillibels)
    }

    @Test
    fun audioSessionChangeRejectsInFlightPressEvenForSameMovie() = runTest {
        val session = Session(backgroundScope)
        val release = CompletableDeferred<Unit>()
        session.beforeExecution = { release.await() }
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        session.boost.updateAudioSession(8, true)
        release.complete(Unit)
        runCurrent()
        assertEquals(0, session.boost.gainMillibels)
        assertTrue(session.replies.isEmpty())
    }

    @Test
    fun routeResetInvalidatesQueuedPressBeforeClientGetsTheNewGeneration() = runTest {
        val session = Session(backgroundScope)
        session.queue.submit(TvColorAction.INCREASE)
        session.boost.reset()
        runCurrent()
        assertEquals(1, session.sent.size)
        assertEquals(0, session.boost.gainMillibels)
        assertTrue(session.replies.isEmpty())
    }

    @Test
    fun explicitResetDropsEarlierUnsentPressesAndStaleFeedback() = runTest {
        val session = Session(backgroundScope)
        val release = CompletableDeferred<Unit>()
        session.beforeExecution = { release.await() }
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        session.queue.submit(TvColorAction.INCREASE)
        session.queue.submit(TvColorAction.RESET)
        // This was captured before the reset executes and must not be rebound to its new generation.
        session.queue.submit(TvColorAction.INCREASE)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(TvColorAction.INCREASE, TvColorAction.RESET, TvColorAction.INCREASE), session.sent.map { it.second })
        assertEquals(0, session.boost.gainMillibels)
        assertTrue(session.replies.isEmpty())
        session.clientContext = session.serverContext
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        assertEquals(50, session.boost.gainMillibels)
        assertEquals(50, session.replies.single().gainMillibels)
    }

    @Test
    fun pauseResumeDropsUnsentWorkAndFeedbackFromPreviousLifecycle() = runTest {
        val session = Session(backgroundScope)
        val release = CompletableDeferred<Unit>()
        session.beforeExecution = { release.await() }
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        session.queue.submit(TvColorAction.INCREASE)
        session.queue.setActive(false)
        session.boost.setActive(false)
        session.queue.setActive(true)
        session.boost.setActive(true)
        session.clientContext = session.serverContext
        release.complete(Unit)
        runCurrent()
        assertEquals(1, session.sent.size)
        assertEquals(0, session.boost.gainMillibels)
        assertTrue(session.replies.isEmpty())
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        assertEquals(50, session.boost.gainMillibels)
    }

    @Test
    fun closedQueueCannotSubmitOrReportDelayedWork() = runTest {
        val session = Session(backgroundScope)
        val release = CompletableDeferred<Unit>()
        session.beforeExecution = { release.await() }
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        session.queue.submit(TvColorAction.INCREASE)
        session.queue.close()
        session.boost.close()
        session.queue.submit(TvColorAction.INCREASE)
        release.complete(Unit)
        runCurrent()
        assertEquals(1, session.sent.size)
        assertEquals(0, session.boost.gainMillibels)
        assertTrue(session.replies.isEmpty())
    }

    @Test
    fun failedTransportDoesNotBreakNextFreshRequestAndNullContextDoesNotQueue() = runTest {
        val session = Session(backgroundScope)
        session.clientContext = null
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        assertTrue(session.sent.isEmpty())
        session.clientContext = session.serverContext
        session.beforeExecution = { throw IllegalStateException("disconnected") }
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        assertEquals(false, session.replies.single().successful)
        session.beforeExecution = null
        session.queue.submit(TvColorAction.INCREASE)
        runCurrent()
        assertEquals(50, session.boost.gainMillibels)
    }
}
