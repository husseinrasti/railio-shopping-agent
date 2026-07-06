package ai.railio.shop.infrastructure.agent

import ai.railio.shop.domain.chat.ChatMessage
import ai.railio.shop.domain.chat.ConversationStore
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory [ConversationStore] keyed by session id.
 *
 * Fine for a single-instance, local-first deployment. The interface is the swap
 * point: a Redis/DB implementation can replace this `@Single` to survive
 * restarts and scale horizontally, with no change to the agent service.
 */
@Single(binds = [ConversationStore::class])
class InMemoryConversationStore : ConversationStore<ChatMessage> {

    private val histories = ConcurrentHashMap<String, CopyOnWriteArrayList<ChatMessage>>()

    override fun history(sessionId: String): List<ChatMessage> =
        histories[sessionId]?.toList() ?: emptyList()

    override fun append(sessionId: String, messages: List<ChatMessage>) {
        histories.computeIfAbsent(sessionId) { CopyOnWriteArrayList() }.addAll(messages)
    }

    override fun clear(sessionId: String) {
        histories.remove(sessionId)
    }
}
