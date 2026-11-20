package com.flashcards.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Image
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.max

class OcrResult(
    /** Recognized text, one line per recognized line. */
    val text: String,
    /** The image that was fed to the recognizer, for the on-screen preview. */
    val processed: BufferedImage,
)

/**
 * Offline text recognition using the OCR engine built into Windows 10/11
 * (Windows.Media.Ocr), reached through Windows PowerShell 5.1, which can call WinRT APIs.
 * Uses the first Windows display language that has OCR support installed.
 */
object WindowsOcr {
    /** Windows OCR rejects images larger than this on either side. */
    private const val MAX_EDGE = 4000

    /** Small screenshots read much better when enlarged first. */
    private const val MIN_EDGE = 1200

    private val script: File by lazy {
        File.createTempFile("flashcards-ocr", ".ps1").apply {
            deleteOnExit()
            // UTF-8 with BOM so Windows PowerShell 5.1 reads the script correctly.
            writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + SCRIPT.toByteArray())
        }
    }

    suspend fun recognize(file: File): OcrResult = withContext(Dispatchers.IO) {
        val image = ImageIO.read(file) ?: error("Not an image file this app can read")
        recognize(image)
    }

    suspend fun recognize(image: BufferedImage): OcrResult = withContext(Dispatchers.IO) {
        val prepared = resize(image)
        val png = File.createTempFile("flashcards-scan", ".png")
        val errLog = File.createTempFile("flashcards-ocr", ".log")
        try {
            ImageIO.write(prepared, "png", png)
            val process = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", script.absolutePath, "-Path", png.absolutePath,
            ).redirectError(errLog).start()
            val out = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("Text recognition timed out")
            }
            if (process.exitValue() != 0) {
                error(errLog.readText().lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "Text recognition failed")
            }
            OcrResult(out.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }.joinToString("\n"), prepared)
        } finally {
            png.delete()
            errLog.delete()
        }
    }

    /** The image on the clipboard (e.g. a Win+Shift+S screenshot), or null. */
    fun clipboardImage(): BufferedImage? = runCatching {
        val clip = Toolkit.getDefaultToolkit().systemClipboard
        if (!clip.isDataFlavorAvailable(DataFlavor.imageFlavor)) return null
        toBuffered(clip.getData(DataFlavor.imageFlavor) as Image)
    }.getOrNull()

    private fun toBuffered(img: Image): BufferedImage {
        if (img is BufferedImage && img.type == BufferedImage.TYPE_INT_RGB) return img
        val w = img.getWidth(null)
        val h = img.getHeight(null)
        return BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().run {
                color = java.awt.Color.WHITE
                fillRect(0, 0, w, h)
                drawImage(img, 0, 0, null)
                dispose()
            }
        }
    }

    private fun resize(src: BufferedImage): BufferedImage {
        val edge = max(src.width, src.height)
        val scale = when {
            edge > MAX_EDGE -> MAX_EDGE.toDouble() / edge
            edge < MIN_EDGE -> minOf(MIN_EDGE.toDouble() / edge, 3.0)
            else -> 1.0
        }
        if (scale == 1.0) return toBuffered(src)
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().run {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                color = java.awt.Color.WHITE
                fillRect(0, 0, w, h)
                drawImage(src, 0, 0, w, h, null)
                dispose()
            }
        }
    }

    private val SCRIPT = """
        param([string]${'$'}Path)
        ${'$'}ErrorActionPreference = 'Stop'
        [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
        Add-Type -AssemblyName System.Runtime.WindowsRuntime
        ${'$'}null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
        ${'$'}null = [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]
        ${'$'}null = [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics, ContentType = WindowsRuntime]
        ${'$'}asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
            ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.GetParameters().Count -eq 1 -and
            ${'$'}_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
        } | Select-Object -First 1
        function Await(${'$'}op, [Type]${'$'}type) {
            ${'$'}task = ${'$'}asTask.MakeGenericMethod(${'$'}type).Invoke(${'$'}null, @(${'$'}op))
            ${'$'}task.Wait() | Out-Null
            ${'$'}task.Result
        }
        ${'$'}engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
        if (${'$'}engine -eq ${'$'}null) { throw 'No Windows OCR language is installed (Settings > Time & language > Language).' }
        ${'$'}file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync(${'$'}Path)) ([Windows.Storage.StorageFile])
        ${'$'}stream = Await (${'$'}file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
        ${'$'}decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync(${'$'}stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
        ${'$'}bitmap = Await (${'$'}decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
        ${'$'}result = Await (${'$'}engine.RecognizeAsync(${'$'}bitmap)) ([Windows.Media.Ocr.OcrResult])
        ${'$'}stream.Dispose()
        foreach (${'$'}line in ${'$'}result.Lines) { ${'$'}line.Text }
    """.trimIndent()
}
