package ai.railio.shop.infrastructure.agent

import ai.railio.shop.application.agent.ChatAgent
import ai.railio.shop.application.catalog.CatalogService
import ai.railio.shop.application.payment.PaymentService
import ai.railio.shop.domain.chat.AgentEvent
import ai.railio.shop.domain.chat.ChatMessage
import ai.railio.shop.domain.chat.ChatRole
import ai.railio.shop.domain.chat.ConversationStore
import ai.railio.shop.infrastructure.config.AppConfig
import ai.railio.shop.infrastructure.config.LlmProvider
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

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
    private val config: AppConfig,
    private val modelManager: OllamaModelManager,
) : ChatAgent {

    private val log = LoggerFactory.getLogger(ShoppingAgentService::class.java)

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

            // Some local models (e.g. coder variants) emit a tool call as raw JSON text
            // instead of using the native tool protocol. Detect that, run the tool
            // ourselves so cards/forms still appear, and replace the shown text.
            val finalText = dispatchTextToolCall(reply, tools) ?: reply

            // Stream the assistant's prose as incremental tokens for a live feel.
            for (chunk in finalText.chunkedForStreaming()) {
                channel.send(AgentEvent.Token(chunk))
                delay(STREAM_DELAY_MS)
            }

            memory.append(
                sessionId,
                listOf(
                    ChatMessage(ChatRole.USER, userMessage),
                    ChatMessage(ChatRole.ASSISTANT, finalText),
                ),
            )
        } catch (t: Throwable) {
            channel.send(AgentEvent.Error("Sorry — I hit a problem: ${t.message ?: "unknown error"}."))
        } finally {
            channel.send(AgentEvent.Done)
            releaseModel()
        }
    }.buffer(Channel.UNLIMITED)

    /**
     * Frees the local model from memory once the turn is done, if configured.
     * Only meaningful for Ollama; a no-op for cloud providers. Trades a slower
     * next request (model reload) for a smaller idle memory footprint.
     */
    private suspend fun releaseModel() {
        if (config.llmProvider == LlmProvider.OLLAMA && config.unloadModelAfterResponse) {
            modelManager.unload(config.llmModel)
        }
    }

    /**
     * If [reply] is (or contains) a tool-call JSON, runs the matching tool and
     * returns friendly user-facing text; otherwise returns `null`.
     */
    private fun dispatchTextToolCall(reply: String, tools: ShoppingTools): String? {
        val call = extractToolCall(reply) ?: return null
        val name = call["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val args = (call["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
        fun str(vararg keys: String): String =
            keys.firstNotNullOfOrNull { k ->
                args[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
            } ?: ""
        fun num(vararg keys: String): Long =
            keys.firstNotNullOfOrNull { k -> args[k]?.jsonPrimitive?.longOrNull } ?: 0L

        log.info("Recovering text-emitted tool call: {}", name)
        return when (name) {
            "searchProducts" -> {
                val text = tools.searchProducts(str("query"), str("category"), num("maxPrice", "maxPriceToman"))
                if (text.startsWith("No products")) "I couldn't find anything matching that. Want to try different terms?"
                else "Here's what I found 👇"
            }
            "listCategories" -> tools.listCategories()
            "getProduct" -> tools.getProduct(str("productId", "id"))
            "startCheckout" -> tools.startCheckout(str("productId", "id"))
            "submitCardDetails" -> tools.submitCardDetails(
                str("sessionId"), str("cardNumber", "card"), str("expiry"), str("cvv2"),
            )
            "submitOtp" -> tools.submitOtp(str("sessionId"), str("otp"))
            else -> null
        }
    }

    /** Parses a single `{"name":...,"arguments":{...}}` object out of model text. */
    private fun extractToolCall(reply: String): JsonObject? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val obj = lenientJson.parseToJsonElement(reply.substring(start, end + 1)).jsonObject
            if (obj.containsKey("name")) obj else null
        }.getOrNull()
    }

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

        /** Tolerant parser for recovering tool calls printed as text by weaker models. */
        val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

        val PERSONA = """
            You are Railio, a friendly shopping assistant for an online store.
            You can ONLY help with this store's catalog (electronics, fashion, home, books)
            and with completing a card payment. Politely decline unrelated requests.

            Guidelines:
            - When the user wants to find or compare products, call searchProducts. The UI shows
              the matching products as cards, so keep your text short — do not re-list every field.
            - Refer to products by name and mention the price in Toman when helpful.
            - When the user wants to buy a specific product, call startCheckout with its id. A secure
              payment form is shown to the user; they enter their card details (number, expiry, CVV2)
              together, then the OTP that is sent to them.
            - Never ask for or repeat full card numbers, CVV2, or OTP values in plain text.
            - Be concise, warm, and helpful.
        """.trimIndent()
    }
}
