package com.poultryguard.ai.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.data.model.HardwareKit
import android.widget.Toast
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareConfigScreen(
    userProfile: UserProfile,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cacheManager = remember { LocalCacheManager(context) }
    
    // Loaded kits from SharedPreferences
    var kitsList by remember { mutableStateOf(cacheManager.getHardwareKits()) }
    
    // Steps: 0 = List of kits, 1 = Claim Key, 2 = BLE Search, 3 = Wi-Fi onboarding, 4 = Success
    var currentStep by remember { mutableStateOf(0) }
    
    // Onboarding Form States
    var kitId by remember { mutableStateOf("") }
    var wifiSsid by remember { mutableStateOf("Raj_Poultry_Secure_5G") }
    var wifiPassword by remember { mutableStateOf("") }
    
    // Scanning simulation states
    var selectedKit by remember { mutableStateOf<HardwareKit?>(null) }
    var bleScanningState by remember { mutableStateOf("starting") } // starting, scanning, found
    var wifiUploadingState by remember { mutableStateOf(0) } // 0 = idle, 1..5 = steps
    
    // Seed initial farmer-specific configuration suggestions
    val farmerName = userProfile.name.ifBlank { "Raj" }
    val farmName = "${farmerName} Poultry Farm"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "IoT Cryptographic Keys",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 0 && currentStep != 4) {
                            currentStep = 0
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = GreenPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            when (currentStep) {
                0 -> KitsListView(
                    kits = kitsList.filter {
                        (it.farmerId == userProfile.uid || (it.farmerId.isEmpty() && it.farmerName.trim().lowercase() == farmerName.trim().lowercase())) && it.isProvisioned
                    },
                    onProvisionClick = { currentStep = 1 },
                    onDeleteClick = { kit ->
                        cacheManager.deleteHardwareKit(kit.gatewayId)
                        kitsList = cacheManager.getHardwareKits()
                    },
                    onSetActiveClick = { kit ->
                        cacheManager.setActiveGatewayId(kit.gatewayId)
                        kitsList = cacheManager.getHardwareKits()
                    }
                )
                1 -> {
                    val assignedKits = kitsList.filter {
                        (it.farmerId == userProfile.uid || (it.farmerId.isEmpty() && it.farmerName.trim().lowercase() == farmerName.trim().lowercase())) && !it.isProvisioned
                    }
                    AssignedKitSelectionView(
                        assignedKits = assignedKits,
                        selectedKit = selectedKit,
                        onSelectKit = { kit ->
                            selectedKit = kit
                            kitId = kit.kitId
                        },
                        onNext = {
                            if (kitId.isNotBlank()) {
                                currentStep = 2
                            }
                        }
                    )
                }
                2 -> BleRadarView(
                    kitId = kitId,
                    scanningState = bleScanningState,
                    onScanningFinished = {
                        bleScanningState = "found"
                    },
                    onNext = {
                        currentStep = 3
                    },
                    onReset = {
                        bleScanningState = "starting"
                    }
                )
                3 -> WifiSetupView(
                    ssid = wifiSsid,
                    onSsidChange = { wifiSsid = it },
                    password = wifiPassword,
                    onPasswordChange = { wifiPassword = it },
                    uploadState = wifiUploadingState,
                    onUploadClick = {
                        wifiUploadingState = 1
                    },
                    onUploadFinished = {
                        currentStep = 4
                    }
                )
                4 -> ProvisioningSuccessView(
                    farmerName = farmerName,
                    farmName = farmName,
                    gatewayId = kitId,
                    ssid = wifiSsid,
                    onComplete = {
                        // Locate the existing registered kit to update it in-place and retain fields set by Admin
                        val existingKits = cacheManager.getHardwareKits()
                        val matchedKit = existingKits.find { 
                            it.kitId.trim().lowercase() == kitId.trim().lowercase() || 
                            it.gatewayId.trim().lowercase() == kitId.trim().lowercase() 
                        }
                        
                        val updatedKit = matchedKit?.copy(
                            isProvisioned = true,
                            ssid = wifiSsid,
                            isActive = kitsList.none { it.isActive && it.farmerName == farmerName },
                            provisionedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                            lifecycleStatus = "Active"
                        ) ?: HardwareKit(
                            farmerName = farmerName,
                            farmName = farmName,
                            gatewayId = kitId,
                            ssid = wifiSsid,
                            isProvisioned = true,
                            isActive = kitsList.isEmpty(),
                            provisionedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                            lifecycleStatus = "Active",
                            farmerId = userProfile.uid
                        )
                        
                        cacheManager.addHardwareKit(updatedKit)
                        kitsList = cacheManager.getHardwareKits()
                        
                        // Reset forms
                        kitId = ""
                        wifiPassword = ""
                        selectedKit = null
                        bleScanningState = "starting"
                        wifiUploadingState = 0
                        currentStep = 0
                    }
                )
            }
        }
    }
}

