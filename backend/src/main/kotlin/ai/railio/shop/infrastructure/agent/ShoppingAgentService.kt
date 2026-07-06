package ai.railio.shop.infrastructure.agent

import ai.railio.shop.application.agent.ChatAgent
import ai.railio.shop.application.catalog.CatalogService
import ai.railio.shop.application.payment.PaymentService
import ai.railio.shop.domain.chat.AgentEvent
import ai.railio.shop.domain.chat.ChatMessage
import ai.railio.shop.domain.chat.ChatRole
import ai.railio.shop.domain.chat.ConversationStore
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import org.koin.core.annotation.Single

/**
 * Koog-backed [ChatAgent].
 *
 * Per turn it builds a fresh [ShoppingTools] instance bound to the turn's event
 * sink, constructs an [AIAgent] with the configured executor/model, runs it, and
 * streams the result. Structured events (product cards, payment forms) are
 * emitted by the tools *during* the run; the assistant's final prose is streamed
 * out word-by-word afterwards.
 *
 * Cross-turn context is provided by [ConversationStore]: recent history is
 * injected into the system prompt. (Swapping to Koog's built-in memory feature
 * or a persistent store is a localized change behind that interface.)
 */
@Single(binds = [ChatAgent::class])
class ShoppingAgentService(
    private val executorFactory: LlmExecutorFactory,
    private val catalog: CatalogService,
    private val payments: PaymentService,
    private val memory: ConversationStore<ChatMessage>,
) : ChatAgent {

    override fun chat(sessionId: String, userMessage: String): Flow<AgentEvent> = channelFlow {
        val sink = AgentEventSink(channel)
        try {
            val tools = ShoppingTools(catalog, payments, sink)
            val agent = AIAgent(
                promptExecutor = executorFactory.executor(),
                systemPrompt = systemPrompt(memory.history(sessionId)),
                llmModel = executorFactory.model(),
                temperature = 0.4,
                toolRegistry = ToolRegistry { tools(tools) },
                maxIterations = 12,
            )

            val reply = agent.run(userMessage)

            // Stream the assistant's prose as incremental tokens for a live feel.
            for (chunk in reply.chunkedForStreaming()) {
                channel.send(AgentEvent.Token(chunk))
                delay(STREAM_DELAY_MS)
            }

            memory.append(
                sessionId,
                listOf(
                    ChatMessage(ChatRole.USER, userMessage),
                    ChatMessage(ChatRole.ASSISTANT, reply),
                ),
            )
        } catch (t: Throwable) {
            channel.send(AgentEvent.Error("Sorry — I hit a problem: ${t.message ?: "unknown error"}."))
        } finally {
            channel.send(AgentEvent.Done)
        }
    }.buffer(Channel.UNLIMITED)

    /** Builds the system prompt: persona, store rules, and recent memory. */
    private fun systemPrompt(history: List<ChatMessage>): String = buildString {
        appendLine(PERSONA)
        if (history.isNotEmpty()) {
            appendLine()
            appendLine("Recent conversation (for context):")
            history.takeLast(MEMORY_TURNS).forEach { msg ->
                val who = if (msg.role == ChatRole.USER) "User" else "Assistant"
                appendLine("$who: ${msg.content}")
            }
        }
    }

    /** Splits text into small word-group chunks for pseudo-streaming. */
    private fun String.chunkedForStreaming(): List<String> {
        if (isBlank()) return emptyList()
        return split(Regex("(?<= )")).chunked(3).map { it.joinToString("") }
    }

    private companion object {
        const val STREAM_DELAY_MS = 25L
        const val MEMORY_TURNS = 10

        val PERSONA = """
            You are Railio, a friendly shopping assistant for an online store.
            You can ONLY help with this store's catalog (electronics, fashion, home, books)
            and with completing a card payment. Politely decline unrelated requests.

            Guidelines:
            - When the user wants to find or compare products, call searchProducts. The UI shows
              the matching products as cards, so keep your text short — do not re-list every field.
            - Refer to products by name and mention the price in Toman when helpful.
            - When the user wants to buy a specific product, call startCheckout with its id. A secure
              payment form is shown to the user; guide them through card, expiry, CVV2, then OTP.
            - Never ask for or repeat full card numbers, CVV2, or OTP values in plain text.
            - Be concise, warm, and helpful.
        """.trimIndent()
    }
}
