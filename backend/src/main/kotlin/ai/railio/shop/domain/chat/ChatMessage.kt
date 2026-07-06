package ai.railio.shop.domain.chat

/** Role of a message author within a conversation. */
enum class ChatRole { USER, ASSISTANT }

/**
 * A single stored conversation message used for cross-turn memory.
 *
 * Deliberately independent of any LLM-framework message type so memory can be
 * persisted and replayed without coupling to Koog internals.
 */
data class ChatMessage(val role: ChatRole, val content: String)
