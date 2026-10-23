package codemapper

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stand-in for a real LLM. Each call returns a String reply or throws a Throwable, either from a script
 * (in call order) or from a responder that looks at the request (safe for concurrent calls).
 */
class FakeChatModel private constructor(private val responder: (ChatRequest) -> Any) : ChatModel {
    constructor(vararg script: Any) : this(scripted(script))

    val requests: MutableList<List<ChatMessage>> = CopyOnWriteArrayList()

    override fun doChat(request: ChatRequest): ChatResponse {
        requests += request.messages()
        val next = responder(request)
        if (next is Throwable) throw next
        return ChatResponse.builder().aiMessage(AiMessage.from(next.toString())).build()
    }

    companion object {
        fun responding(responder: (ChatRequest) -> Any) = FakeChatModel(responder)

        private fun scripted(script: Array<out Any>): (ChatRequest) -> Any {
            val calls = AtomicInteger()
            return {
                val n = calls.getAndIncrement()
                script.getOrNull(n) ?: throw IllegalStateException("FakeChatModel script exhausted after ${script.size} calls")
            }
        }
    }
}

/** The text of the first user message in a request: the Provider Code prompt, even on a re-prompt. */
fun ChatRequest.userText() = messages().filterIsInstance<UserMessage>().first().singleText()

/** Builds an LLM answer in the format the mapping prompt asks for. */
fun answer(
    category: String,
    confidence: String = "high",
    retry: String? = null,
    notAnError: Boolean = false,
    review: Boolean = false,
    reason: String? = null,
    reasoning: String = "Test reasoning.",
) = """{"internal_category":"$category","confidence":"$confidence","reasoning":"$reasoning",""" +
    """"retry_strategy":${retry?.let { "\"$it\"" }},"not_an_error":$notAnError,""" +
    """"needs_human_review":$review,"review_reason":${reason?.let { "\"$it\"" }}}"""
