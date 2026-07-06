package ai.railio.shop.infrastructure.agent

import ai.railio.shop.domain.chat.AgentEvent
import kotlinx.coroutines.channels.SendChannel

/**
 * Non-blocking bridge used by agent tools to push structured UI events
 * (product cards, payment forms) into the active chat turn's stream.
 *
 * Each chat turn creates a fresh sink bound to that turn's channel and a fresh
 * tool instance holding it, so there is no shared mutable state between
 * concurrent sessions and no reliance on thread/coroutine-local propagation
 * through the Koog runtime.
 *
 * The backing channel is expected to be unbounded (or generously buffered) so
 * [emit] never blocks a tool call; [trySend] failures are therefore not expected
 * in practice and are silently dropped rather than throwing inside a tool.
 */
class AgentEventSink(private val channel: SendChannel<AgentEvent>) {

    /** Offers [event] to the stream without suspending the calling tool. */
    fun emit(event: AgentEvent) {
        channel.trySend(event)
    }
}
