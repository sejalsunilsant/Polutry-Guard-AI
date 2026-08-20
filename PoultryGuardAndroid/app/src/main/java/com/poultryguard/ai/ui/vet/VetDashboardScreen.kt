package com.poultryguard.ai.ui.vet

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.poultryguard.ai.data.cache.AppDatabase
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.data.model.Alert
import com.poultryguard.ai.data.model.VeterinaryCase
import com.poultryguard.ai.data.model.FarmerProfile
import com.poultryguard.ai.data.model.MortalityRecord
import com.poultryguard.ai.data.model.Consultation
import com.poultryguard.ai.data.repository.VeterinaryCaseRepository
import com.poultryguard.ai.data.api.DiseasePredictionRepository
import com.poultryguard.ai.data.api.SoundPredictionResponse
import com.poultryguard.ai.ui.theme.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val StatusGreen = Color(0xFF2E7D32)
private val StatusGreenBg = Color(0xFFE8F5E9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetDashboardScreen(
    onLogout: () -> Unit,
    vetRepository: com.poultryguard.ai.data.repository.VetRepository,
    userProfile: UserProfile,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val diseaseRepository = remember(context) { DiseasePredictionRepository(context) }
    
    // Veterinary cases repositories & states
    val caseRepository = remember(context) { VeterinaryCaseRepository(context.applicationContext) }
    val database = remember(context) { AppDatabase.getDatabase(context.applicationContext) }
    val farmerProfileDao = remember(database) { database.farmerProfileDao() }
    
    val veterinariansState = vetRepository.getVeterinariansFlow().collectAsState(initial = emptyList())
    val currentVet = veterinariansState.value.find { it.email.equals(userProfile.email, ignoreCase = true) } 
        ?: veterinariansState.value.find { it.id == "vet_1" }
    
    val activeAvailability = currentVet?.availability ?: "Available"
    
    val allCases by caseRepository.getAllCasesFlow().collectAsState(initial = emptyList())
    val allAlerts by caseRepository.getAllAlertsFlow().collectAsState(initial = emptyList())
    val allFarmers by farmerProfileDao.getAllFarmersFlow().collectAsState(initial = emptyList())
    val allMortality by caseRepository.getAllMortalityRecordsFlow().collectAsState(initial = emptyList())
    val allConsultations by caseRepository.getConsultationsForVetFlow(currentVet?.id ?: "vet_1").collectAsState(initial = emptyList())

    // Tabs navigation state
    var currentTab by remember { mutableStateOf("dashboard") }

    // Seed mock data if empty
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            if (allFarmers.isEmpty()) {
                // Seed a mock farmer
                val mockFarmer = FarmerProfile(
                    id = "farmer_mock_1",
                    name = "Rajesh Kumar",
                    email = "rajesh.kumar@farm.com",
                    phone = "+919876543210",
                    accountStatus = "Active",
                    lastActive = "5m ago",
                    isOnline = true,
                    farmName = "Greenfields Poultry Farm",
                    farmLocation = "Pune, Maharashtra",
                    totalSheds = 3,
                    floorSpaceSqFt = 18000,
                    deviceId = "ESP32-S3-01",
                    deviceSerial = "SN-9823-PG",
                    firmwareVersion = "v2.1.4",
                    activeBatchId = "batch_2026_08",
                    activeBatchStartDate = "2026-08-01",
                    chickAgeDays = 18,
                    feedConsumedKg = 450.0f,
                    mortalitiesCount = 12,
                    tempSensorStatus = "NORMAL",
                    humidSensorStatus = "NORMAL",
                    ammoniaSensorStatus = "WARNING",
                    soundSensorStatus = "NORMAL",
                    openDiseaseAlertsCount = 1,
                    latestAlertText = "High Ammonia levels detected in Shed 2.",
                    assignedVetName = currentVet?.name ?: "Dr. Sarah Jenkins",
                    lastConsultationDate = "2026-08-10",
                    consultationNotes = "Flock shows slight eye irritation. Ammonia levels high. Advise immediate ventilation cycle increase."
                )
                farmerProfileDao.insert(mockFarmer)
            }
            if (allCases.isEmpty()) {
                // Seed mock cases
                val mockAlert = caseRepository.createAlert(
                    batchId = "batch_2026_08",
                    deviceId = "ESP32-S3-01",
                    predictionId = 101,
                    title = "Shed 2 Respiratory Stress Warning",
                    description = "Acoustic sensor detected frequent coughing and sneezing in chicken vocalisations.",
                    severity = "CRITICAL"
                )
                caseRepository.requestVeterinaryReview(mockAlert.id)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        bottomBar = {
            NavigationBar(
                containerColor = CardSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == "dashboard",
                    onClick = { currentTab = "dashboard" },
                    icon = { Icon(Icons.Default.GridView, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = currentTab == "cases",
                    onClick = { currentTab = "cases" },
                    icon = { Icon(Icons.Default.Healing, contentDescription = "Cases") },
                    label = { Text("Cases") }
                )
                NavigationBarItem(
                    selected = currentTab == "directory",
                    onClick = { currentTab = "directory" },
                    icon = { Icon(Icons.Default.ContactPage, contentDescription = "Farmers") },
                    label = { Text("Farmers") }
                )
                NavigationBarItem(
                    selected = currentTab == "consults",
                    onClick = { currentTab = "consults" },
                    icon = { Icon(Icons.Default.Event, contentDescription = "Consults") },
                    label = { Text("Consults") }
                )
                NavigationBarItem(
                    selected = currentTab == "profile",
                    onClick = { currentTab = "profile" },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                "dashboard" -> DashboardView(
                    onLogout = onLogout,
                    currentVet = currentVet,
                    activeAvailability = activeAvailability,
                    allCases = allCases,
                    allAlerts = allAlerts,
                    allFarmers = allFarmers,
                    allConsultations = allConsultations,
                    vetRepository = vetRepository,
                    coroutineScope = coroutineScope
                )
                "cases" -> CasesView(
                    currentVet = currentVet,
                    allCases = allCases,
                    allAlerts = allAlerts,
                    allFarmers = allFarmers,
                    caseRepository = caseRepository,
                    diseaseRepository = diseaseRepository,
                    coroutineScope = coroutineScope,
                    context = context
                )
                "directory" -> DirectoryView(
                    allFarmers = allFarmers,
                    allCases = allCases,
                    allMortality = allMortality,
                    context = context
                )
                "consults" -> ConsultationsView(
                    currentVet = currentVet,
                    allFarmers = allFarmers,
                    allConsultations = allConsultations,
                    caseRepository = caseRepository,
                    coroutineScope = coroutineScope,
                    context = context
                )
                "profile" -> ProfileView(
                    currentVet = currentVet,
                    allAlerts = allAlerts,
                    allCases = allCases,
                    allConsultations = allConsultations,
                    vetRepository = vetRepository,
                    coroutineScope = coroutineScope,
                    context = context
                )
            }
        }
    }
}

// ----------------- 1. DASHBOARD VIEW -----------------
@Composable
fun DashboardView(
    onLogout: () -> Unit,
    currentVet: com.poultryguard.ai.data.model.Veterinarian?,
    activeAvailability: String,
    allCases: List<VeterinaryCase>,
    allAlerts: List<Alert>,
    allFarmers: List<FarmerProfile>,
    allConsultations: List<Consultation>,
    vetRepository: com.poultryguard.ai.data.repository.VetRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val pendingCases = allCases.filter { it.status == "PENDING" }
    val activeCases = allCases.filter { it.veterinarianId == currentVet?.id && it.status != "RESOLVED" }
    val upcomingConsults = allConsultations.filter { it.status == "SCHEDULED" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Poultry Guard AI Clinician",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary
                    )
                    Text(
                        text = (currentVet?.name ?: "Dr. Sarah Jenkins") + " 🩺",
                        style = Typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AlertRed.copy(alpha = 0.08f))
                ) {
                    Icon(Icons.Default.Logout, contentDescription = "Log Out", tint = AlertRed)
                }
            }
        }

        // Availability status switcher
        item {
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
                        Column {
                            Text(
                                text = "Your Availability Status",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Update status for emergency farmers alerts",
                                style = Typography.labelMedium,
                                color = TextMedium
                            )
                        }
                        
                        val statusColor = when (activeAvailability) {
                            "Available" -> Color(0xFF4CAF50)
                            "Busy" -> AlertOrange
                            else -> AlertRed
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = activeAvailability, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Available" to Color(0xFF4CAF50), "Busy" to AlertOrange, "Unavailable" to AlertRed).forEach { (status, color) ->
                            val isSelected = activeAvailability == status
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) color else AppBackground)
                                    .clickable {
                                        coroutineScope.launch {
                                            currentVet?.let {
                                                vetRepository.updateAvailability(it.id, status)
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else color
                                )
                            }
                        }
                    }
                }
            }
        }

        // Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(modifier = Modifier.weight(1f), title = "Farms", value = allFarmers.size.toString(), icon = Icons.Default.Home, color = GreenPrimary)
                StatCard(modifier = Modifier.weight(1f), title = "Active Cases", value = activeCases.size.toString(), icon = Icons.Default.Sick, color = AlertRed)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(modifier = Modifier.weight(1f), title = "Pending Reviews", value = pendingCases.size.toString(), icon = Icons.Default.MedicalServices, color = AlertOrange)
                StatCard(modifier = Modifier.weight(1f), title = "Consultations", value = upcomingConsults.size.toString(), icon = Icons.Default.Event, color = BlueSecondary)
            }
        }

        // Critical Alerts summary
        item {
            Text("Critical System & Regional Alerts", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        val highRiskAlerts = allAlerts.filter { it.severity == "CRITICAL" || it.severity == "HIGH" }
        if (highRiskAlerts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Text(
                        text = "No critical telemetry breaches or sickness outbreaks flagged currently.",
                        style = Typography.bodyMedium,
                        color = TextMedium,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(highRiskAlerts) { alert ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AlertRed.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Critical", tint = AlertRed)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = alert.title, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextDark)
                            Text(text = alert.description, style = Typography.labelMedium, color = TextMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = title, style = Typography.labelMedium, color = TextMedium)
                Text(text = value, style = Typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextDark)
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ----------------- 2. DISEASE CASE MANAGEMENT VIEW -----------------
@Composable
fun CasesView(
    currentVet: com.poultryguard.ai.data.model.Veterinarian?,
    allCases: List<VeterinaryCase>,
    allAlerts: List<Alert>,
    allFarmers: List<FarmerProfile>,
    caseRepository: VeterinaryCaseRepository,
    diseaseRepository: DiseasePredictionRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
) {
    var filterTab by remember { mutableStateOf("pending") }
    val pending = allCases.filter { it.status == "PENDING" }
    val assigned = allCases.filter { it.veterinarianId == currentVet?.id && it.status != "RESOLVED" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text("Disease Case Management", style = Typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // Segmented tab switches
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardSurface)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (filterTab == "pending") GreenPrimary else Color.Transparent)
                        .clickable { filterTab = "pending" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Pending Reviews (${pending.size})", fontWeight = FontWeight.Bold, color = if (filterTab == "pending") Color.White else TextMedium, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (filterTab == "assigned") GreenPrimary else Color.Transparent)
                        .clickable { filterTab = "assigned" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("My Active Cases (${assigned.size})", fontWeight = FontWeight.Bold, color = if (filterTab == "assigned") Color.White else TextMedium, fontSize = 12.sp)
                }
            }
        }

        if (filterTab == "pending") {
            if (pending.isEmpty()) {
                item {
                    Text("No pending diagnostic requests available.", modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = TextMedium)
                }
            } else {
                items(pending) { case ->
                    val alert = allAlerts.find { it.id == case.alertId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(alert?.title ?: "Unspecified Disease Alert", style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AlertOrange.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("Unassigned", color = AlertOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = alert?.description ?: "No description provided.", style = Typography.bodyMedium, color = TextMedium)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        currentVet?.let {
                                            caseRepository.assignVeterinarian(case.id, it.id)
                                            Toast.makeText(context, "Case claimed! Access granted.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                            ) {
                                Text("Claim & Review Case", color = Color.White)
                            }
                        }
                    }
                }
            }
        } else {
            if (assigned.isEmpty()) {
                item {
                    Text("You have no active claimed cases currently.", modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = TextMedium)
                }
            } else {
                items(assigned) { case ->
                    val alert = allAlerts.find { it.id == case.alertId }
                    val farmer = allFarmers.find { it.activeBatchId == case.batchId }

                    var diagnosisText by remember { mutableStateOf(case.diagnosis ?: "") }
                    var recText by remember { mutableStateOf(case.recommendation ?: "") }
                    var treatmentText by remember { mutableStateOf(case.treatment ?: "") }
                    var followUpText by remember { mutableStateOf(case.followUpInstructions ?: "") }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(alert?.title ?: "Active Case Review", style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text("Status: ${case.status}", style = Typography.labelSmall, color = GreenPrimary, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(GreenPrimary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MedicalServices, contentDescription = null, tint = GreenPrimary)
                                }
                            }

                            Divider(color = DividerColor)

                            // Telemetry Snapshot
                            Text("Environmental Telemetry Snapshot", style = Typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TelemetrySnapshotCard(modifier = Modifier.weight(1f), label = "Temp", value = "28.5°C", status = farmer?.tempSensorStatus ?: "NORMAL")
                                TelemetrySnapshotCard(modifier = Modifier.weight(1f), label = "Humid", value = "62%", status = farmer?.humidSensorStatus ?: "NORMAL")
                                TelemetrySnapshotCard(modifier = Modifier.weight(1f), label = "Ammonia", value = "22 ppm", status = farmer?.ammoniaSensorStatus ?: "WARNING")
                            }

                            // Farmer Info
                            Text("Farmer: ${farmer?.name ?: "Rajesh Kumar"}", style = Typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Farm Location: ${farmer?.farmLocation ?: "Pune"}", style = Typography.bodySmall, color = TextMedium)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:${farmer?.phone ?: "+919876543210"}") }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Call Farmer", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("smsto:${farmer?.phone ?: "+919876543210"}")
                                            putExtra("sms_body", "Hi ${farmer?.name ?: "Farmer"}, reviewing your case...")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BlueSecondary)
                                ) {
                                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SMS Message", fontSize = 11.sp)
                                }
                            }

                            Divider(color = DividerColor)

                            // Form fields
                            Text("Clinical Investigation Form", style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = GreenPrimary)
                            
                            OutlinedTextField(
                                value = diagnosisText,
                                onValueChange = { diagnosisText = it },
                                label = { Text("Clinical Diagnosis") },
                                placeholder = { Text("e.g. Broiler Infectious Bronchitis (IBV)...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = treatmentText,
                                onValueChange = { treatmentText = it },
                                label = { Text("Treatment / Prescription") },
                                placeholder = { Text("e.g. Antibiotic schedule, multivitamin dose...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = recText,
                                onValueChange = { recText = it },
                                label = { Text("General Recommendations") },
                                placeholder = { Text("e.g. Increase extraction fan runtime, add pine wood dust...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = followUpText,
                                onValueChange = { followUpText = it },
                                label = { Text("Follow-up Instructions") },
                                placeholder = { Text("e.g. Check respiratory rate in 48 hours...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (diagnosisText.isBlank() || treatmentText.isBlank()) {
                                            Toast.makeText(context, "Clinical Diagnosis and Treatment fields are mandatory.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        coroutineScope.launch {
                                            caseRepository.submitDiagnosis(
                                                caseId = case.id,
                                                diagnosis = diagnosisText,
                                                recommendation = recText,
                                                treatment = treatmentText,
                                                followUpInstructions = followUpText
                                            )
                                            Toast.makeText(context, "Diagnosis recorded successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                                ) {
                                    Text("Save Diagnosis", color = Color.White, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            caseRepository.resolveCase(case.id)
                                            Toast.makeText(context, "Case resolved and archived successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text("Resolve & Close", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Acoustic Diagnostics card at bottom
        item {
            Text("Real-Time Acoustic Diagnostics Assistant", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            AcousticDiagnosticsCard(diseaseRepository = diseaseRepository, coroutineScope = coroutineScope, context = context)
        }
    }
}

@Composable
fun TelemetrySnapshotCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    status: String
) {
    val tint = when (status) {
        "NORMAL", "HEALTHY" -> Color(0xFF4CAF50)
        "WARNING" -> AlertOrange
        else -> AlertRed
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.08f))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontSize = 10.sp, color = TextMedium)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

// ----------------- 3. FARMER & FARM DIRECTORY VIEW -----------------
@Composable
fun DirectoryView(
    allFarmers: List<FarmerProfile>,
    allCases: List<VeterinaryCase>,
    allMortality: List<MortalityRecord>,
    context: android.content.Context
) {
    var expandedFarmerId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text("Farmer & Farm Directory", style = Typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        if (allFarmers.isEmpty()) {
            item {
                Text("No registered farmers found.", modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = TextMedium)
            }
        } else {
            items(allFarmers) { farmer ->
                val isExpanded = expandedFarmerId == farmer.id
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .clickable { expandedFarmerId = if (isExpanded) null else farmer.id }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = farmer.name, style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(text = farmer.farmName, style = Typography.labelMedium, color = TextMedium)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (farmer.isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (farmer.isOnline) "ONLINE" else "OFFLINE",
                                    color = if (farmer.isOnline) StatusGreen else AlertRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isExpanded) {
                            Divider(color = DividerColor)

                            // Farm details
                            Text("Farm Details", style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = GreenPrimary)
                            Text("• Location: ${farmer.farmLocation}")
                            Text("• Total Sheds: ${farmer.totalSheds}")
                            Text("• Floor Space: ${farmer.floorSpaceSqFt} Sq. Ft.")

                            Spacer(modifier = Modifier.height(4.dp))

                            // Flock details
                            Text("Active Flock Information", style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = GreenPrimary)
                            Text("• Batch ID: ${farmer.activeBatchId.ifBlank { "N/A" }}")
                            Text("• Batch Start: ${farmer.activeBatchStartDate.ifBlank { "N/A" }}")
                            Text("• Chick Age: ${farmer.chickAgeDays} days")
                            Text("• Feed Consumed: ${farmer.feedConsumedKg} Kg")
                            Text("• Accrued Mortalities: ${farmer.mortalitiesCount} birds")

                            Spacer(modifier = Modifier.height(4.dp))

                            // Environment Telemetry Indicators
                            Text("Environmental Telemetry Status", style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = GreenPrimary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TelemetrySnapshotCard(modifier = Modifier.weight(1f), label = "Temp", value = "28°C", status = farmer.tempSensorStatus)
                                TelemetrySnapshotCard(modifier = Modifier.weight(1f), label = "Humidity", value = "60%", status = farmer.humidSensorStatus)
                                TelemetrySnapshotCard(modifier = Modifier.weight(1f), label = "Ammonia", value = "18 ppm", status = farmer.ammoniaSensorStatus)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Mortality history log
                            val records = allMortality.filter { it.batchId == farmer.activeBatchId }
                            Text("Mortality Logs (${records.size})", style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = GreenPrimary)
                            if (records.isEmpty()) {
                                Text("No logged mortalities for active batch.", fontSize = 12.sp, color = TextMedium)
                            } else {
                                records.take(3).forEach { record ->
                                    Text("• Death count: ${record.deathCount} | Cause: ${record.suspectedCause} (${record.reason})", fontSize = 12.sp, color = TextMedium)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Clinical history
                            val previousCases = allCases.filter { it.batchId == farmer.activeBatchId }
                            Text("Previous Disease Cases (${previousCases.size})", style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = GreenPrimary)
                            if (previousCases.isEmpty()) {
                                Text("No previous cases recorded.", fontSize = 12.sp, color = TextMedium)
                            } else {
                                previousCases.forEach { case ->
                                    Text("• [${case.status}] Diagnosis: ${case.diagnosis ?: "Unspecified"}", fontSize = 12.sp, color = TextMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------- 4. CONSULTATIONS VIEW -----------------
@Composable
fun ConsultationsView(
    currentVet: com.poultryguard.ai.data.model.Veterinarian?,
    allFarmers: List<FarmerProfile>,
    allConsultations: List<Consultation>,
    caseRepository: VeterinaryCaseRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
) {
    var showDialog by remember { mutableStateOf(false) }
    val upcoming = allConsultations.filter { it.status == "SCHEDULED" }
    val past = allConsultations.filter { it.status != "SCHEDULED" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Clinical Consultations", style = Typography.headlineSmall, fontWeight = FontWeight.Bold)
                Button(
                    onClick = { showDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Schedule", fontSize = 12.sp)
                }
            }
        }

        item {
            Text("Upcoming Consultations (${upcoming.size})", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (upcoming.isEmpty()) {
            item {
                Text("No upcoming consultations scheduled.", modifier = Modifier.fillMaxWidth().padding(12.dp), color = TextMedium)
            }
        } else {
            items(upcoming) { consult ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = consult.farmerName, style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(consult.dateTime)),
                                style = Typography.labelMedium,
                                color = GreenPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(text = "Clinical Notes: ${consult.notes}", style = Typography.bodyMedium, color = TextMedium)
                        if (consult.followUpDate > 0L) {
                            Text(
                                text = "Follow-up Date: " + SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(consult.followUpDate)),
                                fontSize = 11.sp,
                                color = TextMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        caseRepository.updateConsultationStatus(consult.id, "COMPLETED")
                                        Toast.makeText(context, "Consultation marked as Completed!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                            ) {
                                Text("Complete", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        caseRepository.updateConsultationStatus(consult.id, "CANCELLED")
                                        Toast.makeText(context, "Consultation marked as Cancelled!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                            ) {
                                Text("Cancel", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Completed & Past Consultations (${past.size})", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (past.isEmpty()) {
            item {
                Text("No clinical consultation history logs found.", modifier = Modifier.fillMaxWidth().padding(12.dp), color = TextMedium)
            }
        } else {
            items(past) { consult ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.8f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = consult.farmerName, style = Typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (consult.status == "COMPLETED") StatusGreen.copy(alpha = 0.1f) else AlertRed.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = consult.status,
                                    color = if (consult.status == "COMPLETED") StatusGreen else AlertRed,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(text = "Date: " + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(consult.dateTime)), fontSize = 11.sp, color = TextMedium)
                        Text(text = "Clinical Findings: ${consult.notes}", fontSize = 12.sp, color = TextMedium)
                    }
                }
            }
        }
    }

    if (showDialog) {
        var selectedFarmer by remember { mutableStateOf<FarmerProfile?>(null) }
        var notes by remember { mutableStateOf("") }
        var hoursOffset by remember { mutableStateOf("24") } // default 24 hours from now
        var dropdownExpanded by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Schedule Consultation", style = Typography.titleLarge, fontWeight = FontWeight.Bold)

                    // Farmer Dropdown selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedFarmer?.name ?: "Select Farmer Profile",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.clickable { dropdownExpanded = true }) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                            allFarmers.forEach { farmer ->
                                DropdownMenuItem(
                                    text = { Text(farmer.name) },
                                    onClick = {
                                        selectedFarmer = farmer
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Clinical Session Notes") },
                        placeholder = { Text("Purpose of consultation...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = hoursOffset,
                        onValueChange = { hoursOffset = it },
                        label = { Text("Schedule Time (Hours from now)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDialog = false }) {
                            Text("Dismiss")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val farmer = selectedFarmer
                                val offset = hoursOffset.toIntOrNull()
                                if (farmer == null || notes.isBlank() || offset == null || offset <= 0) {
                                    Toast.makeText(context, "Please fill in all details correctly.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                coroutineScope.launch {
                                    val time = System.currentTimeMillis() + (offset * 3600 * 1000L)
                                    caseRepository.scheduleConsultation(
                                        vetId = currentVet?.id ?: "vet_1",
                                        farmerId = farmer.id,
                                        farmerName = farmer.name,
                                        dateTime = time,
                                        notes = notes,
                                        followUpDate = time + (7 * 24 * 3600 * 1000L) // 7 days later
                                    )
                                    Toast.makeText(context, "Consultation scheduled successfully!", Toast.LENGTH_SHORT).show()
                                    showDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

// ----------------- 5. CLINICAL REPORTS, ALERTS & PROFILE VIEW -----------------
@Composable
fun ProfileView(
    currentVet: com.poultryguard.ai.data.model.Veterinarian?,
    allAlerts: List<Alert>,
    allCases: List<VeterinaryCase>,
    allConsultations: List<Consultation>,
    vetRepository: com.poultryguard.ai.data.repository.VetRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
) {
    var subTab by remember { mutableStateOf("profile") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Tab switcher inside Profile section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardSurface)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (subTab == "profile") GreenPrimary else Color.Transparent)
                        .clickable { subTab = "profile" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Profile Details", fontWeight = FontWeight.Bold, color = if (subTab == "profile") Color.White else TextMedium, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (subTab == "alerts") GreenPrimary else Color.Transparent)
                        .clickable { subTab = "alerts" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Outbreaks Alerts", fontWeight = FontWeight.Bold, color = if (subTab == "alerts") Color.White else TextMedium, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (subTab == "reports") GreenPrimary else Color.Transparent)
                        .clickable { subTab = "reports" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Clinical Reports", fontWeight = FontWeight.Bold, color = if (subTab == "reports") Color.White else TextMedium, fontSize = 12.sp)
                }
            }
        }

        if (subTab == "profile") {
            item {
                Text("Veterinarian Profile Configuration", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (currentVet == null) {
                item {
                    Text("Loading profile logs...")
                }
            } else {
                // Interactive profile updates form fields
                item {
                    var specialty by remember { mutableStateOf(currentVet.specialty) }
                    var phone by remember { mutableStateOf(currentVet.phone) }
                    var location by remember { mutableStateOf(currentVet.location) }
                    var qualification by remember { mutableStateOf(currentVet.qualification) }
                    var license by remember { mutableStateOf(currentVet.licenseNumber) }
                    var experience by remember { mutableStateOf(currentVet.experience.toString()) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Avatar display
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(GreenPrimary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Face, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(32.dp))
                                }
                                Column {
                                    Text(text = currentVet.name, style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text(text = currentVet.email, style = Typography.bodySmall, color = TextMedium)
                                }
                            }

                            Divider(color = DividerColor)

                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Contact Phone") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = specialty,
                                onValueChange = { specialty = it },
                                label = { Text("Clinical Specialization") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = qualification,
                                onValueChange = { qualification = it },
                                label = { Text("Qualification Degree") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = license,
                                onValueChange = { license = it },
                                label = { Text("Veterinary License Number") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = experience,
                                onValueChange = { experience = it },
                                label = { Text("Years of Experience") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it },
                                label = { Text("Operating Region / Location") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        vetRepository.updateProfile(
                                            id = currentVet.id,
                                            phone = phone,
                                            specialty = specialty,
                                            location = location,
                                            qualification = qualification,
                                            licenseNumber = license,
                                            experience = experience.toIntOrNull() ?: currentVet.experience
                                        )
                                        Toast.makeText(context, "Profile details successfully updated!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Update Profile Settings")
                            }
                        }
                    }
                }
            }
        } else if (subTab == "alerts") {
            item {
                Text("Active High-Risk Regional Outbreaks", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Flag high-risk zones, disease outbreaks, temperature threshold breaches
            val abnormalAlerts = allAlerts.filter { it.severity == "CRITICAL" }
            if (abnormalAlerts.isEmpty()) {
                item {
                    Text("No high-risk disease outbreaks flagged in the district.", color = TextMedium)
                }
            } else {
                items(abnormalAlerts) { alert ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "🚨 OUTBREAK WARNING: ${alert.title}", fontWeight = FontWeight.Bold, color = AlertRed)
                            Text(text = alert.description, fontSize = 12.sp, color = TextDark)
                        }
                    }
                }
            }
        } else {
            // Clinical Reports
            item {
                Text("Flock Health & Clinical Disease Reports", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Clinical Statistics Aggregations", style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                        
                        val resolved = allCases.filter { it.status == "RESOLVED" }
                        val diagnosed = allCases.filter { it.status == "DIAGNOSED" }

                        Text("• Total Diagnosed Cases: ${diagnosed.size + resolved.size}")
                        Text("• Archived/Resolved Cases: ${resolved.size}")
                        Text("• Consultations Completed: ${allConsultations.filter { it.status == "COMPLETED" }.size}")

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                try {
                                    val reportString = StringBuilder()
                                    reportString.append("--- Poultry Guard AI Clinical Health Report ---\n")
                                    reportString.append("Generated Date: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}\n")
                                    reportString.append("Clinician: ${currentVet?.name ?: "Dr. Sarah Jenkins"}\n\n")
                                    reportString.append("=== Recorded Case Logs ===\n")
                                    allCases.forEach { case ->
                                        reportString.append("- Case ID: ${case.id} [${case.status}]\n")
                                        reportString.append("  Diagnosis: ${case.diagnosis ?: "None"}\n")
                                        reportString.append("  Prescription: ${case.treatment ?: "None"}\n")
                                        reportString.append("  Follow-up instructions: ${case.followUpInstructions ?: "None"}\n\n")
                                    }
                                    
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, reportString.toString())
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Export Health Report")
                                    context.startActivity(shareIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export error: " + e.localizedMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Health Reports Logs")
                        }
                    }
                }
            }
        }
    }
}

// ----------------- ACOUSTIC DIAGNOSTICS CARD -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcousticDiagnosticsCard(
    diseaseRepository: DiseasePredictionRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
) {
    var isRecording by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SoundPredictionResponse?>(null) }
    var recordingTimer by remember { mutableStateOf(0) }
    var isUploading by remember { mutableStateOf(false) }

    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    val audioFile = remember { File(context.cacheDir, "broiler_recording.mp4") }

    fun startRecording() {
        try {
            if (audioFile.exists()) {
                audioFile.delete()
            }
            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }
            recorder.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
            result = null
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to start recording: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            // Perform real prediction upload!
            isUploading = true
            coroutineScope.launch {
                val requestFile = audioFile.asRequestBody("audio/*".toMediaTypeOrNull())
                val filePart = okhttp3.MultipartBody.Part.createFormData(
                    "file",
                    audioFile.name,
                    requestFile
                )
                val res = diseaseRepository.predictSoundFile(filePart)
                res.fold(
                    onSuccess = { prediction ->
                        result = prediction
                    },
                    onFailure = { err ->
                        Toast.makeText(context, "Prediction failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                )
                isUploading = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            isUploading = false
            Toast.makeText(context, "Error stopping recorder: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(context, "Audio recording permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorder?.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTimer = 0
            while (isRecording && recordingTimer < 10) {
                kotlinx.coroutines.delay(1000)
                recordingTimer++
            }
            if (isRecording) {
                stopRecording()
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Real Acoustic Diagnostics",
                style = Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                text = "Record up to 10 seconds of raw broiler audio to execute real sound ML prediction",
                style = Typography.labelMedium,
                color = TextMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action button
            Button(
                onClick = {
                    if (isRecording) {
                        stopRecording()
                    } else {
                        val permission = android.Manifest.permission.RECORD_AUDIO
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, permission
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        
                        if (hasPermission) {
                            startRecording()
                        } else {
                            permissionLauncher.launch(permission)
                        }
                    }
                },
                enabled = !isUploading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) AlertRed else GreenPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else if (isRecording) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Recording ($recordingTimer/10s)", color = Color.White)
                } else {
                    Icon(Icons.Default.Mic, contentDescription = "Mic", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Record Broiler Sound", color = Color.White)
                }
            }

            result?.let { res ->
                Spacer(modifier = Modifier.height(20.dp))
                Divider(color = DividerColor)
                Spacer(modifier = Modifier.height(20.dp))

                // Diagnostic Result Box
                val resultColor = when (res.prediction) {
                    "Healthy" -> StatusGreen
                    "Sick" -> AlertRed
                    else -> Color.Gray
                }
                val resultBg = when (res.prediction) {
                    "Healthy" -> StatusGreenBg
                    "Sick" -> Color(0xFFFFEBEE)
                    else -> Color.LightGray.copy(alpha = 0.2f)
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Acoustic AI Assessment",
                            style = Typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(resultBg)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = res.prediction.uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = resultColor,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (res.status == "fallback") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AlertOrange.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Offline Mode",
                                    tint = AlertOrange,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Offline Fallback Active",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlertOrange
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Confidence meter
                    Text(
                        text = "Model Confidence: ${(res.confidence * 100).toInt()}%",
                        style = Typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = res.confidence,
                        color = resultColor,
                        trackColor = Color.LightGray.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Probabilities Breakdown
                    Text(
                        text = "Classification Breakdown",
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    res.probabilities.forEach { (label, prob) ->
                        val progressColor = when (label) {
                            "Healthy" -> StatusGreen
                            "Sick" -> AlertRed
                            else -> Color.Gray
                        }
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = label, fontSize = 12.sp, color = TextDark)
                                Text(text = "${(prob * 100).toInt()}%", fontSize = 12.sp, color = TextMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = prob,
                                color = progressColor,
                                trackColor = Color.LightGray.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Veterinary recommendation
                    val recommendationText = when (res.prediction) {
                        "Healthy" -> "✅ Flock bio-acoustics are stable. Acoustic signals match healthy templates. No respiratory warnings active."
                        "Sick" -> "🚨 WARNING: Elevated respiratory disease patterns detected in the sound clip. Recommended actions: 1. Verify shed ventilation rates. 2. Log a clinical veterinary visit. 3. Check flock for physical symptoms of infectious bronchitis."
                        "None" -> "ℹ️ No chicken respiratory signals identified in this sample (potential background noise). Please record closer to broiler height."
                        else -> "⚠️ UNCERTAIN: CNN model returned low confidence classification. Please record a clearer sound file free from excessive extractor fan hums."
                    }

                    Text(
                        text = recommendationText,
                        style = Typography.bodyMedium,
                        color = TextDark,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(resultColor.copy(alpha = 0.05f))
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}
