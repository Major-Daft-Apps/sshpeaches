package com.majordaftapps.sshpeaches.app.ui.code

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.isEditable
import androidx.compose.ui.semantics.insertTextAtCursor
import androidx.compose.ui.semantics.requestFocus
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource

/** Languages with bundled VS Code (TextMate) grammars. [PLAIN] has no highlighting. */
enum class CodeLanguage(val scopeName: String?, val label: String) {
    SHELL("source.shell", "Shell"),
    JSON("source.json", "JSON"),
    YAML("source.yaml", "YAML"),
    INI("source.ini", "Config"),
    PYTHON("source.python", "Python"),
    JAVASCRIPT("source.js", "JavaScript"),
    XML("text.xml", "XML"),
    MARKDOWN("text.html.markdown", "Markdown"),
    DOCKERFILE("source.dockerfile", "Dockerfile"),
    PLAIN(null, "Text");

    companion object {
        /** Picks a language from a file name, falling back to its shebang line. */
        fun detect(fileName: String, contents: String = ""): CodeLanguage {
            val name = fileName.substringAfterLast('/').lowercase()
            val extension = name.substringAfterLast('.', missingDelimiterValue = "")
            byName[name]?.let { return it }
            if (name.startsWith("dockerfile") || extension == "dockerfile") return DOCKERFILE
            byExtension[extension]?.let { return it }
            val shebang = contents.lineSequence().firstOrNull().orEmpty()
            return when {
                !shebang.startsWith("#!") -> PLAIN
                Regex("""\b(ba|z|k|da)?sh\b""").containsMatchIn(shebang) -> SHELL
                "python" in shebang -> PYTHON
                "node" in shebang -> JAVASCRIPT
                else -> PLAIN
            }
        }

        private val byName = mapOf(
            ".bashrc" to SHELL, ".bash_profile" to SHELL, ".bash_aliases" to SHELL, ".profile" to SHELL,
            ".zshrc" to SHELL, ".zprofile" to SHELL, ".env" to SHELL, "crontab" to SHELL,
            ".gitconfig" to INI, ".editorconfig" to INI, "sshd_config" to INI, "ssh_config" to INI
        )
        private val byExtension = mapOf(
            "sh" to SHELL, "bash" to SHELL, "zsh" to SHELL, "ksh" to SHELL, "env" to SHELL,
            "json" to JSON, "jsonc" to JSON, "json5" to JSON,
            "yml" to YAML, "yaml" to YAML,
            "ini" to INI, "conf" to INI, "cfg" to INI, "cnf" to INI, "properties" to INI, "toml" to INI,
            "service" to INI, "timer" to INI, "socket" to INI, "mount" to INI, "desktop" to INI,
            "py" to PYTHON,
            "js" to JAVASCRIPT, "mjs" to JAVASCRIPT, "cjs" to JAVASCRIPT, "jsx" to JAVASCRIPT,
            "ts" to JAVASCRIPT, "tsx" to JAVASCRIPT,
            "xml" to XML, "html" to XML, "htm" to XML, "svg" to XML, "plist" to XML,
            "md" to MARKDOWN, "markdown" to MARKDOWN
        )
    }
}

/** Registers the bundled grammars and themes once per process (slow; call off the main thread). */
object CodeEditorAssets {
    const val DARK_THEME = "sshpeaches-dark"
    const val LIGHT_THEME = "sshpeaches-light"

    @Volatile
    var isLoaded = false
        private set

    fun ensureLoaded(context: Context) = synchronized(this) {
        if (isLoaded) return@synchronized
        val files = FileProviderRegistry.getInstance()
        files.addFileProvider(AssetsFileResolver(context.applicationContext.assets))
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        listOf(DARK_THEME to true, LIGHT_THEME to false).forEach { (name, dark) ->
            val path = "textmate/$name.json"
            val source = IThemeSource.fromInputStream(files.tryGetInputStream(path), path, null)
            ThemeRegistry.getInstance().loadTheme(ThemeModel(source, name).apply { isDark = dark })
        }
        isLoaded = true
    }
}

private val symbolKeys = listOf("Tab", "|", "$", "\"", "'", "{", "}", "[", "]", "(", ")", "<", ">", ";", "&", "~", "/", "\\", "-", "=", "*", "#", "!", "`")

/**
 * A VS Code-style code editor (Sora Editor with VS Code TextMate grammars): line numbers,
 * highlighting, bracket matching, auto-indent, undo/redo, find, word wrap, and a symbol row for
 * characters that are awkward on phone keyboards.
 */
