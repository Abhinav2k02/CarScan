package com.carscan.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carscan.ai.screens.DashboardScreen
import com.carscan.ai.screens.HomeScreen
import com.carscan.ai.screens.ManifestScreen
import com.carscan.ai.screens.ProcessingScreen
import com.carscan.ai.screens.ReportScreen
import com.carscan.ai.screens.SettingsScreen
import com.carscan.ai.screens.VideoScreen
import com.carscan.ai.ui.theme.Bg
import com.carscan.ai.ui.theme.CarScanTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarScanTheme {
                val nav = rememberNavController()
                val vm: InspectionViewModel = viewModel()

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Bg),
                    containerColor = Bg
                ) { innerPadding ->
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        modifier = Modifier
                            .padding(innerPadding)
                            .windowInsetsPadding(WindowInsets.systemBars)
                    ) {
                        composable("home") { HomeScreen(nav, vm) }
                        composable("video") { VideoScreen(nav, vm) }
                        composable("dashboard") { DashboardScreen(nav, vm) }
                        composable("processing") { ProcessingScreen(nav, vm) }
                        composable("manifest") { ManifestScreen(nav, vm) }
                        composable("report") { ReportScreen(nav, vm) }
                        composable("settings") { SettingsScreen(nav, vm) }
                    }
                }
            }
        }
    }
}
