package codemapper

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.readText

class MapperTest {
    private val fraud = ProviderCode("QP-006", "Suspected fraud", "The transaction was flagged by the fraud detection system.")

    @Test
    fun `prompt carries the taxonomy and the code`() {
        val prompt = systemPrompt() + userPrompt(fraud)
        DeclineCategory.entries.forEach {
            prompt shouldContain it.name
            prompt shouldContain it.description
        }
        prompt shouldContain "QP-006"
        prompt shouldContain "Suspected fraud"
        prompt shouldContain fraud.description
    }

    @Test
    fun `no VeloPay code or name appears in the prompt`() {
        val prompt = systemPrompt()
        parseDocumentation(Path("examples/providers/velopay-systems-v1.3.txt").readText()).codes.forEach {
            prompt shouldNotContain it.code
            prompt shouldNotContain it.name
        }
    }

    private fun map(reply: String, code: ProviderCode = fraud) =
        Mapper(FakeChatModel.responding { reply }).mapCode(code)

    /** Maps [fraud] with scripted replies; returns the Mapping and the fake for request checks. */
    private fun recover(vararg replies: String, budget: Int = 3): Pair<Mapping, FakeChatModel> {
        val fake = FakeChatModel(*replies)
        return Mapper(fake, budget).mapCode(fraud) to fake
    }

    private fun List<ChatMessage>.text() = joinToString("\n") {
        when (it) {
            is SystemMessage -> it.text()
            is UserMessage -> it.singleText()
            is AiMessage -> it.text()
            else -> ""
        }
    }

    private fun Mapping.shouldBeUnmappedWith(text: String) {
        internalCategory shouldBe null
        confidence shouldBe Confidence.LOW
        needsHumanReview shouldBe true
        reviewReason.shouldNotBeNull() shouldContain text
    }

    @Test
    fun `answer fields become the Mapping and provider fields come from the parsed code`() {
        map("""{"provider_code":"X","provider_message":"Y",""" + answer("ANTIFRAUD", reasoning = "Flagged by fraud detection.").drop(1)) shouldBe
            Mapping("QP-006", "Suspected fraud", DeclineCategory.ANTIFRAUD, Confidence.HIGH,
                "Flagged by fraud detection.", RetryStrategy.NO_RETRY, false, null)
    }

    @Test
    fun `fenced answer is accepted`() {
        map("```json\n${answer("ANTIFRAUD")}\n```").internalCategory shouldBe DeclineCategory.ANTIFRAUD
    }

    @Test
    fun `fixed category ignores the LLM retry strategy`() {
        map(answer("SYSTEM_MALFUNCTION", retry = "no_retry")).retryStrategy shouldBe RetryStrategy.RETRY_WITH_BACKOFF
    }

    @Test
    fun `COMMON_DECLINE uses the LLM retry strategy`() {
        map(answer("COMMON_DECLINE", retry = "retry_after_fix")).retryStrategy shouldBe RetryStrategy.RETRY_AFTER_FIX
    }

    @Test
    fun `not an error gives no_action and Human Review`() {
        val mapping = map(answer("COMMON_DECLINE", "medium", retry = "no_retry", notAnError = true))
        mapping.retryStrategy shouldBe RetryStrategy.NO_ACTION
        mapping.needsHumanReview shouldBe true
        mapping.reviewReason.shouldNotBeNull()
    }

    @Test
    fun `invalid JSON recovers on retry`() {
        val bad = "Sure! The category is COMMON_DECLINE."
        val (mapping, fake) = recover(bad, answer("COMMON_DECLINE", retry = "no_retry"))
        mapping.internalCategory shouldBe DeclineCategory.COMMON_DECLINE
        fake.requests.size shouldBe 2
        fake.requests[1].text() shouldContain bad
        fake.requests[1].last().let { (it as UserMessage).singleText() } shouldContain "JSON"
    }

    @Test
    fun `hallucinated category recovers with the taxonomy`() {
        val (mapping, fake) = recover(answer("FRAUD_SUSPECTED"), answer("ANTIFRAUD"))
        mapping.internalCategory shouldBe DeclineCategory.ANTIFRAUD
        val correction = (fake.requests[1].last() as UserMessage).singleText()
        correction shouldContain "FRAUD_SUSPECTED"
        DeclineCategory.entries.forEach { correction shouldContain it.name }
    }

    @Test
    fun `disallowed retry recovers`() {
        val (mapping, fake) = recover(answer("COMMON_DECLINE", retry = "no_action"), answer("COMMON_DECLINE", retry = "no_retry"))
        mapping.retryStrategy shouldBe RetryStrategy.NO_RETRY
        (fake.requests[1].last() as UserMessage).singleText() shouldContain "retry_after_fix"
    }

    @Test
    fun `missing field recovers`() {
        val (mapping, fake) = recover(answer("ANTIFRAUD").replace(""""confidence":"high",""", ""), answer("ANTIFRAUD"))
        mapping.internalCategory shouldBe DeclineCategory.ANTIFRAUD
        (fake.requests[1].last() as UserMessage).singleText() shouldContain "confidence"
    }

    @Test
    fun `budget exhausted gives an Unmapped Code`() {
        val fake = FakeChatModel.responding { answer("FRAUD_SUSPECTED") }
        Mapper(fake, retryBudget = 3).mapCode(fraud).shouldBeUnmappedWith("FRAUD_SUSPECTED")
        fake.requests.size shouldBe 4
    }

    @Test
    fun `budget comes from the argument`() {
        val fake = FakeChatModel.responding { "not json" }
        Mapper(fake, retryBudget = 1).mapCode(fraud)
        fake.requests.size shouldBe 2
    }

    @Test
    fun `retry notice is logged`() {
        val lines = mutableListOf<String>()
        Mapper(FakeChatModel(answer("FRAUD_SUSPECTED"), answer("ANTIFRAUD")), log = { lines += it }).mapCode(fraud)
        lines shouldBe listOf("QP-006 retry 1/3: unknown Decline Category: FRAUD_SUSPECTED")
    }

    @Test
    fun `invalid JSON becomes an Unmapped Code`() {
        map("Sure! The category is COMMON_DECLINE.").shouldBeUnmappedWith("invalid JSON")
    }

    @Test
    fun `hallucinated category becomes an Unmapped Code`() {
        map(answer("FRAUD_SUSPECTED")).shouldBeUnmappedWith("FRAUD_SUSPECTED")
    }

    @Test
    fun `missing field becomes an Unmapped Code`() {
        map(answer("ANTIFRAUD").replace(""""confidence":"high",""", "")).shouldBeUnmappedWith("confidence")
    }

    @Test
    fun `disallowed COMMON_DECLINE retry becomes an Unmapped Code`() {
        map(answer("COMMON_DECLINE", retry = "no_action")).shouldBeUnmappedWith("no_action")
    }
}
