package ai.gyango.chatbot.data

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStreamReader

object DocumentExtractor {

    private var pdfBoxInitialized = false

    fun init(context: Context) {
        if (!pdfBoxInitialized) {
            PDFBoxResourceLoader.init(context)
            pdfBoxInitialized = true
        }
    }

    sealed class Result {
        data class Success(val text: String, val fileName: String?) : Result()
        data class Error(val message: String) : Result()
    }

    fun extract(
        context: Context,
        uri: Uri,
        mimeType: String?,
        maxChars: Int = 3000
    ): Result {
        init(context)

        val fileName = try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null }

        return when {
            mimeType == "application/pdf" || fileName?.endsWith(".pdf", true) == true ->
                extractPdf(context, uri, maxChars, fileName)
            mimeType?.startsWith("text/") == true || fileName?.endsWith(".txt", true) == true ->
                extractText(context, uri, maxChars, fileName)
            else -> extractText(context, uri, maxChars, fileName)
        }
    }

    private fun extractPdf(context: Context, uri: Uri, maxChars: Int, fileName: String?): Result {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val document = PDDocument.load(input)
                try {
                    val stripper = PDFTextStripper()
                    val fullText = stripper.getText(document).trim()
                    val text = if (fullText.length > maxChars) {
                        fullText.take(maxChars) + "\n\n[Document truncated for length. Summarizing first part.]"
                    } else fullText
                    Result.Success(text, fileName)
                } finally {
                    document.close()
                }
            } ?: Result.Error("Could not open PDF")
        } catch (e: Exception) {
            Result.Error("Failed to read PDF: ${e.message}")
        }
    }

    private fun extractText(context: Context, uri: Uri, maxChars: Int, fileName: String?): Result {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                val fullText = reader.readText().trim()
                val text = if (fullText.length > maxChars) {
                    fullText.take(maxChars) + "\n\n[Document truncated for length. Summarizing first part.]"
                } else fullText
                Result.Success(text, fileName)
            } ?: Result.Error("Could not open file")
        } catch (e: Exception) {
            Result.Error("Failed to read file: ${e.message}")
        }
    }
}
