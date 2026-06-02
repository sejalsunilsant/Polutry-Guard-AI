package com.poultryguard.ai.ui.mortality

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poultryguard.ai.data.model.MortalityRecord
import com.poultryguard.ai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MortalityScreen(
    viewModel: MortalityViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val records by viewModel.historicalRecords.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val submissionSuccess by viewModel.submissionSuccess.collectAsState()

    // Form inputs state
    var deathCount by remember { mutableStateOf(1) }
    val symptomsList = listOf("Respiratory Snick", "Lethargy", "Loose Droppings", "Sudden Death")
    val selectedSymptoms = remember { mutableStateListOf<String>() }
    var customSymptoms by remember { mutableStateOf("") }
    
    val causesList = listOf("Heat Stress", "Infectious Bronchitis", "Ammonia Toxicity", "Feed Contamination", "Predator Panic", "Other")
    var selectedCause by remember { mutableStateOf(causesList[0]) }
    var customCause by remember { mutableStateOf("") }

    var expandedRecordId by remember { mutableStateOf<String?>(null) }

    // Dynamic stats computation
    val totalDeaths = records.sumOf { it.deathCount }
    val avgTemp = if (records.isNotEmpty()) records.map { it.temperature }.average().toFloat() else 0f
    val avgAmmonia = if (records.isNotEmpty()) records.map { it.ammoniaLevel }.average().toFloat() else 0f
    val avgHumid = if (records.isNotEmpty()) records.map { it.humidity }.average().toFloat() else 0f
    val avgSound = if (records.isNotEmpty()) records.map { it.soundLevel }.average().toFloat() else 0f

    val topCause = if (records.isNotEmpty()) {
        records.groupBy { it.suspectedCause }
            .maxByOrNull { it.value.sumOf { r -> r.deathCount } }?.key ?: "None"
    } else "None"

    // Clear form on successful submission
    LaunchedEffect(submissionSuccess) {
        if (submissionSuccess) {
            Toast.makeText(context, "Mortality record logged successfully!", Toast.LENGTH_SHORT).show()
            deathCount = 1
            selectedSymptoms.clear()
            customSymptoms = ""
            selectedCause = causesList[0]
            customCause = ""
            viewModel.resetSubmissionStatus()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // Main Screen Header
        item {
            Column {
                Text(
                    text = stringResource("mortality_mgmt"),
                    style = Typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = GreenPrimary
                )
                Text(
                    text = "Observe, record flock losses, and automatically audit environmental context triggers.",
                    style = Typography.bodyMedium,
                    color = TextMedium
                )
            }
        }

        // Section 1: Record Entry Form Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DividerColor)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GreenLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddBox,
                                contentDescription = "Log",
                                tint = GreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource("log_death"),
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Numerical Count Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bird Death Count:",
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AppBackground)
                                    .clickable { if (deathCount > 1) deathCount-- },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            }

                            Text(
                                text = "$deathCount",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center
                            )

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AppBackground)
                                    .clickable { deathCount++ },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = DividerColor, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Observed Symptoms Selector
                    Text(
                        text = stringResource("symptoms"),
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Symptoms Chips Wrap Layout
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        symptomsList.forEach { symptom ->
                            val active = selectedSymptoms.contains(symptom)
                            FilterChip(
                                selected = active,
                                onClick = {
                                    if (active) selectedSymptoms.remove(symptom)
                                    else selectedSymptoms.add(symptom)
                                },
                                label = { Text(symptom, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GreenPrimary.copy(alpha = 0.12f),
                                    selectedLabelColor = GreenPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom Symptoms Input
                    OutlinedTextField(
                        value = customSymptoms,
                        onValueChange = { customSymptoms = it },
                        label = { Text(stringResource("symptoms_custom")) },
                        placeholder = { Text("Describe specific clinical patterns...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary,
                            focusedLabelColor = GreenPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = DividerColor, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Suspected Cause Dropdown Selection Grid
                    Text(
                        text = stringResource("suspected_cause"),
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        causesList.forEach { cause ->
                            val active = selectedCause == cause
                            ElevatedFilterChip(
                                selected = active,
                                onClick = { selectedCause = cause },
                                label = { Text(cause, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GreenPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Dynamic text box showing up when "Other" is chosen
                    AnimatedVisibility(
                        visible = selectedCause == "Other",
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = customCause,
                                onValueChange = { customCause = it },
                                label = { Text("Specify Custom Cause") },
                                placeholder = { Text("Enter other suspected biosecurity factor...") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    focusedLabelColor = GreenPrimary
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4. Submit button
                    Button(
                        onClick = {
                            viewModel.submitRecord(
                                deathCount = deathCount,
                                selectedSymptoms = selectedSymptoms.toList(),
                                customSymptoms = customSymptoms,
                                suspectedCause = selectedCause,
                                customCause = customCause
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        enabled = !isSubmitting && deathCount > 0
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Sync", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource("submit_log"),
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Interactive Analytics Row Section
        item {
            Text(
                text = "Biosecurity Analytics",
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Total Deaths Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(AlertRed.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.HeartBroken, contentDescription = "Deaths", tint = AlertRed)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Total Logged Deaths", fontSize = 12.sp, color = TextMedium, fontWeight = FontWeight.Medium)
                            Text("$totalDeaths birds", fontSize = 20.sp, color = TextDark, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Predominant Cause", fontSize = 11.sp, color = TextMedium)
                            Text(topCause, fontSize = 14.sp, color = AlertOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Average Environmental Snapshot Indicators during incidents
                if (records.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource("avg_metrics"),
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MiniMetricColumn("Temp", "%.1f°C".format(avgTemp), AlertOrange)
                                MiniMetricColumn("Humid", "%.1f%%".format(avgHumid), BlueSecondary)
                                MiniMetricColumn("Ammonia", "%.1f ppm".format(avgAmmonia), AlertRed)
                                MiniMetricColumn("Sound", "%.0f dB".format(avgSound), Color.Magenta)
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Expandable History logs
        item {
            Text(
                text = "Flock Mortality Logs (${records.size})",
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (records.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No mortality logs registered. Flock is fully healthy!",
                            style = Typography.bodyMedium,
                            color = TextMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(records) { record ->
                val isExpanded = expandedRecordId == record.id
                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val timeStr = dateFormat.format(Date(record.timestamp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedRecordId = if (isExpanded) null else record.id },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AlertRed.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${record.deathCount}",
                                        color = AlertRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = record.suspectedCause,
                                        style = Typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                    Text(
                                        text = timeStr,
                                        fontSize = 11.sp,
                                        color = TextMedium
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Expand",
                                tint = TextMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Brief Symptoms summary always visible
                        Text(
                            text = "Observed Symptoms: ${record.symptoms}",
                            style = Typography.bodyMedium,
                            color = TextDark
                        )

                        // Expandable Telemetry Snapshot Micro-grid
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = DividerColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = "Sensor",
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource("env_snapshot"),
                                        style = Typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GreenPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Telemetry Micro-grid
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MicroTelemetryBox("Temp", "%.1f°C".format(record.temperature), AlertOrange, Modifier.weight(1f))
                                    MicroTelemetryBox("Humid", "%.1f%%".format(record.humidity), BlueSecondary, Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MicroTelemetryBox("Ammonia", "%.1f ppm".format(record.ammoniaLevel), AlertRed, Modifier.weight(1f))
                                    MicroTelemetryBox("Acoustic", "%.0f dB".format(record.soundLevel), Color.Magenta, Modifier.weight(1f))
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Delete option
                                OutlinedButton(
                                    onClick = { viewModel.deleteRecord(record) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Purge", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource("delete"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniMetricColumn(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = TextMedium, fontWeight = FontWeight.SemiBold)
        Text(text = value, fontSize = 14.sp, color = tint, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MicroTelemetryBox(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.05f))
            .border(1.dp, tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(text = label, fontSize = 10.sp, color = TextMedium, fontWeight = FontWeight.SemiBold)
            Text(text = value, fontSize = 14.sp, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}
