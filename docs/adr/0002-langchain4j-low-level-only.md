# LangChain4j as the model transport only

We use LangChain4j so that any LLM Vendor can be selected from configuration: Anthropic natively, and OpenAI, Gemini, OpenRouter or Ollama through the OpenAI-compatible module with `base_url`. We call only the low-level `ChatModel.chat(messages)`. We do not use `AiServices`, typed outputs or LangChain4j agents. The assignment grades a visible, controlled agent: stage orchestration, JSON parsing, validation and re-prompting stay in our own code so that every recovery path from the "typical model errors" list can be seen and tested with a scripted fake `ChatModel`.

## Considered Options

- A hand-written HTTP client per vendor: full control, but it re-implements what LangChain4j already gives us.
- JetBrains Koog or LangChain4j `AiServices`: both hide JSON parsing and retries, which are exactly the parts we must show.
