package com.carscan.ai.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import java.io.Closeable

/**
 * Runs CarScan stages up to InputManifest.
 *
 * VIDEO
 *   -> frame extraction
 *   -> MobileNetV3 classification
 *   -> confidence filtering
 *   -> sharpest frame per class
 *   -> InputManifest
 *
 * DASHBOARD PHOTO
 *   -> sharpness check
 *   -> MobileNetV3 dashboard validation
 *
 * TYRE
 *   -> presence is recorded in InputManifest for later processing
 *
 * OBD
 *   -> parked for later
 */
object PipelineRunner {

    /** Dashboard photos below this Laplacian variance are too soft for later OCR. */
    private const val DASHBOARD_MIN_SHARPNESS = 60.0

    /** Predictions below this confidence become REJECT. */
    private const val MIN_CLASSIFICATION_CONFIDENCE = 0.60f

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

        // Stage 1: Extract frames
        val frames = if (videoUri != null) {
            onStage("Extracting frames")

            FrameExtractor.extract(context, videoUri) { done, total ->
                onStage("Extracting frames  $done / $total")
            }
        } else {
            emptyList()
        }

        // Load dashboard image and measure quality
        var dashboardBitmap: Bitmap? = null
        var dashboardSharpness = 0.0
        var dashboardUsable = false

        if (dashboardUri != null) {
            onStage("Checking dashboard photo quality")

            dashboardBitmap = FrameExtractor.loadImage(
                context,
                dashboardUri
            )

            dashboardBitmap?.let { bitmap ->
                dashboardSharpness = FrameExtractor.sharpness(bitmap)
                dashboardUsable =
                    dashboardSharpness >= DASHBOARD_MIN_SHARPNESS
            }
        }

        // Stage 2: Load MobileNetV3
        onStage("Loading MobileNetV3")

        var mobileNet: MobileNetV3ViewClassifier? = null
        var loadError: String? = null

        try {
            mobileNet = MobileNetV3ViewClassifier(context)
        } catch (e: OutOfMemoryError) {
            loadError = "Out of memory loading MobileNetV3"
        } catch (e: Throwable) {
            loadError = e.message ?: "Unable to load MobileNetV3"
        }

        // Existing fallback keeps the manifest screen usable during development.
        val engine: ViewClassifier =
            mobileNet ?: HeuristicViewClassifier()

        var inferenceMs = 0L
        var inferenceCount = 0

        var classified:
            List<Pair<ExtractedFrame, Classification>> = emptyList()

        var dashboardClass: String? = null
        var dashboardConfidence = 0f

        try {
            // Stage 3A: Classify every extracted video frame
            if (frames.isNotEmpty()) {
                onStage("Classifying video frames")

                val results =
                    ArrayList<Pair<ExtractedFrame, Classification>>(frames.size)

                for ((index, frame) in frames.withIndex()) {
                    onStage("MobileNetV3  ${index + 1} / ${frames.size}")

                    val inferenceStart = SystemClock.elapsedRealtime()

                    var result = try {
                        engine.classify(
                            frame.bitmap,
                            frame.sharpness
                        )
                    } catch (e: OutOfMemoryError) {
                        loadError =
                            "Out of memory during MobileNetV3 inference"

                        Classification(
                            ViewClass.REJECT,
                            0f
                        )
                    } catch (_: Throwable) {
                        Classification(
                            ViewClass.REJECT,
                            0f
                        )
                    }

                    inferenceMs +=
                        SystemClock.elapsedRealtime() - inferenceStart

                    inferenceCount++

                    if (
                        result.viewClass != ViewClass.REJECT &&
                        result.confidence < MIN_CLASSIFICATION_CONFIDENCE
                    ) {
                        result = Classification(
                            ViewClass.REJECT,
                            result.confidence
                        )
                    }

                    results.add(frame to result)

                    if (loadError != null) {
                        break
                    }
                }

                classified = results
            }

            // Stage 3B: Validate dashboard close-up
            val bitmap = dashboardBitmap

            if (
                bitmap != null &&
                loadError == null
            ) {
                onStage("Verifying dashboard photo")

                val inferenceStart = SystemClock.elapsedRealtime()

                val result = try {
                    engine.classify(
                        bitmap,
                        dashboardSharpness
                    )
                } catch (_: Throwable) {
                    Classification(
                        ViewClass.REJECT,
                        0f
                    )
                }

                inferenceMs +=
                    SystemClock.elapsedRealtime() - inferenceStart

                inferenceCount++

                dashboardClass = result.viewClass.label
                dashboardConfidence = result.confidence

                dashboardUsable =
                    dashboardUsable &&
                    result.viewClass == ViewClass.DASHBOARD &&
                    result.confidence >= MIN_CLASSIFICATION_CONFIDENCE
            }
        } finally {
            (engine as? Closeable)?.let {
                try {
                    it.close()
                } catch (_: Exception) {
                }
            }

            try {
                dashboardBitmap?.recycle()
            } catch (_: Exception) {
            }
        }

        // Reject low-confidence / invalid frames
        val kept = classified.filter {
            it.second.viewClass != ViewClass.REJECT
        }

        val rejectedCount =
            classified.size - kept.size

        // Dedupe: keep sharpest frame for each detected class.
        if (kept.isNotEmpty()) {
            onStage("Selecting best frames")
        }

        val bestPerView = kept
            .groupBy {
                it.second.viewClass
            }
            .mapNotNull { (_, group) ->
                group.maxByOrNull {
                    it.first.sharpness
                }
            }
            .sortedBy {
                it.first.timestampMs
            }

        val dedupedCount =
            kept.size - bestPerView.size

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

        onStage("Building input manifest")

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
            backendUsed =
                if (mobileNet != null) {
                    "CPU_XNNPACK"
                } else {
                    "HEURISTIC"
                },
            backendRequested = backend?.name ?: "AUTO",
            backendUnavailable = false,
            loadError = loadError,

            inferenceCount = inferenceCount,
            inferenceMs = inferenceMs,
            elapsedMs =
                SystemClock.elapsedRealtime() - startedAt
        )
    }
}
