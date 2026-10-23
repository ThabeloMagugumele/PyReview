package com.pyreview.app.data.ocr

import android.content.Context
import android.net.Uri
import com.pyreview.app.data.ServiceException
import com.pyreview.app.data.supabase.RemoteReviewService
import com.pyreview.app.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class RecognitionEngine(val label: String) {
    ONLINE("the online reader (Gemini)"),
    ON_DEVICE("the on-device reader"),
}

data class RecognitionResult(
    val merged: MergedCode,
    val engine: RecognitionEngine,
    val photoCount: Int,
    val fallbackReason: String?,
)

class CodeRecognitionService(
    private val context: Context,
    private val remote: RemoteReviewService,
    private val network: NetworkMonitor,
) {
    private val onDeviceReader by lazy { OnDeviceRecognizer(context) }

    suspend fun read(photos: List<Uri>, onProgress: (String) -> Unit = {}): RecognitionResult {
        require(photos.isNotEmpty())
        var fallbackReason: String? =
            if (network.isOnline.value) null else "No internet, so the photos were read on your phone instead."

        if (fallbackReason == null) {
            try {
                onProgress("Preparing ${photos.size.photos()}…")
                val encoded = withContext(Dispatchers.Default) {
                    photos.map { uri ->
                        val bitmap = ImageLoader.load(context, uri)
                        ImageLoader.toJpegBase64(bitmap).also { bitmap.recycle() }
                    }
                }
                onProgress("Reading ${photos.size.photos()} online…")
                val pages = remote.readPhotos(encoded)
                return RecognitionResult(PageMerger.merge(pages), RecognitionEngine.ONLINE, photos.size, null)
            } catch (e: ServiceException) {
                fallbackReason = "${e.message} The photos were read on your phone instead."
            }
        }

        val onDevice = withContext(Dispatchers.Default) { onDeviceReader }
        val pages = photos.mapIndexed { i, uri ->
            onProgress("Reading photo ${i + 1} of ${photos.size} on the phone…")
            val bitmap = withContext(Dispatchers.Default) { ImageLoader.load(context, uri) }
            try {
                onDevice.read(bitmap)
            } finally {
                bitmap.recycle()
            }
        }
        return RecognitionResult(PageMerger.merge(pages), RecognitionEngine.ON_DEVICE, photos.size, fallbackReason)
    }

    private fun Int.photos() = if (this == 1) "1 photo" else "$this photos"
}
