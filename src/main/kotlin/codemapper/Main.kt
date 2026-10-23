package codemapper

import java.io.File
import kotlin.system.exitProcess

/**
 * Usage: `java -jar code-mapper.jar <provider-documentation.txt>`.
 * The Mapping Report goes to stdout; warnings, progress and errors go to stderr.
 */
fun main(args: Array<String>) {
    val path = args.singleOrNull() ?: fail("usage: java -jar code-mapper.jar <provider-documentation.txt>")
    val model = try {
        chatModelFromEnv()
    } catch (e: ConfigException) {
        fail(e.message!!)
    }
    val file = File(path)
    if (!file.isFile) fail("cannot read input: $path")

    val parsed = parseDocumentation(file.readText())
    parsed.warnings.forEach(System.err::println)
    if (parsed.codes.isEmpty()) fail("no Provider Codes found in $path")

    val report = runPipeline(parsed, model, log = { synchronized(System.err) { System.err.println(it) } })
    println(ReportJson.encodeToString(MappingReport.serializer(), report))
}

private fun fail(message: String): Nothing {
    System.err.println("error: $message")
    exitProcess(1)
}
