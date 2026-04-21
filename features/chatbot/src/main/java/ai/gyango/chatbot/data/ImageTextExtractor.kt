package ai.gyango.chatbot.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object ImageTextExtractor {
    sealed class Result {
        data class Success(val text: String) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun extractFromUri(
        context: Context,
        uri: Uri,
        maxChars: Int = 2500,
    ): Result {
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxDim = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                if (maxDim > 1600) {
                    decoder.setTargetSampleSize((maxDim / 1600).coerceAtLeast(1))
                }
            }
            extractText(bitmap, maxChars)
        }.getOrElse { e ->
            Result.Error(e.message ?: "Could not read that image")
        }
    }

    suspend fun extractFromBitmap(
        bitmap: Bitmap,
        maxChars: Int = 2500,
    ): Result {
        return runCatching { extractText(bitmap, maxChars) }
            .getOrElse { e -> Result.Error(e.message ?: "Could not process that photo") }
    }

    private suspend fun extractText(bitmap: Bitmap, maxChars: Int): Result {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val input = InputImage.fromBitmap(bitmap, 0)
            val text = recognizer.process(input).await().text.trim()
            if (text.isBlank()) {
                Result.Error("I could not detect readable text in that image. Try a clearer photo.")
            } else {
                Result.Success(text.take(maxChars))
            }
        } finally {
            recognizer.close()
        }
    }
}

