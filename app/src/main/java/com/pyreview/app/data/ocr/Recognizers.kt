package com.pyreview.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OnDeviceRecognizer(context: Context) {
    private val recognizer = run {
        MlKitContext.initializeIfNeeded(context.applicationContext)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun read(bitmap: Bitmap): List<CodeLine> {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val boxes = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            TextBox(line.text, box.left, box.top, box.right, box.bottom, line.confidence)
        }
        return TextLayout.toCodeLines(boxes, bitmap.height)
    }
}
