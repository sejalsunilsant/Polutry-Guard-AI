package com.poultryguard.ai.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.SolidColor
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poultryguard.ai.data.api.DiseaseRiskLevel
import com.poultryguard.ai.data.model.ConnectionState
import com.poultryguard.ai.data.model.SensorReading
import com.poultryguard.ai.data.model.SensorStatus
import com.poultryguard.ai.ui.components.*
import com.poultryguard.ai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSensorClick: (SensorReading) -> Unit,
    currentLanguage: AppLanguage,
    onLanguageChanged: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    // Chat bottom sheet states
    var showChatBottomSheet by remember { mutableStateOf(false) }
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        floatingActionButton = {
            // AI Copilot FAB
            FloatingActionButton(
                onClick = { showChatBottomSheet = true },
                containerColor = GreenPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = "AI Assistant",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(ShimmerBrush(), shape = RoundedCornerShape(12.dp))
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(150.dp)
                                    .background(ShimmerBrush(), shape = RoundedCornerShape(16.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(150.dp)
                                    .background(ShimmerBrush(), shape = RoundedCornerShape(16.dp))
                            )
                        }
                    }
                }
                is DashboardUiState.Success -> {
                    DashboardContent(
                        state = state,
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshData() },
                        onSensorClick = onSensorClick,
                        onSimulateSpike = { viewModel.triggerSimulatedSpike(it) },
                        onSubmitMortality = { count, symptoms -> viewModel.submitMortalityLog(count, symptoms) },
                        currentLanguage = currentLanguage,
                        onLanguageChanged = onLanguageChanged
                    )

                    // Sliding Bottom Sheet Chat UI Interface
                    if (showChatBottomSheet) {
                        ModalBottomSheet(
                            onDismissRequest = { showChatBottomSheet = false },
                            sheetState = sheetState,
                            dragHandle = { BottomSheetDefaults.DragHandle() },
                            containerColor = CardSurface
                        ) {
                            ChatTerminalContent(
                                messages = chatMessages,
                                isTyping = isTyping,
                                onSendMessage = { text -> viewModel.sendChatMessage(text) },
                                onCloseSheet = { showChatBottomSheet = false }
                            )
                        }
                    }
                }
                is DashboardUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = state.message, color = AlertRed, style = Typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    state: DashboardUiState.Success,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSensorClick: (SensorReading) -> Unit,
    onSimulateSpike: (String) -> Unit,
    onSubmitMortality: (Int, List<String>) -> Unit,
    currentLanguage: AppLanguage,
    onLanguageChanged: (AppLanguage) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp) // Avoid FAB overlaying elements
    ) {
        // Welcomes, Profile, and Language Switcher
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource("app_title"),
                            style = Typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        // Live Pulse Dot
                        val pulseColor = if (state.isMqttConnected) GreenPrimary else AlertOrange
                        val pulseText = if (state.isMqttConnected) stringResource("live") else stringResource("mqtt_sync")
                        
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
                                .size(8.dp * scale)
                                .clip(CircleShape)
                                .background(pulseColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = pulseText,
                            style = Typography.labelMedium,
                            color = pulseColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = stringResource("welcome"),
                        style = Typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // In-App Multi-language Switcher Pill (Visual Masterpiece)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(GreenPrimary.copy(alpha = 0.08f))
                        .border(1.dp, GreenPrimary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
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
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else GreenPrimary
                            )
                        }
                    }
                }
            }
        }

        // Live AI Disease Risk Level Indicator Widget
        item {
            val prediction = state.diseasePrediction
            val riskColor = when (prediction.riskLevel) {
                DiseaseRiskLevel.LOW -> GreenPrimary
                DiseaseRiskLevel.MEDIUM -> AlertOrange
                DiseaseRiskLevel.HIGH -> AlertRed
            }
            val riskBg = when (prediction.riskLevel) {
                DiseaseRiskLevel.LOW -> GreenLight
                DiseaseRiskLevel.MEDIUM -> Color(0xFFFFF3E0)
                DiseaseRiskLevel.HIGH -> Color(0xFFFFEBEE)
            }
            val riskIcon = when (prediction.riskLevel) {
                DiseaseRiskLevel.LOW -> Icons.Default.VerifiedUser
                DiseaseRiskLevel.MEDIUM -> Icons.Default.Shield
                DiseaseRiskLevel.HIGH -> Icons.Default.Coronavirus
            }
            val riskLabel = when (prediction.riskLevel) {
                DiseaseRiskLevel.LOW -> "LOW RISK - FLOCK SECURE"
                DiseaseRiskLevel.MEDIUM -> "MEDIUM RISK - ATTENTION REQUIRED"
                DiseaseRiskLevel.HIGH -> "HIGH RISK - AI RESPIRATORY ALERT!"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = riskBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(riskColor.copy(alpha = 0.4f))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(riskColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = riskIcon,
                                    contentDescription = "Disease Risk",
                                    tint = riskColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = riskLabel,
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = riskColor,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = "AI Confidence: ${(prediction.confidence * 100).toInt()}%",
                            style = Typography.labelMedium,
                            color = riskColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(riskColor.copy(alpha = 0.08f), shape = RoundedCornerShape(100.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = prediction.recommendation,
                        style = Typography.bodyMedium,
                        color = TextDark,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Shed & Stock Info Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Shed",
                            tint = GreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource("active_shed"),
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "${stringResource("healthy_stock")}: ${state.birdCount} broilers",
                                style = Typography.bodyMedium,
                                color = TextMedium
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppBackground)
                            .clickable { onRefresh() }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync Now",
                            tint = GreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2x2 Sensor Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SensorCard(
                        reading = state.sensorReadings[0].copy(name = stringResource("temp")), // Temp
                        onClick = { onSensorClick(state.sensorReadings[0]) },
                        modifier = Modifier.weight(1f)
                    )
                    SensorCard(
                        reading = state.sensorReadings[1].copy(name = stringResource("humid")), // Humid
                        onClick = { onSensorClick(state.sensorReadings[1]) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SensorCard(
                        reading = state.sensorReadings[2].copy(name = stringResource("ammonia")), // Ammonia
                        onClick = { onSensorClick(state.sensorReadings[2]) },
                        modifier = Modifier.weight(1f)
                    )
                    SensorCard(
                        reading = state.sensorReadings[3].copy(name = stringResource("sound")), // Sound
                        onClick = { onSensorClick(state.sensorReadings[3]) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Flock Mortality Management Module
        item {
            var deathCount by remember { mutableStateOf(1) }
            val symptomsList = listOf("Respiratory Snick", "Lethargy", "Loose Droppings", "Sudden Death")
            val selectedSymptoms = remember { mutableStateListOf<String>() }
            var submissionSuccess by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource("mortality_mgmt"),
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        // Live calculated Mortality rate badge
                        val total = state.birdCount + state.loggedMortalities
                        val rate = if (total > 0) (state.loggedMortalities.toFloat() / total) * 100 else 0f
                        Text(
                            text = "Rate: %.2f%%".format(rate),
                            style = Typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (rate > 1.0f) AlertRed else GreenPrimary,
                            modifier = Modifier
                                .background(if (rate > 1.0f) Color(0xFFFFEBEE) else GreenLight, shape = RoundedCornerShape(100.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (submissionSuccess) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Mortality Logged Successfully!",
                                    style = Typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                            }
                        }
                        
                        LaunchedEffect(Unit) {
                            delay(2000)
                            submissionSuccess = false
                            selectedSymptoms.clear()
                            deathCount = 1
                        }
                    } else {
                        // Count selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource("log_death"),
                                style = Typography.bodyMedium,
                                color = TextMedium
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AppBackground)
                                        .clickable { if (deathCount > 1) deathCount-- },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                }

                                Text(
                                    text = "$deathCount",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AppBackground)
                                        .clickable { deathCount++ },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Symptom multi select
                        Text(
                            text = stringResource("symptoms"),
                            style = Typography.labelMedium,
                            color = TextMedium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            symptomsList.take(2).forEach { symptom ->
                                val active = selectedSymptoms.contains(symptom)
                                FilterChip(
                                    selected = active,
                                    onClick = {
                                        if (active) selectedSymptoms.remove(symptom)
                                        else selectedSymptoms.add(symptom)
                                    },
                                    label = { Text(symptom, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            symptomsList.drop(2).forEach { symptom ->
                                val active = selectedSymptoms.contains(symptom)
                                FilterChip(
                                    selected = active,
                                    onClick = {
                                        if (active) selectedSymptoms.remove(symptom)
                                        else selectedSymptoms.add(symptom)
                                    },
                                    label = { Text(symptom, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                onSubmitMortality(deathCount, selectedSymptoms.toList())
                                submissionSuccess = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) {
                            Text(
                                text = stringResource("submit_log"),
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Environmental Controls Integration
        item {
            QuickActionRow(modifier = Modifier.padding(top = 8.dp))
        }

        // Telemetry Simulator / Tester panel
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BlueLight.copy(alpha = 0.3f)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(BlueSecondary.copy(alpha = 0.2f))
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = "Simulator",
                            tint = BlueSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource("sim_deck"),
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = BlueDark
                        )
                    }
                    Text(
                        text = "Trigger environmental anomalies to test MVVM reactivity, MQTT simulated streams, and AI disease prediction fallbacks.",
                        style = Typography.labelMedium,
                        color = TextMedium,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { onSimulateSpike("temp") },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertOrange),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Heat Spike", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = { onSimulateSpike("ammonia") },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Ammonia Gas", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = { onSimulateSpike("sound") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Panic Noise", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = { onSimulateSpike("reset") },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset Live", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// Beautiful AI Copilot Terminal inside ModalBottomSheet
@Composable
fun ChatTerminalContent(
    messages: List<com.poultryguard.ai.data.api.ChatMessage>,
    isTyping: Boolean,
    onSendMessage: (String) -> Unit,
    onCloseSheet: () -> Unit
) {
    var textState by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    // Automatically auto-scroll to the latest message bubble
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Chat Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = "Copilot",
                        tint = GreenPrimary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "AI Farm Copilot",
                        style = Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(GreenPrimary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "flock assistant online",
                            style = Typography.labelSmall,
                            color = TextMedium
                        )
                    }
                }
            }

            IconButton(
                onClick = onCloseSheet,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppBackground)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextMedium,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Suggestion Prompt Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val suggestions = listOf("Assess disease risk", "Is ammonia level safe?", "mortality rate status")
            suggestions.take(2).forEach { prompt ->
                SuggestionChip(
                    onClick = { onSendMessage(prompt) },
                    label = { Text(prompt, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message List bubbles
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(AppBackground, shape = RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.sender == "USER"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                )
                            )
                            .background(if (isUser) GreenPrimary else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isUser) Color.Transparent else DividerColor,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = msg.text,
                            fontSize = 13.sp,
                            color = if (isUser) Color.White else TextDark,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Shimmer typing indicator
            if (isTyping) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                                .background(Color.White)
                                .padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = GreenPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Copilot is analyzing sensors...",
                                    fontSize = 11.sp,
                                    color = TextMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // TextInput sticky dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                placeholder = { Text("Ask Copilot regarding air safety...") },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    focusedLabelColor = GreenPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textState.isNotBlank()) {
                            onSendMessage(textState)
                            textState = ""
                        }
                    }
                )
            )

            IconButton(
                onClick = {
                    if (textState.isNotBlank()) {
                        onSendMessage(textState)
                        textState = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GreenPrimary)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DashboardContentPreview() {

    val sampleSensors = listOf(
        SensorReading(
            id = "temp_1",
            name = "Temperature",
            value = 28.5f,
            unit = "°C",
            status = SensorStatus.IDEAL,
            timestamp = "2026-05-31 18:00",
            description = "Poultry shed temperature",
            rangeMin = 0f,
            rangeMax = 50f,
            idealMin = 24f,
            idealMax = 30f
        ),
        SensorReading(
            id = "hum_1",
            name = "Humidity",
            value = 65f,
            unit = "%",
            status = SensorStatus.IDEAL,
            timestamp = "2026-05-31 18:00",
            description = "Poultry shed humidity",
            rangeMin = 0f,
            rangeMax = 100f,
            idealMin = 50f,
            idealMax = 70f
        ),
        SensorReading(
            id = "amm_1",
            name = "Ammonia",
            value = 12f,
            unit = "ppm",
            status = SensorStatus.WARNING,
            timestamp = "2026-05-31 18:00",
            description = "Ammonia concentration",
            rangeMin = 0f,
            rangeMax = 100f,
            idealMin = 0f,
            idealMax = 10f
        ),
        SensorReading(
            id = "sound_1",
            name = "Sound",
            value = 45f,
            unit = "dB",
            status = SensorStatus.IDEAL,
            timestamp = "2026-05-31 18:00",
            description = "Flock sound level",
            rangeMin = 0f,
            rangeMax = 120f,
            idealMin = 30f,
            idealMax = 60f
        )
    )

    val sampleState = DashboardUiState.Success(
        sensorReadings = sampleSensors,
        connectionState = ConnectionState.CONNECTED,
        shedName = "Shed #4 (Broilers - Day 18)",
        birdCount = 500,
        loggedMortalities = 2,
        alertCount = 1,
        isMqttConnected = true
    )

    PoultryGuardAITheme {   // Replace with your actual theme if different
        DashboardContent(
            state = sampleState,
            isRefreshing = false,
            onRefresh = {},
            onSensorClick = {},
            onSimulateSpike = {},
            onSubmitMortality = { _, _ -> },
            currentLanguage = AppLanguage.ENGLISH,
            onLanguageChanged = {}
        )
    }
}


