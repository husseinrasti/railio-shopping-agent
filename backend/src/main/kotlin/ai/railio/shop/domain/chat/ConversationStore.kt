package ai.railio.shop.domain.chat

/**
 * Per-session conversation memory.
 *
 * Holds the running message history for a chat session so the agent has context
 * across turns. The interface is the swap point for persistence: the current
 * implementation is in-memory
 * ([ai.railio.shop.infrastructure.agent.InMemoryConversationStore]); a future
 * Redis/DB implementation can replace it without touching the agent service.
 *
 * @param M the framework message type stored per session.
 */
interface ConversationStore<M> {

    /** Returns the ordered history for [sessionId] (empty if new). */
    fun history(sessionId: String): List<M>

    /** Appends [messages] to [sessionId]'s history. */
    fun append(sessionId: String, messages: List<M>)

    /** Clears history for [sessionId]. */
    fun clear(sessionId: String)
}
