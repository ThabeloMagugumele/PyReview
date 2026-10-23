package com.pyreview.app.ui.confirm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyreview.app.appContainer
import com.pyreview.app.data.ocr.CodeLine
import com.pyreview.app.data.ocr.RecognitionEngine
import com.pyreview.app.ui.auth.PrimaryButton
import com.pyreview.app.ui.components.CodeRow
import com.pyreview.app.ui.components.NoticeCard
import com.pyreview.app.ui.components.OfflineBanner
import com.pyreview.app.ui.components.ScreenScaffold
import com.pyreview.app.ui.theme.CodeTextStyle
import com.pyreview.app.ui.theme.LocalStatusColors
import com.pyreview.app.util.pluralize

@Composable
fun ConfirmScreen(onBack: () -> Unit, onConfirm: () -> Unit) {
    val container = appContainer()
    val draft = container.draft
    val online by container.network.isOnline.collectAsStateWithLifecycle()
    val recognition = draft.recognition
    val lines = draft.lines
    val status = LocalStatusColors.current

    var editingIndex by remember { mutableIntStateOf(-1) }
    var editAll by rememberSaveable { mutableStateOf(false) }
    var allText by rememberSaveable { mutableStateOf("") }

    val uncertainCount = lines.count { it.uncertain }

    fun finishEditAll() {
        val newLines = allText.trimEnd().lines()
        val updated = newLines.mapIndexed { i, text ->
            val old = lines.getOrNull(i)
            if (old != null && old.text == text) old else CodeLine(text, uncertain = false, page = old?.page ?: lines.lastOrNull()?.page ?: 1)
        }
        lines.clear()
        lines.addAll(updated)
        editAll = false
    }

    ScreenScaffold(
        title = "Check the code",
        onBack = onBack,
        bottomBar = {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                PrimaryButton("Confirm and review", enabled = online && lines.any { it.text.isNotBlank() }) {
                    if (editAll) finishEditAll()
                    draft.confirmPhotos()
                    onConfirm()
                }
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Retake or reorder photos")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item {
                Text("STEP 2 OF 3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Check the code we read", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))

                if (!online) {
                    OfflineBanner("No internet right now. Your edits are safe, but you'll need to be online to review.")
                    Spacer(Modifier.height(8.dp))
                }
                if (uncertainCount > 0) {
                    NoticeCard(
                        "${pluralize(uncertainCount, "line")} might be wrong. Tap a highlighted line to fix it.",
                        accent = status.warning,
                        container = status.warningContainer,
                    )
                } else {
                    NoticeCard("Everything was read clearly, but give it a quick look anyway.", accent = status.good, container = status.goodContainer)
                }
                Spacer(Modifier.height(8.dp))

                recognition?.let { r ->
                    val source = buildString {
                        append("Read from ${pluralize(r.photoCount, "photo")} with ${r.engine.label}.")
                        if (r.engine == RecognitionEngine.ON_DEVICE) append(" Double-check the indentation because the phone reader sometimes gets the spacing wrong.")
                    }
                    NoticeCard(source)
                    r.fallbackReason?.let {
                        Spacer(Modifier.height(8.dp))
                        NoticeCard(it, accent = status.warning, container = status.warningContainer)
                    }
                    r.merged.joins.forEach { join ->
                        Spacer(Modifier.height(8.dp))
                        val text = if (join.overlapFound) {
                            "Photos ${join.previousPage} → ${join.nextPage}: removed ${pluralize(join.removedLines, "repeated line")}."
                        } else {
                            "Photos ${join.previousPage} → ${join.nextPage}: no overlap found, so they were joined as they are. Check nothing is missing or repeated there."
                        }
                        NoticeCard(text, accent = if (join.overlapFound) status.good else status.warning)
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(draft.fileName, style = CodeTextStyle, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        if (editAll) {
                            finishEditAll()
                        } else {
                            allText = lines.joinToString("\n") { it.text }
                            editAll = true
                        }
                    }) { Text(if (editAll) "Done editing" else "Edit as text") }
                }
            }

            if (editAll) {
                item {
                    OutlinedTextField(
                        value = allText,
                        onValueChange = { allText = it },
                        textStyle = CodeTextStyle,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                itemsIndexed(lines) { index, line ->
                    val previousPage = lines.getOrNull(index - 1)?.page
                    if (previousPage != null && previousPage != line.page) {
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text(
                                "photo ${line.page}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            HorizontalDivider(Modifier.weight(1f))
                        }
                    }
                    CodeLineRow(index + 1, line) { editingIndex = index }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (editingIndex in lines.indices) {
        EditLineDialog(
            lineNumber = editingIndex + 1,
            initial = lines[editingIndex].text,
            onDismiss = { editingIndex = -1 },
            onSave = { text ->
                lines[editingIndex] = lines[editingIndex].copy(text = text, uncertain = false)
                editingIndex = -1
            },
            onDelete = {
                lines.removeAt(editingIndex)
                editingIndex = -1
            },
            onInsertBelow = { text ->
                val current = lines[editingIndex].copy(text = text, uncertain = false)
                lines[editingIndex] = current
                val indent = current.text.takeWhile { it == ' ' }
                lines.add(editingIndex + 1, CodeLine(indent, uncertain = false, page = current.page))
                editingIndex += 1
            },
        )
    }
}

@Composable
private fun CodeLineRow(number: Int, line: CodeLine, onClick: () -> Unit) {
    val status = LocalStatusColors.current
    CodeRow(
        number = number,
        text = line.text,
        background = if (line.uncertain) status.warningContainer else Color.Transparent,
        marker = if (line.uncertain) status.warning else Color.Transparent,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp),
    )
}

@Composable
private fun EditLineDialog(
    lineNumber: Int,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onInsertBelow: (String) -> Unit,
) {
    var text by remember(lineNumber, initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit line $lineNumber") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.replace("\n", "") },
                    textStyle = CodeTextStyle,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Keep the spaces at the start of the line. Python needs them for indentation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { onInsertBelow(text) }) { Text("Add line below") }
                    TextButton(onClick = onDelete) { Text("Delete line", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
