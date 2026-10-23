package com.pyreview.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

object ImageLoader {
    private const val MAX_EDGE = 1568

    fun load(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: throw IOException("Couldn't open the photo.")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("That file isn't a photo PyReview can read.")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("That file isn't a photo PyReview can read.")

        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val upright = if (rotation == 0) decoded else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                .also { if (it !== decoded) decoded.recycle() }
        }

        val longest = max(upright.width, upright.height)
        if (longest <= MAX_EDGE) return upright
        val scale = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(upright, (upright.width * scale).roundToInt(), (upright.height * scale).roundToInt(), true)
            .also { if (it !== upright) upright.recycle() }
    }

    fun toJpegBase64(bitmap: Bitmap, quality: Int = 85): String {
        val bytes = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)
            it.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