@Composable
fun CodeEditorPane(
    text: String,
    onTextChange: (String) -> Unit,
    language: CodeLanguage,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    editorTestTag: String = UiTestTags.CODE_EDITOR
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var ready by remember { mutableStateOf(CodeEditorAssets.isLoaded) }
    var editor by remember { mutableStateOf<CodeEditor?>(null) }
    var wordWrap by rememberSaveable { mutableStateOf(true) }
    var findOpen by rememberSaveable { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var undoAvailable by remember { mutableStateOf(false) }
    var redoAvailable by remember { mutableStateOf(false) }
    var editorFocused by remember { mutableStateOf(false) }
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentText by rememberUpdatedState(text)

    LaunchedEffect(Unit) {
        if (!ready) {
            withContext(Dispatchers.Default) { CodeEditorAssets.ensureLoaded(context) }
            ready = true
        }
    }

    Column(modifier = modifier) {
        if (!readOnly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { editor?.undo() }, enabled = undoAvailable, modifier = Modifier.testTag(UiTestTags.CODE_EDITOR_UNDO)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = { editor?.redo() }, enabled = redoAvailable, modifier = Modifier.testTag(UiTestTags.CODE_EDITOR_REDO)) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
                IconToggleButton(checked = findOpen, onCheckedChange = { open ->
                    findOpen = open
                    if (!open) editor?.searcher?.stopSearch()
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Find")
                }
                IconToggleButton(checked = wordWrap, onCheckedChange = { wordWrap = it }) {
                    Icon(Icons.AutoMirrored.Filled.WrapText, contentDescription = "Word wrap")
                }
                Text(
                    language.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        if (findOpen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { query ->
                        findQuery = query
                        val searcher = editor?.searcher ?: return@OutlinedTextField
                        if (query.isEmpty()) searcher.stopSearch()
                        else searcher.search(query, EditorSearcher.SearchOptions(true, false))
                    },
                    singleLine = true,
                    placeholder = { Text("Find") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { runCatching { editor?.searcher?.gotoPrevious() } }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(onClick = { runCatching { editor?.searcher?.gotoNext() } }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                }
                IconButton(onClick = {
                    findOpen = false
                    editor?.searcher?.stopSearch()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close find")
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .heightIn(min = 160.dp)
        ) {
            if (!ready) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                AndroidView(
                    factory = { ctx ->
                        CodeEditor(ctx).apply {
                            typefaceText = Typeface.MONOSPACE
                            setTextSize(14f)
                            isLineNumberEnabled = true
                            setPinLineNumber(true)
                            setTabWidth(4)
                            setOnFocusChangeListener { _, hasFocus -> editorFocused = hasFocus }
                            setText(currentText)
                            subscribeAlways(ContentChangeEvent::class.java) {
                                // The undo stack updates after this event, so read it on the next frame.
                                post {
                                    undoAvailable = canUndo()
                                    redoAvailable = canRedo()
                                }
                                val updated = getText().toString()
                                if (updated != currentText) currentOnTextChange(updated)
                            }
                            editor = this
                        }
                    },
                    update = { view ->
                        ThemeRegistry.getInstance().setTheme(if (dark) CodeEditorAssets.DARK_THEME else CodeEditorAssets.LIGHT_THEME)
                        if (view.tag != Pair(language, dark)) {
                            view.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                            view.setEditorLanguage(language.scopeName?.let { TextMateLanguage.create(it, true) })
                            view.tag = Pair(language, dark)
                        }
                        view.setWordwrap(wordWrap)
                        view.isEditable = !readOnly
                        if (view.text.toString() != text) view.setText(text)
                    },
                    onRelease = { view ->
                        view.release()
                        if (editor === view) editor = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(editorTestTag)
                        .semantics {
                            editableText = AnnotatedString(text)
                            isEditable = !readOnly
                            focused = editorFocused
                            requestFocus { editor?.requestFocus() ?: false }
                            if (!readOnly) {
                                setText { value ->
                                    editor?.setText(value.text)
                                    currentOnTextChange(value.text)
                                    true
                                }
                                insertTextAtCursor { value ->
                                    editor?.insertText(value.text, value.text.length) != null
                                }
                            }
                        }
                )
            }
        }
        if (!readOnly) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag(UiTestTags.CODE_EDITOR_SYMBOLS),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    symbolKeys.forEach { key ->
                        TextButton(
                            onClick = {
                                val target = editor ?: return@TextButton
                                val insert = if (key == "Tab") "    " else key
                                target.insertText(insert, insert.length)
                                target.requestFocus()
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier
                                .defaultMinSize(minWidth = if (key == "Tab") 48.dp else 34.dp)
                                .testTag(UiTestTags.codeEditorSymbol(key))
                        ) {
                            Text(key, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
