package com.carscan.ai.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import java.io.Closeable

/**
 * Runs stages 1 to 3 and produces the input manifest.
 *
 * Both inputs are optional — video only, dashboard photo only, or both.
 *
 * Ordering matters: frames are decoded BEFORE the model loads, and the model is
 * released before the manifest is built. Holding a 571 MB model resident while
 * decoding video is what pushes the CPU path out of memory.
 */
object PipelineRunner {

    /** Dashboard photos below this Laplacian variance are too soft for OCR. */
    private const val DASHBOARD_MIN_SHARPNESS = 60.0

    suspend fun buildManifest(
        context: Context,
        videoUri: Uri?,
        dashboardUri: Uri?,
        tyreUri: Uri? = null,
        obdPresent: Boolean = false,
        backend: ComputeBackend? = null,
        onStage: (String) -> Unit = {}
    ): InputManifest {

        val startedAt = SystemClock.elapsedRealtime()

        // ---- Stage 1: frame extraction (no model resident) ----
        val frames = if (videoUri != null) {
            onStage("Extracting frames")
            FrameExtractor.extract(context, videoUri) { done, total ->
                onStage("Extracting frames  $done / $total")
            }
        } else {
            emptyList()
        }

        // ---- Load the dashboard photo, keep it for classification ----
        var dashboardBitmap: Bitmap? = null
        var dashboardSharpness = 0.0
        var dashboardUsable = false

        if (dashboardUri != null) {
            onStage("Checking photo quality")
            dashboardBitmap = FrameExtractor.loadImage(context, dashboardUri)
            if (dashboardBitmap != null) {
                dashboardSharpness = FrameExtractor.sharpness(dashboardBitmap)
                dashboardUsable = dashboardSharpness >= DASHBOARD_MIN_SHARPNESS
            }
        }

        // ---- Stage 2: load the model only now ----
        onStage(
            if (backend == null) "Loading model (auto)"
            else "Loading model (${backend.name})"
        )

        var clip: ClipViewClassifier? = null
        var loadError: String? = null
        try {
            clip = ClipViewClassifier.create(context, backend)
        } catch (e: OutOfMemoryError) {
            loadError = "Out of memory loading the model"
        } catch (e: Throwable) {
            loadError = e.message
        }

        val engine: ViewClassifier = clip ?: HeuristicViewClassifier()
        val requestedButUnavailable = backend != null && clip == null

        var inferenceMs = 0L
        var inferenceCount = 0
        var classified: List<Pair<ExtractedFrame, Classification>> = emptyList()
        var dashboardClass: String? = null
        var dashboardConfidence = 0f

        try {
            // ---- Classify video frames ----
            if (frames.isNotEmpty()) {
                onStage("Classifying views")
                val results = ArrayList<Pair<ExtractedFrame, Classification>>(frames.size)
                for ((i, frame) in frames.withIndex()) {
                    onStage("Classifying views  ${i + 1} / ${frames.size}")
                    val t0 = SystemClock.elapsedRealtime()
                    val result = try {
                        engine.classify(frame.bitmap, frame.sharpness)
                    } catch (e: OutOfMemoryError) {
                        loadError = "Out of memory during inference"
                        Classification(ViewClass.REJECT, 0f)
                    }
                    inferenceMs += SystemClock.elapsedRealtime() - t0
                    inferenceCount++
                    results.add(frame to result)
                    if (loadError != null) break
                }
                classified = results
            }

            // ---- Validate the dashboard photo with the same model ----
            val bitmap = dashboardBitmap
            if (bitmap != null && loadError == null) {
                onStage("Verifying dashboard photo")
                val t0 = SystemClock.elapsedRealtime()
                val result = try {
                    engine.classify(bitmap, dashboardSharpness)
                } catch (e: OutOfMemoryError) {
                    Classification(ViewClass.REJECT, 0f)
                }
                inferenceMs += SystemClock.elapsedRealtime() - t0
                inferenceCount++
                dashboardClass = result.viewClass.label
                dashboardConfidence = result.confidence
            }
        } finally {
            (engine as? Closeable)?.let {
                try { it.close() } catch (ignored: Exception) {}
            }
            try { dashboardBitmap?.recycle() } catch (ignored: Exception) {}
        }

        val kept = classified.filter { it.second.viewClass != ViewClass.REJECT }
        val rejectedCount = classified.size - kept.size

        // ---- Dedupe: sharpest frame per view class ----
        if (kept.isNotEmpty()) onStage("Removing duplicates")
        val bestPerView = kept
            .groupBy { it.second.viewClass }
            .mapNotNull { (_, group) -> group.maxByOrNull { it.first.sharpness } }
            .sortedBy { it.first.timestampMs }

        val dedupedCount = kept.size - bestPerView.size

        val entries = bestPerView.map { (frame, result) ->
            ManifestEntry(
                viewClass = result.viewClass,
                source = "video @ ${frame.timestampMs / 1000}s",
                timestampMs = frame.timestampMs,
                sharpness = frame.sharpness,
                confidence = result.confidence,
                bitmap = frame.bitmap
            )
        }

        onStage("Building manifest")

        return InputManifest(
            videoPresent = videoUri != null,
            framesExtracted = frames.size,
            framesRejected = rejectedCount,
            framesDeduped = dedupedCount,
            selected = entries,
            dashboardPresent = dashboardUri != null,
            dashboardSharpness = dashboardSharpness,
            dashboardUsable = dashboardUsable,
            dashboardClass = dashboardClass,
            dashboardConfidence = dashboardConfidence,
            tyrePresent = tyreUri != null,
            obdPresent = obdPresent,
            classifierName = engine.name,
            backendUsed = clip?.backend?.name ?: "NONE",
            backendRequested = backend?.name ?: "AUTO",
            backendUnavailable = requestedButUnavailable,
            loadError = loadError,
            inferenceCount = inferenceCount,
            inferenceMs = inferenceMs,
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
        )
    }
}
