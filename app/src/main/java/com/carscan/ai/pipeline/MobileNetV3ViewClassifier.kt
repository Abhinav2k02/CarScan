package com.carscan.ai.pipeline

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * MobileNetV3Small TFLite classifier for CarScan.
 *
 * Expected assets:
 *   app/src/main/assets/carscan_mobilenetv3_float16.tflite
 *   app/src/main/assets/labels.txt
 *
 * Expected tensors:
 *   INPUT  [1, 224, 224, 3] float32
 *   OUTPUT [1, 3] float32
 *
 * The model was trained with MobileNetV3 include_preprocessing=true,
 * so Android feeds RGB values as float32 in the 0..255 range.
 */
class MobileNetV3ViewClassifier(
    private val context: Context
) : ViewClassifier, Closeable {

    companion object {
        private const val MODEL_FILE = "carscan_mobilenetv3_float16.tflite"
        private const val LABEL_FILE = "labels.txt"
        private const val INPUT_SIZE = 224
    }

    override val name: String = "MobileNetV3Small"

    private val labels: List<String> by lazy {
        context.assets
            .open(LABEL_FILE)
            .bufferedReader()
            .use { reader ->
                reader.readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
            .also { loaded ->
                require(loaded.size == 3) {
                    "Expected 3 labels in $LABEL_FILE, found ${loaded.size}: $loaded"
                }
            }
    }

    private val interpreterLazy = lazy {
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            setUseXNNPACK(true)
        }

        Interpreter(
            loadModelFile(MODEL_FILE),
            options
        ).also { tflite ->
            val inputShape = tflite.getInputTensor(0).shape()
            val outputShape = tflite.getOutputTensor(0).shape()

            require(inputShape.contentEquals(intArrayOf(1, 224, 224, 3))) {
                "Unexpected MobileNet input shape: ${inputShape.contentToString()}"
            }

            require(outputShape.contentEquals(intArrayOf(1, 3))) {
                "Unexpected MobileNet output shape: ${outputShape.contentToString()}"
            }
        }
    }

    private val interpreter: Interpreter
        get() = interpreterLazy.value

    override fun classify(
        bitmap: Bitmap,
        sharpness: Double
    ): Classification {

        val resized = if (
            bitmap.width == INPUT_SIZE &&
            bitmap.height == INPUT_SIZE
        ) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(
                bitmap,
                INPUT_SIZE,
                INPUT_SIZE,
                true
            )
        }

        try {
            val inputBuffer = ByteBuffer.allocateDirect(
                INPUT_SIZE * INPUT_SIZE * 3 * 4
            ).apply {
                order(ByteOrder.nativeOrder())
            }

            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

            resized.getPixels(
                pixels,
                0,
                INPUT_SIZE,
                0,
                0,
                INPUT_SIZE,
                INPUT_SIZE
            )

            // Model includes MobileNetV3 preprocessing internally.
            // Feed RGB as float32 values in the 0..255 range.
            for (pixel in pixels) {
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF

                inputBuffer.putFloat(red.toFloat())
                inputBuffer.putFloat(green.toFloat())
                inputBuffer.putFloat(blue.toFloat())
            }

            inputBuffer.rewind()

            val output = Array(1) {
                FloatArray(labels.size)
            }

            interpreter.run(
                inputBuffer,
                output
            )

            val scores = output[0]

            if (scores.isEmpty()) {
                return Classification(
                    ViewClass.REJECT,
                    0f
                )
            }

            var bestIndex = 0
            var bestScore = scores[0]

            for (index in 1 until scores.size) {
                if (scores[index] > bestScore) {
                    bestScore = scores[index]
                    bestIndex = index
                }
            }

            val modelLabel = labels.getOrNull(bestIndex).orEmpty()

            return Classification(
                labelToViewClass(modelLabel),
                bestScore
            )
        } finally {
            if (resized !== bitmap) {
                resized.recycle()
            }
        }
    }

    private fun labelToViewClass(
        label: String
    ): ViewClass {
        return when (label.trim().lowercase()) {
            "dashboard" -> ViewClass.DASHBOARD
            "exterior" -> ViewClass.EXTERIOR
            "interior" -> ViewClass.INTERIOR
            else -> ViewClass.REJECT
        }
    }

    private fun loadModelFile(
        filename: String
    ): ByteBuffer {
        val descriptor = context.assets.openFd(filename)

        return try {
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                stream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength
                )
            }
        } finally {
            descriptor.close()
        }
    }

    override fun close() {
        if (interpreterLazy.isInitialized()) {
            try {
                interpreterLazy.value.close()
            } catch (_: Throwable) {
            }
        }
    }
}
