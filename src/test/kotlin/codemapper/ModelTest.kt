package codemapper

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class ModelTest {
    private fun mapping(
        category: DeclineCategory? = DeclineCategory.ANTIFRAUD,
        confidence: Confidence = Confidence.HIGH,
        retry: RetryStrategy = RetryStrategy.NO_RETRY,
        review: Boolean = false,
        reason: String? = null,
    ) = Mapping("QP-006", "Suspected fraud", category, confidence, "Flagged by fraud detection.", retry, review, reason)

    private fun toJson(mapping: Mapping): Map<String, JsonElement> =
        Json.parseToJsonElement(ReportJson.encodeToString(mapping)).jsonObject

    @Test
    fun `empty report serialises to the PDF schema`() {
        val json = ReportJson.encodeToString(MappingReport.of("X", "1", emptyList()))
        Json.parseToJsonElement(json) shouldBe Json.parseToJsonElement(
            """{"provider":"X","version":"1","mappings":[],
               "summary":{"total_codes":0,"high_confidence":0,"needs_review":0,"unmapped":0}}"""
        )
    }

    @Test
    fun `mapped code serialises every field`() {
        val json = toJson(mapping())
        json.keys.toList() shouldContainExactly listOf(
            "provider_code", "provider_message", "internal_category", "confidence",
            "reasoning", "retry_strategy", "needs_human_review", "review_reason",
        )
        json["internal_category"].toString() shouldBe "\"ANTIFRAUD\""
        json["confidence"].toString() shouldBe "\"high\""
        json["retry_strategy"].toString() shouldBe "\"no_retry\""
        json["needs_human_review"].toString() shouldBe "false"
        json["review_reason"].toString() shouldBe "null"
    }

    @Test
    fun `unmapped code serialises null category`() {
        val json = toJson(mapping(category = null, confidence = Confidence.LOW, review = true, reason = "Retry budget exhausted"))
        json["internal_category"].toString() shouldBe "null"
        json["confidence"].toString() shouldBe "\"low\""
        json["needs_human_review"].toString() shouldBe "true"
        json["review_reason"]!!.jsonPrimitive.content shouldBe "Retry budget exhausted"
    }

    @Test
    fun `enum values serialise as documented`() {
        RetryStrategy.entries.map { toJson(mapping(retry = it))["retry_strategy"]!!.jsonPrimitive.content } shouldContainExactly
            listOf("no_retry", "retry_with_backoff", "retry_after_fix", "no_action")
        Confidence.entries.map { toJson(mapping(confidence = it))["confidence"]!!.jsonPrimitive.content } shouldContainExactly
            listOf("high", "medium", "low")
    }

    @Test
    fun `taxonomy has seven categories with descriptions`() {
        DeclineCategory.entries.map { it.name } shouldContainExactlyInAnyOrder listOf(
            "SYSTEM_MALFUNCTION", "COMMON_DECLINE", "ANTIFRAUD", "BAD_DATA_PROVIDED",
            "CANCELLED_BY_CUSTOMER", "PROVIDER_LIMIT", "AUTHENTICATION_FAILURE",
        )
        DeclineCategory.entries.forEach { it.description.shouldNotBeBlank() }
    }

    @Test
    fun `each category carries its retry rule`() {
        val rules = DeclineCategory.entries.associateWith { it.allowedRetries }
        rules shouldBe mapOf(
            DeclineCategory.SYSTEM_MALFUNCTION to setOf(RetryStrategy.RETRY_WITH_BACKOFF),
            DeclineCategory.PROVIDER_LIMIT to setOf(RetryStrategy.RETRY_WITH_BACKOFF),
            DeclineCategory.ANTIFRAUD to setOf(RetryStrategy.NO_RETRY),
            DeclineCategory.CANCELLED_BY_CUSTOMER to setOf(RetryStrategy.NO_RETRY),
            DeclineCategory.BAD_DATA_PROVIDED to setOf(RetryStrategy.RETRY_AFTER_FIX),
            DeclineCategory.AUTHENTICATION_FAILURE to setOf(RetryStrategy.RETRY_AFTER_FIX),
            DeclineCategory.COMMON_DECLINE to setOf(RetryStrategy.NO_RETRY, RetryStrategy.RETRY_AFTER_FIX, RetryStrategy.RETRY_WITH_BACKOFF),
        )
    }

    @Test
    fun `summary counts mixed mappings`() {
        val report = MappingReport.of("X", "1", listOf(
            mapping(),
            mapping(review = true, reason = "Check with issuer"),
            mapping(confidence = Confidence.MEDIUM),
            mapping(category = null, confidence = Confidence.LOW, review = true, reason = "Retry budget exhausted"),
        ))
        report.summary shouldBe Summary(totalCodes = 4, highConfidence = 2, needsReview = 2, unmapped = 1)
    }
}
