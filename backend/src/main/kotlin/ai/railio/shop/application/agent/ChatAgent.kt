package ai.railio.shop.application.agent

import ai.railio.shop.domain.chat.AgentEvent
import kotlinx.coroutines.flow.Flow

/**
 * Entry point for a single conversational turn.
 *
 * Implementations run the tool-using LLM agent and emit a stream of
 * [AgentEvent]s — interleaved prose tokens and structured UI events — for the
 * web layer to relay over SSE. Keeping this as an interface lets the web layer
 * depend on the use case rather than the Koog-specific implementation.
 */
interface ChatAgent {

    /**
     * Handles [userMessage] within [sessionId] and streams the agent's response.
     *
     * The returned [Flow] completes with an [AgentEvent.Done] and never throws;
     * failures are surfaced as [AgentEvent.Error] events.
     */
    fun chat(sessionId: String, userMessage: String): Flow<AgentEvent>
}
