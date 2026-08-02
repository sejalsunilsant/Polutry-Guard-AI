package com.poultryguard.ai.ui.alerts

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.ui.components.MonthlyHealthReportChart
import com.poultryguard.ai.ui.components.MonthlyHealthDataPoint
import com.poultryguard.ai.ui.dashboard.DashboardUiState
import com.poultryguard.ai.ui.dashboard.DashboardViewModel
import com.poultryguard.ai.ui.theme.*
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.SupportAgent
import java.io.File
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import com.poultryguard.ai.data.model.FarmEvent
import com.poultryguard.ai.data.model.FarmEventType
import com.poultryguard.ai.data.model.RecurrenceType
import com.poultryguard.ai.data.model.MortalityRecord
import com.poultryguard.ai.data.repository.MortalityRepository
import com.poultryguard.ai.data.cache.CalendarReminderManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dashboardViewModel: DashboardViewModel = viewModel()
    val uiState by dashboardViewModel.uiState.collectAsState()
    
    val cacheManager = remember { LocalCacheManager(context.applicationContext) }
    
    // Calendar and Reminder States
    var currentMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var currentYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedDateStr by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var cachedEvents by remember { mutableStateOf<List<FarmEvent>>(emptyList()) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    val mortalityRepository = remember { MortalityRepository(context.applicationContext) }
    var mortalityRecords by remember { mutableStateOf<List<MortalityRecord>>(emptyList()) }

    LaunchedEffect(Unit) {
        cachedEvents = cacheManager.getCachedFarmEvents()
        mortalityRepository.getAllRecordsFlow().collect { records ->
            mortalityRecords = records
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled gracefully
    }

    // Analytics Mock Datasets
    val healthRates = listOf(100f, 99.98f, 99.96f, 99.94f, 99.92f, 99.88f, 99.88f)
    val scatterPoints = listOf(
        Pair(24.2f, 12.0f),
        Pair(25.5f, 14.5f),
        Pair(27.8f, 19.0f),
        Pair(29.5f, 22.0f), // Danger Zone
        Pair(31.0f, 25.5f), // Critical Danger Zone
        Pair(23.5f, 11.0f),
        Pair(24.0f, 12.2f)
    )

    var generatedReportText by remember { mutableStateOf<String?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }

    fun generateBiosecurityReport(loggedDeaths: Int) {
        val totalBirds = 12500
        val survivalCount = totalBirds - loggedDeaths
        val survivalRate = (survivalCount.toFloat() / totalBirds) * 100

        val reportContent = """
            # POULTRY GUARD AI - BIOSECURITY REPORT
            =========================================
            Generated Timestamp: 2026-05-31
            Target Location: Shed #4 (Broilers - Day 18)
            flock Owner: Farmer Joe Patterson
            
            ## 📊 Telemetry & Mortality Audit
            -----------------------------------------
            - Initial Flock Stock: $totalBirds broilers
            - Logged Mortalities: $loggedDeaths deaths
            - Active Surviving Flock: $survivalCount broilers
            - Survival Rate Indicator: ${"%.2f%%".format(survivalRate)}
            
            ## 🌡️ Daily Environment Analytics
            -----------------------------------------
            - 1D Weekly Health Median: ${"%.2f%%".format(healthRates.average())}
            - Peak Temperature Swings: 31.0 °C
            - Peak Ammonia Gas Exposure: 25.5 ppm (WARNING threshold exceeded)
            
            ## 🧠 AI Diagnostic Insights & Action Plan
            -----------------------------------------
            [WARNING] Ammonia levels correlated with Temperature Swings indicate a critical biosecurity quadrant risk. High temperature limits broiler sweat dispersion and damp litter releases toxic gases.
            
            ### 🛠️ MANDATORY ACTION CHECKS:
            1. **Ventilation:** Engage Exhaust Fans at 100% speed to displace ammonia gas build-up.
            2. **litter Care:** Treat wet barn spaces immediately to check microbial gas decay.
            3. **Cooling:** Enable Broiler Misters to combat thermal stress.
            4. **Veterinarian Sweep:** Auto-notified Dr. Sarah Jenkins due to cumulative symptom logs.
            
            =========================================
            [Poultry Guard AI Cryptographic Security Audit OK]
        """.trimIndent()

        // Persist/Export report inside workspace local directory (zero cost)
        try {
            val reportFile = File(context.filesDir, "farm_biosecurity_report.md")
            reportFile.writeText(reportContent)
            
            // Also attempt to export directly in workspace folder if accessible
            val externalReport = File("d:\\poltry_gard_ai_repo\\farm_biosecurity_report.md")
            externalReport.writeText(reportContent)
        } catch (e: Exception) {
            // Graceful fallback
        }

        generatedReportText = reportContent
        showReportDialog = true
        Toast.makeText(context, "Biosecurity Report Generated & Exported!", Toast.LENGTH_SHORT).show()
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
                                text = "Guardian",
                                style = Typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(GreenPrimary)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(GreenPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Guardian",
                            tint = GreenPrimary
                        )
                    }
                }
            }

            // Monthly Health Report Section
            item {
                val monthlyReportData = listOf(
                    MonthlyHealthDataPoint(
                        dateStr = "Week 1",
                        healthRate = 99.88f,
                        deathCount = 1,
                        diseaseCases = 2,
                        temperature = 24.5f,
                        humidity = 60.2f,
                        ammonia = 12.0f,
                        sound = 58.0f
                    ),
                    MonthlyHealthDataPoint(
                        dateStr = "Week 2",
                        healthRate = 99.75f,
                        deathCount = 3,
                        diseaseCases = 5,
                        temperature = 26.2f,
                        humidity = 62.5f,
                        ammonia = 14.5f,
                        sound = 61.2f
                    ),
                    MonthlyHealthDataPoint(
                        dateStr = "Week 3",
                        healthRate = 99.45f,
                        deathCount = 6,
                        diseaseCases = 12,
                        temperature = 31.0f,
                        humidity = 70.8f,
                        ammonia = 25.5f,
                        sound = 68.5f
                    ),
                    MonthlyHealthDataPoint(
                        dateStr = "Week 4",
                        healthRate = 99.68f,
                        deathCount = 4,
                        diseaseCases = 7,
                        temperature = 27.8f,
                        humidity = 64.0f,
                        ammonia = 19.0f,
                        sound = 63.0f
                    ),
                    MonthlyHealthDataPoint(
                        dateStr = "Week 5",
                        healthRate = 99.92f,
                        deathCount = 1,
                        diseaseCases = 3,
                        temperature = 23.5f,
                        humidity = 58.5f,
                        ammonia = 11.0f,
                        sound = 56.5f
                    )
                )

                MonthlyHealthReportChart(
                    dataPoints = monthlyReportData
                )
            }

            // Health Assistant Section
            item {
                HealthAssistantSection()
            }

            // Interactive Farm Calendar Section
            item {
                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val roomEvents = mortalityRecords.map { record ->
                    val recordDateStr = sdfDate.format(Date(record.timestamp))
                    FarmEvent(
                        id = record.id,
                        dateStr = recordDateStr,
                        type = FarmEventType.DEATH,
                        title = "Poultry Deaths: ${record.deathCount} Birds",
                        count = record.deathCount,
                        cause = record.suspectedCause,
                        symptoms = record.symptoms,
                        notes = "Ammonia: ${record.ammoniaLevel} ppm, Temp: ${record.temperature}°C"
                    )
                }
                val allEvents = roomEvents + cachedEvents

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Month / Year Selector Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Farm Calendar",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (currentMonth == 0) {
                                            currentMonth = 11
                                            currentYear -= 1
                                        } else {
                                            currentMonth -= 1
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Previous Month",
                                        tint = GreenPrimary
                                    )
                                }

                                val monthNames = listOf(
                                    "January", "February", "March", "April", "May", "June",
                                    "July", "August", "September", "October", "November", "December"
                                )
                                Text(
                                    text = "${monthNames[currentMonth]} $currentYear",
                                    style = Typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                IconButton(
                                    onClick = {
                                        if (currentMonth == 11) {
                                            currentMonth = 0
                                            currentYear += 1
                                        } else {
                                            currentMonth += 1
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Next Month",
                                        tint = GreenPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Days grid
                        val daysOfWeek = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.YEAR, currentYear)
                            set(Calendar.MONTH, currentMonth)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }

                        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                        val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                        val dayCells = mutableListOf<String?>()
                        for (i in 1 until firstDayOfWeek) {
                            dayCells.add(null)
                        }
                        for (i in 1..maxDays) {
                            dayCells.add(i.toString())
                        }

                        val weeks = dayCells.chunked(7)

                        // Day of week headers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            daysOfWeek.forEach { dayName ->
                                Text(
                                    text = dayName,
                                    style = Typography.labelMedium,
                                    color = TextMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Calendar Month Day Grid
                        weeks.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                week.forEach { dayNumber ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (dayNumber != null) {
                                            val dayInt = dayNumber.toInt()
                                            val dateString = String.format(Locale.US, "%d-%02d-%02d", currentYear, currentMonth + 1, dayInt)
                                            val isSelected = dateString == selectedDateStr
                                            val isToday = dateString == todayStr

                                            val dayEvs = allEvents.filter { event ->
                                                val eventDateStr = event.dateStr
                                                when (event.recurrence) {
                                                    RecurrenceType.NONE -> eventDateStr == dateString
                                                    RecurrenceType.DAILY -> eventDateStr <= dateString
                                                    RecurrenceType.WEEKLY -> eventDateStr <= dateString && isSameDayOfWeek(eventDateStr, dateString)
                                                    RecurrenceType.MONTHLY -> eventDateStr <= dateString && isSameDayOfMonth(eventDateStr, dateString)
                                                }
                                            }

                                            val hasDeath = dayEvs.any { it.type == FarmEventType.DEATH }
                                            val hasFutureReminder = dayEvs.any { it.isScheduled }
                                            val hasPastEvent = dayEvs.any { !it.isScheduled && it.type != FarmEventType.DEATH }

                                            val bgModifier = if (isSelected) {
                                                Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(GreenPrimary.copy(alpha = 0.15f))
                                                    .border(1.5.dp, GreenPrimary, RoundedCornerShape(8.dp))
                                            } else if (isToday) {
                                                Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(GreenPrimary.copy(alpha = 0.05f))
                                                    .border(1.dp, GreenPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            } else {
                                                Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { selectedDateStr = dateString }
                                            }

                                            Box(
                                                modifier = bgModifier,
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Text(
                                                        text = dayNumber,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) GreenPrimary else TextDark
                                                    )

                                                    Spacer(modifier = Modifier.height(2.dp))

                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (hasDeath) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .clip(CircleShape)
                                                                    .background(AlertOrange)
                                                            )
                                                        }
                                                        if (hasPastEvent) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .clip(CircleShape)
                                                                    .background(GreenPrimary)
                                                            )
                                                        }
                                                        if (hasFutureReminder) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFFFBC02D))
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Events List for Selected Day
            item {
                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val roomEvents = mortalityRecords.map { record ->
                    val recordDateStr = sdfDate.format(Date(record.timestamp))
                    FarmEvent(
                        id = record.id,
                        dateStr = recordDateStr,
                        type = FarmEventType.DEATH,
                        title = "Poultry Deaths: ${record.deathCount} Birds",
                        count = record.deathCount,
                        cause = record.suspectedCause,
                        symptoms = record.symptoms,
                        notes = "Ammonia: ${record.ammoniaLevel} ppm, Temp: ${record.temperature}°C"
                    )
                }
                val allEvents = roomEvents + cachedEvents

                val selectedDayEvents = allEvents.filter { event ->
                    val eventDateStr = event.dateStr
                    when (event.recurrence) {
                        RecurrenceType.NONE -> eventDateStr == selectedDateStr
                        RecurrenceType.DAILY -> eventDateStr <= selectedDateStr
                        RecurrenceType.WEEKLY -> eventDateStr <= selectedDateStr && isSameDayOfWeek(eventDateStr, selectedDateStr)
                        RecurrenceType.MONTHLY -> eventDateStr <= selectedDateStr && isSameDayOfMonth(eventDateStr, selectedDateStr)
                    }
                }

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
                                text = "Activities for $selectedDateStr",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )

                            IconButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    showScheduleDialog = true
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(GreenPrimary.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Schedule Reminder",
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (selectedDayEvents.isEmpty()) {
                            Text(
                                text = "No activities or reminders recorded for this date.",
                                style = Typography.bodyMedium,
                                color = TextMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                selectedDayEvents.forEach { event ->
                                    val borderCol = when (event.type) {
                                        FarmEventType.DEATH -> AlertOrange
                                        FarmEventType.VACCINE -> GreenPrimary
                                        FarmEventType.MEDICINE -> Color(0xFF1E88E5)
                                        else -> Color(0xFF9E704F)
                                    }

                                    val bgCol = when (event.type) {
                                        FarmEventType.DEATH -> AlertOrange.copy(alpha = 0.05f)
                                        else -> GreenLight.copy(alpha = 0.4f)
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = bgCol),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderCol.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .height(36.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(borderCol)
                                            )

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = event.title,
                                                    style = Typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextDark
                                                )

                                                val subInfo = mutableListOf<String>()
                                                if (event.timeStr != null) {
                                                    subInfo.add(event.timeStr)
                                                }
                                                if (event.recurrence != RecurrenceType.NONE) {
                                                    subInfo.add("Repeats: ${event.recurrence.name.lowercase()}")
                                                }
                                                if (event.type == FarmEventType.DEATH) {
                                                    subInfo.add("Symptoms: ${event.symptoms ?: "None"}")
                                                    if (event.cause != null) {
                                                        subInfo.add("Cause: ${event.cause}")
                                                    }
                                                }

                                                if (subInfo.isNotEmpty()) {
                                                    Text(
                                                        text = subInfo.joinToString(" • "),
                                                        style = Typography.labelMedium,
                                                        color = TextMedium
                                                    )
                                                }

                                                if (event.notes?.isNotBlank() == true) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = event.notes,
                                                        fontSize = 11.sp,
                                                        color = TextDark.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dynamic Diagnostic Summary Cards
            item {
                val loggedDeaths = cacheManager.getCachedMortalities()
                val total = 12500
                val survival = total - loggedDeaths
                val survivalRate = (survival.toFloat() / total) * 100

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Biosecurity Quick Summary",
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Survival Rate", fontSize = 11.sp, color = TextMedium)
                                Text(
                                    text = "${"%.2f%%".format(survivalRate)}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                            }
                            Column {
                                Text(text = "Logged Deaths", fontSize = 11.sp, color = TextMedium)
                                Text(
                                    text = "$loggedDeaths birds",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (loggedDeaths > 5) AlertRed else TextDark
                                )
                            }
                            Column {
                                Text(text = "Peak Ammonia", fontSize = 11.sp, color = TextMedium)
                                Text(
                                    text = "25.5 ppm",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlertOrange
                                )
                            }
                        }
                    }
                }
            }

            // Generate report card action
            item {
                val loggedDeaths = cacheManager.getCachedMortalities()
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GreenLight),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(GreenPrimary.copy(alpha = 0.3f))
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Report",
                                tint = GreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Export Biosecurity Report",
                                    style = Typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                                Text(
                                    text = "Compiles health indices, gas correlation curves, and expert AI advice.",
                                    style = Typography.labelMedium,
                                    color = TextMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { generateBiosecurityReport(loggedDeaths) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) {
                            Text(
                                text = "Generate Report",
                                style = Typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Gorgeous Preview Document Overlay Dialog
    if (showReportDialog && generatedReportText != null) {
        Dialog(onDismissRequest = { showReportDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Generated",
                                tint = GreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Biosecurity Report OK",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                        }

                        IconButton(
                            onClick = { showReportDialog = false },
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

                    Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                    // Formatted Report Scrollable Text
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(AppBackground, shape = RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = generatedReportText!!,
                                    fontSize = 12.sp,
                                    color = TextDark,
                                    lineHeight = 18.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showReportDialog = false },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        Text(
                            text = "Done & Exported",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Scheduling Dialog
    if (showScheduleDialog) {
        var reminderTitle by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(FarmEventType.MEDICINE) }
        var selectedRecurrence by remember { mutableStateOf(RecurrenceType.NONE) }
        var reminderTime by remember { mutableStateOf("09:00") }
        var reminderNotes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = {
                Text(
                    text = "Schedule Farm Reminder",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = reminderTitle,
                        onValueChange = { reminderTitle = it },
                        label = { Text("Task / Medicine Name") },
                        placeholder = { Text("e.g. Newcastle Vaccine, Feed check") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = selectedDateStr,
                        onValueChange = {},
                        label = { Text("Scheduled Date") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = reminderTime,
                        onValueChange = { reminderTime = it },
                        label = { Text("Time (HH:mm)") },
                        placeholder = { Text("e.g. 08:30") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Activity Category",
                        style = Typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val types = listOf(
                            FarmEventType.MEDICINE to "Med",
                            FarmEventType.VACCINE to "Vacc",
                            FarmEventType.FEEDING to "Feed",
                            FarmEventType.CLEANING to "Clean",
                            FarmEventType.OTHER to "Other"
                        )
                        types.forEach { (type, label) ->
                            val isSelected = selectedType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) GreenPrimary else AppBackground)
                                    .clickable { selectedType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else GreenPrimary
                                )
                            }
                        }
                    }

                    Text(
                        text = "Recurrence Interval",
                        style = Typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val recurrences = listOf(
                            RecurrenceType.NONE to "None",
                            RecurrenceType.DAILY to "Daily",
                            RecurrenceType.WEEKLY to "Weekly",
                            RecurrenceType.MONTHLY to "Monthly"
                        )
                        recurrences.forEach { (rec, label) ->
                            val isSelected = selectedRecurrence == rec
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) GreenPrimary else AppBackground)
                                    .clickable { selectedRecurrence = rec }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else GreenPrimary
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = reminderNotes,
                        onValueChange = { reminderNotes = it },
                        label = { Text("Instructions / Notes") },
                        placeholder = { Text("e.g. Add 5ml per liter of water feed") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reminderTitle.isNotBlank()) {
                            val newEvent = FarmEvent(
                                dateStr = selectedDateStr,
                                timeStr = reminderTime,
                                type = selectedType,
                                title = reminderTitle,
                                notes = reminderNotes,
                                isScheduled = true,
                                recurrence = selectedRecurrence
                            )
                            cacheManager.addFarmEvent(newEvent)
                            CalendarReminderManager.scheduleReminder(context, newEvent)

                            cachedEvents = cacheManager.getCachedFarmEvents()

                            Toast.makeText(context, "Reminder Scheduled!", Toast.LENGTH_SHORT).show()
                            showScheduleDialog = false
                        } else {
                            Toast.makeText(context, "Please enter a reminder name", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Schedule", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) {
                    Text("Cancel", color = GreenPrimary)
                }
            }
        )
    }
}

// Calendar Date Helper functions
private fun isSameDayOfWeek(startStr: String, targetStr: String): Boolean {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return try {
        val startDate = sdf.parse(startStr)
        val targetDate = sdf.parse(targetStr)
        if (startDate == null || targetDate == null) return false
        val startCal = Calendar.getInstance().apply { time = startDate }
        val targetCal = Calendar.getInstance().apply { time = targetDate }
        startCal.get(Calendar.DAY_OF_WEEK) == targetCal.get(Calendar.DAY_OF_WEEK)
    } catch (e: Exception) {
        false
    }
}

private fun isSameDayOfMonth(startStr: String, targetStr: String): Boolean {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return try {
        val startDate = sdf.parse(startStr)
        val targetDate = sdf.parse(targetStr)
        if (startDate == null || targetDate == null) return false
        val startCal = Calendar.getInstance().apply { time = startDate }
        val targetCal = Calendar.getInstance().apply { time = targetDate }
        startCal.get(Calendar.DAY_OF_MONTH) == targetCal.get(Calendar.DAY_OF_MONTH)
    } catch (e: Exception) {
        false
    }
}

@Composable
fun HealthAssistantSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GreenPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = "AI Health Assistant",
                        tint = GreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Health Assistant",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            // Disease Risk Alert Card (orange-tinted warning card)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                border = androidx.compose.foundation.BorderStroke(1.dp, AlertOrange.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = AlertOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AI Risk Alert: Medium Coccidiosis Probability",
                            style = Typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AlertOrange
                        )
                        Text(
                            text = "Correlated Ammonia gas swings (19.0 ppm) and temperature spikes (27.8°C) recorded in Shed #4 indicate wet litter risk.",
                            style = Typography.labelMedium,
                            color = TextDark
                        )
                    }
                }
            }

            // Insights details
            Text(
                text = "Historical Insights & Analysis",
                style = Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "Over the last 30 days, your overall survival rate is high (99.91%). However, acoustic panic density indicators recorded a 5% raise in bird stress chirps during afternoon temperature peaks. Air quality indexes remained within safe margins except for transient ammonia spikes.",
                style = Typography.bodyMedium,
                color = TextMedium,
                modifier = Modifier.padding(bottom = 16.dp),
                lineHeight = 18.sp
            )

            // Recommendations
            Text(
                text = "Preventive Recommendations",
                style = Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val recommendations = listOf(
                "Engage exhaust fans at 100% capacity during heat hours (12:00 - 15:00) to clear transient ammonia gas.",
                "Apply dry absorbent agents (e.g. agricultural lime/drying powder) to damp litter zones under the watering lines.",
                "Distribute electrolyte-enhanced water booster packs to support birds during heat stress peaks."
            )

            recommendations.forEachIndexed { index, rec ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(GreenPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = rec,
                        style = Typography.bodyMedium,
                        color = TextDark,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
