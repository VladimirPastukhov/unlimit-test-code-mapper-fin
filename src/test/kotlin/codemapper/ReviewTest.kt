package codemapper

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class ReviewTest {
    private val fraud = ProviderCode("QP-006", "Suspected fraud", "")

    private fun mapping(
        confidence: Confidence,
        reasoning: String = "Flagged by fraud detection.",
        review: Boolean = false,
        reason: String? = null,
    ) = Mapping("QP-006", "Suspected fraud", DeclineCategory.ANTIFRAUD, confidence, reasoning, RetryStrategy.NO_RETRY, review, reason)

    @Test
    fun `low confidence implies Human Review with a reason`() {
        val reviewed = review(mapping(Confidence.LOW))
        reviewed.needsHumanReview shouldBe true
        reviewed.reviewReason.shouldNotBeNull()
    }

    @Test
    fun `high confidence with hedging reasoning becomes an Unmapped Code`() {
        val reviewed = review(mapping(Confidence.HIGH, "It could be fraud or a card restriction."))
        reviewed.internalCategory shouldBe null
        reviewed.reviewReason!! shouldContain "\"could be\""
    }

    @Test
    fun `hedging in review_reason matches in any case`() {
        review(mapping(Confidence.HIGH, review = true, reason = "The scope is UNCLEAR.")).internalCategory shouldBe null
    }

    @Test
    fun `no false positive on may or depends`() {
        val clear = mapping(Confidence.HIGH, "The customer may add funds; success depends on the issuer.")
        review(clear) shouldBe clear
    }

    @Test
    fun `phrases match whole words only`() {
        val clear = mapping(Confidence.HIGH, "Card data uncertainty is not involved here.")
        review(clear) shouldBe clear
    }

    @Test
    fun `medium confidence may hedge`() {
        val hedged = mapping(Confidence.MEDIUM, "It could be fraud.", review = true, reason = "Check with the issuer.")
        review(hedged) shouldBe hedged
    }

    @Test
    fun `Human Review without a reason gets a default reason`() {
        val reviewed = review(mapping(Confidence.MEDIUM, review = true))
        reviewed.needsHumanReview shouldBe true
        reviewed.reviewReason.shouldNotBeNull()
    }


    @Test
    fun `Unmapped Code is returned unchanged`() {
        val unmapped = Mapping.unmapped(fraud, "invalid JSON")
        review(unmapped) shouldBe unmapped
    }
}
