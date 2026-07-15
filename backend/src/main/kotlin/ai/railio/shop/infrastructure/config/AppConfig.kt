package ai.railio.shop.infrastructure.config

/**
 * Supported LLM providers. Selected at runtime via the `LLM_PROVIDER` env var so
 * the same build runs local-first (Ollama) or against a cloud model.
 */
enum class LlmProvider(val id: String) {
    OLLAMA("ollama"),
    OPENAI("openai");

    companion object {
        fun from(id: String?): LlmProvider =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: OLLAMA
    }
}

/**
 * Strongly-typed application configuration, sourced entirely from environment
 * variables with sensible local-first defaults.
 *
 * Centralising config here keeps `System.getenv` out of the rest of the code and
 * makes the swap points explicit. Everything the deployment needs to change
 * lives in `.env` / `.env.example`.
 *
 * @property llmProvider which [LlmProvider] to use.
 * @property llmModel model id (e.g. `gemma4:12b` for Ollama, `gpt-4o` for OpenAI).
 * @property ollamaBaseUrl base URL of the Ollama server.
 * @property openAiApiKey API key when [llmProvider] is OpenAI; may be blank otherwise.
 * @property mockOtp the OTP the mock payment provider accepts.
 * @property agentSecret shared secret reserved for future API authentication.
 * @property corsOrigins allowed CORS origins for the web UI.
 */
data class AppConfig(
    val llmProvider: LlmProvider,
    val llmModel: String,
    val ollamaBaseUrl: String,
    val openAiApiKey: String,
    val mockOtp: String,
    val agentSecret: String,
    val corsOrigins: List<String>,
    val unloadModelAfterResponse: Boolean,
) {
    companion object {
        /** Reads configuration from the process environment, applying defaults. */
        fun fromEnv(getenv: (String) -> String? = System::getenv): AppConfig {
            fun env(key: String, default: String) = getenv(key)?.takeIf { it.isNotBlank() } ?: default
            return AppConfig(
                llmProvider = LlmProvider.from(getenv("LLM_PROVIDER")),
                llmModel = env("LLM_MODEL", "gemma4:12b"),
                ollamaBaseUrl = env("OLLAMA_BASE_URL", "http://localhost:11434"),
                openAiApiKey = env("OPENAI_API_KEY", ""),
                mockOtp = env("MOCK_OTP", "12345"),
                agentSecret = env("AGENT_SECRET", "dev-secret-change-me"),
                corsOrigins = env("CORS_ORIGINS", "http://localhost:3000")
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                // Free the local model from memory after each response. Off by default
                // (keeps the model warm for a fast next turn); enable to save RAM.
                unloadModelAfterResponse = env("OLLAMA_UNLOAD_AFTER_RESPONSE", "false").toBoolean(),
            )
        }
    }
}
