package com.carscan.ai.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.carscan.ai.Category
import com.carscan.ai.InspectionViewModel
import com.carscan.ai.ui.theme.Accent
import com.carscan.ai.ui.theme.Bad
import com.carscan.ai.ui.theme.Bg
import com.carscan.ai.ui.theme.Good
import com.carscan.ai.ui.theme.SurfaceAlt
import com.carscan.ai.ui.theme.TextMuted
import com.carscan.ai.ui.theme.TextPrimary
import com.carscan.ai.ui.theme.Warn

@Composable
fun ReportScreen(nav: NavController, vm: InspectionViewModel) {

    val report = vm.report
    if (report == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg),
            contentAlignment = Alignment.Center
        ) {
            Text("No report available", color = TextMuted)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("Inspection Report") {
            nav.popBackStack("home", inclusive = false)
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // ---------- Vehicle ----------
            SectionCard {
                Text(
                    report.vehicle,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(report.variant, color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row {
                    Chip(Icons.Default.Speed, report.odometer)
                    Spacer(Modifier.width(16.dp))
                    Chip(Icons.Default.LocalGasStation, report.fuel)
                }
                Spacer(Modifier.height(6.dp))
                Chip(Icons.Default.LocationOn, report.location)
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Overall score ----------
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${report.overall}",
                            color = Accent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            report.verdict,
                            color = Good,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            report.summary,
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ---------- Categories ----------
            Text(
                "Category-wise Analysis",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            report.categories.forEach { CategoryRow(it) }

            Spacer(Modifier.height(22.dp))

            // ---------- Findings ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Warn,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Key Findings",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Bad.copy(alpha = 0.20f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        "${report.findings.size} issues",
                        color = Bad,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            report.findings.forEach { finding ->
                SectionCard {
                    Text(
                        finding.title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(finding.detail, color = TextMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(20.dp))

            PrimaryButton("Download PDF Report") {
                // TODO: PDF export
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CategoryRow(category: Category) {
    val tint = when {
        category.score >= 80 -> Good
        category.score >= 65 -> Accent
        else -> Warn
    }
    Column(modifier = Modifier.padding(vertical = 7.dp)) {
        Row {
            Text(
                category.name,
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${category.score}",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { category.score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = tint,
            trackColor = SurfaceAlt,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(text, color = TextMuted, fontSize = 11.sp)
    }
}
