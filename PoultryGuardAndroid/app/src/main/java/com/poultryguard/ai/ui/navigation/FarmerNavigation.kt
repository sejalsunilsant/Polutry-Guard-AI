package com.poultryguard.ai.ui.navigation

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.poultryguard.ai.R
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.ui.alerts.AlertsScreen
import com.poultryguard.ai.ui.controls.ControlsScreen
import com.poultryguard.ai.ui.dashboard.DashboardScreen
import com.poultryguard.ai.ui.dashboard.DashboardViewModel
import com.poultryguard.ai.ui.mortality.MortalityScreen
import com.poultryguard.ai.ui.mortality.MortalityViewModel
import com.poultryguard.ai.ui.profile.HardwareConfigScreen
import com.poultryguard.ai.ui.profile.ProfileScreen
import com.poultryguard.ai.ui.theme.*
import com.poultryguard.ai.ui.localization.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerNavigationContainer(
    userProfile: UserProfile,
    currentLanguage: AppLanguage,
    onLanguageChanged: (AppLanguage) -> Unit,
    onLogout: () -> Unit,
    vetRepository: com.poultryguard.ai.data.repository.VetRepository
) {
    val navController = rememberNavController()
    val dashboardViewModel: DashboardViewModel = viewModel()
    var currentTab by remember { mutableStateOf("dashboard") }
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val uiState by dashboardViewModel.uiState.collectAsState()
            val isMqttConnected = (uiState as? com.poultryguard.ai.ui.dashboard.DashboardUiState.Success)?.isMqttConnected ?: false

            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Poultry Guard Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource("app_title"),
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val pulseColor = if (isMqttConnected) GreenPrimary else AlertOrange
                                val pulseText = if (isMqttConnected) stringResource("live") else stringResource("mqtt_sync")
                                
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 0.7f,
                                    targetValue = 1.2f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = FastOutLinearInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "pulse"
                                )

                                Box(
                                    modifier = Modifier
                                        .size(6.dp * scale)
                                        .clip(CircleShape)
                                        .background(pulseColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = pulseText,
                                    fontSize = 10.sp,
                                    color = pulseColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(GreenPrimary.copy(alpha = 0.08f))
                            .border(1.dp, GreenPrimary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val langPills = listOf(
                            AppLanguage.ENGLISH to "EN",
                            AppLanguage.HINDI to "हिंदी",
                            AppLanguage.MARATHI to "मरा"
                        )
                        langPills.forEach { (lang, label) ->
                            val selected = lang == currentLanguage
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) GreenPrimary else Color.Transparent)
                                    .clickable { onLanguageChanged(lang) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else GreenPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            currentTab = "profile"
                            navController.navigate("profile") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = GreenPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == "dashboard",
                    onClick = {
                        currentTab = "dashboard"
                        navController.navigate("dashboard") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.GridView, contentDescription = "Dashboard") },
                    label = { Text(stringResource("dashboard")) }
                )
                NavigationBarItem(
                    selected = currentTab == "mortality",
                    onClick = {
                        currentTab = "mortality"
                        navController.navigate("mortality") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.HeartBroken, contentDescription = "Mortality") },
                    label = { Text(stringResource("mortality")) }
                )
                NavigationBarItem(
                    selected = currentTab == "controls",
                    onClick = {
                        currentTab = "controls"
                        navController.navigate("controls") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Controls") },
                    label = { Text(stringResource("controls")) }
                )
                NavigationBarItem(
                    selected = currentTab == "alerts",
                    onClick = {
                        currentTab = "alerts"
                        navController.navigate("alerts") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Visibility, contentDescription = "Guardian") },
                    label = { Text(stringResource("alerts")) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    farmerName = userProfile.name,
                    onSensorClick = { sensor ->
                        Toast.makeText(
                            context,
                            "Detailed telemetry charts for ${sensor.name} are ready for integration.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    currentLanguage = currentLanguage,
                    onLanguageChanged = onLanguageChanged,
                    onNavigateToMortality = {
                        currentTab = "mortality"
                        navController.navigate("mortality") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToStartBatch = {
                        navController.navigate("start_batch")
                    },
                    onNavigateToBatchHistory = {
                        navController.navigate("batch_history")
                    }
                )
            }
            composable("start_batch") {
                com.poultryguard.ai.ui.batch.StartBatchScreen(
                    viewModel = dashboardViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("batch_history") {
                com.poultryguard.ai.ui.batch.BatchHistoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("mortality") {
                val mortalityViewModel: MortalityViewModel = viewModel()
                MortalityScreen(
                    viewModel = mortalityViewModel
                )
            }
            composable("controls") {
                ControlsScreen()
            }
            composable("alerts") {
                AlertsScreen(farmerName = userProfile.name)
            }
            composable("profile") {
                ProfileScreen(
                    userProfile = userProfile,
                    onLogout = onLogout,
                    vetRepository = vetRepository,
                    onNavigateToHardwareConfig = {
                        navController.navigate("hardware_config")
                    }
                )
            }
            composable("hardware_config") {
                HardwareConfigScreen(
                    userProfile = userProfile,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
