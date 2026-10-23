package com.pyreview.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun JoinDepartmentDialog(
    busy: Boolean,
    error: String?,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    var code by remember { mutableStateOf("") }
    val valid = code.length in 6..12

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join your department") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter the invite code your department admin gave you. Your reviews will then " +
                        "follow your team's coding standards.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(12) },
                    label = { Text("Invite code") },
                    placeholder = { Text("e.g. 7F3A9C2B") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onJoin(code) }, enabled = valid && !busy) { Text(if (busy) "Joining…" else "Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(dismissLabel) } },
    )
}
