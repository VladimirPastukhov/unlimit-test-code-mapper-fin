package codemapper

/** One error/decline entry from the Provider Documentation. */
data class ProviderCode(val code: String, val name: String, val description: String)

/** What the parser found: the optional header values, the Provider Codes in input order, and warnings for stderr. */
data class ParsedDocumentation(
    val providerName: String?,
    val version: String?,
    val codes: List<ProviderCode>,
    val warnings: List<String>,
)

private val codeLine = Regex("""([A-Za-z0-9][A-Za-z0-9_.-]*)\s+"([^"]*)"\s*""")
private val looksLikeCode = Regex("""^(?=\S*\d)[A-Za-z0-9][A-Za-z0-9_.-]*(\s|$)""")
private val headerLine = Regex("""(.+?)\s+—\s+.*\(v([^)\s]+)\)""")

/** Parses the one supported Provider Documentation format (ADR-0001). */
fun parseDocumentation(text: String): ParsedDocumentation {
    val lines = text.lines()
    val header = lines.firstOrNull { it.isNotBlank() }?.let { headerLine.matchEntire(it.trim()) }
    val codes = LinkedHashMap<String, ProviderCode>()
    val firstSeenOn = mutableMapOf<String, Int>()
    val warnings = mutableListOf<String>()
    var open: Triple<String, String, MutableList<String>>? = null

    fun close() {
        open?.let { (code, name, description) -> codes[code] = ProviderCode(code, name, description.joinToString(" ")) }
        open = null
    }

    lines.forEachIndexed { index, line ->
        val lineNo = index + 1
        when {
            line.isBlank() -> close()
            line[0].isWhitespace() -> open?.third?.add(line.trim())
            else -> {
                close()
                val match = codeLine.matchEntire(line)
                val code = match?.groupValues?.get(1)
                when {
                    match == null -> if (looksLikeCode.containsMatchIn(line))
                        warnings += "warning: line $lineNo: malformed Provider Code line: $line"
                    code in firstSeenOn ->
                        warnings += "warning: line $lineNo: duplicate code $code (first on line ${firstSeenOn[code]})"
                    else -> {
                        firstSeenOn[code!!] = lineNo
                        open = Triple(code, match.groupValues[2], mutableListOf())
                    }
                }
            }
        }
    }
    close()
    return ParsedDocumentation(header?.groupValues?.get(1), header?.groupValues?.get(2), codes.values.toList(), warnings)
}
