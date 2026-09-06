package com.carscan.ai.pipeline

import android.graphics.Bitmap

/**
 * Stage 2 of the pipeline.
 *
 * This is the seam where OpenAI CLIP plugs in. The rest of the app talks only
 * to this interface, so swapping the implementation touches nothing else.
 */

enum class ViewClass(val label: String) {
    EXTERIOR("Exterior"),
    INTERIOR("Interior"),
    ENGINE_BAY("Engine bay"),
    TYRE("Tyre"),
    DASHBOARD("Dashboard"),
    REJECT("Reject")
}

data class Classification(
    val viewClass: ViewClass,
    val confidence: Float
)

interface ViewClassifier {
    fun classify(bitmap: Bitmap, sharpness: Double): Classification
    val name: String
}

/**
 * PLACEHOLDER IMPLEMENTATION — not the real classifier.
 *
 * It uses two cheap image statistics (sharpness and mean brightness) to produce
 * a plausible label so the manifest and the screens downstream can be built and
 * tested. It is NOT accurate and must not be demoed as the real thing.
 *
 * To replace it with CLIP:
 *   1. Download the OpenAI CLIP TFLite export from Qualcomm AI Hub.
 *   2. Put the .tflite in app/src/main/assets/.
 *   3. Write ClipViewClassifier : ViewClassifier that runs the image encoder and
 *      compares against cached text embeddings for the six prompts.
 *   4. Change the one line in PipelineRunner that constructs the classifier.
 */
class HeuristicViewClassifier : ViewClassifier {

    override val name: String = "Heuristic placeholder"

    override fun classify(bitmap: Bitmap, sharpness: Double): Classification {
        if (sharpness < FrameExtractor.BLUR_THRESHOLD) {
            return Classification(ViewClass.REJECT, 0.9f)
        }

        val brightness = FrameExtractor.brightness(bitmap)

        return when {
            brightness < 55.0 -> Classification(ViewClass.REJECT, 0.7f)
            brightness < 95.0 -> Classification(ViewClass.INTERIOR, 0.45f)
            brightness < 135.0 -> Classification(ViewClass.ENGINE_BAY, 0.40f)
            else -> Classification(ViewClass.EXTERIOR, 0.55f)
        }
    }
}
