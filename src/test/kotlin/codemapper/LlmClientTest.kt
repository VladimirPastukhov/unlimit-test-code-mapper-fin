package codemapper

import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class LlmClientTest {
    private fun build(vararg vars: Pair<String, String>) = chatModelFromEnv(mapOf(*vars)::get)

    @Test
    fun `anthropic is the default vendor`() {
        val model = build("ANTHROPIC_API_KEY" to "key")
        model.shouldBeInstanceOf<AnthropicChatModel>()
        model.defaultRequestParameters().modelName() shouldBe "claude-sonnet-5-5"
    }

    @Test
    fun `openai vendor with model and base url`() {
        val model = build(
            "LLM_VENDOR" to "openai", "OPENAI_API_KEY" to "key",
            "LLM_MODEL" to "llama3.1", "LLM_BASE_URL" to "http://localhost:11434/v1",
        )
        model.shouldBeInstanceOf<OpenAiChatModel>()
        model.defaultRequestParameters().modelName() shouldBe "llama3.1"
    }

    @Test
    fun `missing or blank key fails naming the variable`() {
        shouldThrow<ConfigException> { build() }.message!! shouldContain "ANTHROPIC_API_KEY"
        shouldThrow<ConfigException> { build("ANTHROPIC_API_KEY" to " ") }.message!! shouldContain "ANTHROPIC_API_KEY"
    }

    @Test
    fun `unknown vendor fails`() {
        shouldThrow<ConfigException> { build("LLM_VENDOR" to "cohere") }.message!! shouldContain "cohere"
    }
}
