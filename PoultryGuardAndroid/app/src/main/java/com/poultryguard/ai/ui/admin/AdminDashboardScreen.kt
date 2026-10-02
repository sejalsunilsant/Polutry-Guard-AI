package com.poultryguard.ai.ui.admin

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poultryguard.ai.data.api.AssignKitRequest
import com.poultryguard.ai.data.api.CreateKitRequest
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.data.model.HardwareKit
import com.poultryguard.ai.data.model.SystemStats
import com.poultryguard.ai.ui.theme.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    database: com.poultryguard.ai.data.cache.AppDatabase? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = database ?: remember { com.poultryguard.ai.data.cache.AppDatabase.getDatabase(context) }
    val statsDao = remember { db.systemStatsDao() }
    val systemStatsState = statsDao.getSystemStats().collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    fun updateStats(updateBlock: (com.poultryguard.ai.data.model.SystemStats) -> com.poultryguard.ai.data.model.SystemStats) {
        coroutineScope.launch {
            val current = systemStatsState.value ?: com.poultryguard.ai.data.model.SystemStats()
            statsDao.insertOrUpdate(updateBlock(current))
        }
    }

    val farmerDao = remember { db.farmerProfileDao() }
    val farmersListState by farmerDao.getAllFarmersFlow().collectAsState(initial = emptyList())
    var selectedFarmer by remember { mutableStateOf<com.poultryguard.ai.data.model.FarmerProfile?>(null) }

    var farmerSearchQuery by remember { mutableStateOf("") }
    var farmerStatusFilter by remember { mutableStateOf("All") }
    var farmerLocationFilter by remember { mutableStateOf("All") }

    fun toggleFarmerAccountStatus(farmerId: String, currentStatus: String) {
        coroutineScope.launch {
            val newStatus = if (currentStatus == "Active") "Disabled" else "Active"
            farmerDao.updateAccountStatus(farmerId, newStatus)
            Toast.makeText(context, "Account status updated to $newStatus", Toast.LENGTH_SHORT).show()
        }
    }

    fun resetFarmerAccess(farmerId: String) {
        coroutineScope.launch {
            farmerDao.resetAccess(farmerId)
            Toast.makeText(context, "Account password and access tokens reset successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    val vetRepository = remember { com.poultryguard.ai.data.repository.VetRepository(context) }
    val vetsListState by vetRepository.getVeterinariansFlow().collectAsState(initial = emptyList())
    val cacheManager = remember { LocalCacheManager(context) }

    var kitsList by remember {
        mutableStateOf(cacheManager.getHardwareKits())
    }

    LaunchedEffect(farmersListState, vetsListState, kitsList) {
        val current = statsDao.getSystemStats().firstOrNull() ?: com.poultryguard.ai.data.model.SystemStats()
        statsDao.insertOrUpdate(current.copy(
            totalFarmers = farmersListState.size,
            activeFarmers = farmersListState.count { it.accountStatus == "Active" },
            registeredVets = vetsListState.size,
            activeVets = vetsListState.count { it.verificationStatus == "VERIFIED" },
            totalFarms = farmersListState.map { it.farmName }.distinct().count { it.isNotBlank() },
            activeFarms = farmersListState.filter { it.isOnline }.map { it.farmName }.distinct().count { it.isNotBlank() },
            totalDevices = kitsList.size,
            onlineDevices = kitsList.count { it.isActive || it.lifecycleStatus == "Active" },
            offlineDevices = kitsList.count { !it.isActive && it.lifecycleStatus != "Active" }
        ))
    }

    var selectedVet by remember { mutableStateOf<com.poultryguard.ai.data.model.Veterinarian?>(null) }
    val authRepository = remember { com.poultryguard.ai.data.repository.SupabaseAuthRepository(context) }
    LaunchedEffect(Unit) {
        authRepository.syncAllFarmers()
        vetRepository.syncVeterinarians()
        val kitsRes = authRepository.syncAllKits()
        if (kitsRes.isSuccess) {
            kitsList = kitsRes.getOrDefault(emptyList())
        }
    }
    var showAddVetDialog by remember { mutableStateOf(false) }

    var vetSearchQuery by remember { mutableStateOf("") }
    var vetVerificationFilter by remember { mutableStateOf("All") }

    fun approveVetRegistration(vetId: String) {
        coroutineScope.launch {
            vetRepository.updateVerificationStatus(vetId, "VERIFIED")
            Toast.makeText(context, "Veterinarian credentials verified and registration approved!", Toast.LENGTH_SHORT).show()
        }
    }

    fun rejectVetRegistration(vetId: String) {
        coroutineScope.launch {
            vetRepository.updateVerificationStatus(vetId, "REJECTED")
            Toast.makeText(context, "Veterinarian credentials rejected.", Toast.LENGTH_SHORT).show()
        }
    }

    fun suspendVetAccount(vetId: String) {
        coroutineScope.launch {
            vetRepository.updateVerificationStatus(vetId, "SUSPENDED")
            Toast.makeText(context, "Veterinarian account suspended.", Toast.LENGTH_SHORT).show()
        }
    }

    fun activateVetAccount(vetId: String) {
        coroutineScope.launch {
            vetRepository.updateVerificationStatus(vetId, "VERIFIED")
            Toast.makeText(context, "Veterinarian account activated.", Toast.LENGTH_SHORT).show()
        }
    }

    fun changeVetAvailability(vetId: String, status: String) {
        coroutineScope.launch {
            vetRepository.updateAvailability(vetId, status)
            Toast.makeText(context, "Veterinarian availability updated to $status.", Toast.LENGTH_SHORT).show()
        }
    }

    var peopleSubTab by remember { mutableStateOf(0) }

    val ticketDao = remember { db.supportTicketDao() }
    val ticketsListState by ticketDao.getAllTicketsFlow().collectAsState(initial = emptyList())
    var selectedTicket by remember { mutableStateOf<com.poultryguard.ai.data.model.SupportTicket?>(null) }
    
    var supportSearchQuery by remember { mutableStateOf("") }
    var supportCategoryFilter by remember { mutableStateOf("All") }
    var supportPriorityFilter by remember { mutableStateOf("All") }

    fun toggleTicketStatus(ticketId: String, currentStatus: String) {
        coroutineScope.launch {
            val newStatus = if (currentStatus == "Resolved") "Unresolved" else "Resolved"
            ticketDao.updateStatus(ticketId, newStatus)
            Toast.makeText(context, "Ticket status updated to $newStatus", Toast.LENGTH_SHORT).show()
        }
    }

    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 4) {
            authRepository.syncAllFarmers()
        } else if (selectedTab == 2) {
            val kitsRes = authRepository.syncAllKits()
            if (kitsRes.isSuccess) {
                kitsList = kitsRes.getOrDefault(emptyList())
            }
        }
    }

    // Dialog state variables
    var showAddFarmerDialog by remember { mutableStateOf(false) }
    var showAddKitDialog by remember { mutableStateOf(false) }
    var activeAssignKit by remember { mutableStateOf<HardwareKit?>(null) }
    var activeFirmwareKit by remember { mutableStateOf<HardwareKit?>(null) }
    var activeReplaceKit by remember { mutableStateOf<HardwareKit?>(null) }

    // Search & Filter state
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }



    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        floatingActionButton = {
            if (selectedTab == 2) {
                FloatingActionButton(
                    onClick = { showAddKitDialog = true },
                    containerColor = GreenPrimary,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add New Kit")
                }
            } else if (selectedTab == 1 && peopleSubTab == 0) {
                FloatingActionButton(
                    onClick = { showAddFarmerDialog = true },
                    containerColor = GreenPrimary,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add New Farmer")
                }
            } else if (selectedTab == 1 && peopleSubTab == 1) {
                FloatingActionButton(
                    onClick = { showAddVetDialog = true },
                    containerColor = GreenPrimary,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add New Veterinarian")
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.GridView, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.People, contentDescription = "People") },
                    label = { Text("People") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Router, contentDescription = "Kits") },
                    label = { Text("Kits") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Feedback, contentDescription = "Support") },
                    label = { Text("Support") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                    label = { Text("Map") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Poultry Guard Admin",
                            style = Typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AlertOrange
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AlertOrange)
                        )
                    }
                }

                // Logout Button
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

            // Renders selected tab screen
            when (selectedTab) {
                0 -> {
                    // System Dashboard tab
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
                    ) {
                        item {
                            val stats = systemStatsState.value ?: com.poultryguard.ai.data.model.SystemStats()
                            
                            // Visual replica card of user's box layout
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface),
                                border = BorderStroke(1.dp, DividerColor)
                            ) {
                                Column {
                                    // Header of replica
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(GreenPrimary)
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "POULTRY GUARD ADMIN OVERVIEW",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = Typography.labelMedium,
                                            letterSpacing = 1.5.sp
                                        )
                                    }
                                    
                                    // Row 1
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                                    ) {
                                        // Cell 1: Farmers
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${stats.totalFarmers}",
                                                style = Typography.displayLarge,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = TextDark
                                            )
                                            Text(
                                                text = "Farmers",
                                                style = Typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextMedium
                                            )
                                        }
                                        
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(DividerColor))
                                        
                                        // Cell 2: Active Farmers
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${stats.activeFarmers}",
                                                style = Typography.displayLarge,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = GreenPrimary
                                            )
                                            Text(
                                                text = "Active",
                                                style = Typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextMedium
                                            )
                                        }
                                        
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(DividerColor))
                                        
                                        // Cell 3: Total Devices
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${stats.totalDevices}",
                                                style = Typography.displayLarge,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = TextDark
                                            )
                                            Text(
                                                text = "Devices",
                                                style = Typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextMedium
                                            )
                                        }
                                    }
                                    
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                                    
                                    // Row 2
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                                    ) {
                                        // Cell 1: Online Devices
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${stats.onlineDevices}",
                                                style = Typography.displayLarge,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF2E7D32)
                                            )
                                            Text(
                                                text = "Online",
                                                style = Typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextMedium
                                            )
                                        }
                                        
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(DividerColor))
                                        
                                        // Cell 2: Offline Devices
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${stats.offlineDevices}",
                                                style = Typography.displayLarge,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = AlertRed
                                            )
                                            Text(
                                                text = "Offline",
                                                style = Typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextMedium
                                            )
                                        }
                                        
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(DividerColor))
                                        
                                        // Cell 3: Disease Alerts
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${stats.openDiseaseAlerts}",
                                                style = Typography.displayLarge,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = AlertOrange
                                            )
                                            Text(
                                                text = "Alerts",
                                                style = Typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Category Details Cards
                        item {
                            val stats = systemStatsState.value ?: com.poultryguard.ai.data.model.SystemStats()
                            
                            // Farmer & Farm Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Agriculture, contentDescription = "Farms", tint = GreenPrimary, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Farmer & Farm Infrastructure", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Farmers Registry", style = Typography.labelMedium)
                                            Text("Total: ${stats.totalFarmers}  |  Active: ${stats.activeFarmers}", style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    updateStats { it.copy(totalFarmers = it.totalFarmers + 1, activeFarmers = it.activeFarmers + 1) }
                                                },
                                                modifier = Modifier.size(32.dp).background(GreenLight, RoundedCornerShape(6.dp))
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Add Farmer", tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    updateStats {
                                                        it.copy(
                                                            totalFarmers = (it.totalFarmers - 1).coerceAtLeast(0),
                                                            activeFarmers = (it.activeFarmers - 1).coerceAtLeast(0)
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp).background(AppBackground, RoundedCornerShape(6.dp))
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Remove Farmer", tint = TextMedium, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Poultry Farms", style = Typography.labelMedium)
                                            Text("Total: ${stats.totalFarms}  |  Active: ${stats.activeFarms}", style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    updateStats { it.copy(totalFarms = it.totalFarms + 1, activeFarms = it.activeFarms + 1) }
                                                },
                                                modifier = Modifier.size(32.dp).background(GreenLight, RoundedCornerShape(6.dp))
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Add Farm", tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    updateStats {
                                                        it.copy(
                                                            totalFarms = (it.totalFarms - 1).coerceAtLeast(0),
                                                            activeFarms = (it.activeFarms - 1).coerceAtLeast(0)
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp).background(AppBackground, RoundedCornerShape(6.dp))
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Remove Farm", tint = TextMedium, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        item {
                            val stats = systemStatsState.value ?: com.poultryguard.ai.data.model.SystemStats()
                            
                            // Veterinarian Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.MedicalServices, contentDescription = "Vets", tint = BlueSecondary, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Veterinarian Network", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Registered Veterinarians", style = Typography.labelMedium)
                                            Text("Total Vets: ${stats.registeredVets}  |  Active: ${stats.activeVets}", style = Typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    updateStats { it.copy(registeredVets = it.registeredVets + 1, activeVets = it.activeVets + 1) }
                                                },
                                                modifier = Modifier.size(32.dp).background(GreenLight, RoundedCornerShape(6.dp))
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Add Vet", tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    updateStats {
                                                        it.copy(
                                                            registeredVets = (it.registeredVets - 1).coerceAtLeast(0),
                                                            activeVets = (it.activeVets - 1).coerceAtLeast(0)
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp).background(AppBackground, RoundedCornerShape(6.dp))
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Remove Vet", tint = TextMedium, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        item {
                            val stats = systemStatsState.value ?: com.poultryguard.ai.data.model.SystemStats()
                            
                            // Security & Support Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = "Alerts", tint = AlertRed, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Security & Support Center", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Open Disease Alerts", style = Typography.labelMedium)
                                            Text("${stats.openDiseaseAlerts} Active Incidents", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = if (stats.openDiseaseAlerts > 0) AlertRed else TextDark)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    updateStats { it.copy(openDiseaseAlerts = it.openDiseaseAlerts + 1) }
                                                },
                                                modifier = Modifier.size(32.dp).background(AlertRed.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Trigger Alert", tint = AlertRed, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    updateStats { it.copy(openDiseaseAlerts = (it.openDiseaseAlerts - 1).coerceAtLeast(0)) }
                                                },
                                                modifier = Modifier.size(32.dp).background(AppBackground, RoundedCornerShape(6.dp))
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Resolve Alert", tint = TextMedium, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Pending Support Requests", style = Typography.labelMedium)
                                            Text("${stats.pendingSupportRequests} Open Tickets", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = if (stats.pendingSupportRequests > 0) AlertOrange else TextDark)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    updateStats { it.copy(pendingSupportRequests = it.pendingSupportRequests + 1) }
                                                },
                                                modifier = Modifier.size(32.dp).background(AlertOrange.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Open Ticket", tint = AlertOrange, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    updateStats { it.copy(pendingSupportRequests = (it.pendingSupportRequests - 1).coerceAtLeast(0)) }
                                                },
                                                modifier = Modifier.size(32.dp).background(AppBackground, RoundedCornerShape(6.dp))
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Resolve Ticket", tint = TextMedium, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // People Tab (Farmers & Veterinarians Directories combined)
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(
                            selectedTabIndex = peopleSubTab,
                            containerColor = Color.Transparent,
                            contentColor = GreenPrimary,
                            indicator = { tabPositions ->
                                TabRowDefaults.Indicator(
                                    Modifier.tabIndicatorOffset(tabPositions[peopleSubTab]),
                                    color = GreenPrimary
                                )
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Tab(
                                selected = peopleSubTab == 0,
                                onClick = { peopleSubTab = 0 },
                                text = { Text("Farmers", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = peopleSubTab == 1,
                                onClick = { peopleSubTab = 1 },
                                text = { Text("Veterinarians", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = peopleSubTab == 2,
                                onClick = { peopleSubTab = 2 },
                                text = { Text("Requests", fontWeight = FontWeight.Bold) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when (peopleSubTab) {
                            0 -> {
                                // Farmer Directory Sub-Screen
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    // Search and status filters row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = farmerSearchQuery,
                                            onValueChange = { farmerSearchQuery = it },
                                            placeholder = { Text("Search farmer or farm...") },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = GreenPrimary,
                                                focusedLabelColor = GreenPrimary
                                            )
                                        )
                                        
                                        // Status filter toggle ("All" vs "Active")
                                        var showStatusMenu by remember { mutableStateOf(false) }
                                        Box {
                                            OutlinedButton(
                                                onClick = { showStatusMenu = true },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, if (farmerStatusFilter != "All") GreenPrimary else DividerColor),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = if (farmerStatusFilter != "All") GreenPrimary else TextMedium
                                                )
                                            ) {
                                                Icon(Icons.Default.FilterList, contentDescription = "Status Filter")
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(farmerStatusFilter)
                                            }
                                            DropdownMenu(
                                                expanded = showStatusMenu,
                                                onDismissRequest = { showStatusMenu = false }
                                            ) {
                                                val statusOpts = listOf("All", "Active", "Disabled")
                                                statusOpts.forEach { opt ->
                                                    DropdownMenuItem(
                                                        text = { Text(opt) },
                                                        onClick = {
                                                            farmerStatusFilter = opt
                                                            showStatusMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        
                                        // Location filter dropdown
                                        var showLocationMenu by remember { mutableStateOf(false) }
                                        Box {
                                            OutlinedButton(
                                                onClick = { showLocationMenu = true },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, if (farmerLocationFilter != "All") GreenPrimary else DividerColor),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = if (farmerLocationFilter != "All") GreenPrimary else TextMedium
                                                )
                                            ) {
                                                Icon(Icons.Default.Place, contentDescription = "Location Filter")
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (farmerLocationFilter.length > 8) farmerLocationFilter.take(8) + "..." else farmerLocationFilter)
                                            }
                                            DropdownMenu(
                                                expanded = showLocationMenu,
                                                onDismissRequest = { showLocationMenu = false }
                                            ) {
                                                val locs = listOf("All", "North Sector", "East Valley Barns", "South Hills", "West Plains")
                                                locs.forEach { loc ->
                                                    DropdownMenuItem(
                                                        text = { Text(loc) },
                                                        onClick = {
                                                            farmerLocationFilter = loc
                                                            showLocationMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Table Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(GreenPrimary.copy(alpha = 0.08f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Farmer", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, color = TextDark, fontSize = 11.sp)
                                        Text("Farm", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = TextDark, fontSize = 11.sp)
                                        Text("Device", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = TextDark, fontSize = 11.sp)
                                        Text("Status", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, color = TextDark, fontSize = 11.sp)
                                        Text("Last Active", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = TextDark, fontSize = 11.sp)
                                    }
                                    
                                    // Farmer list computation
                                    val filteredFarmers = farmersListState.filter { f ->
                                        val matchesSearch = f.name.contains(farmerSearchQuery, ignoreCase = true) ||
                                                f.farmName.contains(farmerSearchQuery, ignoreCase = true)
                                        val matchesStatus = when (farmerStatusFilter) {
                                            "Active" -> f.accountStatus == "Active" && f.isOnline
                                            "Disabled" -> f.accountStatus == "Disabled"
                                            else -> true
                                        }
                                        val matchesLocation = farmerLocationFilter == "All" || f.farmLocation.contains(farmerLocationFilter, ignoreCase = true)
                                        matchesSearch && matchesStatus && matchesLocation
                                    }
                                    
                                    if (filteredFarmers.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No farmers match search/filter criteria.", color = TextMedium, textAlign = TextAlign.Center)
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                                        ) {
                                            items(filteredFarmers) { farmer ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { selectedFarmer = farmer },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                                                    border = BorderStroke(1.dp, DividerColor)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(farmer.name, modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, color = TextDark, fontSize = 12.sp)
                                                        Text(farmer.farmName, modifier = Modifier.weight(1f), color = TextMedium, fontSize = 12.sp)
                                                        Text(farmer.deviceId, modifier = Modifier.weight(1f), color = TextMedium, fontSize = 12.sp)
                                                        
                                                        // Status Badge
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(0.8f)
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (farmer.isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = if (farmer.isOnline) "Online" else "Offline",
                                                                color = if (farmer.isOnline) Color(0xFF2E7D32) else AlertRed,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        
                                                        Text(farmer.lastActive, modifier = Modifier.weight(1f), color = TextMedium, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // Veterinarian Directory Sub-Screen
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    // Search bar & status filters
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = vetSearchQuery,
                                            onValueChange = { vetSearchQuery = it },
                                            placeholder = { Text("Search vet by name, specialty, location...") },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Vets") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = GreenPrimary,
                                                focusedLabelColor = GreenPrimary
                                            )
                                        )
                                        
                                        // Verification filter dropdown
                                        var showVerificationFilterMenu by remember { mutableStateOf(false) }
                                        Box {
                                            OutlinedButton(
                                                onClick = { showVerificationFilterMenu = true },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, if (vetVerificationFilter != "All") GreenPrimary else DividerColor),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = if (vetVerificationFilter != "All") GreenPrimary else TextMedium
                                                )
                                            ) {
                                                Icon(Icons.Default.FilterList, contentDescription = "Filter Verification")
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(vetVerificationFilter)
                                            }
                                            DropdownMenu(
                                                expanded = showVerificationFilterMenu,
                                                onDismissRequest = { showVerificationFilterMenu = false }
                                            ) {
                                                val statusOpts = listOf("All", "PENDING", "VERIFIED", "REJECTED", "SUSPENDED")
                                                statusOpts.forEach { opt ->
                                                    DropdownMenuItem(
                                                        text = { Text(opt) },
                                                        onClick = {
                                                            vetVerificationFilter = opt
                                                            showVerificationFilterMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // List display
                                    val filteredVets = vetsListState.filter { v ->
                                        val matchesSearch = v.name.contains(vetSearchQuery, ignoreCase = true) ||
                                                v.specialty.contains(vetSearchQuery, ignoreCase = true) ||
                                                v.location.contains(vetSearchQuery, ignoreCase = true)
                                        val matchesStatus = vetVerificationFilter == "All" || v.verificationStatus == vetVerificationFilter
                                        matchesSearch && matchesStatus
                                    }
                                    
                                    if (filteredVets.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No veterinarians match search/filter criteria.", color = TextMedium, textAlign = TextAlign.Center)
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                                        ) {
                                            items(filteredVets) { vet ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { selectedVet = vet },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                                                    border = BorderStroke(1.dp, DividerColor)
                                                ) {
                                                    Column(modifier = Modifier.padding(14.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(vet.name, style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextDark)
                                                                Text(vet.specialty, style = Typography.bodyMedium, color = TextMedium)
                                                            }
                                                            
                                                            // Verification status badge
                                                            val statusColor = when (vet.verificationStatus) {
                                                                "VERIFIED" -> Color(0xFF2E7D32)
                                                                "PENDING" -> AlertOrange
                                                                "REJECTED" -> AlertRed
                                                                "SUSPENDED" -> Color.Gray
                                                                else -> TextMedium
                                                            }
                                                            val statusBg = when (vet.verificationStatus) {
                                                                "VERIFIED" -> Color(0xFFE8F5E9)
                                                                "PENDING" -> Color(0xFFFFF3E0)
                                                                "REJECTED" -> Color(0xFFFFEBEE)
                                                                "SUSPENDED" -> Color(0xFFECEFF1)
                                                                else -> DividerColor
                                                            }
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(statusBg)
                                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                                            ) {
                                                                Text(vet.verificationStatus, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                            }
                                                        }
                                                        
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                        ) {
                                                            Text("Location: ${vet.location}", style = Typography.bodySmall, color = TextMedium)
                                                            Text("Availability: ${vet.availability}", style = Typography.bodySmall, color = if (vet.availability == "Available") GreenPrimary else TextMedium)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                PendingFarmerRequestsSubScreen(
                                    authRepository = remember { com.poultryguard.ai.data.repository.SupabaseAuthRepository(context) },
                                    coroutineScope = coroutineScope,
                                    context = context
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // Device Kit Management screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Search and filter headers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search ESP32 hardware kits...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Kits") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    focusedLabelColor = GreenPrimary
                                )
                            )

                            var showStatusDropdown by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { showStatusDropdown = true },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (statusFilter != "All") GreenPrimary else DividerColor),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (statusFilter != "All") GreenPrimary else TextMedium
                                    )
                                ) {
                                    Icon(Icons.Default.FilterAlt, contentDescription = "Filter Status")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(statusFilter)
                                }
                                DropdownMenu(
                                    expanded = showStatusDropdown,
                                    onDismissRequest = { showStatusDropdown = false }
                                ) {
                                    val states = listOf("All", "Available", "Active", "Maintenance", "Retired")
                                    states.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(s) },
                                            onClick = {
                                                statusFilter = s
                                                showStatusDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Kits list
                        val filteredList = kitsList.filter { kit ->
                            val matchesSearch = kit.kitId.contains(searchQuery, ignoreCase = true) ||
                                    kit.farmerName.contains(searchQuery, ignoreCase = true) ||
                                    kit.farmName.contains(searchQuery, ignoreCase = true)
                            val matchesStatus = statusFilter == "All" || kit.lifecycleStatus == statusFilter
                            matchesSearch && matchesStatus
                        }

                        if (filteredList.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No hardware kits found in database.", color = TextMedium, textAlign = TextAlign.Center)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                            ) {
                                items(filteredList) { kit ->
                                    AdminHardwareKitCard(
                                        kit = kit,
                                        onAssignClick = { activeAssignKit = kit },
                                        onUnassignClick = {
                                            val farmerIdToClear = kit.farmerId
                                            val updatedList = kitsList.map { k ->
                                                if (k.kitId == kit.kitId) {
                                                    k.copy(
                                                        farmerId = "",
                                                        farmerName = "",
                                                        farmName = "",
                                                        lifecycleStatus = "Available"
                                                    )
                                                } else k
                                            }
                                            kitsList = updatedList
                                            cacheManager.saveHardwareKits(updatedList)
                                            if (farmerIdToClear.isNotEmpty()) {
                                                coroutineScope.launch {
                                                    val farmerProfile = farmerDao.getFarmerById(farmerIdToClear)
                                                    if (farmerProfile != null) {
                                                        val updatedProfile = farmerProfile.copy(
                                                            deviceId = "",
                                                            deviceSerial = "",
                                                            firmwareVersion = ""
                                                        )
                                                        farmerDao.insert(updatedProfile)
                                                    }
                                                }
                                            }
                                            updateStats {
                                                it.copy(
                                                    activeFarmers = (it.activeFarmers - 1).coerceAtLeast(0),
                                                    activeFarms = (it.activeFarms - 1).coerceAtLeast(0),
                                                    onlineDevices = (it.onlineDevices - 1).coerceAtLeast(0),
                                                    offlineDevices = it.offlineDevices + 1
                                                )
                                            }
                                            Toast.makeText(context, "Kit ${kit.kitId} unassigned.", Toast.LENGTH_SHORT).show()
                                        },
                                        onMaintenanceClick = {
                                            val updatedList = kitsList.map { k ->
                                                if (k.kitId == kit.kitId) {
                                                    k.copy(lifecycleStatus = "Maintenance")
                                                } else k
                                            }
                                            kitsList = updatedList
                                            cacheManager.saveHardwareKits(updatedList)
                                            Toast.makeText(context, "Kit ${kit.kitId} set to Maintenance.", Toast.LENGTH_SHORT).show()
                                        },
                                        onRetireClick = {
                                            val updatedList = kitsList.map { k ->
                                                if (k.kitId == kit.kitId) {
                                                    k.copy(lifecycleStatus = "Retired")
                                                } else k
                                            }
                                            kitsList = updatedList
                                            cacheManager.saveHardwareKits(updatedList)
                                            Toast.makeText(context, "Kit ${kit.kitId} retired.", Toast.LENGTH_SHORT).show()
                                        },
                                        onFirmwareClick = { activeFirmwareKit = kit },
                                        onReplaceClick = { activeReplaceKit = kit }
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Support & Complaints tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Filters row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = supportSearchQuery,
                                onValueChange = { supportSearchQuery = it },
                                placeholder = { Text("Search complaints & requests...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Tickets") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    focusedLabelColor = GreenPrimary
                                )
                            )
                            
                            // Category filter dropdown
                            var showCatFilterMenu by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { showCatFilterMenu = true },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (supportCategoryFilter != "All") GreenPrimary else DividerColor),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (supportCategoryFilter != "All") GreenPrimary else TextMedium
                                    )
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filter Category")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (supportCategoryFilter.length > 8) supportCategoryFilter.take(8) + "..." else supportCategoryFilter)
                                }
                                DropdownMenu(
                                    expanded = showCatFilterMenu,
                                    onDismissRequest = { showCatFilterMenu = false }
                                ) {
                                    val catOpts = listOf("All", "Farmer Complaint", "Device Problem", "Installation Request", "Veterinarian Request", "Technical Support")
                                    catOpts.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt) },
                                            onClick = {
                                                supportCategoryFilter = opt
                                                showCatFilterMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // List computation
                        val filteredTickets = ticketsListState.filter { t ->
                            val matchesSearch = t.farmerName.contains(supportSearchQuery, ignoreCase = true) ||
                                    t.issue.contains(supportSearchQuery, ignoreCase = true) ||
                                    t.ticketId.contains(supportSearchQuery, ignoreCase = true)
                            val matchesCategory = supportCategoryFilter == "All" || t.category == supportCategoryFilter
                            matchesSearch && matchesCategory
                        }
                        
                        if (filteredTickets.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No support tickets found.", color = TextMedium, textAlign = TextAlign.Center)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                            ) {
                                items(filteredTickets) { ticket ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedTicket = ticket },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                                        border = BorderStroke(1.dp, DividerColor)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Ticket #${ticket.ticketId}", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextDark)
                                                    Text("Farmer: ${ticket.farmerName}", style = Typography.bodyMedium, color = TextMedium)
                                                }
                                                
                                                // Priority & Status Badge
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    val priorityColor = when (ticket.priority) {
                                                        "HIGH" -> AlertRed
                                                        "MEDIUM" -> AlertOrange
                                                        else -> Color(0xFF2979FF)
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(priorityColor.copy(alpha = 0.1f))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(ticket.priority, color = priorityColor, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                                    }
                                                    
                                                    val statusColor = if (ticket.status == "Resolved") Color(0xFF2E7D32) else AlertOrange
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(statusColor.copy(alpha = 0.1f))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(ticket.status, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                                    }
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(10.dp))
                                            
                                            Text("Category: ${ticket.category}", style = Typography.bodySmall, color = TextMedium, fontWeight = FontWeight.Bold)
                                            Text("Issue: ${ticket.issue}", style = Typography.bodyMedium, color = TextDark)
                                            if (ticket.deviceId != "N/A" && ticket.deviceId.isNotEmpty()) {
                                                Text("Device: ${ticket.deviceId}", style = Typography.bodySmall, color = TextMedium)
                                            }
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Button(
                                                    onClick = { toggleTicketStatus(ticket.ticketId, ticket.status) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (ticket.status == "Resolved") AlertOrange else GreenPrimary
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Text(
                                                        text = if (ticket.status == "Resolved") "Mark Unresolved" else "Resolve",
                                                        fontSize = 11.sp,
                                                        color = Color.White
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
                4 -> {
                    AdminMapView(farmers = farmersListState)
                }
            }
        }

        if (showAddKitDialog) {
            AddKitDialog(
                onDismiss = { showAddKitDialog = false },
                onAddKit = { newKit ->
                    val updated = kitsList.toMutableList().apply { add(newKit) }
                    kitsList = updated
                    cacheManager.saveHardwareKits(updated)
                    updateStats { it.copy(totalDevices = it.totalDevices + 1, offlineDevices = it.offlineDevices + 1) }
                    showAddKitDialog = false
                    Toast.makeText(context, "Successfully registered Device Kit ${newKit.kitId}!", Toast.LENGTH_SHORT).show()
                },
                nextSuggestedId = "PG-KIT-000${kitsList.size + 46}",
                authRepository = authRepository,
                coroutineScope = coroutineScope,
                context = context
            )
        }

        activeAssignKit?.let { kit ->
            AssignKitDialog(
                kit = kit,
                farmers = farmersListState,
                onDismiss = { activeAssignKit = null },
                onAssign = { id, farmer, farm ->
                    // Backend must succeed BEFORE any local state is touched.
                    coroutineScope.launch {
                        val api = authRepository.getAuthApi()
                        if (api == null) {
                            Toast.makeText(
                                context,
                                "Cannot assign: backend is unreachable. Check server URL in settings.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }

                        // Step 1 — POST to backend; Supabase must persist first
                        try {
                            val response = api.assignKit(
                                AssignKitRequest(
                                    deviceId = kit.gatewayId,
                                    farmerProfileId = id
                                )
                            )

                            if (response.status != "success") {
                                // Backend rejected — do not touch local state
                                val msg = response.message ?: "Unknown error from server"
                                Log.e("AdminDashboard", "[ADMIN KIT ASSIGN] failed: $msg")
                                Toast.makeText(
                                    context,
                                    "Assignment failed: $msg",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@launch
                            }

                            Log.d("AdminDashboard", "[ADMIN KIT ASSIGN] device=${kit.gatewayId} farmer=$id farm=$farm")
                            Log.d("AdminDashboard", "[ADMIN KIT ASSIGN] success")

                        } catch (e: Exception) {
                            val msg = e.message ?: "Network error"
                            Log.e("AdminDashboard", "[ADMIN KIT ASSIGN] failed: $msg")
                            Toast.makeText(
                                context,
                                "Assignment failed: $msg",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }

                        // Step 2 — Backend succeeded; now update local Room/cache/UI
                        val farmerProfile = farmerDao.getFarmerById(id)
                        if (farmerProfile != null) {
                            farmerDao.insert(
                                farmerProfile.copy(
                                    deviceId = kit.gatewayId,
                                    deviceSerial = kit.serialNumber,
                                    firmwareVersion = kit.firmwareVersion
                                )
                            )
                        }

                        val updatedList = kitsList.map { k ->
                            if (k.kitId == kit.kitId) {
                                k.copy(
                                    farmerId = id,
                                    farmerName = farmer,
                                    farmName = farm,
                                    lifecycleStatus = "Active"
                                )
                            } else k
                        }
                        kitsList = updatedList
                        cacheManager.saveHardwareKits(updatedList)

                        updateStats {
                            it.copy(
                                activeFarmers = it.activeFarmers + 1,
                                activeFarms = it.activeFarms + 1,
                                onlineDevices = it.onlineDevices + 1,
                                offlineDevices = (it.offlineDevices - 1).coerceAtLeast(0)
                            )
                        }
                        activeAssignKit = null
                        Toast.makeText(
                            context,
                            "Kit ${kit.kitId} assigned to $farmer at $farm.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        // Update Firmware Version Dialog Layout
        activeFirmwareKit?.let { kit ->
            UpdateFirmwareDialog(
                kit = kit,
                onDismiss = { activeFirmwareKit = null },
                onUpdate = { version ->
                    val updatedList = kitsList.map { k ->
                        if (k.kitId == kit.kitId) {
                            k.copy(firmwareVersion = version)
                        } else k
                    }
                    kitsList = updatedList
                    cacheManager.saveHardwareKits(updatedList)
                    activeFirmwareKit = null
                    Toast.makeText(context, "Kit ${kit.kitId} firmware updated to $version.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Replace Device ID / Serial Number Dialog Layout
        activeReplaceKit?.let { kit ->
            ReplaceDeviceDialog(
                kit = kit,
                onDismiss = { activeReplaceKit = null },
                onReplace = { newDeviceId, newSerial ->
                    val updatedList = kitsList.map { k ->
                        if (k.kitId == kit.kitId) {
                            k.copy(
                                gatewayId = newDeviceId,
                                serialNumber = newSerial,
                                lifecycleStatus = "Installed"
                            )
                        } else k
                    }
                    kitsList = updatedList
                    cacheManager.saveHardwareKits(updatedList)
                    coroutineScope.launch {
                        if (kit.farmerId.isNotEmpty()) {
                            val farmerProfile = farmerDao.getFarmerById(kit.farmerId)
                            if (farmerProfile != null) {
                                val updatedProfile = farmerProfile.copy(
                                    deviceId = newDeviceId,
                                    deviceSerial = newSerial
                                )
                                farmerDao.insert(updatedProfile)
                            }
                        }
                    }
                    activeReplaceKit = null
                    Toast.makeText(context, "Kit ${kit.kitId} hardware replaced with ESP32 node $newDeviceId.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Add Farmer Dialog Layout
        if (showAddFarmerDialog) {
            AddFarmerDialog(
                onDismiss = { showAddFarmerDialog = false },
                onAddFarmer = { newFarmer, password ->
                    coroutineScope.launch {
                        val result = authRepository.register(
                            name = newFarmer.name,
                            email = newFarmer.email,
                            password = password,
                            role = com.poultryguard.ai.data.model.UserRole.FARMER,
                            farmName = newFarmer.farmName,
                            farmLocation = newFarmer.farmLocation,
                            totalSheds = newFarmer.totalSheds,
                            floorSpaceSqFt = newFarmer.floorSpaceSqFt
                        )
                        result.fold(
                            onSuccess = { profile ->
                                // Sync all farmers from backend to update local Room cache
                                authRepository.syncAllFarmers()
                                showAddFarmerDialog = false
                                Toast.makeText(context, "Successfully registered Farmer ${profile.name}!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                Toast.makeText(context, "Registration failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            )
        }


        // Farmer Details Dialog Layout
        selectedFarmer?.let { farmer ->
            FarmerDetailDialog(
                farmer = farmer,
                onDismiss = { selectedFarmer = null },
                onToggleStatus = { toggleFarmerAccountStatus(farmer.id, farmer.accountStatus) },
                onResetAccess = { resetFarmerAccess(farmer.id) }
            )
        }

        // Add Vet Dialog Layout
        if (showAddVetDialog) {
            AddVetDialog(
                onDismiss = { showAddVetDialog = false },
                onAddVet = { newVet, password ->
                    coroutineScope.launch {
                        val result = authRepository.register(
                            name = newVet.name,
                            email = newVet.email,
                            password = password,
                            role = com.poultryguard.ai.data.model.UserRole.VETERINARIAN,
                            farmName = "",
                            farmLocation = newVet.location
                        )
                        result.fold(
                            onSuccess = { profile ->
                                // Sync veterinarians from backend to update local Room cache
                                vetRepository.syncVeterinarians()
                                showAddVetDialog = false
                                Toast.makeText(context, "Successfully registered Veterinarian ${profile.name}!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                Toast.makeText(context, "Registration failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            )
        }

        // Vet Details Dialog Layout
        selectedVet?.let { vet ->
            VetDetailDialog(
                vet = vet,
                onDismiss = { selectedVet = null },
                onApprove = { approveVetRegistration(vet.id) },
                onReject = { rejectVetRegistration(vet.id) },
                onSuspend = { suspendVetAccount(vet.id) },
                onActivate = { activateVetAccount(vet.id) },
                onChangeAvailability = { availability -> changeVetAvailability(vet.id, availability) }
            )
        }
    }
}

@Composable
fun AdminHardwareKitCard(
    kit: HardwareKit,
    onAssignClick: () -> Unit,
    onUnassignClick: () -> Unit,
    onMaintenanceClick: () -> Unit,
    onRetireClick: () -> Unit,
    onFirmwareClick: () -> Unit,
    onReplaceClick: () -> Unit
) {
    val statusColor = getStatusColor(kit.lifecycleStatus)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Kit",
                        tint = GreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = kit.kitId,
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = kit.lifecycleStatus,
                        color = statusColor,
                        style = Typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Specs Metadata Grid
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ESP32 Device ID:", style = Typography.labelMedium, color = TextMedium)
                    Text(kit.gatewayId, style = Typography.labelMedium, color = TextDark, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("PCB Serial No:", style = Typography.labelMedium, color = TextMedium)
                    Text(kit.serialNumber, style = Typography.labelMedium, color = TextDark)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Firmware Version:", style = Typography.labelMedium, color = TextMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(kit.firmwareVersion, style = Typography.labelMedium, color = TextDark)
                        if (kit.lifecycleStatus != "Retired") {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Firmware",
                                tint = GreenPrimary,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { onFirmwareClick() }
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Last Comm Status:", style = Typography.labelMedium, color = TextMedium)
                    Text(kit.lastCommunication, style = Typography.labelMedium, color = if (kit.lastCommunication.startsWith("Active")) GreenPrimary else TextMedium)
                }
            }

            // Selected/Integrated Sensors Icons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppBackground.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sensors:", style = Typography.labelMedium, color = TextMedium)
                
                SensorBadge(label = "Temp", enabled = kit.hasTempSensor)
                SensorBadge(label = "Humid", enabled = kit.hasHumidSensor)
                SensorBadge(label = "NH3", enabled = kit.hasAmmoniaSensor)
                SensorBadge(label = "Mic", enabled = kit.hasSoundSensor)
                SensorBadge(label = "Cam", enabled = kit.hasCameraSensor)
            }

            // Assignee details Row
            if (kit.farmerName.isNotEmpty() && kit.farmName.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GreenLight.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Agriculture,
                        contentDescription = "Farmer",
                        tint = GreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Assigned Farmer: ${kit.farmerName}",
                            style = Typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "Location/Farm: ${kit.farmName}",
                            style = Typography.labelMedium,
                            color = TextMedium
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.LightGray.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Unassigned",
                        tint = TextMedium,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Warehouse Inventory (Unassigned)",
                        style = Typography.labelMedium,
                        color = TextMedium
                    )
                }
            }

            // Life Cycle Action Panel (Only if not retired)
            if (kit.lifecycleStatus != "Retired") {
                Divider(color = DividerColor, thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Assign/Unassign Trigger
                    if (kit.farmerName.isEmpty()) {
                        Button(
                            onClick = onAssignClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text("Assign", color = Color.White, fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onUnassignClick,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, AlertOrange),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertOrange),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text("Unassign", fontSize = 12.sp)
                        }
                    }

                    // Repair/Replace Trigger
                    OutlinedButton(
                        onClick = onReplaceClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Text("Replace Node", fontSize = 12.sp, maxLines = 1)
                    }

                    // Context Menu for Maintenance/Retire
                    var showActionsMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.wrapContentSize()) {
                        IconButton(
                            onClick = { showActionsMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Actions",
                                tint = TextMedium
                            )
                        }
                        DropdownMenu(
                            expanded = showActionsMenu,
                            onDismissRequest = { showActionsMenu = false }
                        ) {
                            if (kit.lifecycleStatus != "Maintenance") {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = "Maintenance", tint = Color(0xFFFFB300)) },
                                    text = { Text("Mark Maintenance", color = Color(0xFFFFB300)) },
                                    onClick = {
                                        showActionsMenu = false
                                        onMaintenanceClick()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = "Retire", tint = AlertRed) },
                                text = { Text("Retire / Archive", color = AlertRed) },
                                onClick = {
                                    showActionsMenu = false
                                    onRetireClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorBadge(label: String, enabled: Boolean) {
    val bg = if (enabled) GreenPrimary.copy(alpha = 0.1f) else Color.LightGray.copy(alpha = 0.15f)
    val tc = if (enabled) GreenPrimary else TextMedium
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = tc,
            fontSize = 10.sp,
            fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddKitDialog(
    onDismiss: () -> Unit,
    onAddKit: (HardwareKit) -> Unit,
    nextSuggestedId: String,
    authRepository: com.poultryguard.ai.data.repository.SupabaseAuthRepository,
    coroutineScope: CoroutineScope,
    context: android.content.Context
) {
    var kitId by remember { mutableStateOf(nextSuggestedId) }
    var deviceId by remember { mutableStateOf("ESP32-00" + nextSuggestedId.takeLast(2)) }
    var serialNumber by remember { mutableStateOf("PGESP0" + nextSuggestedId.takeLast(2)) }
    var firmwareVersion by remember { mutableStateOf("1.2.0") }

    // ThingSpeak cloud telemetry credentials
    var thingspeakChannelId by remember { mutableStateOf("") }
    var thingspeakReadApiKey by remember { mutableStateOf("") }
    var thingspeakWriteApiKey by remember { mutableStateOf("") }
    var writeKeyVisible by remember { mutableStateOf(false) }

    // Sensors checkboxes
    var tempChecked by remember { mutableStateOf(true) }
    var humidChecked by remember { mutableStateOf(true) }
    var ammoniaChecked by remember { mutableStateOf(true) }
    var soundChecked by remember { mutableStateOf(true) }
    var cameraChecked by remember { mutableStateOf(false) }

    // Sensor serial numbers
    var tempSerial by remember { mutableStateOf("TMP-" + nextSuggestedId.takeLast(2)) }
    var humidSerial by remember { mutableStateOf("HUM-" + nextSuggestedId.takeLast(2)) }
    var ammoniaSerial by remember { mutableStateOf("NH3-" + nextSuggestedId.takeLast(2)) }
    var soundSerial by remember { mutableStateOf("MIC-" + nextSuggestedId.takeLast(2)) }
    var cameraSerial by remember { mutableStateOf("CAM-" + nextSuggestedId.takeLast(2)) }

    // API submission state
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddBox, contentDescription = "Add Kit", tint = GreenPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Register Device Kit", fontWeight = FontWeight.Bold, color = TextDark)
            }
        },
        text = {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Kit ID generator field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = kitId,
                        onValueChange = { kitId = it },
                        label = { Text("Kit ID") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        shape = RoundedCornerShape(8.dp),
                        onClick = {
                            val randomSuffix = (10000..99999).random()
                            kitId = "PG-KIT-$randomSuffix"
                            deviceId = "ESP32-$randomSuffix"
                            serialNumber = "PGESP$randomSuffix"
                            tempSerial = "TMP-$randomSuffix"
                            humidSerial = "HUM-$randomSuffix"
                            ammoniaSerial = "NH3-$randomSuffix"
                            soundSerial = "MIC-$randomSuffix"
                            cameraSerial = "CAM-$randomSuffix"
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("Gen ID")
                    }
                }

                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("ESP32 Device ID") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = serialNumber,
                    onValueChange = { serialNumber = it },
                    label = { Text("PCB Serial Number") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = firmwareVersion,
                    onValueChange = { firmwareVersion = it },
                    label = { Text("Firmware Version") },
                    modifier = Modifier.fillMaxWidth()
                )

                Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                // ThingSpeak telemetry credentials section
                Text(
                    text = "ThingSpeak Channel (Optional)",
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                OutlinedTextField(
                    value = thingspeakChannelId,
                    onValueChange = { thingspeakChannelId = it },
                    label = { Text("Channel ID") },
                    placeholder = { Text("e.g. 2345678") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GreenPrimary)
                )

                OutlinedTextField(
                    value = thingspeakReadApiKey,
                    onValueChange = { thingspeakReadApiKey = it },
                    label = { Text("Read API Key") },
                    placeholder = { Text("Shared with farmer app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GreenPrimary)
                )

                OutlinedTextField(
                    value = thingspeakWriteApiKey,
                    onValueChange = { thingspeakWriteApiKey = it },
                    label = { Text("Write API Key (server-side only)") },
                    placeholder = { Text("Never exposed to farmer") },
                    singleLine = true,
                    visualTransformation = if (writeKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { writeKeyVisible = !writeKeyVisible }) {
                            Icon(
                                imageVector = if (writeKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (writeKeyVisible) "Hide" else "Show"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GreenPrimary)
                )

                Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                Text("Available Sensors Checklist", fontWeight = FontWeight.Bold, color = TextDark)

                // Checkboxes
                SensorCheckRow(label = "Temperature Sensor", checked = tempChecked, serial = tempSerial, onCheckChange = { tempChecked = it }, onSerialChange = { tempSerial = it })
                SensorCheckRow(label = "Humidity Sensor", checked = humidChecked, serial = humidSerial, onCheckChange = { humidChecked = it }, onSerialChange = { humidSerial = it })
                SensorCheckRow(label = "Ammonia Gas Sensor", checked = ammoniaChecked, serial = ammoniaSerial, onCheckChange = { ammoniaChecked = it }, onSerialChange = { ammoniaSerial = it })
                SensorCheckRow(label = "Acoustic Microphone", checked = soundChecked, serial = soundSerial, onCheckChange = { soundChecked = it }, onSerialChange = { soundSerial = it })
                SensorCheckRow(label = "Biosecurity Video Camera", checked = cameraChecked, serial = cameraSerial, onCheckChange = { cameraChecked = it }, onSerialChange = { cameraSerial = it })
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                enabled = !isSubmitting,
                onClick = {
                    if (kitId.isBlank() || deviceId.isBlank() || serialNumber.isBlank()) return@Button
                    val kit = HardwareKit(
                        kitId = kitId,
                        gatewayId = deviceId,
                        serialNumber = serialNumber,
                        firmwareVersion = firmwareVersion,
                        lifecycleStatus = "Available",
                        hasTempSensor = tempChecked,
                        hasHumidSensor = humidChecked,
                        hasAmmoniaSensor = ammoniaChecked,
                        hasSoundSensor = soundChecked,
                        hasCameraSensor = cameraChecked,
                        tempSensorId = tempSerial,
                        humidSensorId = humidSerial,
                        ammoniaSensorId = ammoniaSerial,
                        soundSensorId = soundSerial,
                        cameraSensorId = cameraSerial,
                        farmerName = "",
                        farmName = "",
                        lastCommunication = "Offline (Warehouse Inventory)",
                        farmerId = "",
                        thingspeakChannelId = thingspeakChannelId.trim(),
                        thingspeakReadApiKey = thingspeakReadApiKey.trim()
                    )
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            val api = authRepository.getAuthApi()
                            if (api == null) {
                                Toast.makeText(
                                    context,
                                    "Cannot register kit: backend is unreachable. Check server URL in settings.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@launch
                            }

                            val response = api.createKit(
                                CreateKitRequest(
                                    deviceId = deviceId.trim(),
                                    name = "${kitId.trim()} ESP32 Controller",
                                    kitId = kitId.trim(),
                                    serialNumber = serialNumber.trim(),
                                    firmwareVersion = firmwareVersion.trim(),
                                    thingspeakChannelId = thingspeakChannelId.trim().ifBlank { null },
                                    thingspeakReadApiKey = thingspeakReadApiKey.trim().ifBlank { null },
                                    thingspeakWriteApiKey = thingspeakWriteApiKey.trim().ifBlank { null }
                                )
                            )

                            if (response.status == "success") {
                                Log.d("AdminDashboard", "Kit $deviceId registered in Supabase via API")
                                onAddKit(kit)
                            } else {
                                // Backend explicitly rejected — do NOT save locally
                                val msg = response.message ?: "Unknown error from server"
                                Log.e("AdminDashboard", "createKit backend error: $msg")
                                Toast.makeText(
                                    context,
                                    "Registration failed: $msg",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                        } catch (e: Exception) {
                            // Network or parsing failure — do NOT save locally
                            val msg = e.message ?: "Network error"
                            Log.e("AdminDashboard", "createKit API call failed: $msg")
                            Toast.makeText(
                                context,
                                "Registration failed: $msg",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            isSubmitting = false
                        }
                    }
                }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Register Kit", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isSubmitting) onDismiss() }) {
                Text("Cancel")
            }
        },
        containerColor = CardSurface
    )
}

@Composable
fun SensorCheckRow(
    label: String,
    checked: Boolean,
    serial: String,
    onCheckChange: (Boolean) -> Unit,
    onSerialChange: (String) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckChange,
                colors = CheckboxDefaults.colors(checkedColor = GreenPrimary)
            )
            Text(label, style = Typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
        }
        if (checked) {
            OutlinedTextField(
                value = serial,
                onValueChange = onSerialChange,
                label = { Text("Sensor Serial Number") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, bottom = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GreenPrimary)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignKitDialog(
    kit: HardwareKit,
    farmers: List<com.poultryguard.ai.data.model.FarmerProfile>,
    onDismiss: () -> Unit,
    onAssign: (String, String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedFarmer by remember { mutableStateOf<com.poultryguard.ai.data.model.FarmerProfile?>(null) }

    LaunchedEffect(farmers) {
        if (farmers.isNotEmpty() && selectedFarmer == null) {
            selectedFarmer = farmers.firstOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AssignmentInd, contentDescription = "Assign", tint = GreenPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Assign Kit to Farm", fontWeight = FontWeight.Bold, color = TextDark)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Target Kit: ${kit.kitId}", style = Typography.bodyMedium, color = TextMedium)

                Text("Select Farmer", style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = TextDark)
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDark)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedFarmer?.let { "${it.name} (${it.farmName})" } ?: "Select Farmer Profile...",
                                maxLines = 1,
                                color = TextDark
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GreenPrimary)
                        }
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        farmers.forEach { farmer ->
                            DropdownMenuItem(
                                text = { Text("${farmer.name} - ${farmer.farmName} (${farmer.farmLocation})", color = TextDark) },
                                onClick = {
                                    selectedFarmer = farmer
                                    expanded = false
                                }
                            )
                        }
                        if (farmers.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No registered farmers found", color = AlertRed) },
                                onClick = { expanded = false },
                                enabled = false
                            )
                        }
                    }
                }
                
                selectedFarmer?.let { farmer ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppBackground),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Farmer Name: ${farmer.name}", style = Typography.bodyMedium, color = TextDark, fontWeight = FontWeight.Bold)
                            Text("Email: ${farmer.email}", style = Typography.bodySmall, color = TextMedium)
                            Text("Farm Name: ${farmer.farmName}", style = Typography.bodyMedium, color = TextDark)
                            Text("Location: ${farmer.farmLocation}", style = Typography.bodySmall, color = TextMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                enabled = selectedFarmer != null,
                onClick = {
                    val farmer = selectedFarmer ?: return@Button
                    onAssign(farmer.id, farmer.name, farmer.farmName)
                }
            ) {
                Text("Complete Assignment", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = CardSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateFirmwareDialog(
    kit: HardwareKit,
    onDismiss: () -> Unit,
    onUpdate: (String) -> Unit
) {
    var version by remember { mutableStateOf(kit.firmwareVersion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SystemUpdateAlt, contentDescription = "Update FW", tint = GreenPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Update Firmware Status", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Target Kit: ${kit.kitId}", style = Typography.bodyMedium, color = TextMedium)
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("Firmware Version String") },
                    singleLine = true,
                    placeholder = { Text("e.g. 1.2.5") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                onClick = {
                    if (version.isBlank()) return@Button
                    onUpdate(version)
                }
            ) {
                Text("Update Version", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = CardSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplaceDeviceDialog(
    kit: HardwareKit,
    onDismiss: () -> Unit,
    onReplace: (String, String) -> Unit
) {
    var deviceId by remember { mutableStateOf("") }
    var serialNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Replace", tint = GreenPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Replace IoT Gateway Node", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Replacing gateway inside Kit ${kit.kitId}. Old Device ID: ${kit.gatewayId}", style = Typography.bodySmall, color = TextMedium)

                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("New ESP32 Device ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = serialNumber,
                    onValueChange = { serialNumber = it },
                    label = { Text("New PCB Serial Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                onClick = {
                    if (deviceId.isBlank() || serialNumber.isBlank()) return@Button
                    onReplace(deviceId, serialNumber)
                }
            ) {
                Text("Confirm Swap", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = CardSurface
    )
}

fun getStatusColor(status: String): Color {
    return when (status) {
        "Manufactured" -> Color.Gray
        "Available" -> Color(0xFF00B0FF)
        "Assigned to Farmer" -> Color(0xFFFF9100)
        "Installed" -> Color(0xFF2979FF)
        "Active" -> GreenPrimary
        "Maintenance" -> Color(0xFFFFAB00)
        "Retired" -> AlertRed
        else -> TextMedium
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerDetailDialog(
    farmer: com.poultryguard.ai.data.model.FarmerProfile,
    onDismiss: () -> Unit,
    onToggleStatus: () -> Unit,
    onResetAccess: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = "Farmer Detail", tint = GreenPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = farmer.name,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Personal Information
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("1. Personal Information", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("Email Address", farmer.email)
                    DetailRow("Phone Number", farmer.phone)
                    DetailRow("Account Status", farmer.accountStatus)
                    DetailRow("Connection State", if (farmer.isOnline) "Online" else "Offline")
                    DetailRow("Last Seen Active", farmer.lastActive)
                }
                
                Divider(color = DividerColor)
                
                // Section 2: Farm Information
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("2. Farm Information", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("Farm/Facility Name", farmer.farmName)
                    DetailRow("Geographic Location", farmer.farmLocation)
                    DetailRow("Total Active Sheds", "${farmer.totalSheds} Barns")
                    DetailRow("Total Floor Space", "${farmer.floorSpaceSqFt} Sq. Ft.")
                }
                
                Divider(color = DividerColor)
                
                // Section 3: Active Batch
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("3. Active Batch Info", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("Batch Identifier", farmer.activeBatchId)
                    DetailRow("Placement Date", farmer.activeBatchStartDate)
                    DetailRow("Chick Age", "${farmer.chickAgeDays} Days")
                    DetailRow("Cumulative Feed", "${farmer.feedConsumedKg} Kg")
                    DetailRow("Mortality Registered", "${farmer.mortalitiesCount} Birds")
                }
                
                Divider(color = DividerColor)
                
                // Section 4: Connected Devices
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("4. Connected Devices", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("IoT Gateway Node ID", farmer.deviceId)
                    DetailRow("PCB Serial No.", farmer.deviceSerial)
                    DetailRow("Firmware Version", farmer.firmwareVersion)
                    DetailRow("Signal Strength", farmer.signalStrengthRssi)
                    DetailRow("Node Battery level", farmer.batteryPercentage)
                }
                
                Divider(color = DividerColor)
                
                // Section 5: Sensor Status
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("5. Sensor Status", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("Temp Sensor", farmer.tempSensorStatus)
                    DetailRow("Humidity Sensor", farmer.humidSensorStatus)
                    DetailRow("Ammonia Gas (NH3)", farmer.ammoniaSensorStatus)
                    DetailRow("Sound Monitor", farmer.soundSensorStatus)
                }
                
                Divider(color = DividerColor)
                
                // Section 6: Disease Alerts
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("6. Disease Alerts", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("Open Alerts", "${farmer.openDiseaseAlertsCount} Open Alerts")
                    DetailRow("Latest Warning", farmer.latestAlertText)
                }
                
                Divider(color = DividerColor)
                
                // Section 7: Veterinarian Consultations
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("7. Veterinarian Consultations", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("Consulting Vet", farmer.assignedVetName)
                    DetailRow("Last Session Date", farmer.lastConsultationDate)
                    DetailRow("Clinical Findings", farmer.consultationNotes)
                }
                
                Divider(color = DividerColor)
                
                // Section 8: Activity History
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("8. Activity History Logs", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("Last User Login", farmer.lastLoginTime)
                    DetailRow("Last Admin/Shed Action", farmer.lastActionDesc)
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (farmer.accountStatus == "Active") AlertRed else Color(0xFF2E7D32)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        onToggleStatus()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (farmer.accountStatus == "Active") "Disable Acc" else "Activate Acc",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
                
                OutlinedButton(
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        onResetAccess()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset Access", fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Close")
            }
        },
        containerColor = CardSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFarmerDialog(
    onDismiss: () -> Unit,
    onAddFarmer: (com.poultryguard.ai.data.model.FarmerProfile, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var farmName by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var sheds by remember { mutableStateOf("4") }
    var size by remember { mutableStateOf("24000") }
    var deviceId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddCircle, contentDescription = "Add Farmer", tint = GreenPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Register Farmer Account", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Account Password") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = farmName, onValueChange = { farmName = it }, label = { Text("Farm Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Farm Location") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sheds, onValueChange = { sheds = it }, label = { Text("Total Sheds / Barns") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = size, onValueChange = { size = it }, label = { Text("Floor Space (Sq. Ft.)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = deviceId, onValueChange = { deviceId = it }, label = { Text("Associated Device ID (Optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                onClick = {
                    if (name.isBlank() || email.isBlank() || farmName.isBlank() || password.isBlank()) return@Button
                    val farmer = com.poultryguard.ai.data.model.FarmerProfile(
                        id = "",
                        name = name,
                        email = email,
                        phone = phone,
                        accountStatus = "Active",
                        lastActive = "Just now",
                        isOnline = false,
                        farmName = farmName,
                        farmLocation = location.ifBlank { "Unspecified Sector" },
                        totalSheds = sheds.toIntOrNull() ?: 4,
                        floorSpaceSqFt = size.toIntOrNull() ?: 24000,
                        deviceId = deviceId.ifBlank { "Unassigned" },
                        deviceSerial = "",
                        firmwareVersion = "",
                        activeBatchId = "",
                        activeBatchStartDate = "",
                        chickAgeDays = 0,
                        feedConsumedKg = 0.0f,
                        mortalitiesCount = 0,
                        openDiseaseAlertsCount = 0
                    )
                    onAddFarmer(farmer, password)
                }
            ) {
                Text("Register Farmer", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = CardSurface
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = Typography.labelMedium, color = TextMedium)
        Text(text = value, style = Typography.bodyMedium, color = TextDark, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVetDialog(
    onDismiss: () -> Unit,
    onAddVet: (com.poultryguard.ai.data.model.Veterinarian, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    var qualification by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddCircle, contentDescription = "Add Vet", tint = GreenPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Register Veterinarian", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = specialty, onValueChange = { specialty = it }, label = { Text("Specialization (Specialty)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Account Password") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = licenseNumber, onValueChange = { licenseNumber = it }, label = { Text("License Number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = qualification, onValueChange = { qualification = it }, label = { Text("Qualification") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = experience, onValueChange = { experience = it }, label = { Text("Experience (e.g. 5 years)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                onClick = {
                    if (name.isBlank() || specialty.isBlank() || email.isBlank() || password.isBlank()) return@Button
                    val vet = com.poultryguard.ai.data.model.Veterinarian(
                        id = "",
                        name = name,
                        specialty = specialty,
                        phone = phone,
                        email = email,
                        location = location,
                        photoUrl = "default_avatar",
                        availability = "Available",
                        verificationStatus = "PENDING",
                        assignedFarmsCount = 0,
                        openCasesCount = 0,
                        credentialsDetails = "License: $licenseNumber, Qualification: $qualification, Experience: $experience",
                        consultationHistory = "No consultation history recorded yet.",
                        licenseNumber = licenseNumber,
                        qualification = qualification,
                        experience = experience.toIntOrNull() ?: 0
                    )
                    onAddVet(vet, password)
                }
            ) {
                Text("Register Vet", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = CardSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetDetailDialog(
    vet: com.poultryguard.ai.data.model.Veterinarian,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onSuspend: () -> Unit,
    onActivate: () -> Unit,
    onChangeAvailability: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = "Vet Detail", tint = GreenPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(vet.name, fontWeight = FontWeight.Bold, color = TextDark)
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Demographics
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("1. Demographics & Contact", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("Specialty", vet.specialty)
                    DetailRow("Location", vet.location)
                    DetailRow("Phone", vet.phone)
                    DetailRow("Email", vet.email)
                }
                
                Divider(color = DividerColor)
                
                // Section 2: Case & Farm Metrics
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("2. Farms & Active Cases", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("Assigned Farms", "${vet.assignedFarmsCount} Farms")
                    DetailRow("Open Active Cases", "${vet.openCasesCount} Cases")
                }
                
                Divider(color = DividerColor)
                
                // Section 3: Status Details
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("3. Verification & Availability Status", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("Verification Status", vet.verificationStatus)
                    DetailRow("Availability State", vet.availability)
                }
                
                Divider(color = DividerColor)
                
                // Section 4: Credentials Details
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("4. Verified Credentials & Experience", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    DetailRow("License Number", vet.licenseNumber.ifBlank { "Unspecified" })
                    DetailRow("Qualification", vet.qualification.ifBlank { "Unspecified" })
                    DetailRow("Experience", if (vet.experience > 0) "${vet.experience} years" else "Unspecified")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(vet.credentialsDetails, style = Typography.bodyMedium, color = TextDark)
                }
                
                Divider(color = DividerColor)
                
                // Section 5: Consultation History
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("5. Consultation & Case History", style = Typography.bodyLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    Text(vet.consultationHistory, style = Typography.bodyMedium, color = TextDark)
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Verification management actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (vet.verificationStatus == "PENDING") {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp),
                            onClick = {
                                onApprove()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Verify & Approve", color = Color.White, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            border = BorderStroke(1.dp, AlertRed),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                            shape = RoundedCornerShape(8.dp),
                            onClick = {
                                onReject()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reject", fontSize = 11.sp)
                        }
                    } else if (vet.verificationStatus == "VERIFIED") {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                            shape = RoundedCornerShape(8.dp),
                            onClick = {
                                onSuspend()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Deactivate / Suspend Account", color = Color.White, fontSize = 11.sp)
                        }
                    } else if (vet.verificationStatus == "SUSPENDED" || vet.verificationStatus == "REJECTED") {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp),
                            onClick = {
                                onActivate()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Activate Account", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
                
                // Availability update row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Set Availability: ", style = Typography.labelMedium, color = TextMedium, modifier = Modifier.weight(1f))
                    val avails = listOf("Available", "Busy", "Unavailable")
                    avails.forEach { a ->
                        OutlinedButton(
                            onClick = { onChangeAvailability(a); onDismiss() },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (vet.availability == a) GreenPrimary else DividerColor),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (vet.availability == a) GreenPrimary else TextMedium
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(a, fontSize = 9.sp)
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Close")
            }
        },
        containerColor = CardSurface
    )
}

@Composable
fun PendingFarmerRequestsSubScreen(
    authRepository: com.poultryguard.ai.data.repository.AuthRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
) {
    var pendingFarmers by remember { mutableStateOf<List<com.poultryguard.ai.data.api.PendingFarmerDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var selectedRequest by remember { mutableStateOf<com.poultryguard.ai.data.api.PendingFarmerDto?>(null) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectionReasonText by remember { mutableStateOf("") }
    
    fun loadRequests() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val res = authRepository.getPendingFarmers()
            res.fold(
                onSuccess = { list ->
                    pendingFarmers = list
                    isLoading = false
                },
                onFailure = { err ->
                    errorMessage = err.localizedMessage ?: "Failed loading requests"
                    isLoading = false
                }
            )
        }
    }
    
    LaunchedEffect(Unit) {
        loadRequests()
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GreenPrimary)
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage!!, color = AlertRed, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { loadRequests() }, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                        Text("Retry", color = Color.White)
                    }
                }
            }
        } else if (pendingFarmers.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No pending farmer registration requests.", color = TextMedium, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(pendingFarmers) { request ->
                    val farmName = request.farmMembers?.firstOrNull()?.farms?.name ?: "Unspecified Farm"
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedRequest = request },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = BorderStroke(1.dp, DividerColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(request.name, fontWeight = FontWeight.Bold, color = TextDark, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(request.email, color = TextMedium, fontSize = 13.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AlertOrange.copy(alpha = 0.1f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Pending", color = AlertOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = DividerColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Farm Name", style = Typography.labelMedium, color = TextMedium)
                                    Text(farmName, fontWeight = FontWeight.SemiBold, color = TextDark, fontSize = 14.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Joined", style = Typography.labelMedium, color = TextMedium)
                                    Text(request.joinDate.substringBefore("T"), fontWeight = FontWeight.SemiBold, color = TextDark, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Details Sheet Dialog
    selectedRequest?.let { request ->
        val farmName = request.farmMembers?.firstOrNull()?.farms?.name ?: "Unspecified Farm"
        AlertDialog(
            onDismissRequest = { selectedRequest = null },
            title = {
                Text(
                    text = "Review Registration Request",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Farmer Information",
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary,
                        fontSize = 14.sp
                    )
                    Column {
                        Text("Name: ${request.name}", color = TextDark, fontSize = 14.sp)
                        Text("Email: ${request.email}", color = TextDark, fontSize = 14.sp)
                        Text("Registered: ${request.joinDate}", color = TextMedium, fontSize = 12.sp)
                    }
                    Divider(color = DividerColor)
                    Text(
                        text = "Farm Information",
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary,
                        fontSize = 14.sp
                    )
                    Column {
                        Text("Farm Name: $farmName", color = TextDark, fontSize = 14.sp)
                        Text("Verification Status: PENDING_APPROVAL", color = TextDark, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val res = authRepository.reviewFarmer(request.id, "APPROVE")
                            res.fold(
                                onSuccess = {
                                    Toast.makeText(context, "Farmer Approved Successfully!", Toast.LENGTH_SHORT).show()
                                    selectedRequest = null
                                    loadRequests()
                                },
                                onFailure = { err ->
                                    Toast.makeText(context, "Approval failed: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Approve", color = Color.White)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selectedRequest = null }) {
                        Text("Cancel", color = TextMedium)
                    }
                    Button(
                        onClick = {
                            showRejectDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reject", color = Color.White)
                    }
                }
            }
        )
    }
    
    // Rejection Reason Prompt Dialog
    if (showRejectDialog && selectedRequest != null) {
        val request = selectedRequest!!
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = {
                Text("Enter Rejection Reason", fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = rejectionReasonText,
                    onValueChange = { rejectionReasonText = it },
                    placeholder = { Text("e.g. Invalid farm address, unverified documents...") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlertRed,
                        focusedLabelColor = AlertRed
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rejectionReasonText.isBlank()) {
                            Toast.makeText(context, "Rejection reason cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            val res = authRepository.reviewFarmer(request.id, "REJECT", rejectionReasonText)
                            res.fold(
                                onSuccess = {
                                    Toast.makeText(context, "Farmer Registration Rejected", Toast.LENGTH_SHORT).show()
                                    showRejectDialog = false
                                    selectedRequest = null
                                    rejectionReasonText = ""
                                    loadRequests()
                                },
                                onFailure = { err ->
                                    Toast.makeText(context, "Rejection failed: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Submit Rejection", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Cancel", color = TextMedium)
                }
            }
        )
    }
}
