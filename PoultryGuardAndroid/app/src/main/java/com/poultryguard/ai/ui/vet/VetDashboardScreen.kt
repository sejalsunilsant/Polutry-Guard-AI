package com.poultryguard.ai.ui.vet

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poultryguard.ai.data.cache.AppDatabase
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.data.model.Alert
import com.poultryguard.ai.data.model.VeterinaryCase
import com.poultryguard.ai.data.model.FarmerProfile
import com.poultryguard.ai.data.model.MortalityRecord
import com.poultryguard.ai.data.repository.VeterinaryCaseRepository
import com.poultryguard.ai.data.api.DiseasePredictionRepository
import com.poultryguard.ai.data.api.SoundPredictionResponse
import com.poultryguard.ai.ui.theme.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

private val StatusGreen = Color(0xFF2E7D32)
private val StatusGreenBg = Color(0xFFE8F5E9)

data class HealthAnomaly(
    val name: String,
    val description: String,
    val severity: String, // CRITICAL, ATTENTION, HEALTHY
    val time: String
)

@Composable
fun VaccineRow(
    day: String,
    name: String,
    status: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = day)

        Spacer(modifier = Modifier.weight(1f))

        Column {
            Text(text = name)
            Text(
                text = status,
                color = statusColor
            )
        }
    }
}

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

    // Filter cases based on roles & assignments (access protection)
    val pendingCases = allCases.filter { it.status == "PENDING" }
    val assignedCases = allCases.filter { it.veterinarianId == currentVet?.id && it.status != "RESOLVED" }
    val resolvedCases = allCases.filter { it.veterinarianId == currentVet?.id && it.status == "RESOLVED" }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Poultry Guard Health",
                                style = Typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = BlueSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(BlueSecondary)
                            )
                        }
                        Text(
                            text = (currentVet?.name ?: "Dr. Sarah Jenkins") + " 🩺",
                            style = Typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Logout trigger
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AlertRed.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Log Out",
                            tint = AlertRed
                        )
                    }
                }
            }

            // Availability Status Switcher Panel
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
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Text(
                                    text = "Alert farmers of your current availability",
                                    style = Typography.labelMedium,
                                    color = TextMedium
                                )
                            }
                            
                            val currentStatusColor = when (activeAvailability) {
                                "Available" -> Color(0xFF4CAF50)
                                "Busy" -> AlertOrange
                                else -> AlertRed
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(currentStatusColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = activeAvailability,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentStatusColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val statuses = listOf(
                                "Available" to Color(0xFF4CAF50),
                                "Busy" to AlertOrange,
                                "Unavailable" to AlertRed
                            )

                            statuses.forEach { (status, color) ->
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

            // 1. Pending Cases Section (Claim Workflow)
            item {
                Text(
                    text = "Awaiting Veterinary Review (${pendingCases.size})",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (pendingCases.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Text(
                            text = "No pending diagnostic review cases available.",
                            style = Typography.bodyMedium,
                            color = TextMedium,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(pendingCases) { case ->
                    val alert = allAlerts.find { it.id == case.alertId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = alert?.title ?: "Disease Diagnostic Request",
                                    style = Typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AlertOrange.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Awaiting Claim",
                                        color = AlertOrange,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = alert?.description ?: "An AI alert was flagged by a controller device. Needs clinician review.",
                                style = Typography.bodyMedium,
                                color = TextMedium
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        currentVet?.let {
                                            caseRepository.assignVeterinarian(case.id, it.id)
                                            Toast.makeText(context, "Case assigned successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = BlueSecondary)
                            ) {
                                Text("Claim & Review Case", color = Color.White)
                            }
                        }
                    }
                }
            }

            // 2. Active Assigned Cases Section
            item {
                Text(
                    text = "Your Active Case Reviews (${assignedCases.size})",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (assignedCases.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Text(
                            text = "No active cases. Claim a pending review case above.",
                            style = Typography.bodyMedium,
                            color = TextMedium,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(assignedCases) { case ->
                    val alert = allAlerts.find { it.id == case.alertId }
                    val farmer = allFarmers.find { it.activeBatchId == case.batchId }

                    var diagnosisText by remember { mutableStateOf(case.diagnosis ?: "") }
                    var recommendationText by remember { mutableStateOf(case.recommendation ?: "") }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = alert?.title ?: "Review: Respiratory Alert",
                                        style = Typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                    Text(
                                        text = "Status: ${case.status}",
                                        style = Typography.labelMedium,
                                        color = GreenPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(GreenPrimary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MedicalServices, contentDescription = "Active", tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = DividerColor)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Farmer Contact Details (Only visible because case is ASSIGNED to this vet)
                            Text(
                                text = "Farmer Information (Assigned Farm)",
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Owner: ${farmer?.name ?: "Unspecified"}",
                                style = Typography.bodyMedium,
                                color = TextMedium
                            )
                            Text(
                                text = "Farm: ${farmer?.farmName ?: "Unspecified"} - ${farmer?.farmLocation ?: "Unspecified"}",
                                style = Typography.bodyMedium,
                                color = TextMedium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val phone = farmer?.phone ?: ""
                                val email = farmer?.email ?: ""

                                Button(
                                    onClick = {
                                        if (phone.isBlank()) {
                                            android.widget.Toast.makeText(context, "No phone number available", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$phone") }
                                            context.startActivity(intent)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Call", color = Color.White, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        if (phone.isBlank()) {
                                            android.widget.Toast.makeText(context, "No phone number available", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("smsto:$phone")
                                                putExtra("sms_body", "Hi ${farmer?.name ?: "Farmer"}, Poultry Guard AI flagged case review: resolving...")
                                            }
                                            context.startActivity(intent)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BlueSecondary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Sms, contentDescription = "SMS", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SMS", color = Color.White, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = DividerColor)
                            Spacer(modifier = Modifier.height(16.dp))

                            // Diagnosis & Recommendation Form
                            Text(
                                text = "Submit Diagnosis & Recommendations",
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = diagnosisText,
                                onValueChange = { diagnosisText = it },
                                label = { Text("Clinical Diagnosis") },
                                placeholder = { Text("Describe observed patterns or pathology...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = recommendationText,
                                onValueChange = { recommendationText = it },
                                label = { Text("Treatment & Recommendations") },
                                placeholder = { Text("Advise on ventilation cycles, litter moisture...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (diagnosisText.isBlank() || recommendationText.isBlank()) {
                                            Toast.makeText(context, "Please fill in both fields.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        coroutineScope.launch {
                                            caseRepository.submitDiagnosis(case.id, diagnosisText, recommendationText)
                                            Toast.makeText(context, "Diagnosis recorded successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Save Diagnosis", color = Color.White)
                                }

                                if (case.status == "DIAGNOSED") {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                caseRepository.resolveCase(case.id)
                                                Toast.makeText(context, "Case marked as RESOLVED!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Mark Resolved", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Acoustic Diagnostics Hub (Existing Feature)
            item {
                Text(
                    text = "Acoustic Disease Diagnostic Hub",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            item {
                AcousticDiagnosticsCard(
                    diseaseRepository = diseaseRepository,
                    coroutineScope = coroutineScope,
                    context = context
                )
            }

            // 4. Resolved Cases History Section
            if (resolvedCases.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Resolved Case History (${resolvedCases.size})",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(resolvedCases) { case ->
                    val alert = allAlerts.find { it.id == case.alertId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.7f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = alert?.title ?: "Case Resolved",
                                    style = Typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark.copy(alpha = 0.8f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(StatusGreen.copy(alpha = 0.1f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("RESOLVED", color = StatusGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Diagnosis: ${case.diagnosis ?: "None"}", fontSize = 12.sp, color = TextMedium)
                            Text("Rec: ${case.recommendation ?: "None"}", fontSize = 12.sp, color = TextMedium)
                        }
                    }
                }
            }
        }
    }
}

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
    val audioFile = remember { java.io.File(context.cacheDir, "broiler_recording.mp4") }

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
                                fontSize = 11.sp,
                                color = resultColor
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
