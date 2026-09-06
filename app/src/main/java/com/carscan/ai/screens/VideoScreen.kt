package com.carscan.ai.screens

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.carscan.ai.InspectionViewModel
import com.carscan.ai.ui.theme.Accent
import com.carscan.ai.ui.theme.Bad
import com.carscan.ai.ui.theme.Bg
import com.carscan.ai.ui.theme.Good
import com.carscan.ai.ui.theme.Surface
import com.carscan.ai.ui.theme.SurfaceAlt
import com.carscan.ai.ui.theme.TextMuted
import com.carscan.ai.ui.theme.TextPrimary
import java.io.File

/**
 * MediaMetadataRetriever only implements AutoCloseable from API 29, and minSdk
 * here is 26 — so release() is called explicitly rather than using `use { }`.
 */
private fun extractThumbnail(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0)
    } catch (e: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (ignored: Exception) {
            // no-op
        }
    }
}

@Composable
fun VideoScreen(nav: NavController, vm: InspectionViewModel) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            vm.videoUri = uri
            thumbnail = extractThumbnail(context, uri)
        }
    }

    val recordVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success: Boolean ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            vm.videoUri = uri
            thumbnail = extractThumbnail(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("Inspect with Video") { nav.popBackStack() }
        Spacer(Modifier.height(8.dp))
        Stepper(current = 1)
        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ---------- Upload ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(1.dp, SurfaceAlt, RoundedCornerShape(16.dp))
                    .clickable {
                        pickVideo.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.VideoOnly
                            )
                        )
                    }
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.FileUpload,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Upload a Vehicle Video",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap to choose a file from your device",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Accent)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Choose Video",
                        color = Bg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Supported: MP4, MOV, AVI  ·  max 500 MB",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Or Record Now",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            // ---------- Preview ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface),
                contentAlignment = Alignment.Center
            ) {
                val bmp = thumbnail
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Bg.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                    val file = File(dir, "walkaround_${System.currentTimeMillis()}.mp4")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    pendingCaptureUri = uri
                    recordVideo.launch(uri)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = Bad,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Start Recording", color = TextPrimary, fontSize = 15.sp)
            }

            Spacer(Modifier.height(16.dp))

            // ---------- Selection confirmation ----------
            if (vm.videoUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Good.copy(alpha = 0.12f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Good,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Video ready", color = TextPrimary, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            // ---------- Tip ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent.copy(alpha = 0.10f))
                    .padding(14.dp)
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Walk around the car slowly, covering exterior, interior and engine bay.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---------- Analyze now ----------
            PrimaryButton(
                text = "Analyze",
                enabled = vm.videoUri != null
            ) {
                nav.navigate("processing")
            }

            Spacer(Modifier.height(12.dp))

            // ---------- Optional extra input ----------
            OutlinedButton(
                onClick = { nav.navigate("dashboard") },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (vm.dashboardUri == null) "Add dashboard photo (optional)"
                    else "Dashboard photo added — change",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
