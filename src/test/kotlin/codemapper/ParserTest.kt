package codemapper

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.readText

class ParserTest {
    private fun parse(vararg lines: String) = parseDocumentation(lines.joinToString("\n"))

    private fun ParsedDocumentation.code(code: String) = codes.single { it.code == code }

    @Test
    fun `code line gives code and name`() {
        parse("QP-006 \"Suspected fraud\"").codes shouldBe listOf(ProviderCode("QP-006", "Suspected fraud", ""))
    }

    @Test
    fun `placeholder in name is kept verbatim`() {
        parse("QP-200 \"Missing required field: {field_name}\"").codes.single().name shouldBe
            "Missing required field: {field_name}"
    }

    @Test
    fun `numeric code`() {
        parse("1001 \"Issuer declined\"").codes.single() shouldBe ProviderCode("1001", "Issuer declined", "")
    }

    @Test
    fun `indented lines are joined into the description`() {
        parse(
            "1003 \"Card reported lost or stolen\"",
            "    The card has been reported lost or stolen by its owner.",
            "    The issuer has permanently blocked it.",
        ).codes.single().description shouldBe
            "The card has been reported lost or stolen by its owner. The issuer has permanently blocked it."
    }

    @Test
    fun `code followed by a blank line has an empty description`() {
        parse("QP-001 \"Transaction declined\"", "", "    stray indented line").codes.single().description shouldBe ""
    }

    @Test
    fun `section headers and prose are ignored`() {
        val parsed = parse(
            "QuickPay Global — API Error Reference (v2.4)",
            "",
            "All API responses include a `result_code` field (string).",
            "",
            "=== System / Connectivity Errors ===",
            "",
            "QP-100 \"Internal server error\"",
            "    An unexpected error occurred.",
        )
        parsed.codes shouldHaveSize 1
        parsed.warnings.shouldBeEmpty()
    }

    @Test
    fun `header gives name and version`() {
        val parsed = parse("QuickPay Global — API Error Reference (v2.4)", "QP-001 \"Transaction declined\"")
        parsed.providerName shouldBe "QuickPay Global"
        parsed.version shouldBe "2.4"
    }

    @Test
    fun `no header when first line is a code`() {
        val parsed = parse("QP-001 \"Transaction declined\"")
        parsed.providerName.shouldBeNull()
        parsed.version.shouldBeNull()
    }

    @Test
    fun `missing quotes is warned about with the line number`() {
        val parsed = parse("QP-001 \"Transaction declined\"", "", "", "", "", "", "QP-500 Card blocked", "    ignored")
        parsed.codes.map { it.code } shouldBe listOf("QP-001")
        parsed.warnings.single() shouldContain "line 7"
        parsed.warnings.single() shouldContain "QP-500 Card blocked"
    }

    @Test
    fun `unclosed quote is warned about`() {
        val parsed = parse("QP-501 \"Card blocked")
        parsed.codes.shouldBeEmpty()
        parsed.warnings shouldHaveSize 1
    }

    @Test
    fun `prose is not warned about`() {
        parse("All API responses include a result_code field.").warnings.shouldBeEmpty()
    }

    @Test
    fun `duplicate code keeps the first occurrence`() {
        val parsed = parse(
            "",
            "",
            "QP-001 \"Transaction declined\"",
            "    First.",
            "",
            "",
            "",
            "",
            "QP-001 \"Declined again\"",
            "    Second.",
        )
        parsed.codes shouldBe listOf(ProviderCode("QP-001", "Transaction declined", "First."))
        parsed.warnings.single() shouldContain "QP-001"
        parsed.warnings.single() shouldContain "line 9"
    }

    @Test
    fun `QuickPay example parses exactly`() {
        val parsed = parseDocumentation(Path("examples/providers/quickpay-global-v2.4.txt").readText())
        parsed.codes shouldHaveSize 26
        parsed.codes.first().code shouldBe "QP-001"
        parsed.codes.last().code shouldBe "QP-402"
        parsed.providerName shouldBe "QuickPay Global"
        parsed.version shouldBe "2.4"
        parsed.warnings.shouldBeEmpty()
        parsed.code("QP-001") shouldBe ProviderCode(
            "QP-001", "Transaction declined", "The acquiring bank declined the transaction. No further details available.",
        )
        parsed.code("QP-200") shouldBe ProviderCode(
            "QP-200", "Missing required field: {field_name}", "A mandatory request field is missing.",
        )
        parsed.code("QP-103").description shouldBe
            "Too many API requests in the current time window. Retry after the period indicated in `Retry-After` header."
    }

    @Test
    fun `VeloPay example parses exactly`() {
        val parsed = parseDocumentation(Path("examples/providers/velopay-systems-v1.3.txt").readText())
        parsed.codes shouldHaveSize 18
        parsed.codes.first().code shouldBe "1001"
        parsed.codes.last().code shouldBe "5002"
        parsed.providerName shouldBe "VeloPay Systems"
        parsed.version shouldBe "1.3"
        parsed.warnings.shouldBeEmpty()
        parsed.code("1003").description shouldBe
            "The card has been reported lost or stolen by its owner. The issuer has permanently blocked it."
        parsed.code("1004") shouldBe ProviderCode(
            "1004", "Card restricted",
            "The issuer has placed a restriction on this card. Restrictions may be geographic, " +
                "merchant-category based, or the result of issuer risk rules. The issuer does not say which one applies.",
        )
        parsed.code("5002") shouldBe ProviderCode(
            "5002", "Redirect not completed",
            "The shopper was redirected to the bank authentication page but never returned. " +
                "This happens when the shopper leaves, and also when the bank page fails to load.",
        )
    }
}
