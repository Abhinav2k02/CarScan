package com.carscan.ai.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.carscan.ai.InspectionViewModel
import com.carscan.ai.ui.theme.Accent
import com.carscan.ai.ui.theme.Bad
import com.carscan.ai.ui.theme.Bg
import com.carscan.ai.ui.theme.Good
import com.carscan.ai.ui.theme.TextMuted
import com.carscan.ai.ui.theme.TextPrimary

/**
 * Renders whatever stages the pipeline reports, in order, rather than matching
 * against a hardcoded list. PipelineRunner skips stages that do not apply —
 * frame extraction with no video, dashboard checks with no photo — so a fixed
 * list would silently fall out of sync and the screen would look frozen.
 */
@Composable
fun ProcessingScreen(nav: NavController, vm: InspectionViewModel) {

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.runPipeline(context)
        if (vm.pipelineError == null && vm.manifest != null) {
            nav.navigate("manifest") {
                popUpTo("home") { inclusive = false }
            }
        }
    }

    val error = vm.pipelineError
    val stages = vm.stageHistory
    val running = vm.running

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Analysing",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Running on-device. Nothing leaves your phone.",
            color = TextMuted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(32.dp))

        if (stages.isEmpty() && error == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Accent
                )
                Spacer(Modifier.width(14.dp))
                Text("Starting", color = TextPrimary, fontSize = 14.sp)
            }
        }

        stages.forEachIndexed { index, label ->
            val isLast = index == stages.lastIndex
            val done = !isLast || (!running && error == null)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (done) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Good,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Accent
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Bad,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(error, color = Bad, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Back") {
                nav.popBackStack("home", inclusive = false)
            }
        }
    }
}
