package codemapper

import dev.langchain4j.model.chat.ChatModel

/**
 * Turns parsed Provider Documentation into a Mapping Report: the map and validate stages run one [Mapper]
 * call per Provider Code in parallel, then the review stage checks each Mapping. A code whose LLM call
 * fails becomes an Unmapped Code. Mappings keep input order.
 */
fun runPipeline(parsed: ParsedDocumentation, model: ChatModel, log: (String) -> Unit = {}): MappingReport {
    val mapper = Mapper(model, log = log)
    val mappings = parsed.codes.parallelStream().map { code ->
        val mapping = try {
            review(mapper.mapCode(code))
        } catch (e: Exception) {
            Mapping.unmapped(code, "LLM call failed: ${e.message}")
        }
        log("${code.code} → ${mapping.internalCategory?.name ?: "UNMAPPED"} (${
            if (mapping.internalCategory == null) mapping.reviewReason else mapping.confidence.name.lowercase()
        })")
        mapping
    }.toList()
    return MappingReport.of(parsed.providerName ?: "default_provider", parsed.version ?: "default_version", mappings)
}
