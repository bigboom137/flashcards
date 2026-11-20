package com.flashcards.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "OcrPipelineTest"

@RunWith(AndroidJUnit4::class)
class OcrPipelineTest {
    private val vocab = listOf(
        "inquit" to "says, said",
        "amicus" to "friend",
        "bellum" to "war",
        "carpe diem" to "seize the day",
        "quoque" to "also",
        "gaudium" to "joy, gladness",
        "libertas" to "freedom, liberty",
        "nihil" to "nothing",
    )

    /** A compact vocabulary page photographed on thin paper: bleed-through + shading. */
    private fun renderPage(textSize: Float, bleedAlpha: Int, tilt: Float): Bitmap {
        val w = 2000
        val h = 1400
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // Uneven lighting: light on the left, darker toward the right.
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                Color.rgb(250, 248, 240), Color.rgb(165, 160, 150), Shader.TileMode.CLAMP)
        })
        c.save()
        c.rotate(tilt, w / 2f, h / 2f)

        // Bleed-through: mirrored faint text from the other side of the page.
        val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(bleedAlpha, 40, 40, 40)
            this.textSize = textSize
        }
        c.save()
        c.scale(-1f, 1f, w / 2f, h / 2f)
        val ghostLines = listOf("Chapter XII Vocabulary", "dominus, -i m. lord", "puella, -ae f. girl",
            "servus, -i m. slave", "verbum, -i n. word", "video, -ere to see", "amo, amare to love")
        ghostLines.forEachIndexed { i, t -> c.drawText(t, 260f, 140f + i * textSize * 1.25f + textSize * 0.4f, ghost) }
        c.restore()

        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 25, 30); this.textSize = textSize }
        vocab.forEachIndexed { i, (term, def) ->
            val y = 160f + i * textSize * 1.2f
            c.drawText(term, 120f, y, ink)
            c.drawText(def, 900f, y, ink)
        }
        c.restore()
        return bmp
    }

    private fun dump(label: String, enhanced: Enhanced) = runBlocking {
        val bmp = enhanced.bitmap
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val t = rec.process(InputImage.fromBitmap(bmp, 0)).await()
        Log.i(TAG, "=== $label ===")
        for (b in t.textBlocks) for (l in b.lines) {
            Log.i(TAG, String.format("  conf=%.2f  weird=%.2f  %s", l.confidence, LineFilter.weirdRatio(l.text), l.text))
        }
        val res = OcrEngine.recognize(enhanced)
        Log.i(TAG, String.format("--- layout (%s) skew=%.2f deg ---", label, Math.toDegrees(enhanced.skew)))
        res.text.lines().forEach { Log.i(TAG, "  | " + it.replace("\t", " <TAB> ")) }
        Log.i(TAG, "--- dropped: ${res.dropped}")
        res
    }

    @Test
    fun compactPageWithBleedThrough() {
        for ((size, alpha) in listOf(36f to 70, 28f to 70, 36f to 110, 24f to 90)) {
            val raw = renderPage(textSize = size, bleedAlpha = alpha, tilt = 2f)
            dump("RAW size=$size bleed=$alpha", Enhanced(raw, 0.0))
            val enhanced = ImagePreprocessor.enhance(raw)
            val res = dump("ENHANCED size=$size bleed=$alpha", enhanced)
            val text = res.text
            val hits = vocab.count { (term, def) -> text.contains(term) && text.contains(def) }
            Log.i(TAG, "RESULT size=$size bleed=$alpha: $hits/${vocab.size} pairs intact")
            assertTrue("expected most vocab pairs, got $hits", hits >= vocab.size - 2)
        }
    }
}
