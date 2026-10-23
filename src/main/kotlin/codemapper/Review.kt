package codemapper

/** Words in the reasoning that contradict a `high` Confidence. */
val HEDGING_PHRASES = listOf("ambiguous", "unclear", "could be", "might be", "cannot determine", "not clear", "uncertain")

const val LOW_CONFIDENCE_REASON = "Low confidence; confirm the Decline Category."
const val MISSING_REASON = "Flagged for review by the model without a reason; confirm the mapping."

private val hedging = HEDGING_PHRASES.map { it to Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE) }

/** Review stage: deterministic cross-field rules on each Mapping. Never calls the LLM (ADR-0005). */
fun review(mapping: Mapping): Mapping {
    if (mapping.internalCategory == null) return mapping
    if (mapping.confidence == Confidence.HIGH) {
        val text = "${mapping.reasoning} ${mapping.reviewReason.orEmpty()}"
        hedging.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.let { (phrase, _) ->
            return Mapping.unmapped(
                mapping.providerCode, mapping.providerMessage,
                "inconsistent answer: high confidence but the reasoning says \"$phrase\"",
            )
        }
    }
    val review = mapping.needsHumanReview || mapping.confidence == Confidence.LOW
    val reason = mapping.reviewReason
        ?: if (mapping.confidence == Confidence.LOW) LOW_CONFIDENCE_REASON else if (review) MISSING_REASON else null
    return mapping.copy(needsHumanReview = review, reviewReason = reason)
}
