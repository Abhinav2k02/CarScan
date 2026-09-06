package com.carscan.ai.screens

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.carscan.ai.InspectionViewModel
import com.carscan.ai.ui.theme.Accent
import com.carscan.ai.ui.theme.Bg
import com.carscan.ai.ui.theme.Surface
import com.carscan.ai.ui.theme.TextMuted
import com.carscan.ai.ui.theme.TextPrimary

@Composable
fun HomeScreen(nav: NavController, vm: InspectionViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ---- Brand header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Bg)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "CarScan",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("Buy Smarter. Drive Safer.", color = TextMuted, fontSize = 11.sp)
            }
            IconButton(onClick = { nav.navigate("settings") }) {
                Icon(Icons.Default.Menu, contentDescription = "Settings", tint = TextPrimary)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- Hero ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Surface),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("Hero Vehicle Image", color = TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "AI Inspection for",
            color = TextPrimary,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )
        Text("Cars", color = Accent, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Upload a video or dashboard image and get an instant AI-powered report.",
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(24.dp))

        ActionCard(
            icon = Icons.Default.PlayCircleFilled,
            title = "Inspect with Video",
            subtitle = "Upload or record a full walkaround video",
            highlighted = true
        ) {
            vm.reset()
            nav.navigate("video")
        }

        Spacer(Modifier.height(12.dp))

        ActionCard(
            icon = Icons.Default.Speed,
            title = "Check Dashboard",
            subtitle = "Upload a dashboard image for odometer & details",
            highlighted = false
        ) {
            vm.reset()
            nav.navigate("dashboard")
        }

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Feature(Icons.Default.PhoneAndroid, "On-Device AI", "Fast & Private")
            Feature(Icons.Default.Layers, "Multi-Modal", "Analysis")
            Feature(Icons.Default.VerifiedUser, "Trusted", "Insights")
        }

        Spacer(Modifier.height(28.dp))

        Text("A Better Journey", color = Accent, fontSize = 13.sp, fontStyle = FontStyle.Italic)
        Text(
            "Starts with the Right Car",
            color = Accent,
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic
        )

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) Accent else Surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (highlighted) Bg else Accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) Bg else TextPrimary
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = if (highlighted) Bg.copy(alpha = 0.7f) else TextMuted
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (highlighted) Bg else TextMuted
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, line1: String, line2: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(line1, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(line2, color = TextMuted, fontSize = 10.sp)
    }
}
