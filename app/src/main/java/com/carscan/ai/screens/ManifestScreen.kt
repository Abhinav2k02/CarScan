package com.carscan.ai.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.carscan.ai.InspectionViewModel
import com.carscan.ai.pipeline.ComputeBackend
import com.carscan.ai.pipeline.ManifestEntry
import com.carscan.ai.ui.theme.Accent
import com.carscan.ai.ui.theme.Bad
import com.carscan.ai.ui.theme.Bg
import com.carscan.ai.ui.theme.Good
import com.carscan.ai.ui.theme.Surface
import com.carscan.ai.ui.theme.SurfaceAlt
import com.carscan.ai.ui.theme.TextMuted
import com.carscan.ai.ui.theme.TextPrimary
import com.carscan.ai.ui.theme.Warn
import kotlin.math.roundToInt

@Composable
fun ManifestScreen(nav: NavController, vm: InspectionViewModel) {

    val manifest = vm.manifest
    if (manifest == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg),
            contentAlignment = Alignment.Center
        ) {
            Text("No manifest available", color = TextMuted)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("Input Manifest") {
            nav.popBackStack("home", inclusive = false)
        }
        Spacer(Modifier.height(4.dp))
        Stepper(current = 2)
        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ---------- On-device compute ----------
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "On-device compute",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(12.dp))
                StatRow("Model", manifest.classifierName)
                StatRow("Requested", manifest.backendRequested)
                StatRow("Actually used", manifest.backendUsed)
                StatRow("Inferences run", "${manifest.inferenceCount}")
                StatRow("Total inference", "${manifest.inferenceMs} ms")
                StatRow(
                    "Average per frame",
                    "${(manifest.averageInferenceMs * 10).roundToInt() / 10.0} ms"
                )
                StatRow("Cloud calls", "0")

                if (manifest.backendUnavailable) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${manifest.backendRequested} could not run this model on " +
                                "this device — no inference ran.",
                        color = Bad,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                val error = manifest.loadError
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Bad, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Backend comparison ----------
            SectionCard {
                Text(
                    "Backend comparison",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Re-run the same inputs on a different compute unit",
                    color = TextMuted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(12.dp))

                if (vm.benchmarks.isEmpty()) {
                    Text("No runs recorded", color = TextMuted, fontSize = 11.sp)
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Backend", color = TextMuted, fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Runs", color = TextMuted, fontSize = 10.sp,
                            modifier = Modifier.width(50.dp)
                        )
                        Text(
                            "Avg ms", color = TextMuted, fontSize = 10.sp,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val fastest = vm.benchmarks.minByOrNull { it.averageMs }
                    vm.benchmarks.forEach { run ->
                        val isFastest = run === fastest && vm.benchmarks.size > 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Text(
                                run.backend,
                                color = if (isFastest) Good else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isFastest) FontWeight.Bold
                                else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${run.inferences}",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.width(50.dp)
                            )
                            Text(
                                "${(run.averageMs * 10).roundToInt() / 10.0}",
                                color = if (isFastest) Good else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isFastest) FontWeight.Bold
                                else FontWeight.Normal,
                                modifier = Modifier.width(60.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "NPU" to ComputeBackend.NPU,
                        "GPU" to ComputeBackend.GPU,
                        "CPU" to ComputeBackend.CPU
                    ).forEachIndexed { index, (label, backend) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceAlt)
                                .clickable {
                                    vm.backendChoice = backend
                                    nav.navigate("processing") {
                                        popUpTo("home") { inclusive = false }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Accent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(label, color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                        if (index < 2) Spacer(Modifier.width(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Video extraction ----------
            if (manifest.videoPresent) {
                SectionCard {
                    Text(
                        "Video extraction",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    StatRow("Frames extracted", "${manifest.framesExtracted}")
                    StatRow("Rejected", "${manifest.framesRejected}")
                    StatRow("Removed as duplicates", "${manifest.framesDeduped}")
                    StatRow("Selected for analysis", "${manifest.selected.size}")
                }
                Spacer(Modifier.height(14.dp))
            }

            // ---------- Dashboard photo ----------
            if (manifest.dashboardPresent) {
                SectionCard {
                    Text(
                        "Dashboard photo",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    StatRow(
                        "Sharpness",
                        "${manifest.dashboardSharpness.roundToInt()}  " +
                                if (manifest.dashboardUsable) "(usable)" else "(too soft)"
                    )
                    StatRow("Classified as", manifest.dashboardClass ?: "not checked")
                    StatRow(
                        "Confidence",
                        "${(manifest.dashboardConfidence * 100).roundToInt()}%"
                    )

                    Spacer(Modifier.height(10.dp))
                    if (manifest.dashboardVerified) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Good,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Verified as a dashboard image",
                                color = Good,
                                fontSize = 11.sp
                            )
                        }
                    } else if (manifest.dashboardClass != null) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Warn,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "This does not look like a dashboard — it reads as " +
                                        "${manifest.dashboardClass}. Retake with the " +
                                        "instrument cluster filling the frame.",
                                color = Warn,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ---------- Other inputs ----------
            SectionCard {
                Text(
                    "Other inputs",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                AvailabilityRow(
                    label = "Tyre photo",
                    present = manifest.tyrePresent,
                    detail = if (manifest.tyrePresent) "provided" else "not provided",
                    ok = manifest.tyrePresent
                )
                AvailabilityRow(
                    label = "OBD file",
                    present = manifest.obdPresent,
                    detail = if (manifest.obdPresent) "provided" else "not provided",
                    ok = manifest.obdPresent
                )
                StatRow("Total elapsed", "${manifest.elapsedMs} ms")
            }

            Spacer(Modifier.height(22.dp))

            // ---------- Selected frames ----------
            if (manifest.videoPresent) {
                Text(
                    "Selected frames",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "One sharpest frame kept per view class",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))

                if (manifest.selected.isEmpty()) {
                    SectionCard {
                        Text(
                            "No usable frames. Try a steadier, brighter video.",
                            color = Warn,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    manifest.selected.forEach { entry ->
                        FrameRow(entry)
                        Spacer(Modifier.height(10.dp))
                    }
                }

                Spacer(Modifier.height(22.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent.copy(alpha = 0.10f))
                    .padding(14.dp)
            ) {
                Text(
                    "Pipeline stops here. Orchestrator, vision calls, OCR and " +
                            "reasoning are not built yet — the report below is " +
                            "placeholder data.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            PrimaryButton("Continue to placeholder report") {
                vm.buildMockReport()
                nav.navigate("report")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AvailabilityRow(
    label: String,
    present: Boolean,
    detail: String,
    ok: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (ok) Good else if (present) Warn else SurfaceAlt,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(detail, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun FrameRow(entry: ManifestEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceAlt)
        ) {
            Image(
                bitmap = entry.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.viewClass.label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(entry.source, color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "sharpness ${entry.sharpness.roundToInt()}  ·  " +
                        "conf ${(entry.confidence * 100).roundToInt()}%",
                color = if (entry.confidence >= 0.5f) Good else Warn,
                fontSize = 11.sp
            )
        }
    }
}
