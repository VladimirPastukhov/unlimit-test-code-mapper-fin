# Decline Code Mapper

An internal tool for payment integration engineers that maps a Payment Provider's error codes onto our internal Decline Taxonomy when onboarding that provider.

## Provider side

**Payment Provider**:
An external payment company whose API we integrate with (e.g. QuickPay Global).
_Avoid_: Provider (on its own, because it clashes with LLM Vendor), PSP, gateway

**Provider Documentation**:
The raw text of a Payment Provider's error reference. It is the tool's only input.
_Avoid_: Spec, docs, error reference file

**Provider Code**:
One error/decline entry from the Provider Documentation, made of a code (e.g. `QP-006`), a name (the `message`), and a description.
_Avoid_: Decline code, error code, result code

## Internal side

**Decline Taxonomy**:
The fixed set of seven Decline Categories that every Provider Code must be mapped to.
_Avoid_: Categories list, classification

**Decline Category**:
One of `SYSTEM_MALFUNCTION`, `COMMON_DECLINE`, `ANTIFRAUD`, `BAD_DATA_PROVIDED`, `CANCELLED_BY_CUSTOMER`, `PROVIDER_LIMIT`, `AUTHENTICATION_FAILURE`.
_Avoid_: Internal code, type, class

**Retry Strategy**:
What our platform should do after a Provider Code is returned. One of `no_retry`, `retry_with_backoff`, `retry_after_fix`, or `no_action` (not a real error).
_Avoid_: Retry policy, retryable flag

## Result

**Mapping**:
The verdict for one Provider Code. It records the Decline Category, Confidence, reasoning, Retry Strategy, and whether Human Review is needed.
_Avoid_: Classification, result entry

**Mapping Report**:
The full output for one Provider Documentation input. It contains the provider name, the version, every Mapping, and a summary.
_Avoid_: Result, output, mapping file

**Confidence**:
How certain a Mapping is, rated `high`, `medium`, or `low`. `low` always implies Human Review.
_Avoid_: Score, certainty

**Human Review**:
A flag on a Mapping saying an engineer must confirm it. It always comes with a review reason that says why the Mapping is uncertain and what information would resolve it.
_Avoid_: Manual check, escalation

**Unmapped Code**:
A Provider Code for which no valid Mapping could be produced, because the retry budget ran out or the model's answer contradicted itself. It is still reported, with no Decline Category, and is flagged for Human Review.
_Avoid_: Skipped code, failed code

## LLM side

**LLM Vendor**:
The company or runtime that serves the language model (Anthropic, OpenAI, Ollama, …).
_Avoid_: Provider, LLM provider
