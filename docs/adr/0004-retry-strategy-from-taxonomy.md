# Retry Strategy is derived from the taxonomy, not chosen by the LLM

The Retry Strategy is looked up from the Decline Category, using the retryability column of the taxonomy. The LLM chooses it only for `COMMON_DECLINE` ("depends on sub-code"), from `no_retry | retry_after_fix | retry_with_backoff`. Mapping a terminal error as retryable is the costliest mistake the assignment names, and a lookup table can't hallucinate. To still cover idempotency guards such as a duplicate transaction, the LLM may answer `not_an_error: true` for any code. That sets `no_action` and always forces Human Review.
