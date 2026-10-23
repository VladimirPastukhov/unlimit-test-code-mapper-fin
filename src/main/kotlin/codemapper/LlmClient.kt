package codemapper

import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel

/** The LLM Vendors we can call. Any OpenAI-compatible runtime (e.g. Ollama) uses [OPENAI] with `LLM_BASE_URL`. */
enum class Vendor(val keyVariable: String, val defaultModel: String) {
    ANTHROPIC("ANTHROPIC_API_KEY", "claude-sonnet-5-5"),
    OPENAI("OPENAI_API_KEY", "gpt-5-mini"),
}

class ConfigException(message: String) : Exception(message)

/**
 * Builds the LangChain4j ChatModel from environment variables: `LLM_VENDOR` (anthropic or openai, default
 * anthropic), `LLM_MODEL`, `LLM_BASE_URL`, and the vendor's API key variable. Makes no network call.
 */
fun chatModelFromEnv(env: (String) -> String? = System::getenv): ChatModel {
    val vendorName = env("LLM_VENDOR") ?: "anthropic"
    val vendor = Vendor.entries.find { it.name.equals(vendorName, ignoreCase = true) }
        ?: throw ConfigException("unsupported LLM_VENDOR: $vendorName (use anthropic or openai)")
    val apiKey = env(vendor.keyVariable)?.takeIf { it.isNotBlank() }
        ?: throw ConfigException("${vendor.keyVariable} is not set")
    val model = env("LLM_MODEL") ?: vendor.defaultModel
    val baseUrl = env("LLM_BASE_URL")
    return when (vendor) {
        Vendor.ANTHROPIC -> AnthropicChatModel.builder().apiKey(apiKey).modelName(model)
            .apply { baseUrl?.let { baseUrl(it) } }.build()
        Vendor.OPENAI -> OpenAiChatModel.builder().apiKey(apiKey).modelName(model)
            .apply { baseUrl?.let { baseUrl(it) } }.build()
    }
}
