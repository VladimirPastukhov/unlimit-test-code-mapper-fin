package codemapper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class RetryStrategy {
    @SerialName("no_retry") NO_RETRY,
    @SerialName("retry_with_backoff") RETRY_WITH_BACKOFF,
    @SerialName("retry_after_fix") RETRY_AFTER_FIX,
    @SerialName("no_action") NO_ACTION;

    /** The JSON value, e.g. `no_retry`. */
    val wireName get() = name.lowercase()
}

@Serializable
enum class Confidence {
    @SerialName("high") HIGH,
    @SerialName("medium") MEDIUM,
    @SerialName("low") LOW,
}

/** The Decline Taxonomy. Each Decline Category carries its description and the Retry Strategies it allows. */
@Serializable
enum class DeclineCategory(val description: String, val allowedRetries: Set<RetryStrategy>) {
    SYSTEM_MALFUNCTION(
        "Internal system error, provider infrastructure failure, unexpected response",
        setOf(RetryStrategy.RETRY_WITH_BACKOFF),
    ),
    COMMON_DECLINE(
        "Generic transaction decline by the bank or provider (insufficient funds, limit exceeded, card expired)",
        setOf(RetryStrategy.NO_RETRY, RetryStrategy.RETRY_AFTER_FIX, RetryStrategy.RETRY_WITH_BACKOFF),
    ),
    ANTIFRAUD(
        "Transaction blocked by fraud detection (provider-side or bank-side)",
        setOf(RetryStrategy.NO_RETRY),
    ),
    BAD_DATA_PROVIDED(
        "Invalid input data: wrong account number, invalid currency, malformed request fields",
        setOf(RetryStrategy.RETRY_AFTER_FIX),
    ),
    CANCELLED_BY_CUSTOMER(
        "Customer explicitly cancelled, abandoned, or refused the transaction",
        setOf(RetryStrategy.NO_RETRY),
    ),
    PROVIDER_LIMIT(
        "Provider-side rate limit, daily/monthly volume cap, or account quota reached",
        setOf(RetryStrategy.RETRY_WITH_BACKOFF),
    ),
    AUTHENTICATION_FAILURE(
        "Invalid credentials, expired token, signature mismatch with the provider",
        setOf(RetryStrategy.RETRY_AFTER_FIX),
    ),
}

/** The verdict for one Provider Code. A null [internalCategory] marks an Unmapped Code. */
@Serializable
data class Mapping(
    @SerialName("provider_code") val providerCode: String,
    @SerialName("provider_message") val providerMessage: String,
    @SerialName("internal_category") val internalCategory: DeclineCategory?,
    val confidence: Confidence,
    val reasoning: String,
    @SerialName("retry_strategy") val retryStrategy: RetryStrategy,
    @SerialName("needs_human_review") val needsHumanReview: Boolean,
    @SerialName("review_reason") val reviewReason: String?,
) {
    companion object {
        /** An Unmapped Code: no Decline Category, low Confidence, flagged for Human Review with [reason]. */
        fun unmapped(code: String, message: String, reason: String) =
            Mapping(code, message, null, Confidence.LOW, reason, RetryStrategy.NO_RETRY, true, reason)

        fun unmapped(code: ProviderCode, reason: String) = unmapped(code.code, code.name, reason)
    }
}

@Serializable
data class Summary(
    @SerialName("total_codes") val totalCodes: Int,
    @SerialName("high_confidence") val highConfidence: Int,
    @SerialName("needs_review") val needsReview: Int,
    val unmapped: Int,
)

@Serializable
data class MappingReport(
    val provider: String,
    val version: String,
    val mappings: List<Mapping>,
    val summary: Summary,
) {
    companion object {
        fun of(provider: String, version: String, mappings: List<Mapping>) = MappingReport(
            provider, version, mappings,
            Summary(
                totalCodes = mappings.size,
                highConfidence = mappings.count { it.confidence == Confidence.HIGH },
                needsReview = mappings.count { it.needsHumanReview },
                unmapped = mappings.count { it.internalCategory == null },
            ),
        )
    }
}

val ReportJson = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = true
}
