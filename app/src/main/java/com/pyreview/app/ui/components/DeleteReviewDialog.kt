package com.pyreview.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.pyreview.app.data.local.ReviewWithSubmission

@Composable
fun DeleteReviewDialog(item: ReviewWithSubmission, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val where = if (item.review.syncedToCloud) "everywhere" else "from this phone"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this review?") },
        text = {
            Text(
                "This deletes the review of ${item.submission.fileName} and its code $where, and you can't get it back.\n\n" +
                    "If you just want a cleaner History, archive it instead.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
