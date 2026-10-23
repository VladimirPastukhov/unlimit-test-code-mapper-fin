Always add new files to git
Keep code simple and concise. The less code the better. Code should be human-readable.

## Before you change anything
- Don't add features the task does not ask for.
- Use the terms in `GLOSSARY.md` exactly, in code and docs. Never use the words listed under _Avoid_ (for example "Provider" on its own: say Payment Provider or LLM Vendor).
- Read `docs/adr/` before changing architecture. To deviate from an ADR, add a new ADR in the same change.

## Commands
- `./gradlew test`: unit tests. They never call a real LLM; they use the scripted fake `ChatModel`.
- `./gradlew jar && java -jar build/libs/code-mapper.jar examples/providers/quickpay-global-v2.4.txt`: run it. The JSON goes to stdout.

## Secrets
- Never commit API keys. They come only from environment variables (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`).
