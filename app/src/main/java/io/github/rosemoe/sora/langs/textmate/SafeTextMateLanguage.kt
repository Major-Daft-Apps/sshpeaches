// In sora-editor's package so it can replace TextMateLanguage's package-private analyzer.
package io.github.rosemoe.sora.langs.textmate

import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.util.ArrayList
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration

/**
 * TextMate highlighting that survives tm4e failures. tm4e (sora-editor 0.23.4) throws on some lines, e.g. an emoji
 * right before a quote in shell or Python, or a heredoc delimiter with `)` in it, and overflows the stack on others
 * (a long run of `<<<`). sora's analyzer thread only catches Exception, so the first failure ended highlighting for
 * the rest of the file and a StackOverflowError crashed the app. A failed line is now shown unhighlighted and the
 * lines after it carry on from the state before it.
 */
object SafeTextMateLanguage {
    fun create(scopeName: String, autoComplete: Boolean): TextMateLanguage {
        val language = TextMateLanguage.create(scopeName, autoComplete)
        val grammar = language.grammarRegistry.findGrammar(scopeName) ?: return language
        language.textMateAnalyzer?.let { original ->
            original.setReceiver(null)
            original.destroy()
        }
        language.textMateAnalyzer =
            SafeTextMateAnalyzer(language, grammar, language.languageConfiguration, language.themeRegistry)
        return language
    }
}

private class SafeTextMateAnalyzer(
    language: TextMateLanguage,
    grammar: IGrammar,
    configuration: LanguageConfiguration?,
    themeRegistry: ThemeRegistry
) : TextMateAnalyzer(language, grammar, configuration, themeRegistry) {

    override fun tokenizeLine(
        line: CharSequence,
        state: MyState?,
        lineIndex: Int
    ): IncrementalAnalyzeManager.LineTokenizeResult<MyState, Span> =
        try {
            super.tokenizeLine(line, state, lineIndex)
        } catch (error: Exception) {
            unhighlighted(state)
        } catch (error: StackOverflowError) {
            unhighlighted(state)
        }

    private fun unhighlighted(state: MyState?): IncrementalAnalyzeManager.LineTokenizeResult<MyState, Span> {
        val spans = ArrayList<Span>()
        spans.add(SpanFactory.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
        // The identifier list must not be null: with autocomplete on, sora iterates it for every state.
        return IncrementalAnalyzeManager.LineTokenizeResult(
            MyState(state?.tokenizeState, null, 0, java.util.ArrayList()),
            null,
            spans
        )
    }
}
