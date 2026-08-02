package com.poultryguard.ai.ui.vet

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.poultryguard.ai.ui.theme.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.poultryguard.ai.data.api.DiseasePredictionRepository
import com.poultryguard.ai.data.api.SoundPredictionResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

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
@Composable
fun VetDashboardScreen(
    onLogout: () -> Unit,
    vetRepository: com.poultryguard.ai.data.repository.VetRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val diseaseRepository = remember(context) { DiseasePredictionRepository(context) }
    val veterinariansState = vetRepository.getVeterinariansFlow().collectAsState(initial = emptyList())
    val currentVet = veterinariansState.value.find { it.id =="vet_1" }
    val activeAvailability = currentVet?.availability ?: "Available"
    
    val anomalies = listOf(
        HealthAnomaly("Acoustic Respiratory Snick Log", "AI model matching detected 14 coughing events/minute in Shed 4. Potential infectious bronchitis sign.", "ATTENTION", "14 mins ago"),
        HealthAnomaly("Thermal Heat Exhaustion Alert", "High temp combined with panting vocalization indicators in Shed #3.", "CRITICAL", "1 hour ago"),
        HealthAnomaly("Gastrointestinal Feed Ratios", "Normal sound-based feed mechanical trigger consumption rates.", "HEALTHY", "4 hours ago")
    )

    // Direct Intent Triggers for Contacting Farmer Joe
    val farmerPhone = "+15553827492"
    val farmerEmail = "joe.patterson@farmsecure.net"
    val smsBody = "Hi Farmer Joe, Poultry Guard AI is reporting active respiratory anomalies in Shed #4. Please verify fans are at max capacity, I am reviewing the health stats."

    fun triggerCall() {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$farmerPhone")
        }
        context.startActivity(intent)
    }

    fun triggerSms() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$farmerPhone")
            putExtra("sms_body", smsBody)
        }
        context.startActivity(intent)
    }

    fun triggerEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$farmerEmail")
            putExtra(Intent.EXTRA_SUBJECT, "Urgent Biosecurity Review: Shed #4 Anomalies")
            putExtra(Intent.EXTRA_TEXT, smsBody)
        }
        context.startActivity(intent)
    }

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
                            text = "Dr. Sarah Jenkins 🩺",
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
                                                vetRepository.updateAvailability("vet_1", status)
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

            // NEW FEATURE: Stylized Farm GIS Distribution Map View
            item {
                Text(
                    text = "Assigned Barn GIS Layout",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Broiler Sector-4 Map Topology",
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "6 Sheds registered • Shed #3 Critical Heat • Shed #4 Audits pending",
                            style = Typography.labelMedium,
                            color = TextMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Stylized Custom GIS Map Canvas (Premium graphics rendering)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(GreenLight.copy(alpha = 0.4f))
                        ) {
                            // Draw Grid Lines
                            val gridSpacing = 40.dp.toPx()
                            var x = 0f
                            while (x < size.width) {
                                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1f)
                                x += gridSpacing
                            }
                            var y = 0f
                            while (y < size.height) {
                                drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f)
                                y += gridSpacing
                            }

                            // Draw Sheds (Shed 1 to 6)
                            val shedWidth = 70.dp.toPx()
                            val shedHeight = 35.dp.toPx()

                            // Shed #1 (Healthy)
                            drawRect(
                                color = StatusGreen,
                                topLeft = Offset(20.dp.toPx(), 20.dp.toPx()),
                                size = Size(shedWidth, shedHeight)
                            )

                            // Shed #2 (Healthy)
                            drawRect(
                                color = StatusGreen,
                                topLeft = Offset(110.dp.toPx(), 20.dp.toPx()),
                                size = Size(shedWidth, shedHeight)
                            )

                            // Shed #3 (Critical Heat Warning - Red)
                            drawRect(
                                color = AlertRed,
                                topLeft = Offset(200.dp.toPx(), 20.dp.toPx()),
                                size = Size(shedWidth, shedHeight)
                            )

                            // Shed #4 (Audits Attention - Amber)
                            drawRect(
                                color = AlertOrange,
                                topLeft = Offset(20.dp.toPx(), 80.dp.toPx()),
                                size = Size(shedWidth, shedHeight)
                            )

                            // Shed #5 & #6 (Healthy)
                            drawRect(
                                color = StatusGreen,
                                topLeft = Offset(110.dp.toPx(), 80.dp.toPx()),
                                size = Size(shedWidth, shedHeight)
                            )
                            drawRect(
                                color = StatusGreen,
                                topLeft = Offset(200.dp.toPx(), 80.dp.toPx()),
                                size = Size(shedWidth, shedHeight)
                            )

                            // Draw Vet Location Pin Dot (Clinician position)
                            drawCircle(
                                color = BlueSecondary,
                                radius = 6.dp.toPx(),
                                center = Offset(145.dp.toPx(), 70.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = Offset(145.dp.toPx(), 70.dp.toPx())
                            )
                        }

                        // Map Legend
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MapLegendItem("Healthy", StatusGreen)
                            MapLegendItem("Warning", AlertOrange)
                            MapLegendItem("Critical", AlertRed)
                            MapLegendItem("Dr. Jenkins (Vet)", BlueSecondary)
                        }
                    }
                }
            }

            // NEW FEATURE: Direct Contact Farmer Panel
            item {
                Text(
                    text = "Direct Farmer Security Liaison",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(GreenPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("J", fontWeight = FontWeight.Bold, color = GreenPrimary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Farmer Joe Patterson",
                                    style = Typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Text(
                                    text = "Site Owner & Operator • Sheds 1-6",
                                    style = Typography.labelMedium,
                                    color = TextMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Trigger Call / SMS / Email actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { triggerCall() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Call", fontSize = 13.sp, color = Color.White)
                            }

                            Button(
                                onClick = { triggerSms() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BlueSecondary)
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = "SMS", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SMS", fontSize = 13.sp, color = Color.White)
                            }

                            Button(
                                onClick = { triggerEmail() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = "Email", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Email", fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // NEW FEATURE: Acoustic Diagnostics Hub
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

            // AI Health Anomalies list
            item {
                Text(
                    text = "AI Health Audits",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(anomalies.size) { index ->
                val anomaly = anomalies[index]
                val statusColor = when (anomaly.severity) {
                    "CRITICAL" -> AlertRed
                    "ATTENTION" -> AlertOrange
                    else -> StatusGreen
                }
                val statusBg = when (anomaly.severity) {
                    "CRITICAL" -> Color(0xFFFFEBEE)
                    "ATTENTION" -> Color(0xFFFFF3E0)
                    else -> StatusGreenBg
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(statusBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (anomaly.severity) {
                                    "CRITICAL" -> Icons.Default.HearingDisabled
                                    "ATTENTION" -> Icons.Default.MedicalServices
                                    else -> Icons.Default.CheckCircle
                                },
                                contentDescription = "Anomaly Type",
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = anomaly.name,
                                    style = Typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Text(
                                    text = anomaly.time,
                                    style = Typography.labelMedium,
                                    color = TextMedium
                                )
                            }
                            Text(
                                text = anomaly.description,
                                style = Typography.bodyMedium,
                                color = TextDark,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Broiler Vaccine Schedule
            item {
                Text(
                    text = stringResource("immunization"),
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        VaccineRow(
                            day = "Day 1",
                            name = "Marek's Disease (HVT)",
                            status = "COMPLETED",
                            statusColor = StatusGreen
                        )
                        Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                        VaccineRow(
                            day = "Day 14",
                            name = "Infectious Bursal (Gumboro) Vaccine",
                            status = "COMPLETED",
                            statusColor = StatusGreen
                        )
                        Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                        VaccineRow(
                            day = "Day 21",
                            name = "Newcastle Disease / IB Spray",
                            status = "PENDING ORDER",
                            statusColor = AlertOrange
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MapLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = TextMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AcousticDiagnosticsCard(
    diseaseRepository: DiseasePredictionRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    var predictionResult by remember { mutableStateOf<SoundPredictionResponse?>(null) }
    var isPredicting by remember { mutableStateOf(false) }
    var predictionError by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isPredicting = true
                predictionError = null
                predictionResult = null
                selectedFileName = "audio_upload.wav"

                try {
                    val contentResolver = context.contentResolver
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                selectedFileName = it.getString(nameIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore and keep fallback filename
                }

                try {
                    val contentResolver = context.contentResolver
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        predictionError = "Could not open selected audio file."
                        isPredicting = false
                        return@launch
                    }
                    
                    val fileBytes = inputStream.readBytes()
                    inputStream.close()
                    
                    val requestFile = fileBytes.toRequestBody("audio/wav".toMediaTypeOrNull())

                    
                    val multipartBody = okhttp3.MultipartBody.Part.createFormData(
                        "file",
                        selectedFileName ?: "flock_audio.wav",
                        requestFile
                    )
                    
                    val result = diseaseRepository.predictSoundFile(multipartBody)
                    result.onSuccess { response ->
                        predictionResult = response
                    }.onFailure { err ->
                        predictionError = err.localizedMessage ?: "Failed to connect to backend diagnostics."
                    }
                } catch (e: Exception) {
                    predictionError = "Error reading audio file: ${e.localizedMessage}"
                } finally {
                    isPredicting = false
                }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BlueSecondary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = "Acoustic Audio",
                        tint = BlueSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Acoustic Disease Classifier",
                        style = Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = "Powered by 2D CNN Mel-Spectrogram Model",
                        style = Typography.labelMedium,
                        color = TextMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Upload a WAV recording of flock respiratory vocalizations to perform an AI-driven biosecurity audit. The model pre-processes raw acoustics into a 128-bin Mel-Spectrogram and evaluates it through a 2D CNN.",
                style = Typography.bodyMedium,
                color = TextDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedFileName != null && (isPredicting || predictionResult != null || predictionError != null)) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AppBackground)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = "Selected File",
                            tint = TextMedium,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedFileName ?: "audio.wav",
                            style = Typography.bodyMedium,
                            color = TextDark,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Action Button
            Button(
                onClick = { filePickerLauncher.launch("audio/*") },
                enabled = !isPredicting,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueSecondary)
            ) {
                if (isPredicting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Running CNN Inference...", color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Upload",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Audio Sample", color = Color.White)
                }
            }

            // Error display
            if (predictionError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = AlertRed
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = predictionError ?: "An unknown error occurred.",
                            color = AlertRed,
                            style = Typography.bodyMedium
                        )
                    }
                }
            }

            // Prediction Result Card
            predictionResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                
                val resultColor = when (result.prediction) {
                    "Healthy" -> StatusGreen
                    "Sick" -> AlertRed
                    "None" -> Color.Gray
                    else -> AlertOrange // Uncertain / Unknown
                }
                
                val resultBg = when (result.prediction) {
                    "Healthy" -> StatusGreenBg
                    "Sick" -> Color(0xFFFFEBEE)
                    "None" -> Color.LightGray.copy(alpha = 0.2f)
                    else -> Color(0xFFFFF3E0)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppBackground),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, resultColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CNN Diagnosis",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )

                            // Result Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(resultBg)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = result.prediction.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = resultColor
                                )
                            }
                        }

                        if (result.status == "fallback") {
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
                            text = "Model Confidence: ${(result.confidence * 100).toInt()}%",
                            style = Typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = result.confidence,
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
                        
                        result.probabilities.forEach { (label, prob) ->
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
                        val recommendationText = when (result.prediction) {
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
}

