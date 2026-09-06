package com.carscan.ai.pipeline

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/** Which compute unit TFLite should use. */
enum class ComputeBackend { NPU, GPU, CPU }

/**
 * OpenAI CLIP ViT-B/16 image encoder plus a linear head.
 *
 * Head format, read from clip_prompts.json:
 *   - no "_bias" key -> zero-shot prompts, scored softmax(100 * cosine)
 *   - has "_bias"    -> trained probe weights, scored softmax(W . v + b)
 *
 * Model tensors (verified against the Qualcomm AI Hub TFLite export):
 *   input  "image"          [1, 224, 224, 3]  float32  NHWC
 *   input  "text"           [1, 5, 77]        int32
 *   output "image_features" [1, 512]          float32
 *   output "text_features"  [5, 512]          float32
 */
class ClipViewClassifier private constructor(
    private val interpreter: Interpreter,
    private val nnApiDelegate: NnApiDelegate?,
    private val gpuDelegate: GpuDelegate?,
    val backend: ComputeBackend,
    private val classNames: List<String>,
    private val weights: Array<FloatArray>,
    private val bias: FloatArray,
    private val isProbe: Boolean,
    private val imageInputIndex: Int,
    private val textInputIndex: Int,
    private val imageOutputIndex: Int,
    private val textOutputIndex: Int,
    private val embeddingDim: Int,
    textSlots: Int,
    textLength: Int
) : ViewClassifier, Closeable {

    private val headName = if (isProbe) "probe" else "zero-shot"

    override val name: String = "CLIP ViT-B/16 $headName (${backend.name})"

    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(1 * SIDE * SIDE * 3 * 4).order(ByteOrder.nativeOrder())

    private val dummyText: ByteBuffer =
        ByteBuffer.allocateDirect(textSlots * textLength * 4).order(ByteOrder.nativeOrder())

    private val imageFeatures = Array(1) { FloatArray(embeddingDim) }
    private val textFeatures = Array(textSlots) { FloatArray(embeddingDim) }

    override fun classify(bitmap: Bitmap, sharpness: Double): Classification {
        if (sharpness < FrameExtractor.BLUR_THRESHOLD) {
            return Classification(ViewClass.REJECT, 0.95f)
        }

        val embedding = encode(bitmap) ?: return Classification(ViewClass.REJECT, 0.0f)

        val scale = if (isProbe) 1f else LOGIT_SCALE
        val logits = FloatArray(weights.size) { i ->
            var dot = 0f
            val w = weights[i]
            for (d in 0 until embeddingDim) dot += embedding[d] * w[d]
            dot * scale + bias[i]
        }

        var maxLogit = Float.NEGATIVE_INFINITY
        for (l in logits) if (l > maxLogit) maxLogit = l

        var sum = 0f
        val probs = FloatArray(logits.size) { i ->
            val e = exp((logits[i] - maxLogit).toDouble()).toFloat()
            sum += e
            e
        }

        var bestIndex = 0
        for (i in probs.indices) if (probs[i] > probs[bestIndex]) bestIndex = i

        val confidence = probs[bestIndex] / sum
        val label = toViewClass(classNames[bestIndex])

        if (confidence < MIN_CONFIDENCE) {
            return Classification(ViewClass.REJECT, confidence)
        }
        return Classification(label, confidence)
    }

    private fun encode(bitmap: Bitmap): FloatArray? {
        return try {
            fillInputBuffer(bitmap)
            dummyText.rewind()

            val inputs = arrayOfNulls<Any>(2)
            inputs[imageInputIndex] = inputBuffer
            inputs[textInputIndex] = dummyText

            val outputs = HashMap<Int, Any>(2)
            outputs[imageOutputIndex] = imageFeatures
            outputs[textOutputIndex] = textFeatures

            @Suppress("UNCHECKED_CAST")
            interpreter.runForMultipleInputsOutputs(inputs as Array<Any>, outputs)

            val v = imageFeatures[0].copyOf()
            var norm = 0f
            for (x in v) norm += x * x
            norm = sqrt(norm)
            if (norm <= 0f) return null
            for (i in v.indices) v[i] = v[i] / norm
            v
        } catch (e: Exception) {
            Log.e(TAG, "inference failed", e)
            null
        }
    }

    private fun fillInputBuffer(bitmap: Bitmap) {
        val side = min(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2

        val cropped = if (side == bitmap.width && side == bitmap.height) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, left, top, side, side)
        }

        val scaled = if (cropped.width == SIDE && cropped.height == SIDE) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, SIDE, SIDE, true)
        }

        val pixels = IntArray(SIDE * SIDE)
        scaled.getPixels(pixels, 0, SIDE, 0, 0, SIDE, SIDE)

        inputBuffer.rewind()
        val fb = inputBuffer.asFloatBuffer()
        fb.rewind()

        for (p in pixels) {
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            fb.put((r - MEAN_R) / STD_R)
            fb.put((g - MEAN_G) / STD_G)
            fb.put((b - MEAN_B) / STD_B)
        }
        inputBuffer.rewind()

        if (scaled !== cropped) scaled.recycle()
        if (cropped !== bitmap) cropped.recycle()
    }

    private fun toViewClass(name: String): ViewClass = when (name) {
        "EXTERIOR" -> ViewClass.EXTERIOR
        "INTERIOR" -> ViewClass.INTERIOR
        "ENGINE_BAY" -> ViewClass.ENGINE_BAY
        "TYRE" -> ViewClass.TYRE
        "DASHBOARD" -> ViewClass.DASHBOARD
        else -> ViewClass.REJECT
    }

    override fun close() {
        try { interpreter.close() } catch (ignored: Exception) {}
        try { nnApiDelegate?.close() } catch (ignored: Exception) {}
        try { gpuDelegate?.close() } catch (ignored: Exception) {}
    }

    companion object {
        private const val TAG = "ClipViewClassifier"

        private const val MODEL_ASSET = "openai_clip.tflite"
        private const val WEIGHTS_ASSET = "clip_prompts.json"

        private const val SIDE = 224
        private const val LOGIT_SCALE = 100f
        private const val MIN_CONFIDENCE = 0.35f

        private const val MEAN_R = 0.48145466f
        private const val MEAN_G = 0.4578275f
        private const val MEAN_B = 0.40821073f
        private const val STD_R = 0.26862954f
        private const val STD_G = 0.26130258f
        private const val STD_B = 0.27577711f

        fun create(
            context: Context,
            preferred: ComputeBackend? = null
        ): ClipViewClassifier? {

            val model = try {
                loadModel(context)
            } catch (e: Exception) {
                Log.e(TAG, "could not load $MODEL_ASSET", e)
                return null
            }

            val head = try {
                loadHead(context)
            } catch (e: Exception) {
                Log.e(TAG, "could not load $WEIGHTS_ASSET", e)
                return null
            }
            if (head.names.isEmpty()) return null

            val order = preferred?.let { listOf(it) }
                ?: listOf(ComputeBackend.NPU, ComputeBackend.GPU, ComputeBackend.CPU)

            for (backend in order) {
                var nnApi: NnApiDelegate? = null
                var gpu: GpuDelegate? = null
                var interpreter: Interpreter? = null

                try {
                    val options = Interpreter.Options()
                    when (backend) {
                        ComputeBackend.NPU -> {
                            nnApi = NnApiDelegate()
                            options.addDelegate(nnApi)
                        }
                        ComputeBackend.GPU -> {
                            val compat = CompatibilityList()
                            if (!compat.isDelegateSupportedOnThisDevice) {
                                throw IllegalStateException("GPU delegate unsupported")
                            }
                            gpu = GpuDelegate(compat.bestOptionsForThisDevice)
                            options.addDelegate(gpu)
                        }
                        ComputeBackend.CPU -> {
                            // XNNPACK repacks weights into heap memory, which
                            // means a second ~571 MB copy of this model and an
                            // immediate OOM. Slower, but it survives.
                            options.setNumThreads(2)
                            options.setUseXNNPACK(false)
                        }
                    }

                    interpreter = Interpreter(model, options)

                    var imageIn = -1
                    var textIn = -1
                    for (i in 0 until interpreter.inputTensorCount) {
                        when (interpreter.getInputTensor(i).name()) {
                            "image" -> imageIn = i
                            "text" -> textIn = i
                        }
                    }
                    var imageOut = -1
                    var textOut = -1
                    for (i in 0 until interpreter.outputTensorCount) {
                        when (interpreter.getOutputTensor(i).name()) {
                            "image_features" -> imageOut = i
                            "text_features" -> textOut = i
                        }
                    }
                    if (imageIn < 0) imageIn = 0
                    if (textIn < 0) textIn = 1
                    if (imageOut < 0) imageOut = 0
                    if (textOut < 0) textOut = 1

                    val textShape = interpreter.getInputTensor(textIn).shape()
                    val textSlots = if (textShape.size >= 2) textShape[1] else 5
                    val textLength = if (textShape.size >= 3) textShape[2] else 77
                    val dim = interpreter.getOutputTensor(imageOut).shape().last()

                    val weights = Array(head.names.size) { i ->
                        val w = head.weights[head.names[i]]!!
                        if (w.size != dim) {
                            throw IllegalStateException(
                                "head dim ${w.size} != model dim $dim — regenerate $WEIGHTS_ASSET"
                            )
                        }
                        w
                    }
                    val bias = FloatArray(head.names.size) { i ->
                        head.bias[head.names[i]] ?: 0f
                    }

                    val classifier = ClipViewClassifier(
                        interpreter = interpreter,
                        nnApiDelegate = nnApi,
                        gpuDelegate = gpu,
                        backend = backend,
                        classNames = head.names,
                        weights = weights,
                        bias = bias,
                        isProbe = head.isProbe,
                        imageInputIndex = imageIn,
                        textInputIndex = textIn,
                        imageOutputIndex = imageOut,
                        textOutputIndex = textOut,
                        embeddingDim = dim,
                        textSlots = textSlots,
                        textLength = textLength
                    )

                    val probe = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
                    val ok = classifier.encode(probe) != null
                    probe.recycle()
                    if (!ok) throw IllegalStateException("warm-up inference failed")

                    Log.i(TAG, "CLIP on $backend, dim=$dim, head=${classifier.headName}")
                    return classifier

                } catch (e: OutOfMemoryError) {
                    Log.w(TAG, "backend $backend out of memory")
                    try { interpreter?.close() } catch (ignored: Exception) {}
                    try { nnApi?.close() } catch (ignored: Exception) {}
                    try { gpu?.close() } catch (ignored: Exception) {}
                } catch (e: Throwable) {
                    Log.w(TAG, "backend $backend unavailable: ${e.message}")
                    try { interpreter?.close() } catch (ignored: Exception) {}
                    try { nnApi?.close() } catch (ignored: Exception) {}
                    try { gpu?.close() } catch (ignored: Exception) {}
                }
            }
            return null
        }

        private class Head(
            val names: List<String>,
            val weights: Map<String, FloatArray>,
            val bias: Map<String, Float>,
            val isProbe: Boolean
        )

        private fun loadModel(context: Context): MappedByteBuffer {
            val fd: AssetFileDescriptor = context.assets.openFd(MODEL_ASSET)
            FileInputStream(fd.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset,
                    fd.declaredLength
                )
            }
        }

        private fun loadHead(context: Context): Head {
            val text = context.assets.open(WEIGHTS_ASSET)
                .bufferedReader()
                .use { it.readText() }

            val json = JSONObject(text)

            val biasMap = HashMap<String, Float>()
            var isProbe = false
            if (json.has("_bias")) {
                isProbe = true
                val biasJson = json.getJSONObject("_bias")
                val keys = biasJson.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    biasMap[k] = biasJson.getDouble(k).toFloat()
                }
            }

            val names = ArrayList<String>()
            val weights = LinkedHashMap<String, FloatArray>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "_bias") continue
                val arr = json.getJSONArray(key)
                weights[key] = FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
                names.add(key)
            }

            return Head(names, weights, biasMap, isProbe)
        }
    }
}
