package ai.railio.shop.infrastructure.agent

import ai.railio.shop.infrastructure.config.AppConfig
import ai.railio.shop.infrastructure.config.LlmProvider
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import org.koin.core.annotation.Single

/**
 * Builds the Koog [PromptExecutor] and [LLModel] from [AppConfig].
 *
 * This is the single place that knows about concrete LLM providers, so the rest
 * of the app stays provider-agnostic. Switching between local Ollama and a cloud
 * model is purely a matter of environment variables (`LLM_PROVIDER`, `LLM_MODEL`,
 * `OLLAMA_BASE_URL`, `OPENAI_API_KEY`).
 *
 * The model id is taken verbatim from config so any model pulled into the local
 * Ollama instance (or any OpenAI model name) works without code changes.
 */
@Single
class LlmExecutorFactory(private val config: AppConfig) {

    /**
     * The prompt executor for the configured provider.
     *
     * A single-client [MultiLLMPromptExecutor] is used: it routes requests to the
     * one configured client based on the model's provider. (The `llms-all`
     * convenience `simple*Executor` helpers are not published in the stable line,
     * so the client is wired explicitly here.)
     */
    fun executor(): PromptExecutor = when (config.llmProvider) {
        LlmProvider.OLLAMA -> MultiLLMPromptExecutor(OllamaClient(baseUrl = config.ollamaBaseUrl))
        LlmProvider.OPENAI -> MultiLLMPromptExecutor(OpenAILLMClient(config.openAiApiKey))
    }

    /** The model descriptor for the configured provider, with tool support. */
    fun model(): LLModel {
        val provider = when (config.llmProvider) {
            LlmProvider.OLLAMA -> LLMProvider.Ollama
            LlmProvider.OPENAI -> LLMProvider.OpenAI
        }
        return LLModel(
            provider = provider,
            id = config.llmModel,
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.Schema.JSON.Standard,
            ),
        )
    }
}
