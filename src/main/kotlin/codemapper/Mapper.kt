package codemapper

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private fun resource(path: String) = object {}.javaClass.getResource(path)!!.readText()

/** Enrich stage: the Decline Taxonomy rendered from the enum, so prompts never drift from the code. */
fun taxonomyText() = DeclineCategory.entries.joinToString("\n") { category ->
    "- ${category.name}: ${category.description}. Retry: ${category.allowedRetries.joinToString(" | ") { it.wireName }}"
}

/** The system prompt: role, answer format, rules, taxonomy and few-shot examples. Same for every code. */
fun systemPrompt() = resource("/prompts/mapping-system.txt").replace("{{taxonomy}}", taxonomyText())

fun userPrompt(code: ProviderCode) = resource("/prompts/mapping-user.txt")
    .replace("{{code}}", code.code).replace("{{name}}", code.name).replace("{{description}}", code.description)

/** The JSON object the mapping prompt asks the LLM for. */
@Serializable
data class Answer(
    @SerialName("internal_category") val category: String,
    val confidence: String,
    val reasoning: String,
    @SerialName("retry_strategy") val retryStrategy: String? = null,
    @SerialName("not_an_error") val notAnError: Boolean = false,
    @SerialName("needs_human_review") val needsHumanReview: Boolean,
    @SerialName("review_reason") val reviewReason: String? = null,
)

/** The hard failures the validate stage recognises; each has its own correction prompt. */
enum class Failure(val prompt: String) {
    INVALID_JSON("invalid-json"),
    UNKNOWN_CATEGORY("unknown-category"),
    INVALID_RETRY("invalid-retry"),
    INVALID_FIELD("invalid-field"),
}

/** An LLM answer that cannot become a Mapping; [message] says why. */
class AnswerException(val kind: Failure, message: String) : Exception(message)

/** The re-prompt for [failure]; an unknown category gets the whole taxonomy again. */
fun correctionPrompt(failure: AnswerException) = resource("/prompts/corrections/${failure.kind.prompt}.txt")
    .replace("{{error}}", failure.message!!).replace("{{taxonomy}}", taxonomyText())

private val answerJson = Json { ignoreUnknownKeys = true }
private val fence = Regex("""^```[a-zA-Z]*\s*(.*?)\s*```$""", RegexOption.DOT_MATCHES_ALL)

const val NOT_AN_ERROR_REASON =
    "Flagged by the model as not an error (e.g. an idempotency guard); confirm before ignoring this code."

@OptIn(ExperimentalSerializationApi::class)
/** Turns the raw LLM text into a Mapping for [code], or throws [AnswerException]. */
fun parseAnswer(code: ProviderCode, raw: String): Mapping {
    val text = raw.trim().let { fence.matchEntire(it)?.groupValues?.get(1) ?: it }
    val answer = try {
        answerJson.decodeFromString(Answer.serializer(), text)
    } catch (e: MissingFieldException) {
        throw AnswerException(Failure.INVALID_FIELD, "missing field: ${e.missingFields.joinToString()}")
    } catch (e: IllegalArgumentException) { // SerializationException is one
        throw AnswerException(Failure.INVALID_JSON, "invalid JSON: ${e.message?.lineSequence()?.first()}")
    }
    val category = DeclineCategory.entries.find { it.name == answer.category }
        ?: throw AnswerException(Failure.UNKNOWN_CATEGORY, "unknown Decline Category: ${answer.category}")
    val confidence = Confidence.entries.find { it.name.lowercase() == answer.confidence }
        ?: throw AnswerException(Failure.INVALID_FIELD, "invalid confidence: ${answer.confidence}")
    val retry = category.allowedRetries.singleOrNull()
        ?: category.allowedRetries.find { it.wireName == answer.retryStrategy }
        ?: throw AnswerException(Failure.INVALID_RETRY, "invalid retry_strategy for ${category.name}: ${answer.retryStrategy}")
    return if (answer.notAnError) {
        Mapping(code.code, code.name, category, confidence, answer.reasoning, RetryStrategy.NO_ACTION,
            true, answer.reviewReason ?: NOT_AN_ERROR_REASON)
    } else {
        Mapping(code.code, code.name, category, confidence, answer.reasoning, retry,
            answer.needsHumanReview, answer.reviewReason)
    }
}

/**
 * Map and validate stages for one Provider Code: one LLM call, then up to [retryBudget] re-prompts that
 * continue the conversation with a correction for the hard failure found. Throws if the call itself fails.
 */
class Mapper(
    private val model: ChatModel,
    private val retryBudget: Int = 3,
    private val log: (String) -> Unit = {},
) {
    private val system = systemPrompt()

    fun mapCode(code: ProviderCode): Mapping {
        val messages = mutableListOf<ChatMessage>(SystemMessage.from(system), UserMessage.from(userPrompt(code)))
        for (attempt in 0..retryBudget) {
            val raw = model.chat(messages).aiMessage().text()
            try {
                return parseAnswer(code, raw)
            } catch (e: AnswerException) {
                if (attempt == retryBudget) return Mapping.unmapped(code, "no valid answer after $retryBudget retries: ${e.message}")
                log("${code.code} retry ${attempt + 1}/$retryBudget: ${e.message}")
                messages += AiMessage.from(raw)
                messages += UserMessage.from(correctionPrompt(e))
            }
        }
        error("unreachable")
    }
}
