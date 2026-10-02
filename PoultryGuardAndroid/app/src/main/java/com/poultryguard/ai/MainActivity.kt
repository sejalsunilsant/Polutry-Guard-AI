package com.poultryguard.ai

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.poultryguard.ai.ui.navigation.FarmerNavigationContainer
import com.poultryguard.ai.data.model.UserRole
import com.poultryguard.ai.ui.admin.AdminDashboardScreen
import com.poultryguard.ai.ui.alerts.AlertsScreen
import com.poultryguard.ai.ui.auth.*
import com.poultryguard.ai.ui.controls.ControlsScreen
import com.poultryguard.ai.ui.dashboard.DashboardScreen
import com.poultryguard.ai.ui.dashboard.DashboardViewModel
import com.poultryguard.ai.ui.profile.ProfileScreen
import com.poultryguard.ai.ui.profile.HardwareConfigScreen
import com.poultryguard.ai.ui.theme.*
import com.poultryguard.ai.ui.localization.*
import com.poultryguard.ai.ui.vet.VetDashboardScreen
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.ui.Alignment
import com.poultryguard.ai.ui.mortality.MortalityScreen
import com.poultryguard.ai.ui.mortality.MortalityViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize FCM Notification Channel
        com.poultryguard.ai.data.service.PoultryGuardFirebaseMessagingService.createNotificationChannel(applicationContext)

        // Retrieve and sync FCM device registration token
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrBlank()) {
                    val token = task.result
                    val cacheManager = com.poultryguard.ai.data.cache.LocalCacheManager(applicationContext)
                    cacheManager.saveFcmToken(token)
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            com.poultryguard.ai.data.repository.SupabaseAuthRepository(applicationContext).updateFcmToken(token)
                        } catch (e: Exception) {
                            // Non-blocking background sync
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Graceful fallback if Firebase credentials are initializing
        }

        setContent {
            val db = remember { com.poultryguard.ai.data.cache.AppDatabase.getDatabase(applicationContext) }
            val vetRepository = remember { com.poultryguard.ai.data.repository.VetRepository(applicationContext) }
            
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
                                            vetRepository = vetRepository,
                                            userProfile = userProfile
                                        )
                                    }
                                    UserRole.ADMIN, UserRole.SUPER_ADMIN -> {
                                        AdminDashboardScreen(
                                            onLogout = { authViewModel.logout() },
                                            database = db
                                        )
                                    }
                                }
                            }
                            is AuthUiState.PendingApproval -> {
                                PendingApprovalScreen(
                                    message = state.message,
                                    onBackToLogin = { authViewModel.resetState() }
                                )
                            }
                            is AuthUiState.RejectedApproval -> {
                                RejectedApprovalScreen(
                                    reason = state.reason,
                                    onBackToLogin = { authViewModel.resetState() }
                                )
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
    private fun PendingApprovalScreen(message: String, onBackToLogin: () -> Unit) {
        Box(
            modifier = Modifier.fillMaxSize().background(AppBackground).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, DividerColor)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Pending Approval",
                        tint = AlertOrange,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Registration Pending Approval",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = Typography.bodyMedium,
                        color = TextMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onBackToLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Back to Login", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    @Composable
    private fun RejectedApprovalScreen(reason: String, onBackToLogin: () -> Unit) {
        Box(
            modifier = Modifier.fillMaxSize().background(AppBackground).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, DividerColor)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Rejected",
                        tint = AlertRed,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Registration Rejected",
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your request to register has been rejected by the administrator.",
                        style = Typography.bodyMedium,
                        color = TextMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AlertRed.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .border(1.dp, AlertRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Reason: $reason",
                            style = Typography.bodyMedium,
                            color = AlertRed,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onBackToLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Back to Login", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
