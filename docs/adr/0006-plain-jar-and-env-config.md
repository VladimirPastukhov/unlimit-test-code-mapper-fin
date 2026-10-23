# Plain jar, configuration from environment variables

The tool is one runnable jar with a single argument, the Provider Documentation file: `java -jar code-mapper.jar <file>`. There is no CLI library, no `code-mapper.yaml` and no LLM Profiles. The LLM is chosen with environment variables, which is what the assignment asks for: `LLM_VENDOR` (`anthropic` or `openai`, default `anthropic`), `LLM_MODEL`, `LLM_BASE_URL`, and the vendor's key variable (`ANTHROPIC_API_KEY` or `OPENAI_API_KEY`). The retry budget (3) and the hedging phrases are constants.

This narrows two earlier decisions. ADR-0003: codes are mapped on a parallel stream instead of with bounded coroutine concurrency, and a code whose LLM call throws becomes an Unmapped Code at once instead of being re-run; LangChain4j already retries failed HTTP calls. ADR-0005: the hedging list is fixed, and quality is guarded by unit tests with the scripted fake `ChatModel` only, without the golden and live tests.

## Considered Options

- Keep the YAML config, profiles, extra commands and flags: flexible, but most of the code and tests served features the assignment does not ask for.
- Drop LangChain4j for a hand-written HTTP client (tried on the `oversimplified` branch): fewer dependencies, but only one LLM Vendor and a rewrite of a working agent.
