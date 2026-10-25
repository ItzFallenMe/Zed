package dev.zed.assistant.vision

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.zed.assistant.core.ZedState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

data class OcrLine(
    val text: String,
    val bounds: Rect,
    val source: String
) {
    fun centerX(scale: Float): Float = bounds.centerX() * scale
    fun centerY(scale: Float): Float = bounds.centerY() * scale
}

data class OcrResult(
    val fullText: String,
    val lines: List<OcrLine>
) {
    companion object {
        val EMPTY = OcrResult("", emptyList())
    }
}

/**
 * Reads the screen with on-device models only. Latin and Devanagari are run
 * separately then merged so Hinglish works: "Rahul ko bolo ki 5 baje milte hain".
 */
class OcrEngine {

    private val latin: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val devanagari: TextRecognizer =
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val latinResult = safeRecognize(latin, image)
        val devanagariResult = safeRecognize(devanagari, image)

        val lines = ArrayList<OcrLine>(64)
        collect(latinResult, "latin", lines)
        collect(devanagariResult, "devanagari", lines)

        val merged = mergeByPosition(lines)
        val text = merged.joinToString("\n") { it.text }.trim()
        if (text.isBlank()) {
            ZedState.log("OCR", "No readable text on screen")
            return OcrResult.EMPTY
        }
        ZedState.log("OCR", "Read ${text.length} characters")
        return OcrResult(text, merged)
    }

    private fun mergeByPosition(lines: List<OcrLine>): List<OcrLine> {
        if (lines.isEmpty()) return emptyList()
        val sorted = lines.sortedBy { it.bounds.top }
        val result = ArrayList<OcrLine>(sorted.size)
        for (line in sorted) {
            val duplicate = result.any { existing ->
                existing.text.equals(line.text, ignoreCase = true) ||
                    (verticalOverlap(existing.bounds, line.bounds) &&
                        abs(existing.bounds.centerX() - line.bounds.centerX()) < 40 &&
                        existing.text.length > 1 && line.text.contains(existing.text, ignoreCase = true))
            }
            if (!duplicate) result.add(line)
        }
        return result
    }

    private fun verticalOverlap(a: Rect, b: Rect): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val minHeight = minOf(a.height(), b.height()).coerceAtLeast(1)
        return overlap > minHeight / 2
    }

    private fun collect(result: com.google.mlkit.vision.text.Text?, source: String, out: MutableList<OcrLine>) {
        result ?: return
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val rect = line.boundingBox ?: continue
                val value = line.text.trim()
                if (value.isNotEmpty()) out.add(OcrLine(value, rect, source))
            }
        }
    }

    private suspend fun safeRecognize(
        recognizer: TextRecognizer,
        image: InputImage
    ): com.google.mlkit.vision.text.Text? {
        return try {
            recognizer.process(image).await()
        } catch (t: Throwable) {
            ZedState.log("OCR", "Recognizer failed: ${t.message}")
            null
        }
    }

    fun close() {
        runCatching { latin.close() }
        runCatching { devanagari.close() }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.cancel(error) }
        addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
    }
}
