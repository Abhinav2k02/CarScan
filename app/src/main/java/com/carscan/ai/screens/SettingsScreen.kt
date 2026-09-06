package com.carscan.ai.screens

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.carscan.ai.InspectionViewModel
import com.carscan.ai.ui.theme.Accent
import com.carscan.ai.ui.theme.Bg
import com.carscan.ai.ui.theme.Good
import com.carscan.ai.ui.theme.TextMuted
import com.carscan.ai.ui.theme.TextPrimary

@Composable
fun SettingsScreen(nav: NavController, vm: InspectionViewModel) {

    val context = LocalContext.current
    val device = remember { readDeviceInfo(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("Settings") { nav.popBackStack() }
        Spacer(Modifier.height(12.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ---------- Privacy ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Good.copy(alpha = 0.12f))
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = Good,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Fully on-device",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Videos and photos never leave this phone. No account, " +
                                "no upload, no network calls during analysis.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Compute backend ----------
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Compute backend",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Auto tries NPU, then GPU, then CPU. Pick one to compare speeds.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                BackendSelector(
                    selected = vm.backendChoice,
                    onSelect = { vm.backendChoice = it }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Device ----------
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "This device",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(12.dp))
                device.forEach { (k, v) -> InfoRow(k, v) }
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Models ----------
            SectionCard {
                Text(
                    "Models on device",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                InfoRow("View classifier", "OpenAI CLIP ViT-B/16")
                InfoRow("Odometer OCR", "not integrated yet")
                InfoRow("Vision + reasoning", "not integrated yet")
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Last run ----------
            val manifest = vm.manifest
            if (manifest != null) {
                SectionCard {
                    Text(
                        "Last run",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    InfoRow("Compute unit", manifest.backendUsed)
                    InfoRow("Inferences", "${manifest.inferenceCount}")
                    InfoRow("Total time", "${manifest.elapsedMs} ms")
                    InfoRow("Cloud calls", "0")
                }
                Spacer(Modifier.height(14.dp))
            }

            SectionCard {
                Text(
                    "About",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                InfoRow("App", "CarScan 1.0")
                InfoRow("Build", "Prototype")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(
            value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun readDeviceInfo(context: Context): List<Pair<String, String>> {
    val info = mutableListOf<Pair<String, String>>()

    info.add("Model" to "${Build.MANUFACTURER} ${Build.MODEL}")
    info.add("Android" to "${Build.VERSION.RELEASE}  (API ${Build.VERSION.SDK_INT})")

    val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manufacturer = Build.SOC_MANUFACTURER
        val model = Build.SOC_MODEL
        if (manufacturer.isNotBlank() || model.isNotBlank()) {
            "$manufacturer $model".trim()
        } else {
            Build.HARDWARE
        }
    } else {
        Build.HARDWARE
    }
    info.add("Chipset" to soc)

    info.add("ABI" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"))

    try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val totalGb = mem.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availGb = mem.availMem / (1024.0 * 1024.0 * 1024.0)
        info.add("RAM" to String.format("%.1f GB total", totalGb))
        info.add("Available" to String.format("%.1f GB", availGb))
    } catch (e: Exception) {
        // memory info unavailable
    }

    val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
    info.add("App heap limit" to "$maxHeapMb MB")

    return info
}
