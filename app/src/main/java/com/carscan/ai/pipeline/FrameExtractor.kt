package com.carscan.ai.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stage 1 of the pipeline: decode the walkaround video into candidate frames
 * and score each one for sharpness.
 *
 * No model is involved — this is plain Android + Kotlin.
 */

data class ExtractedFrame(
    val index: Int,
    val timestampMs: Long,
    val bitmap: Bitmap,
    val sharpness: Double
)

object FrameExtractor {

    /**
     * Frames are downscaled to this longest edge. 384 is plenty — CLIP crops to
     * 224 anyway, and it roughly halves the memory of 480.
     */
    private const val MAX_DIMENSION = 384

    /** Sample one frame per second. */
    private const val SAMPLE_INTERVAL_MS = 1_000L

    /**
     * Hard cap so a long video can't exhaust memory. Lower this further if the
     * CPU backend still runs out on your device.
     */
    private const val MAX_FRAMES = 16

    /**
     * Laplacian variance below this is treated as too blurry to use.
     * Tune against real footage — 40 is a conservative starting point.
     */
    const val BLUR_THRESHOLD = 40.0

    suspend fun extract(
        context: Context,
        uri: Uri,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<ExtractedFrame> = withContext(Dispatchers.IO) {

        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<ExtractedFrame>()

        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L

            if (durationMs <= 0L) return@withContext emptyList()

            val plannedCount = ((durationMs / SAMPLE_INTERVAL_MS).toInt() + 1)
                .coerceIn(1, MAX_FRAMES)

            // Spread the samples across the whole clip rather than only the
            // first N seconds, so a long video still covers the full walkaround.
            val step = if (plannedCount > 1) durationMs / (plannedCount - 1) else 0L

            for (i in 0 until plannedCount) {
                val timestampMs = (i * step).coerceAtMost(durationMs)

                val raw = try {
                    retriever.getFrameAtTime(
                        timestampMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }

                if (raw != null) {
                    try {
                        val scaled = downscale(raw)
                        if (scaled !== raw) raw.recycle()
                        frames.add(
                            ExtractedFrame(
                                index = i,
                                timestampMs = timestampMs,
                                bitmap = scaled,
                                sharpness = sharpness(scaled)
                            )
                        )
                    } catch (e: OutOfMemoryError) {
                        try { raw.recycle() } catch (ignored: Exception) {}
                        break
                    }
                }

                onProgress(i + 1, plannedCount)
            }
        } catch (e: Exception) {
            // Unreadable or unsupported video — return whatever was collected.
        } catch (e: OutOfMemoryError) {
            // Keep what we have rather than dying.
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
                // no-op
            }
        }

        frames
    }

    /** Loads a still image (dashboard / tyre photo) at pipeline resolution. */
    suspend fun loadImage(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri).use { stream ->
                    val bounds = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(stream, null, bounds)

                    val longest = maxOf(bounds.outWidth, bounds.outHeight)
                    var sample = 1
                    while (longest / sample > MAX_DIMENSION * 2) sample *= 2

                    context.contentResolver.openInputStream(uri).use { second ->
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = sample
                        }
                        val decoded = BitmapFactory.decodeStream(second, null, opts)
                            ?: return@use null
                        val scaled = downscale(decoded)
                        if (scaled !== decoded) decoded.recycle()
                        scaled
                    }
                }
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
        }

    private fun downscale(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_DIMENSION) return source

        val ratio = MAX_DIMENSION.toFloat() / longest
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /**
     * Variance of the Laplacian — the standard cheap blur metric.
     * Higher means more high-frequency detail, so a sharper frame.
     */
    fun sharpness(source: Bitmap): Double {
        if (source.width < 3 || source.height < 3) return 0.0

        val width = 160
        val height = (source.height.toFloat() * width / source.width)
            .toInt()
            .coerceAtLeast(3)

        val small = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== source) small.recycle()

        val gray = DoubleArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = 0.299 * ((p shr 16) and 0xFF) +
                    0.587 * ((p shr 8) and 0xFF) +
                    0.114 * (p and 0xFF)
        }

        var sum = 0.0
        var sumSquares = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val laplacian = -4.0 * gray[i] +
                        gray[i - 1] + gray[i + 1] +
                        gray[i - width] + gray[i + width]
                sum += laplacian
                sumSquares += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 0.0
        val mean = sum / count
        return (sumSquares / count) - (mean * mean)
    }

    /** Mean luminance 0..255. Used by the placeholder classifier. */
    fun brightness(source: Bitmap): Double {
        val width = 64
        val height = (source.height.toFloat() * width / source.width)
            .toInt()
            .coerceAtLeast(1)

        val small = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== source) small.recycle()

        var total = 0.0
        for (p in pixels) {
            total += 0.299 * ((p shr 16) and 0xFF) +
                    0.587 * ((p shr 8) and 0xFF) +
                    0.114 * (p and 0xFF)
        }
        return total / pixels.size
    }
}
