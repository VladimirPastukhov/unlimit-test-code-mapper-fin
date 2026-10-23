# Deterministic consistency rules instead of an LLM reviewer

The validate/review stage has no second "critic" LLM pass. Hard failures (invalid JSON, a category outside the taxonomy, a disallowed retry strategy, missing fields or codes) are re-prompted up to `retry_budget` times (default 3). Inconsistencies are not re-prompted: `low` confidence forces Human Review, and `high` confidence whose reasoning contains a hedging phrase from a narrow, configurable list makes the code an Unmapped Code. A reviewer LLM would be a second unverified model judging the first, and its verdicts can't be pinned down in tests. Quality is instead guarded by canonical tests: a golden test with a scripted fake model, plus live tests against partial `expectations.json` files for QuickPay and for VeloPay, a held-out invented provider.

## Consequences

The hedging list has to stay narrow (no "may", "depends" or "either"). Otherwise clear high-confidence answers turn into Unmapped Codes.
