package com.pyreview.app.ui.submit

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyreview.app.AppContainer
import com.pyreview.app.data.local.SubmissionMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SubmitViewModel(private val container: AppContainer) : ViewModel() {
    val draft = container.draft
    val isOnline = container.network.isOnline

    var readingProgress by mutableStateOf<String?>(null)
        private set

    var message by mutableStateOf<String?>(null)

    fun clearCode(): String {
        val old = draft.code
        draft.code = ""
        if (draft.method == SubmissionMethod.FILE) draft.method = SubmissionMethod.TEXT
        return old
    }

    fun restoreCode(code: String) {
        draft.code = code
    }

    fun pasteFromClipboard(context: Context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrBlank()) {
            message = "There's no text on the clipboard to paste."
            return
        }
        val cleaned = text.replace("\r\n", "\n")
        onCodeChanged(if (draft.code.isBlank()) cleaned else draft.code.trimEnd() + "\n" + cleaned)
        message = "Pasted ${cleaned.count { it == '\n' } + 1} lines"
    }

    fun onCodeChanged(code: String) {
        draft.code = code
        if (draft.method == SubmissionMethod.IMAGES) draft.method = SubmissionMethod.TEXT
    }

    fun addPhotos(uris: List<Uri>) {
        val room = MAX_PHOTOS - draft.photos.size
        if (uris.size > room) message = "You can add up to $MAX_PHOTOS photos per file."
        draft.photos.addAll(uris.take(room.coerceAtLeast(0)))
    }

    fun movePhoto(from: Int, to: Int) {
        if (from !in draft.photos.indices || to !in draft.photos.indices) return
        draft.photos.add(to, draft.photos.removeAt(from))
    }

    fun removePhoto(index: Int) {
        if (index in draft.photos.indices) draft.photos.removeAt(index)
    }

    fun newPhotoUri(context: Context): Uri {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "code_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun loadPythonFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, text) = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "upload.py"
                    if (!name.endsWith(".py", ignoreCase = true)) {
                        return@withContext name to null
                    }
                    val text = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?.takeIf { it.size <= MAX_FILE_BYTES }
                        ?.toString(Charsets.UTF_8)
                    name to (text ?: "")
                }
                when {
                    text == null -> message = "\"$name\" isn't a .py file. Choose a Python source file."
                    text.isBlank() -> message = "\"$name\" is empty or larger than ${MAX_FILE_BYTES / 1000} KB."
                    else -> {
                        draft.startOver(code = text.replace("\r\n", "\n"), fileName = name)
                        draft.method = SubmissionMethod.FILE
                        message = "Loaded $name"
                    }
                }
            } catch (e: Exception) {
                message = "Couldn't open that file: ${e.message}"
            }
        }
    }

    fun readPhotos(onRead: () -> Unit) {
        if (readingProgress != null || draft.photos.isEmpty()) return
        readingProgress = "Getting started…"
        viewModelScope.launch {
            try {
                val result = container.recognition.read(draft.photos.toList()) { readingProgress = it }
                if (result.merged.lines.isEmpty()) {
                    message = "Couldn't find any code in ${if (draft.photos.size == 1) "that photo" else "those photos"}. Try again closer up and in good light."
                } else {
                    draft.loadRecognition(result)
                    onRead()
                }
            } catch (e: Exception) {
                message = "Couldn't read the photos: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                readingProgress = null
            }
        }
    }

    companion object {
        const val MAX_PHOTOS = 10
        const val MAX_FILE_BYTES = 200_000
    }
}
