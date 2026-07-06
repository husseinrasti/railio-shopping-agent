package ai.railio.shop.infrastructure.agent

import ai.railio.shop.infrastructure.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Manages the lifetime of the loaded Ollama model.
 *
 * Ollama keeps a model resident in (V)RAM for `keep_alive` after each request
 * (default 5 minutes). Sending a request with `keep_alive: 0` unloads it
 * immediately, freeing memory when the app is idle. This does **not** run
 * inference — it only tells Ollama to release the model.
 *
 * Used by [ShoppingAgentService] after a turn completes when
 * [AppConfig.unloadModelAfterResponse] is enabled. No-op for non-Ollama providers.
 */
@Single
class OllamaModelManager(private val config: AppConfig) {

    private val log = LoggerFactory.getLogger(OllamaModelManager::class.java)

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    /**
     * Requests Ollama to unload [model] from memory (keep_alive=0). Best-effort:
     * failures are logged and swallowed so they never affect the user's response.
     *
     * @return `true` if Ollama accepted the unload request, `false` otherwise.
     */
    suspend fun unload(model: String): Boolean = withContext(Dispatchers.IO) {
        val body = """{"model":${model.jsonString()},"keep_alive":0}"""
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${config.ollamaBaseUrl.trimEnd('/')}/api/generate"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        runCatching {
            val status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
            log.info("Unload requested for model '{}' (HTTP {})", model, status)
            status in 200..299
        }.getOrElse {
            log.warn("Model unload request failed: {}", it.message)
            false
        }
    }

    /** Minimal JSON string escaping for the model id. */
    private fun String.jsonString(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
