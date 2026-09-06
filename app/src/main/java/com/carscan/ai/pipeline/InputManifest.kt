package com.carscan.ai.pipeline

import android.graphics.Bitmap

/**
 * Stage 3 output: the input manifest.
 *
 * A plain in-memory object shown on ManifestScreen. Not written to disk.
 * Records what inputs exist and at what quality — no findings.
 */

data class ManifestEntry(
    val viewClass: ViewClass,
    val source: String,
    val timestampMs: Long,
    val sharpness: Double,
    val confidence: Float,
    val bitmap: Bitmap
)

data class InputManifest(
    val videoPresent: Boolean,
    val framesExtracted: Int,
    val framesRejected: Int,
    val framesDeduped: Int,
    val selected: List<ManifestEntry>,
    val dashboardPresent: Boolean,
    val dashboardSharpness: Double,
    val dashboardUsable: Boolean,
    /** What the classifier thinks the dashboard photo actually shows. */
    val dashboardClass: String?,
    val dashboardConfidence: Float,
    val tyrePresent: Boolean,
    val obdPresent: Boolean,
    val classifierName: String,
    val backendUsed: String,
    val backendRequested: String,
    val backendUnavailable: Boolean,
    val loadError: String?,
    val inferenceCount: Int,
    val inferenceMs: Long,
    val elapsedMs: Long
) {
    val availableViews: List<ViewClass>
        get() = selected.map { it.viewClass }.distinct()

    /** True when the uploaded photo really does look like a dashboard. */
    val dashboardVerified: Boolean
        get() = dashboardClass == ViewClass.DASHBOARD.label

    val hasMinimumInputs: Boolean
        get() = selected.isNotEmpty() || dashboardUsable

    val averageInferenceMs: Double
        get() = if (inferenceCount > 0) inferenceMs.toDouble() / inferenceCount else 0.0
}
