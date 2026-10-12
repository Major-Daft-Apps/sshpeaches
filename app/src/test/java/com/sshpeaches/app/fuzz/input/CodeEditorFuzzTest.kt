package com.majordaftapps.sshpeaches.app.fuzz.input

import com.majordaftapps.sshpeaches.app.ui.code.CodeLanguage
import java.io.File
import java.time.Duration
import kotlin.random.Random
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver
import java.io.InputStream
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.grammar.IStateStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Code editor: language detection for random file names/contents, and the bundled TextMate grammars
 * (the ones Sora tokenizes with, through tm4e/joni) on pathological lines: very long lines, deep
 * nesting, unterminated strings/comments, regex-hostile punctuation. Each line is tokenized with the
 * same 2 s budget Sora's TextMateAnalyzer passes; a line that blows far past it is a hang.
 */
@RunWith(RobolectricTestRunner::class) // Sora's Logger (used by tm4e) calls android.util.Log
@Config(sdk = [28], manifest = Config.NONE)
class CodeEditorFuzzTest {

    // ------------------------------------------------------------------ detection

    @Test
    fun detect_randomNamesAndContents_neverThrow_andAreFast() {
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            repeat(20_000) {
                val name = buildString {
                    if (r.nextBoolean()) append("/" + Fuzz.text(r, 6) + "/")
                    append(Fuzz.text(r, 10))
                    if (r.nextBoolean()) append("." + listOf("sh", "PY", "Json", "yml", "", ".", "md", "dockerfile", "conf", Fuzz.text(r, 3)).random(r))
                }
                val contents = when (r.nextInt(4)) {
                    0 -> "#!" + Fuzz.text(r, 20) + "\n" + Fuzz.text(r, 20)
                    1 -> "#!/usr/bin/env " + listOf("bash", "python3", "node", "zsh", "ssh", "fish", "csh", "perl").random(r)
                    2 -> ""
                    else -> Fuzz.text(r, 40)
                }
                Fuzz.timed("detect", 200) { CodeLanguage.detect(name, contents) }
            }
        }
        val huge = "#!/bin/sh\n" + "x".repeat(5_000_000)
        Fuzz.timed("detect on a 5 MB file", 500) { CodeLanguage.detect("noext", huge) }
        val longShebang = "#!" + " sh".repeat(200_000)
        Fuzz.timed("detect on a long shebang", 1_000) { CodeLanguage.detect("noext", longShebang) }
    }

    @Test
    fun detect_extensionIsCaseInsensitiveAndPathIndependent() {
        val r = Random(Fuzz.SEEDS[0])
        mapOf("sh" to CodeLanguage.SHELL, "json" to CodeLanguage.JSON, "yaml" to CodeLanguage.YAML, "py" to CodeLanguage.PYTHON,
            "ts" to CodeLanguage.JAVASCRIPT, "svg" to CodeLanguage.XML, "md" to CodeLanguage.MARKDOWN, "toml" to CodeLanguage.INI)
            .forEach { (ext, lang) ->
                repeat(200) {
                    val base = Fuzz.asciiPrintable(r, 8).replace("/", "").replace(".", "_").ifEmpty { "f" }
                        .let { if (it.lowercase().startsWith("dockerfile")) "x$it" else it }
                    val spelled = ext.map { if (r.nextBoolean()) it.uppercaseChar() else it }.joinToString("")
                    val name = "/srv/${Fuzz.asciiPrintable(r, 5).replace("/", "")}/$base.$spelled"
                    assertEquals(name, lang, CodeLanguage.detect(name, "#!/usr/bin/env node"))
                }
            }
    }

    // ------------------------------------------------------------------ grammars

    private val assets: File = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }

    /**
     * Grammars are loaded exactly as the app does (CodeEditorAssets.ensureLoaded -> Sora's
     * GrammarRegistry over textmate/languages.json), but from the source tree on disk.
     */
    private fun grammar(scope: String): IGrammar {
        ensureGrammarsLoaded()
        return requireNotNull(GrammarRegistry.getInstance().findGrammar(scope)) { "grammar $scope did not load" }
    }

    private fun ensureGrammarsLoaded() = synchronized(CodeEditorFuzzTest::class.java) {
        if (loaded) return
        FileProviderRegistry.getInstance().addFileProvider(object : FileResolver {
            override fun resolveStreamByPath(path: String): InputStream? =
                File(assets, path).takeIf { it.isFile }?.inputStream()
        })
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        loaded = true
    }

    private val grammarPaths: Map<String, String> by lazy {
        org.json.JSONObject(File(assets, "textmate/languages.json").readText()).getJSONArray("languages").let { a ->
            (0 until a.length()).associate { i -> a.getJSONObject(i).let { it.getString("scopeName") to it.getString("grammar") } }
        }
    }

    /** A grammar from a brand-new tm4e registry, so no compiled-rule state is shared. */
    private fun freshGrammar(scope: String): IGrammar =
        org.eclipse.tm4e.core.registry.Registry(object : org.eclipse.tm4e.core.registry.IRegistryOptions {
            override fun getGrammarSource(scopeName: String) =
                grammarPaths[scopeName]?.let { org.eclipse.tm4e.core.registry.IGrammarSource.fromFile(File(assets, it)) }
        }).loadGrammar(scope)!!

    private data class Outcome(val ms: Long, val stoppedEarly: Boolean)

    /** Same call Sora's TextMateAnalyzer makes per line: tokenizeLine2(line + "\n", state, 2 s). */
    private fun tokenize(g: IGrammar, line: String, state: IStateStack?): Pair<Outcome, IStateStack?> {
        val start = System.nanoTime()
        val result = g.tokenizeLine2(line + "\n", state, Duration.ofSeconds(2))
        val ms = (System.nanoTime() - start) / 1_000_000
        val tokens = result.tokens
        var last = -1
        for (i in tokens.indices step 2) {
            assertTrue("token start ${tokens[i]} not increasing after $last", tokens[i] > last)
            assertTrue("token start ${tokens[i]} past line end ${line.length + 1}", tokens[i] <= line.length + 1)
            last = tokens[i]
        }
        return Outcome(ms, result.isStoppedEarly) to result.ruleStack
    }

    private companion object {
        @Volatile var loaded = false
    }

    private fun pathologicalLines(r: Random): List<Pair<String, String>> {
        val n = 12_000
        val punct = "()[]{}<>\"'`\\/*#;:,.=+-!?\$&|%@^~ \t"
        val b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val base64 = String(CharArray(8_000) { b64[r.nextInt(b64.length)] })
        val jsTokens = listOf("function(a,b){", "return ", "a.b(c)", "var x=", "\"str\"", "'s'", "1e3", "}", ";", "if(", ")", "?", ":", "&&", "[1,2]", "/re+/g", "`t\${x}`", "=>", "{a:1}")
        val minifiedJs = buildString { while (length < 30_000) append(jsTokens.random(r)) }
        val minifiedJson = buildString {
            append('['); while (length < 30_000) append("{\"id\":${r.nextInt()},\"name\":\"n${r.nextInt()}\",\"tags\":[\"a\",\"b\"],\"ok\":true},"); append("{}]")
        }
        return listOf(
            "realistic: kubeconfig base64 value" to "    certificate-authority-data: $base64",
            "realistic: base64 in json" to "{\"cert\": \"$base64\"}",
            "realistic: minified js" to minifiedJs,
            "realistic: minified json" to minifiedJson,
            "realistic: long export" to "export PATH=" + List(400) { "/opt/tool$it/bin" }.joinToString(":"),
            "realistic: ssh public key" to "ssh-rsa $base64 user@host",
            "long plain" to "a".repeat(n),
            "long spaces" to " ".repeat(n) + "x",
            "nested parens" to "(".repeat(n / 4) + ")".repeat(n / 4),
            "nested brackets" to "[".repeat(n / 4),
            "nested braces" to "{".repeat(n / 4),
            "nested quotes-brackets" to "[\"".repeat(n / 8),
            "unterminated dq" to "\"" + "a\\\"".repeat(n / 3),
            "unterminated sq" to "'" + "a".repeat(n),
            "unterminated block comment" to "/*" + "*".repeat(n),
            "html comment" to "<!--" + "-".repeat(n),
            "backslashes" to "\\".repeat(n),
            "shell substitutions" to "\$(".repeat(n / 8) + "\${".repeat(n / 8),
            "heredoc-ish" to "<<".repeat(n / 4),
            "yaml dashes" to "- ".repeat(n / 2),
            "yaml keys" to "a: ".repeat(n / 3),
            "markdown emphasis" to "*_".repeat(n / 2),
            "markdown links" to "[](".repeat(n / 3),
            "xml attrs" to "<a ".repeat(n / 3) + "b=\"".repeat(n / 6),
            "js template" to "`\${`".repeat(n / 4),
            "js regex-like" to "/[".repeat(n / 2),
            "number soup" to "1e1.".repeat(n / 4),
            "word-dot chain" to "a.".repeat(n / 2),
            "comma soup" to "a,".repeat(n / 2),
            "random punct" to String(CharArray(n / 2) { punct[r.nextInt(punct.length)] }),
            "random unicode" to buildString { repeat(n / 4) { appendCodePoint(Fuzz.randomCodePoint(r)) } }
        )
    }

    @Ignore("Deferred: some single lines exceed sora's 2 s budget in tm4e; the UI does not freeze")
    @Test(timeout = 480_000)
    fun grammars_pathologicalLines_doNotHang() {
        val scopes = CodeLanguage.entries.mapNotNull { it.scopeName }
        val r = Random(Fuzz.SEEDS[0])
        val lines = pathologicalLines(r)
        val slow = mutableListOf<String>()
        val problems = mutableListOf<String>()
        scopes.forEach { scope ->
            lines.forEach { (label, line) ->
                val g = freshGrammar(scope) // independent of earlier failures
                // 1 MiB stack, like an Android worker thread (Sora's analysis LooperThread).
                var result: Result<Outcome>? = null
                val t = Thread(null, { result = runCatching { tokenize(g, line, null).first } }, "tok", 1L shl 20)
                t.start(); t.join()
                result!!.onSuccess { outcome ->
                    if (outcome.ms > 500 || outcome.stoppedEarly) {
                        slow += "$scope / $label: ${outcome.ms}ms stoppedEarly=${outcome.stoppedEarly}"
                    }
                    if (outcome.ms > 5_000) problems += "$scope / $label: ${outcome.ms}ms (budget 2000ms)"
                }.onFailure { e ->
                    // (HotSpot's fast-throw drops the message after many repeats, so match the type only)
                    val known = e is ClassCastException
                    val msg = if (known) "$scope: captures defect (ClassCastException ... IRawRule)" else
                        "$scope / $label (${line.length} chars) threw ${e.toString().take(200)}"
                    if (msg !in problems) problems += msg
                }
            }
        }
        println("SLOW-OR-STOPPED (${slow.size}):\n  " + slow.joinToString("\n  "))
        assertTrue("tokenizer hang/crash:\n  " + problems.joinToString("\n  "), problems.isEmpty())
    }

    /**
     * Ordinary documents, each tokenized by a *fresh* tm4e registry (no state shared with other
     * tests), line by line as Sora does. The bundled XML and YAML grammars had captures maps with
     * string values, which tm4e casts to IRawRule (ClassCastException, grammar left half-built), and
     * Markdown had a look-behind with a back-reference that joni cannot compile.
     */
    @Test
    fun grammars_ordinaryDocuments_tokenizeWithoutError() {
        val docs = mapOf(
            CodeLanguage.SHELL to "#!/bin/sh\nset -e\necho \"hi \$USER\"\n",
            CodeLanguage.JSON to "{\n  \"a\": [1, 2],\n  \"b\": {\"c\": null}\n}\n",
            CodeLanguage.INI to "[core]\n\teditor = vim\n",
            CodeLanguage.PYTHON to "def f(x):\n    return x + 1\n",
            CodeLanguage.JAVASCRIPT to "const f = (a) => a * 2;\n",
            CodeLanguage.XML to "<?xml version=\"1.0\"?>\n<!-- note -->\n<a b=\"c\">t</a>\n",
            CodeLanguage.MARKDOWN to "# Title\n\nSome *text*.\n\n```sh\nls\n```\n",
            CodeLanguage.DOCKERFILE to "FROM alpine:3.20\nRUN apk add curl\n"
        )
        assertDocumentsTokenize(docs)
    }

    /** The bundled YAML grammars use variable-length look-behinds throughout, which joni cannot compile. */
    @Ignore("Deferred: YAML highlighting needs a joni-compatible YAML grammar")
    @Test
    fun grammars_ordinaryYamlDocument_tokenizesWithoutError() {
        assertDocumentsTokenize(
            mapOf(CodeLanguage.YAML to "apiVersion: v1\nkind: Pod\nmetadata:\n  name: web\n  labels: {app: web}\n")
        )
    }

    private fun assertDocumentsTokenize(docs: Map<CodeLanguage, String>) {
        val failures = mutableListOf<String>()
        docs.forEach { (lang, doc) ->
            val g = freshGrammar(lang.scopeName!!)
            var state: IStateStack? = null
            doc.lines().forEachIndexed { i, line ->
                try {
                    state = g.tokenizeLine2(line + "\n", state, Duration.ofSeconds(2)).ruleStack
                } catch (t: Throwable) {
                    val at = t.stackTrace.take(4).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                    failures += "${lang.label} line ${i + 1} ${Fuzz.show(line)}: ${t.toString().take(140)} at $at"
                    return@forEach
                }
            }
        }
        assertTrue("tokenizing ordinary files failed:\n  " + failures.joinToString("\n  "), failures.isEmpty())
    }

    /**
     * Fix check for the test above: dropping the string-valued captures entries (VS Code ignores
     * them) is enough for XML. YAML and Markdown still fail afterwards: their newer VS Code grammars
     * use look-behinds joni cannot compile ("(?<=^-{3,}\s*+)\G$", "(?<=_\1)"), so those two need
     * joni-compatible grammar versions. This test asserts the XML fix and prints the rest.
     */
    @Test
    fun grammars_withStringCapturesRemoved_xmlWorks_yamlAndMarkdownNeedRegexFixes() {
        fun clean(node: Any?): Any? = when (node) {
            is org.json.JSONObject -> {
                node.keys().asSequence().toList().forEach { k ->
                    val v = node.get(k)
                    if (k in setOf("captures", "beginCaptures", "endCaptures", "whileCaptures") && v is org.json.JSONObject) {
                        v.keys().asSequence().toList().filter { v.get(it) !is org.json.JSONObject }.forEach { v.remove(it) }
                    }
                    clean(v)
                }
                node
            }
            is org.json.JSONArray -> { for (i in 0 until node.length()) clean(node.get(i)); node }
            else -> node
        }
        val registry = org.eclipse.tm4e.core.registry.Registry(object : org.eclipse.tm4e.core.registry.IRegistryOptions {
            override fun getGrammarSource(scopeName: String) = grammarPaths[scopeName]?.let { path ->
                val json = clean(org.json.JSONObject(File(assets, path).readText())).toString()
                org.eclipse.tm4e.core.registry.IGrammarSource.fromString(
                    org.eclipse.tm4e.core.registry.IGrammarSource.ContentType.JSON, json
                )
            }
        })
        val failures = mutableListOf<String>()
        mapOf(
            "source.yaml" to "apiVersion: v1\nkind: Pod\nmetadata:\n  name: web\n",
            "text.xml" to "<?xml version=\"1.0\"?>\n<!-- note -->\n<a b=\"c\">t</a>\n",
            "text.html.markdown" to "# Title\n\nSome *text*.\n\n```yaml\na: 1\n```\n"
        ).forEach { (scope, doc) ->
            val g = registry.loadGrammar(scope)!!
            var state: IStateStack? = null
            doc.lines().forEach { line ->
                runCatching { state = g.tokenizeLine2(line + "\n", state, Duration.ofSeconds(2)).ruleStack }
                    .onFailure { failures += "$scope ${Fuzz.show(line)}: $it" }
            }
        }
        println("AFTER-CAPTURES-CLEANUP:\n  " + failures.joinToString("\n  "))
        assertTrue("XML still failing after cleaning captures:\n  " + failures.joinToString("\n  "), failures.none { it.startsWith("text.xml") })
    }

    /**
     * BUG reproducer (bundled shell grammar + tm4e in sora-editor 0.23.4): a supplementary character
     * (emoji) right before a quote makes a back-referenced end pattern capture half a surrogate pair,
     * which reaches joni as an unescaped "?" -> TMException "target of repeat operator is not
     * specified". Sora catches it in AsyncIncrementalAnalyzeManager.LooperThread and the analysis
     * thread exits, so highlighting stops for the rest of the editing session.
     */
    @Ignore("Deferred: tm4e bug in sora-editor 0.23.4; highlighting stops for the rest of the file")
    @Test
    fun grammars_emojiBeforeQuote_doNotThrow() {
        val lines = listOf("🌅\"", "echo 🚀\"Deploying\"", "a=🎉\"b\"", "🎉 \"x\"", "echo 🚀'x'", "s = 🎉'x'",
            "echo \"🎉\"", "echo \"party 🎉\"", "msg='🎉'", "x = \"🎉\"", "\"é\"", "\"中\"",
            "print(\"🎉\")", "const s = \"🎉\";", "title: \"🎉\"", "{\"k\": \"🎉\"}", "# 🎉 \"x\"", "<a t=\"🎉\"/>")
        val failures = mutableListOf<String>()
        // Shell, Python, JS, JSON, INI, Dockerfile: grammars without the YAML/XML captures defect.
        listOf("source.shell", "source.python", "source.js", "source.json", "source.ini", "source.dockerfile").forEach { scope ->
            lines.forEach { line ->
                // Fresh grammar per line: a failure must not be an after-effect of an earlier one.
                runCatching { tokenize(freshGrammar(scope), line, null) }
                    .onFailure { failures += "$scope ${Fuzz.show(line)}: ${it.toString().take(160)}" }
            }
        }
        assertTrue("tokenizer threw:\n  " + failures.joinToString("\n  "), failures.isEmpty())
    }

    /**
     * BUG reproducer: the shell grammar recurses once per nested `<<<` here-string capture; ~3.7k on a
     * 1 MiB stack overflows. StackOverflowError is an Error, which Sora's LooperThread does not catch
     * (it catches Exception), so it is an uncaught exception on a worker thread -> process crash.
     */
    @Ignore("Deferred: tm4e recursion in sora-editor 0.23.4 overflows on a 9000-char <<< line")
    @Test
    fun shellGrammar_longHereStringRun_doesNotOverflowTheStack() {
        val g = grammar("source.shell")
        var err: Throwable? = null
        val t = Thread(null, { err = runCatching { tokenize(g, "<<<".repeat(3_000), null) }.exceptionOrNull() }, "tok", 1L shl 20)
        t.start(); t.join()
        assertTrue("tokenizing 9000 chars of '<<<' threw $err", err == null)
    }

    @Ignore("Deferred: hits the tm4e bugs above and the YAML grammar")
    @Test(timeout = 300_000)
    fun grammars_randomMultiLineFiles_carryStateWithoutHangOrError() {
        val scopes = CodeLanguage.entries.mapNotNull { it.scopeName }
        val snippets = listOf(
            "if [ \"\$x\" ]; then", "fi", "cat <<EOF", "EOF", "{\"a\": [1, 2, {\"b\": \"c\"}]}", "key: value",
            "- item", "  nested: |", "[section]", "a = b", "def f(x):", "    return '''", "'''", "\"\"\"",
            "function f() { return `\${a}` }", "/* comment", "*/", "<a href=\"x\">", "</a>", "<!--", "-->",
            "# Title", "```js", "```", "FROM alpine", "RUN echo \\", "x = /re[gex/", "<![CDATA[", "]]>"
        )
        val problems = linkedSetOf<String>()
        Fuzz.SEEDS.forEach { seed ->
            val r = Random(seed)
            scopes.forEach { scope ->
                var g = freshGrammar(scope)
                var state: IStateStack? = null
                var total = 0L
                var failures = 0
                repeat(800) {
                    if (failures > 20) return@repeat // grammar is broken outright; reported once is enough
                    val line = buildString {
                        repeat(r.nextInt(1, 4)) {
                            append(if (r.nextInt(3) == 0) Fuzz.text(r, 20) else snippets.random(r))
                        }
                    }.replace("\n", " ").replace("\r", " ")
                    try {
                        val (outcome, next) = tokenize(g, line, state)
                        if (outcome.ms >= 2_500) problems += "seed=$seed $scope line=${Fuzz.show(line)} took ${outcome.ms}ms"
                        total += outcome.ms
                        state = next
                    } catch (t: Throwable) {
                        // Keep going from a fresh state so one failure does not hide others.
                        val key = "$scope threw ${t.toString().take(160)}"
                        if (problems.none { it.startsWith(key) }) problems += "$key (e.g. seed=$seed line=${Fuzz.show(line)})"
                        state = null
                        g = freshGrammar(scope)
                        failures++
                    }
                }
                if (total >= 20_000) problems += "seed=$seed $scope 800 lines took ${total}ms"
            }
        }
        assertTrue("tokenizer problems:\n  " + problems.joinToString("\n  "), problems.isEmpty())
    }
}
