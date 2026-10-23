package com.pyreview.app.data

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pyreview.app.data.local.SubmissionMethod
import com.pyreview.app.data.ocr.CodeLine
import com.pyreview.app.data.ocr.RecognitionResult

class DraftStore {
    var fileName by mutableStateOf(DEFAULT_FILE_NAME)
    var code by mutableStateOf("")
    var method by mutableStateOf(SubmissionMethod.TEXT)

    val photos = mutableStateListOf<Uri>()

    var recognition by mutableStateOf<RecognitionResult?>(null)

    val lines = mutableStateListOf<CodeLine>()

    fun loadRecognition(result: RecognitionResult) {
        recognition = result
        lines.clear()
        lines.addAll(result.merged.lines)
    }

    fun confirmPhotos() {
        code = lines.joinToString("\n") { it.text }.trimEnd()
        method = SubmissionMethod.IMAGES
    }

    fun toRequest(): ReviewRequest = ReviewRequest(
        fileName = fileName.trim().ifEmpty { DEFAULT_FILE_NAME },
        code = code,
        method = method,
        reconstructedCode = if (method == SubmissionMethod.IMAGES) recognition?.merged?.text else null,
        imageCount = if (method == SubmissionMethod.IMAGES) recognition?.photoCount ?: 0 else 0,
        uncertainLineCount = if (method == SubmissionMethod.IMAGES) recognition?.merged?.lines?.count { it.uncertain } ?: 0 else 0,
    )

    fun startOver(code: String = "", fileName: String = DEFAULT_FILE_NAME) {
        this.code = code
        this.fileName = fileName
        method = SubmissionMethod.TEXT
        photos.clear()
        recognition = null
        lines.clear()
    }

    companion object {
        const val DEFAULT_FILE_NAME = "main.py"
    }
}
