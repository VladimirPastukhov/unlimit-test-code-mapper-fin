package codemapper

import dev.langchain4j.data.message.UserMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class FakeChatModelTest {
    @Test
    fun `replies in order and records requests`() {
        val fake = FakeChatModel("first", "second")
        fake.chat("a") shouldBe "first"
        fake.chat("b") shouldBe "second"
        fake.requests.map { (it.single() as UserMessage).singleText() } shouldBe listOf("a", "b")
    }

    @Test
    fun `scripted throwable is thrown`() {
        val fake = FakeChatModel(RuntimeException("boom"))
        shouldThrow<RuntimeException> { fake.chat("a") }.message shouldBe "boom"
    }

    @Test
    fun `exhausted script fails`() {
        val fake = FakeChatModel("only")
        fake.chat("a")
        shouldThrow<IllegalStateException> { fake.chat("b") }.message!! shouldContain "script exhausted"
    }

    @Test
    fun `responder mode answers by request content`() {
        val fake = FakeChatModel.responding { if ("QP-001" in it.userText()) "A" else "B" }
        fake.chat("about QP-001") shouldBe "A"
        fake.chat("about QP-002") shouldBe "B"
        fake.requests.size shouldBe 2
    }
}
