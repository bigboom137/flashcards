package com.flashcards.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OcrResult(
    /** Recognized text in reading order. */
    val text: String,
    /** The cleaned-up image that was actually fed to the recognizer. */
    val processed: Bitmap,
    /** Lines that were dropped because the recognizer was not confident in them. */
    val dropped: List<String>,
)

object OcrEngine {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Runs on-device OCR on the image and returns its text in reading order. */
    suspend fun recognize(context: Context, uri: Uri): OcrResult {
        val enhanced = withContext(Dispatchers.Default) { ImagePreprocessor.load(context, uri) }
        return recognize(enhanced)
    }

    /** OCR on an already oriented and enhanced image. */
    suspend fun recognize(enhanced: Enhanced): OcrResult {
        val bitmap = enhanced.bitmap
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

        val kept = mutableListOf<TextLayout.RawLine>()
        val dropped = mutableListOf<String>()
        result.textBlocks.forEachIndexed { bi, block ->
            for (line in block.lines) {
                if (LineFilter.isNoise(line.text, line.confidence)) {
                    dropped += line.text
                } else {
                    kept += TextLayout.RawLine(line.text, cornersOf(line), bi)
                }
            }
        }
        return OcrResult(TextLayout.layout(kept, enhanced.skew), bitmap, dropped)
    }

    private fun cornersOf(line: Text.Line): List<TextLayout.Pt> =
        line.cornerPoints?.map { TextLayout.Pt(it.x.toDouble(), it.y.toDouble()) }
            ?: line.boundingBox?.let { r ->
                listOf(
                    TextLayout.Pt(r.left.toDouble(), r.top.toDouble()),
                    TextLayout.Pt(r.right.toDouble(), r.top.toDouble()),
                    TextLayout.Pt(r.right.toDouble(), r.bottom.toDouble()),
                    TextLayout.Pt(r.left.toDouble(), r.bottom.toDouble()),
                )
            }
            ?: emptyList()
}
