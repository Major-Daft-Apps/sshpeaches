package com.majordaftapps.sshpeaches.app.ui.code

import io.github.rosemoe.sora.langs.textmate.MyState
import io.github.rosemoe.sora.langs.textmate.SafeTextMateLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateAnalyzer
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver
import io.github.rosemoe.sora.text.ContentLine
import java.io.File
import java.io.InputStream
import org.eclipse.tm4e.core.registry.IThemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) // Sora's Logger calls android.util.Log
@Config(sdk = [28], manifest = Config.NONE)
class SafeTextMateLanguageTest {

    @Before
    fun loadAssets() = synchronized(SafeTextMateLanguageTest::class.java) {
        if (loaded) return@synchronized
        val assets = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
        FileProviderRegistry.getInstance().addFileProvider(object : FileResolver {
            override fun resolveStreamByPath(path: String): InputStream? =
                File(assets, path).takeIf { it.isFile }?.inputStream()
        })
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        val themePath = "textmate/sshpeaches-dark.json"
        val theme = IThemeSource.fromInputStream(File(assets, themePath).inputStream(), themePath, null)
        ThemeRegistry.getInstance().loadTheme(ThemeModel(theme, "sshpeaches-dark").apply { isDark = true })
        loaded = true
    }

    private fun analyzer(language: TextMateLanguage) = language.analyzeManager as TextMateAnalyzer

    @Test
    fun aLineTm4eCannotParseIsShownPlainAndHighlightingContinues() {
        val emojiBeforeQuote = ContentLine("\uD83C\uDF05\"") // tm4e builds an invalid back-referenced pattern here.
        val nextLine = ContentLine("echo \"hi\" | grep h")
        val plain = runCatching { analyzer(TextMateLanguage.create("source.shell", false)).tokenizeLine(emojiBeforeQuote, null, 0) }
        assertNotNull("expected tm4e to fail on this line, so the test exercises the guard", plain.exceptionOrNull())
        val baseline = analyzer(SafeTextMateLanguage.create("source.shell", true)).tokenizeLine(nextLine, null, 0)
        assertTrue("an ordinary line should be highlighted: ${baseline.spans.size} spans", baseline.spans.size > 1)

        val safe = analyzer(SafeTextMateLanguage.create("source.shell", true))
        val failed = safe.tokenizeLine(emojiBeforeQuote, null, 0)
        assertEquals(1, failed.spans.size)
        // Sora hands every state to the analyzer; with autocomplete on (as in the app) it reads the identifiers.
        safe.onAddState(failed.state)
        safe.onAbandonState(failed.state)
        val next = safe.tokenizeLine(nextLine, failed.state, 1)
        assertEquals("the next line should be highlighted again", baseline.spans.size, next.spans.size)
    }

    @Test
    fun aStackOverflowInTm4eDoesNotEscape() {
        val line = ContentLine("<<<".repeat(3_000))
        val plainError = onSmallStack { analyzer(TextMateLanguage.create("source.shell", false)).tokenizeLine(line, null, 0) }
        assertTrue("expected tm4e to overflow here, got $plainError", plainError is StackOverflowError)

        val safe = analyzer(SafeTextMateLanguage.create("source.shell", true))
        var state: MyState? = null
        val error = onSmallStack { state = safe.tokenizeLine(line, null, 0).state }
        assertNull("tokenizing escaped with $error", error)
        assertNotNull(state)
    }

    /** Runs [block] on a 1 MiB stack, like a worker thread's, and returns what it threw. */
    private fun onSmallStack(block: () -> Unit): Throwable? {
        var error: Throwable? = null
        val thread = Thread(null, { error = runCatching(block).exceptionOrNull() }, "tokenize", 1L shl 20)
        thread.start()
        thread.join(120_000)
        return error
    }

    private companion object {
        var loaded = false
    }
}
