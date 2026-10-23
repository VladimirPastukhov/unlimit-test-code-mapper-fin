# code-mapper: AI Decline Code Mapper

## Initial doubts

- **LLM or a decision-making model?** I considered a decision-making model such as JEV, or an embedded decision model, instead of an LLM. I decided it is overkill: the input is small (at most thousands of Provider Codes), and such a model cannot produce the reasoning field the task requires.
- **Batch queries to the LLM?** I decided not to use them, again because the input is small.

A CLI for payment integration engineers. It reads a Payment Provider's error-code documentation (plain text) and maps every Provider Code onto our internal Decline Taxonomy. The output is a Mapping Report in JSON: for each code, a Decline Category, a Confidence, a short reasoning, a Retry Strategy, and a Human Review flag with its reason.

Terms used here (Provider Code, Mapping, Unmapped Code, …) are defined in [GLOSSARY.md](GLOSSARY.md). Architecture decisions are in [docs/adr/](docs/adr/).

## How to run

Requires JDK 17.

```bash
./gradlew jar
export ANTHROPIC_API_KEY=sk-ant-...
java -jar build/libs/code-mapper.jar examples/providers/quickpay-global-v2.4.txt > quickpay.report.json
```

The only argument is the Provider Documentation file. The Mapping Report goes to stdout. Parse warnings, progress (one line per code, e.g. `QP-006 → ANTIFRAUD (high)`) and retry notices go to stderr. Exit code 0 means success, even when codes need Human Review. Exit code 1 means a fatal error: wrong arguments, a missing API key, unreadable input, or no Provider Codes found.

### LLM settings

All settings are environment variables ([ADR-0006](docs/adr/0006-plain-jar-and-env-config.md)):

| Variable | Default | Meaning |
|---|---|---|
| `LLM_VENDOR` | `anthropic` | `anthropic`, or `openai` for OpenAI and any OpenAI-compatible runtime |
| `LLM_MODEL` | `claude-sonnet-5-5` / `gpt-5-mini` | model ID |
| `LLM_BASE_URL` | vendor default | e.g. `http://localhost:11434/v1` for Ollama |
| `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` | | API key for the chosen vendor (any value for Ollama) |

## How the agent works

The LLM is one controlled component inside a pipeline of five explicit stages. Only the map stage talks to the model.

1. **Parse** (`Parser.kt`, deterministic, [ADR-0001](docs/adr/0001-deterministic-parser-single-format.md)):
   - Reads the header (`<Name> — … (v<version>)`) and the Provider Codes: an unindented `CODE "name"` line followed by indented description lines.
   - Warns about malformed lines and duplicate codes.
   - Every later check relies on this list being exact, so no LLM is involved here.
2. **Enrich** (`Mapper.kt`, `prompts/mapping-system.txt`):
   - Builds the system prompt: the seven Decline Categories with their descriptions and allowed Retry Strategies (rendered from the `DeclineCategory` enum), the JSON answer format, the rules, and fixed few-shot examples.
   - The examples are the 19 QuickPay codes marked `in_prompt` plus two invented codes. No VeloPay code appears in any prompt.
3. **Map** (`Mapper.kt`, `Pipeline.kt`, [ADR-0003](docs/adr/0003-one-llm-call-per-provider-code.md)):
   - One LangChain4j `ChatModel` call per Provider Code; codes are mapped in parallel.
   - The pipeline, not the LLM, fills in `provider_code` and `provider_message`.
   - The Retry Strategy comes from the taxonomy table ([ADR-0004](docs/adr/0004-retry-strategy-from-taxonomy.md)). The LLM chooses it only for COMMON_DECLINE.
   - `not_an_error: true` (e.g. an idempotency guard) gives `no_action` and Human Review.
4. **Validate and recover** (`Mapper.kt`, `Pipeline.kt`, `prompts/corrections/`):
   - Hard failures are re-prompted in the same conversation with a correction for that failure type, up to 3 times. The failures are: invalid JSON, a category outside the taxonomy (the correction re-sends the taxonomy), a disallowed COMMON_DECLINE retry, and a missing or invalid field.
   - When the budget runs out, or the LLM call itself fails, the code becomes an Unmapped Code: no category, `low` Confidence, Human Review, and a reason.
5. **Review** (`Review.kt`, [ADR-0005](docs/adr/0005-deterministic-consistency-no-llm-reviewer.md)): deterministic cross-field rules, with no second LLM and no re-prompt.
   - `low` Confidence ⇒ Human Review.
   - `high` Confidence with a hedging phrase ("could be", "unclear", …) in the reasoning ⇒ Unmapped Code.
   - Human Review always comes with a review reason.

Finally, `MappingReport.of` computes the summary: total codes, high confidence, needs review, unmapped.

## How it was tested

- **Unit tests** (`./gradlew test`) never call a real LLM. They use a scripted fake `ChatModel` that replies by Provider Code and attempt, so parallel calls don't make results flaky. They cover:
  - the parser on both example providers and on edge cases;
  - the prompt contents, and that no VeloPay code appears in any prompt;
  - every failure type recovering on re-prompt, and the budget running out;
  - a failed LLM call becoming an Unmapped Code;
  - each review rule, including no false positives on "may" or "depends";
  - the LLM settings from environment variables.
- **Manual runs** of the jar on [QuickPay Global](examples/providers/quickpay-global-v2.4.txt) from the assignment and on [VeloPay Systems](examples/providers/velopay-systems-v1.3.txt), an invented provider with 18 codes that never appears in a prompt, as the generalisation test.

## Trade-offs and future work

What was simplified to fit the ~3-hour budget:
- **One input format.** The parser handles the documented layout only. Tables, HTML or PDF have to be converted first. With more time, more parsers would sit behind the same interface.
- **One call per code, no batching.** This isolates failures and keeps prompts small, but costs 26 calls for QuickPay and gives the model no cross-code context.
- **A narrow hedging list.** It catches obvious contradictions without flagging clear answers. It cannot detect subtle overconfidence.
- **No automated accuracy check.** Unit tests pin down the agent's control flow with a fake model; mapping quality on a real model is checked by hand.

What would come next:
- A decline-code database to reuse earlier Mappings as examples.
- Batch processing of several Payment Providers.
- An interactive disambiguation UI for codes that need Human Review.
- Caching the system prompt with the LLM Vendor.
- Live tests against a real model with expected categories per code, plus more invented providers for held-out testing.