// ==========================================
// 1. KITS LIST VIEW
// ==========================================
@Composable
fun KitsListView(
    kits: List<HardwareKit>,
    onProvisionClick: () -> Unit,
    onDeleteClick: (HardwareKit) -> Unit,
    onSetActiveClick: (HardwareKit) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Hardware Gateway Integration",
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = "Onboard and claim secure sensor network kits over BLE.",
                    style = Typography.bodyMedium,
                    color = TextMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (kits.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(GreenPrimary.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "Sensors",
                                tint = GreenPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Gateways Connected",
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "Connect a hardware kit (ESP32 Gateway + Sensors) to enable telemetry mapping in real-time.",
                            style = Typography.bodyMedium,
                            color = TextMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onProvisionClick,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Onboard New Hardware Kit", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(kits) { kit ->
                HardwareKitCard(
                    kit = kit,
                    onDeleteClick = { onDeleteClick(kit) },
                    onSetActiveClick = { onSetActiveClick(kit) }
                )
            }

            item {
                OutlinedButton(
                    onClick = onProvisionClick,
                    border = BorderStroke(1.5.dp, GreenPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Provision Additional Kit", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HardwareKitCard(
    kit: HardwareKit,
    onDeleteClick: () -> Unit,
    onSetActiveClick: () -> Unit
) {
    var expandedTree by remember { mutableStateOf(true) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (kit.isActive) Color(0xFF4CAF50) else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = kit.gatewayId,
                        style = Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    if (kit.isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GreenPrimary.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Active Feeding",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!kit.isActive) {
                        TextButton(
                            onClick = onSetActiveClick,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Set Active", color = GreenPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete config",
                            tint = AlertRed.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

            // Body Meta Details
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MetaLabelValue("Farmer", kit.farmerName)
                MetaLabelValue("Associated Farm", kit.farmName)
                MetaLabelValue("Wi-Fi SSID", kit.ssid.ifBlank { "Not Connected" })
                MetaLabelValue("Registered On", kit.provisionedAt)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable Tree Visualizer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedTree = !expandedTree }
                    .background(AppBackground, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Security Status",
                        tint = BlueSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cryptographic Branch Structure",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMedium
                    )
                }
                Icon(
                    imageVector = if (expandedTree) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle tree",
                    tint = TextMedium,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (expandedTree) {
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 8.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Gateway: ${kit.gatewayId}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextDark,
                        fontWeight = FontWeight.Bold
                    )
                    SensorTreeLine("├──", kit.tempSensorId, "Temperature")
                    SensorTreeLine("├──", kit.humidSensorId, "Humidity")
                    SensorTreeLine("├──", kit.ammoniaSensorId, "Ammonia")
                    SensorTreeLine("└──", kit.soundSensorId, "Sound")
                }
            }
        }
    }
}

@Composable
fun MetaLabelValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = Typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = TextMedium,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            style = Typography.bodyMedium,
            color = TextDark
        )
    }
}

