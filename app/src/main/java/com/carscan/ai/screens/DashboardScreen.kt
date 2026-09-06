package com.carscan.ai.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.carscan.ai.InspectionViewModel
import com.carscan.ai.pipeline.ComputeBackend
import com.carscan.ai.ui.theme.Accent
import com.carscan.ai.ui.theme.Bg
import com.carscan.ai.ui.theme.Good
import com.carscan.ai.ui.theme.Surface
import com.carscan.ai.ui.theme.SurfaceAlt
import com.carscan.ai.ui.theme.TextMuted
import com.carscan.ai.ui.theme.TextPrimary
import java.io.File

@Composable
fun DashboardScreen(nav: NavController, vm: InspectionViewModel) {
    val context = LocalContext.current
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) vm.dashboardUri = uri
    }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) vm.dashboardUri = pendingCaptureUri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("Dashboard Image") { nav.popBackStack() }
        Spacer(Modifier.height(8.dp))
        Stepper(current = 1)
        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ---------- Odometer preview ----------
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(Surface),
                contentAlignment = Alignment.Center
            ) {
                val uri = vm.dashboardUri
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("Odometer", color = TextMuted, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Odometer reading", color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(24.dp))

            // ---------- Upload ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface)
                    .clickable {
                        pickImage.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Good)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Upload Dashboard Image",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Clear photo of the odometer and cluster",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- Camera ----------
            OutlinedButton(
                onClick = {
                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                    val file = File(dir, "dashboard_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    pendingCaptureUri = uri
                    takePhoto.launch(uri)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(8.dp))
                Text("Take Photo", color = TextPrimary, fontSize = 15.sp)
            }

            Spacer(Modifier.height(20.dp))

            // ---------- Inputs summary ----------
            SectionCard {
                Text(
                    "Inputs for this analysis",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                InputRow(
                    icon = Icons.Default.Videocam,
                    label = "Walkaround video",
                    present = vm.videoUri != null
                )
                InputRow(
                    icon = Icons.Default.Image,
                    label = "Dashboard photo",
                    present = vm.dashboardUri != null
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Either one is enough. Both gives a fuller report.",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(14.dp))

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
                    "Auto tries NPU, then GPU, then CPU",
                    color = TextMuted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(12.dp))
                BackendSelector(
                    selected = vm.backendChoice,
                    onSelect = { vm.backendChoice = it }
                )
            }

            Spacer(Modifier.height(20.dp))

            PrimaryButton(
                text = "Analyze",
                enabled = vm.videoUri != null || vm.dashboardUri != null
            ) {
                nav.navigate("processing")
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun InputRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    present: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (present) Icons.Default.CheckCircle else icon,
            contentDescription = null,
            tint = if (present) Good else SurfaceAlt,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            if (present) "added" else "not added",
            color = if (present) Good else TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun BackendSelector(
    selected: ComputeBackend?,
    onSelect: (ComputeBackend?) -> Unit
) {
    val options: List<Pair<String, ComputeBackend?>> = listOf(
        "Auto" to null,
        "NPU" to ComputeBackend.NPU,
        "GPU" to ComputeBackend.GPU,
        "CPU" to ComputeBackend.CPU
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (label, value) ->
            val active = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Accent else SurfaceAlt)
                    .border(
                        1.dp,
                        if (active) Accent else SurfaceAlt,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) Bg else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (index < options.lastIndex) Spacer(Modifier.width(8.dp))
        }
    }
}
