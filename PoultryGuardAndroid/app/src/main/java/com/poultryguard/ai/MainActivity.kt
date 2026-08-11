package com.poultryguard.ai

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.animation.*

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.data.model.UserRole
import com.poultryguard.ai.ui.admin.AdminDashboardScreen
import com.poultryguard.ai.ui.alerts.AlertsScreen
import com.poultryguard.ai.ui.auth.*
import com.poultryguard.ai.ui.controls.ControlsScreen
import com.poultryguard.ai.ui.dashboard.DashboardScreen
import com.poultryguard.ai.ui.dashboard.DashboardViewModel
import com.poultryguard.ai.ui.profile.ProfileScreen
import com.poultryguard.ai.ui.profile.HardwareConfigScreen
import com.poultryguard.ai.ui.theme.AppLanguage
import com.poultryguard.ai.ui.theme.LocalAppLanguage
import com.poultryguard.ai.ui.theme.PoultryGuardTheme
import com.poultryguard.ai.ui.theme.stringResource
import com.poultryguard.ai.ui.vet.VetDashboardScreen
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.ui.Alignment
import com.poultryguard.ai.ui.mortality.MortalityScreen
import com.poultryguard.ai.ui.mortality.MortalityViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val db = remember { com.poultryguard.ai.data.cache.AppDatabase.getDatabase(applicationContext) }
            val statsDao = remember { db.systemStatsDao() }
            val vetRepository = remember { com.poultryguard.ai.data.repository.VetRepository(applicationContext) }
            
            LaunchedEffect(Unit) {
                vetRepository.populateInitialVetsIfNeeded()
                if (statsDao.getCount() == 0) {
                    statsDao.insertOrUpdate(
                        com.poultryguard.ai.data.model.SystemStats(
                            id = 1,
                            totalFarmers = 124,
                            activeFarmers = 98,
                            totalFarms = 112,
                            activeFarms = 88,
                            totalDevices = 117,
                            onlineDevices = 89,
                            offlineDevices = 12,
                            registeredVets = 15,
                            activeVets = 11,
                            openDiseaseAlerts = 7,
                            pendingSupportRequests = 3
                        )
                    )
                }
            }
            
            var currentLanguage by remember { mutableStateOf(AppLanguage.ENGLISH) }

            CompositionLocalProvider(LocalAppLanguage provides currentLanguage) {
                PoultryGuardTheme {
                    val authViewModel: AuthViewModel = viewModel()
                    val authState by authViewModel.uiState.collectAsState(initial = AuthUiState.Loading)

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (val state = authState) {
                            is AuthUiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(24.dp)
                                    )
                                }
                            }
                            is AuthUiState.Authenticated -> {
                                val userProfile = state.user
                                when (userProfile.role) {
                                    UserRole.FARMER -> {
                                        FarmerNavigationContainer(
                                            userProfile = userProfile,
                                            currentLanguage = currentLanguage,
                                            onLanguageChanged = { currentLanguage = it },
                                            onLogout = { authViewModel.logout() },
                                            vetRepository = vetRepository
                                        )
                                    }
                                    UserRole.VETERINARIAN -> {
                                        VetDashboardScreen(
                                            onLogout = { authViewModel.logout() },
                                            vetRepository = vetRepository
                                        )
                                    }
                                    UserRole.ADMIN -> {
                                        AdminDashboardScreen(
                                            onLogout = { authViewModel.logout() },
                                            database = db
                                        )
                                    }
                                }
                            }
                            else -> {
                                AuthNavigationContainer(authViewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AuthNavigationContainer(authViewModel: AuthViewModel) {
        val authNavController = rememberNavController()

        NavHost(
            navController = authNavController,
            startDestination = "login"
        ) {
            composable("login") {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToRegister = {
                        authViewModel.resetState()
                        authNavController.navigate("register")
                    },
                    onNavigateToForgotPassword = {
                        authViewModel.resetState()
                        authNavController.navigate("forgot_password")
                    }
                )
            }
            composable("register") {
                RegisterScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = {
                        authViewModel.resetState()
                        authNavController.navigate("login") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("forgot_password") {
                ForgotPasswordScreen(
                    viewModel = authViewModel,
                    onNavigateBack = {
                        authViewModel.resetState()
                        authNavController.navigate("login") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun FarmerNavigationContainer(
        userProfile: UserProfile,
        currentLanguage: AppLanguage,
        onLanguageChanged: (AppLanguage) -> Unit,
        onLogout: () -> Unit,
        vetRepository: com.poultryguard.ai.data.repository.VetRepository
    ) {
        val navController = rememberNavController()
        val dashboardViewModel: DashboardViewModel = viewModel()
        var currentTab by remember { mutableStateOf("dashboard") }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
                    NavigationBarItem(
                        selected = currentTab == "profile",
                        onClick = {
                            currentTab = "profile"
                            navController.navigate("profile") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text(stringResource("profile")) }
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
                                this@MainActivity,
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
                    AlertsScreen()
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
}