@Composable
fun SensorTreeLine(prefix: String, sensorId: String, name: String) {
    Row(
        modifier = Modifier.padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$prefix ",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = GreenPrimary
        )
        Text(
            text = sensorId,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = TextDark,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = " → ",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = TextMedium
        )
        Text(
            text = name,
            fontSize = 11.sp,
            color = TextMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ==========================================
// 2. CLAIM KEY VIEW (STEP 1)
// ==========================================
@Composable
fun AssignedKitSelectionView(
    assignedKits: List<HardwareKit>,
    selectedKit: HardwareKit?,
    onSelectKit: (HardwareKit) -> Unit,
    onNext: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Select Assigned Kit",
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = "Select one of the device kits assigned to you by the farm administrator to begin configuration.",
                    style = Typography.bodyMedium,
                    color = TextMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (assignedKits.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AlertOrange.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No Kits",
                                tint = AlertOrange,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Assigned Kits Found",
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "There are no unprovisioned hardware kits assigned to your farmer profile at this moment.\n\nPlease contact your farm administrator to register and assign a kit to you.",
                            style = Typography.bodyMedium,
                            color = TextMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(assignedKits) { kit ->
                val isSelected = selectedKit?.kitId == kit.kitId
                val borderStroke = if (isSelected) BorderStroke(2.dp, GreenPrimary) else BorderStroke(1.dp, DividerColor)
                val cardBg = if (isSelected) GreenPrimary.copy(alpha = 0.04f) else CardSurface
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectKit(kit) },
                    shape = RoundedCornerShape(16.dp),
                    border = borderStroke,
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = "IoT Device",
                                    tint = if (isSelected) GreenPrimary else TextMedium,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = kit.kitId,
                                    style = Typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Device ID: ", style = Typography.bodySmall, color = TextMedium, modifier = Modifier.width(100.dp))
                                Text(kit.gatewayId, style = Typography.bodySmall, color = TextDark, fontWeight = FontWeight.Medium)
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Serial No: ", style = Typography.bodySmall, color = TextMedium, modifier = Modifier.width(100.dp))
                                Text(kit.serialNumber, style = Typography.bodySmall, color = TextDark)
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Firmware: ", style = Typography.bodySmall, color = TextMedium, modifier = Modifier.width(100.dp))
                                Text(kit.firmwareVersion, style = Typography.bodySmall, color = TextDark)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNext,
                    enabled = selectedKit != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenPrimary,
                        disabledContainerColor = DividerColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Proceed to BLE pairing",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. BLE RADAR VIEW (STEP 2)
// ==========================================
@Composable
fun BleRadarView(
    kitId: String,
    scanningState: String,
    onScanningFinished: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Animate radar sweep radii
    val pulseFraction1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val pulseFraction2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 1100, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    LaunchedEffect(scanningState) {
        if (scanningState == "starting") {
            delay(500)
            onScanningFinished()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Locating ESP32 Beacon",
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Establishing secure Bluetooth pairing to program Wi-Fi parameters.",
                style = Typography.bodyMedium,
                color = TextMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Custom Pulsing Bluetooth Radar
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxRadius = size.minDimension / 2
                
                // Draw ripple 1
                drawCircle(
                    color = GreenPrimary,
                    radius = maxRadius * pulseFraction1,
                    alpha = 1f - pulseFraction1,
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Draw ripple 2
                drawCircle(
                    color = GreenPrimary,
                    radius = maxRadius * pulseFraction2,
                    alpha = 1f - pulseFraction2,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            
            // Central BLE Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (scanningState == "found") Color(0xFF4CAF50) else GreenPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (scanningState == "found") Icons.Default.Check else Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Loading & Result Console Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (scanningState != "found") {
                    CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Searching for broadcast beacon ($kitId)...",
                        style = Typography.bodyMedium,
                        color = TextMedium,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Found",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connection Secured",
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Device paired successfully via encrypted Bluetooth (RSSI: -45 dBm). Ready for network provisioning.",
                        style = Typography.bodyMedium,
                        color = TextMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Button(
            onClick = onNext,
            enabled = scanningState == "found",
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenPrimary,
                disabledContainerColor = DividerColor
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(bottom = 12.dp)
        ) {
            Text("Proceed to Wi-Fi Setup", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 4. WIFI SETUP VIEW (STEP 3)
// ==========================================
@Composable
fun WifiSetupView(
    ssid: String,
    onSsidChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    uploadState: Int,
    onUploadClick: () -> Unit,
    onUploadFinished: () -> Unit
) {
    // Simulate step by step progress for credentials transfer
    LaunchedEffect(uploadState) {
        if (uploadState in 1..4) {
            delay(1200)
            if (uploadState == 4) {
                onUploadFinished()
            } else {
                onUploadClick()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Wi-Fi Credentials Provisioning",
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = "Upload credentials over BLE so the gateway ESP32 can connect to your local Internet network.",
                    style = Typography.bodyMedium,
                    color = TextMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (uploadState == 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = ssid,
                            onValueChange = onSsidChange,
                            label = { Text("Wi-Fi SSID / Network Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = DividerColor,
                                focusedLabelColor = GreenPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            label = { Text("Wi-Fi Security Key (WPA2)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                unfocusedBorderColor = DividerColor,
                                focusedLabelColor = GreenPrimary
                            )
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onUploadClick,
                    enabled = ssid.isNotBlank() && password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenPrimary,
                        disabledContainerColor = DividerColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = "WiFi", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Provision Credentials over BLE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Provisioning Console",
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        ConsoleStep(
                            stepNumber = 1,
                            label = "Opening cryptographically-locked pipeline...",
                            isActive = uploadState == 1,
                            isDone = uploadState > 1
                        )
                        ConsoleStep(
                            stepNumber = 2,
                            label = "Injecting network profile block over Bluetooth...",
                            isActive = uploadState == 2,
                            isDone = uploadState > 2
                        )
                        ConsoleStep(
                            stepNumber = 3,
                            label = "Verifying ESP32 local connection response...",
                            isActive = uploadState == 3,
                            isDone = uploadState > 3
                        )
                        ConsoleStep(
                            stepNumber = 4,
                            label = "Binding device identity key to PoultryGuard backend...",
                            isActive = uploadState == 4,
                            isDone = uploadState > 4
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleStep(stepNumber: Int, label: String, isActive: Boolean, isDone: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDone) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Done",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
        } else if (isActive) {
            CircularProgressIndicator(
                color = GreenPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(DividerColor)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) TextDark else if (isDone) TextDark.copy(alpha = 0.5f) else TextMedium
        )
    }
}

// ==========================================
// 5. PROVISIONING SUCCESS VIEW (STEP 4)
// ==========================================
@Composable
fun ProvisioningSuccessView(
    farmerName: String,
    farmName: String,
    gatewayId: String,
    ssid: String,
    onComplete: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success check",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Gateway Provisioned!",
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Your hardware kit is now registered and transmitting secure telemetry.",
                style = Typography.bodyMedium,
                color = TextMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 4.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Provisioning Configuration Details",
                        style = Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

                    MetaLabelValue("Farmer Owner", farmerName)
                    MetaLabelValue("Registered Farm", farmName)
                    MetaLabelValue("Active Gateway", gatewayId)
                    MetaLabelValue("Connection Pipeline", "HTTPS/Secure MQTT")
                    MetaLabelValue("Target SSID", ssid)
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppBackground, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Gateway: $gatewayId",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextDark,
                                fontWeight = FontWeight.Bold
                            )
                            SensorTreeLine("├──", "TMP-001", "Temperature")
                            SensorTreeLine("├──", "HUM-001", "Humidity")
                            SensorTreeLine("├──", "NH3-001", "Ammonia")
                            SensorTreeLine("└──", "MIC-001", "Sound")
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Complete Onboarding", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
