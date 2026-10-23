package codemapper

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.Path
import kotlin.io.path.readText

class PipelineTest {
    private val quickPay = parseDocumentation(Path("examples/providers/quickpay-global-v2.4.txt").readText())

    @Test
    fun `one call per code and Mappings in input order`() {
        val fake = FakeChatModel.responding { answer("ANTIFRAUD") }
        val report = runPipeline(quickPay, fake)
        fake.requests.size shouldBe 26
        report.mappings.map { it.providerCode } shouldBe quickPay.codes.map { it.code }
        report.provider shouldBe "QuickPay Global"
        report.version shouldBe "2.4"
    }

    @Test
    fun `one bad answer leaves the other codes mapped`() {
        val fake = FakeChatModel.responding { if ("QP-001 " in it.userText()) "not json" else answer("ANTIFRAUD") }
        val report = runPipeline(quickPay, fake)
        report.summary.unmapped shouldBe 1
        report.mappings.first().internalCategory shouldBe null
        report.mappings.drop(1).all { it.internalCategory == DeclineCategory.ANTIFRAUD } shouldBe true
    }

    @Test
    fun `a failed LLM call gives an Unmapped Code with the error`() {
        val fake = FakeChatModel.responding {
            if ("QP-100 " in it.userText()) RuntimeException("connection reset") else answer("ANTIFRAUD")
        }
        val mapping = runPipeline(quickPay, fake).mappings.single { it.providerCode == "QP-100" }
        mapping.internalCategory shouldBe null
        mapping.reviewReason!! shouldContain "connection reset"
    }

    @Test
    fun `one progress line per code`() {
        val lines = CopyOnWriteArrayList<String>()
        runPipeline(quickPay, FakeChatModel.responding { answer("ANTIFRAUD") }, log = { lines += it })
        lines.size shouldBe 26
        lines shouldContain "QP-006 → ANTIFRAUD (high)"
    }

    @Test
    fun `review stage turns a hedging high answer into an Unmapped Code without extra calls`() {
        val fake = FakeChatModel.responding {
            if ("QP-008 " in it.userText()) answer("COMMON_DECLINE", retry = "no_retry", reasoning = "It might be fraud.")
            else answer("ANTIFRAUD")
        }
        val report = runPipeline(quickPay, fake)
        report.mappings.single { it.providerCode == "QP-008" }.internalCategory shouldBe null
        report.summary.unmapped shouldBe 1
        fake.requests.size shouldBe 26
    }

    @Test
    fun `defaults when the header is missing`() {
        val report = runPipeline(parseDocumentation("QP-001 \"Transaction declined\"\n"), FakeChatModel.responding { answer("ANTIFRAUD") })
        report.provider shouldBe "default_provider"
        report.version shouldBe "default_version"
    }
}
