package j2keval

import java.nio.file.Path

/**
 * Per-sample structured result. One of these per .kt file scored. Serialized
 * as JSONL (one object per line) so downstream tools -- multi-agent
 * benchmarking dashboards, RL dataset extraction, anything that needs to
 * diff "Claude vs J2K vs GPT-5 on the same Java input" -- can join on
 * `(corpus, source, file)` without re-parsing markdown.
 *
 * Schema is intentionally permissive (a lot of nullable fields). The eval
 * doesn't always have everything: a corpus might be Kotlin-only with no
 * paired Java input, hypothesis checks are opt-in via --expectations=.
 *
 * Bump `SCHEMA_VERSION` whenever a field changes meaning. Adding optional
 * fields doesn't require a bump.
 */
data class SampleResult(
    val schemaVersion: Int = SCHEMA_VERSION,
    val corpus: String,
    val source: String,                    // "j2k", "claude-sonnet-4-6", "gpt-5", ...
    val file: String,                      // relpath under corpus
    val javaInput: String?,                // relpath of paired .java if any
    val compile: CompileBlock,
    val metricsRegex: MetricsRegexBlock,
    val metricsPsi: MetricsPsiBlock?,      // null if PSI scan failed/skipped
    val metricsJava: MetricsJavaBlock? = null,   // null if no paired .java
    val hypotheses: List<HypothesisBlock>,
    val baseline: BaselineBlock? = null,   // null if --baseline-corpus= not set
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

data class MetricsJavaBlock(
    val loc: Int,
    val parseFailed: Boolean,
    val tryWithResourceCount: Int,
    val resourceCount: Int,
    val anonymousClassExprs: Int,
    val staticFinalFields: Int,
    val staticFinalLiteralFields: Int,
    val varargParameters: Int,
    val innerClassDecls: Int,
    val singleAbstractMethodInterfaces: Int,
)

data class BaselineBlock(
    val identical: Boolean,
    val deltaCount: Int,
    val baselineMissing: Boolean,
    val unifiedDiff: String?,
)

data class CompileBlock(
    val ok: Boolean,
    val errors: List<String>,
    val durationMs: Long,
)

data class MetricsRegexBlock(
    val loc: Int,
    val notNullAsserts: Int,
    val anonObjects: Int,
    val funInterface: Int,
    val constVal: Int,
    val plainVal: Int,
    val constEligibleVal: Int,
    val throwsAnnotations: Int,
    val innerClass: Int,
    val vararg_: Int,
    val useBlocks: Int,
)

data class MetricsPsiBlock(
    val loc: Int,
    val notNullAsserts: Int,
    val objectLiteralExprs: Int,
    val funInterfaces: Int,
    val constVals: Int,
    val plainVals: Int,
    val constEligibleVals: Int,
    val innerClasses: Int,
    val varargParams: Int,
)

data class HypothesisBlock(
    val tag: String,
    val passed: Boolean,
    val shouldMatch: Boolean,
    val pattern: String,
    val expectation: String,
    val sample: String?,
)

object Jsonl {

    fun write(out: Path, results: List<SampleResult>) {
        out.toFile().parentFile?.mkdirs()
        out.toFile().bufferedWriter().use { w ->
            for (r in results) {
                w.write(encode(r))
                w.newLine()
            }
        }
    }

    fun encode(r: SampleResult): String {
        val sb = StringBuilder()
        sb.append('{')
        val c = Ctx(sb)
        c.kv("schema_version", r.schemaVersion)
        c.kv("corpus", r.corpus)
        c.kv("source", r.source)
        c.kv("file", r.file)
        c.kvNullable("java_input", r.javaInput)
        c.kvObj("compile") {
            kv("ok", r.compile.ok)
            kvArrStr("errors", r.compile.errors)
            kv("duration_ms", r.compile.durationMs)
        }
        c.kvObj("metrics_regex") {
            with(r.metricsRegex) {
                kv("loc", loc)
                kv("not_null_asserts", notNullAsserts)
                kv("anon_objects", anonObjects)
                kv("fun_interface", funInterface)
                kv("const_val", constVal)
                kv("plain_val", plainVal)
                kv("const_eligible_val", constEligibleVal)
                kv("throws_annotations", throwsAnnotations)
                kv("inner_class", innerClass)
                kv("vararg", vararg_)
                kv("use_blocks", useBlocks)
            }
        }
        if (r.metricsPsi != null) {
            c.kvObj("metrics_psi") {
                with(r.metricsPsi) {
                    kv("loc", loc)
                    kv("not_null_asserts", notNullAsserts)
                    kv("object_literal_exprs", objectLiteralExprs)
                    kv("fun_interfaces", funInterfaces)
                    kv("const_vals", constVals)
                    kv("plain_vals", plainVals)
                    kv("const_eligible_vals", constEligibleVals)
                    kv("inner_classes", innerClasses)
                    kv("vararg_params", varargParams)
                }
            }
        }
        if (r.metricsJava != null) {
            c.kvObj("metrics_java") {
                with(r.metricsJava) {
                    kv("loc", loc)
                    kv("parse_failed", parseFailed)
                    kv("try_with_resource_count", tryWithResourceCount)
                    kv("resource_count", resourceCount)
                    kv("anonymous_class_exprs", anonymousClassExprs)
                    kv("static_final_fields", staticFinalFields)
                    kv("static_final_literal_fields", staticFinalLiteralFields)
                    kv("vararg_parameters", varargParameters)
                    kv("inner_class_decls", innerClassDecls)
                    kv("single_abstract_method_interfaces", singleAbstractMethodInterfaces)
                }
            }
        }
        c.kvArr("hypotheses") {
            for (h in r.hypotheses) {
                obj {
                    kv("tag", h.tag)
                    kv("passed", h.passed)
                    kv("should_match", h.shouldMatch)
                    kv("pattern", h.pattern)
                    kv("expectation", h.expectation)
                    kvNullable("sample", h.sample)
                }
            }
        }
        if (r.baseline != null) {
            c.kvObj("baseline") {
                kv("identical", r.baseline.identical)
                kv("delta_count", r.baseline.deltaCount)
                kv("baseline_missing", r.baseline.baselineMissing)
                kvNullable("unified_diff", r.baseline.unifiedDiff)
            }
        }
        sb.append('}')
        return sb.toString()
    }

    // hand-rolled JSON encoder. one object per output line; no pretty
    // printing. enough to round-trip ints, longs, booleans, strings, lists
    // of strings, and nested objects -- which is all the schema needs.

    private class Ctx(val sb: StringBuilder) {
        private var first = true
        private fun comma() { if (!first) sb.append(','); first = false }
        fun kv(name: String, v: Int) { comma(); writeKey(sb, name); sb.append(v) }
        fun kv(name: String, v: Long) { comma(); writeKey(sb, name); sb.append(v) }
        fun kv(name: String, v: Boolean) { comma(); writeKey(sb, name); sb.append(if (v) "true" else "false") }
        fun kv(name: String, v: String) { comma(); writeKey(sb, name); writeStr(sb, v) }
        fun kvNullable(name: String, v: String?) {
            comma(); writeKey(sb, name)
            if (v == null) sb.append("null") else writeStr(sb, v)
        }
        fun kvArrStr(name: String, items: List<String>) {
            comma(); writeKey(sb, name); sb.append('[')
            items.forEachIndexed { i, s -> if (i > 0) sb.append(','); writeStr(sb, s) }
            sb.append(']')
        }
        fun kvObj(name: String, body: Ctx.() -> Unit) {
            comma(); writeKey(sb, name); sb.append('{')
            Ctx(sb).body()
            sb.append('}')
        }
        fun kvArr(name: String, body: ArrCtx.() -> Unit) {
            comma(); writeKey(sb, name); sb.append('[')
            ArrCtx(sb).body()
            sb.append(']')
        }
    }

    private class ArrCtx(val sb: StringBuilder) {
        private var first = true
        fun obj(body: Ctx.() -> Unit) {
            if (!first) sb.append(','); first = false
            sb.append('{')
            Ctx(sb).body()
            sb.append('}')
        }
    }

    private fun writeKey(sb: StringBuilder, name: String) {
        writeStr(sb, name)
        sb.append(':')
    }

    private fun writeStr(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code == 0x08 -> sb.append("\\b")
                c.code == 0x0C -> sb.append("\\f")
                c.code < 0x20 -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }
}
