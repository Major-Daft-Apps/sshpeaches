package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.diagnostics.ProblemReport
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import kotlinx.coroutines.launch

/** Shows exactly what a problem report contains and sends it only when the user taps Send. */
@Composable
internal fun ProblemReportDialog(
    report: ProblemReport,
    send: suspend (ProblemReport) -> Result<Unit>,
    onSent: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var note by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The F-Droid edition can't send (our server only takes reports from the verified Google Play app): it copies the
    // same report for a GitHub issue or an email instead.
    val canSend = com.majordaftapps.sshpeaches.app.BuildConfig.REPORTS_AVAILABLE
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        modifier = Modifier.testTag(UiTestTags.PROBLEM_REPORT_DIALOG),
        title = { Text(if (canSend) "Send to developer" else "Report a problem") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (canSend) {
                        "This goes to the SSHPeaches developer to help fix the problem. Host names, addresses, " +
                            "usernames, and file paths are removed. This is everything that will be sent:"
                    } else {
                        "Copy this report and paste it into an issue at github.com/Major-Daft-Apps/sshpeaches/issues " +
                            "or an email to the developer. Host names, addresses, usernames, and file paths are removed:"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    SelectionContainer {
                        Text(
                            report.preview(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                                .testTag(UiTestTags.PROBLEM_REPORT_PREVIEW)
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(2_000) },
                    label = { Text("What were you doing? (optional)") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.PROBLEM_REPORT_NOTE)
                )
                if (sending) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Sending…")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    if (!canSend) {
                        val withNote = ProblemReport.create(report.summary, report.details, report.logLines, note)
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(withNote.preview()))
                        onSent()
                        return@TextButton
                    }
                    sending = true
                    error = null
                    scope.launch {
                        val withNote = ProblemReport.create(report.summary, report.details, report.logLines, note)
                        send(withNote).onSuccess { onSent() }.onFailure { error = it.message ?: "Couldn't send the report." }
                        sending = false
                    }
                },
                modifier = Modifier.testTag(UiTestTags.PROBLEM_REPORT_SEND)
            ) { Text(if (canSend) "Send" else "Copy report") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancel") } }
    )
}
