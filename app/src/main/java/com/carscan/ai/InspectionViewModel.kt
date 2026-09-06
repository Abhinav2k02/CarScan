package com.carscan.ai

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.carscan.ai.pipeline.ComputeBackend
import com.carscan.ai.pipeline.InputManifest
import com.carscan.ai.pipeline.PipelineRunner

data class Category(val name: String, val score: Int)

data class Finding(val title: String, val detail: String)

data class Report(
    val vehicle: String,
    val variant: String,
    val odometer: String,
    val fuel: String,
    val location: String,
    val overall: Int,
    val verdict: String,
    val summary: String,
    val categories: List<Category>,
    val findings: List<Finding>
)

/** One completed run, kept so backends can be compared side by side. */
data class BenchmarkRun(
    val backend: String,
    val inferences: Int,
    val totalMs: Long,
    val averageMs: Double
)

class InspectionViewModel : ViewModel() {

    // ---- Inputs ----
    var videoUri by mutableStateOf<Uri?>(null)
    var dashboardUri by mutableStateOf<Uri?>(null)
    var tyreUri by mutableStateOf<Uri?>(null)

    /** null = auto (NPU -> GPU -> CPU). */
    var backendChoice by mutableStateOf<ComputeBackend?>(null)

    // ---- Pipeline state ----
    var stage by mutableStateOf("Starting")
        private set

    /**
     * Every distinct stage the pipeline has reported, in order. The processing
     * screen renders this directly, so it can never fall out of sync with the
     * stage names PipelineRunner actually emits.
     */
    val stageHistory = mutableStateListOf<String>()

    var running by mutableStateOf(false)
        private set
    var manifest by mutableStateOf<InputManifest?>(null)
        private set
    var pipelineError by mutableStateOf<String?>(null)
        private set

    /** Results from every run this session, for the comparison table. */
    val benchmarks = mutableStateListOf<BenchmarkRun>()

    // ---- Downstream (still mocked) ----
    var report by mutableStateOf<Report?>(null)

    val hasAnyInput: Boolean
        get() = videoUri != null || dashboardUri != null

    fun reset() {
        videoUri = null
        dashboardUri = null
        tyreUri = null
        stage = "Starting"
        stageHistory.clear()
        running = false
        manifest = null
        pipelineError = null
        report = null
        benchmarks.clear()
    }

    private fun pushStage(text: String) {
        stage = text
        // Collapse progress counters ("Extracting frames  3 / 12") onto one row.
        val base = text.substringBefore("  ").trim()
        val lastBase = stageHistory.lastOrNull()?.substringBefore("  ")?.trim()
        if (base == lastBase && stageHistory.isNotEmpty()) {
            stageHistory[stageHistory.lastIndex] = text
        } else {
            stageHistory.add(text)
        }
    }

    suspend fun runPipeline(context: Context) {
        pipelineError = null
        manifest = null
        stageHistory.clear()
        running = true

        if (!hasAnyInput) {
            pipelineError = "Add a video or a dashboard photo first."
            stage = "Failed"
            running = false
            return
        }

        try {
            val result = PipelineRunner.buildManifest(
                context = context,
                videoUri = videoUri,
                dashboardUri = dashboardUri,
                tyreUri = tyreUri,
                obdPresent = false,
                backend = backendChoice,
                onStage = { pushStage(it) }
            )
            manifest = result
            stage = "Done"

            if (result.inferenceCount > 0) {
                benchmarks.removeAll { it.backend == result.backendUsed }
                benchmarks.add(
                    BenchmarkRun(
                        backend = result.backendUsed,
                        inferences = result.inferenceCount,
                        totalMs = result.inferenceMs,
                        averageMs = result.averageInferenceMs
                    )
                )
            }
        } catch (e: Exception) {
            pipelineError = e.message ?: "Pipeline failed"
            stage = "Failed"
        } catch (e: OutOfMemoryError) {
            pipelineError = "Out of memory"
            stage = "Failed"
        } finally {
            running = false
        }
    }

    /**
     * PLACEHOLDER for everything after the manifest.
     * The orchestrator, vision calls, OCR, and reasoning are not built yet.
     */
    fun buildMockReport() {
        report = Report(
            vehicle = "Honda",
            variant = "2.8 4x4 AT  |  2020",
            odometer = "80,000 km",
            fuel = "Petrol",
            location = "Chennai, TN",
            overall = 78,
            verdict = "Good Condition",
            summary = "Generally in good condition with minor issues. " +
                    "Suitable for purchase with inspection.",
            categories = listOf(
                Category("Exterior", 85),
                Category("Interior", 72),
                Category("Engine Bay", 65),
                Category("Dashboard / Odometer", 90),
                Category("Tyres & Brakes", 70),
                Category("Documents (Visual)", 88)
            ),
            findings = listOf(
                Finding("Minor scratch", "Front bumper — cosmetic only"),
                Finding("Tyre tread", "Moderate wear, front-left"),
                Finding("Engine bay", "Light surface corrosion visible")
            )
        )
    }
}
