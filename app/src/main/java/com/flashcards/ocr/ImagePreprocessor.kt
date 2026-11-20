package com.flashcards.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.min

/** The cleaned-up image plus the page tilt measured from it (radians). */
class Enhanced(val bitmap: Bitmap, val skew: Double)

/** Loads a photo, fixes its orientation, and cleans it up for text recognition. */
object ImagePreprocessor {
    /**
     * Long edge of the image fed to the recognizer. Kept high because small, dense
     * text (a compact vocabulary list) needs every pixel it can get.
     */
    private const val MAX_EDGE = 4000

    /** Background estimate is computed on a 1/[BG_SCALE] copy with a box blur. */
    private const val BG_SCALE = 8
    private const val BG_RADIUS = 6 // in downscaled pixels => ~48px at full size

    /**
     * Levels curve applied after normalizing paper to white:
     * luminance <= BLACK_POINT -> black, >= WHITE_POINT -> white, linear in between.
     * Bleed-through from the reverse side of thin paper sits around 200-235 and is
     * pushed to white; printed ink sits well under 120 and stays dark.
     */
    private const val BLACK_POINT = 70
    private const val WHITE_POINT = 195

    fun load(context: Context, uri: Uri): Enhanced {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Could not decode image")

        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val oriented = if (rotation == 0) decoded else Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true,
        )
        return enhance(oriented)
    }

    /**
     * Grayscale, flatten uneven lighting (shadows, vignetting), then stretch levels
     * so the paper is white, ink is black, and faint ghosting disappears.
     */
    fun enhance(src: Bitmap): Enhanced {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Luminance in place.
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            pixels[i] = (r * 77 + g * 151 + b * 28) shr 8
        }

        val background = estimateBackground(pixels, w, h)
        val bw = (w + BG_SCALE - 1) / BG_SCALE

        val range = (WHITE_POINT - BLACK_POINT).toFloat()
        val lut = IntArray(256) { v ->
            (((v - BLACK_POINT) / range).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }

        var i = 0
        for (y in 0 until h) {
            val by = (y / BG_SCALE) * bw
            for (x in 0 until w) {
                val bg = background[by + x / BG_SCALE]
                // Paper -> 255 regardless of local lighting.
                val normalized = if (bg <= 0) pixels[i] else min(255, pixels[i] * 255 / bg)
                pixels[i] = lut[normalized]
                i++
            }
        }

        val skew = SkewDetector.detect(pixels, w, h)

        for (j in pixels.indices) {
            val v = pixels[j]
            pixels[j] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return Enhanced(out, skew)
    }

    /**
     * Local paper brightness: downscale by [BG_SCALE] taking the *max* of each cell
     * (so ink strokes do not pull the estimate down), then box-blur.
     */
    private fun estimateBackground(lum: IntArray, w: Int, h: Int): IntArray {
        val bw = (w + BG_SCALE - 1) / BG_SCALE
        val bh = (h + BG_SCALE - 1) / BG_SCALE
        val small = IntArray(bw * bh)
        for (y in 0 until h) {
            val row = y * w
            val srow = (y / BG_SCALE) * bw
            for (x in 0 until w) {
                val idx = srow + x / BG_SCALE
                val v = lum[row + x]
                if (v > small[idx]) small[idx] = v
            }
        }

        // Integral image for an O(1) box blur.
        val integral = LongArray((bw + 1) * (bh + 1))
        for (y in 1..bh) {
            var rowSum = 0L
            for (x in 1..bw) {
                rowSum += small[(y - 1) * bw + (x - 1)]
                integral[y * (bw + 1) + x] = integral[(y - 1) * (bw + 1) + x] + rowSum
            }
        }
        val blurred = IntArray(bw * bh)
        for (y in 0 until bh) {
            val y0 = max(0, y - BG_RADIUS)
            val y1 = min(bh, y + BG_RADIUS + 1)
            for (x in 0 until bw) {
                val x0 = max(0, x - BG_RADIUS)
                val x1 = min(bw, x + BG_RADIUS + 1)
                val sum = integral[y1 * (bw + 1) + x1] - integral[y0 * (bw + 1) + x1] -
                    integral[y1 * (bw + 1) + x0] + integral[y0 * (bw + 1) + x0]
                blurred[y * bw + x] = (sum / ((y1 - y0) * (x1 - x0))).toInt()
            }
        }
        return blurred
    }
}
