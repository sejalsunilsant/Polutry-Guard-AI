package com.poultryguard.ai.ui.admin

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.data.model.HardwareKit
import com.poultryguard.ai.data.model.SystemStats
import com.poultryguard.ai.ui.theme.*
import kotlinx.coroutines.launch

data class IoTNode(
    val id: String,
    val battery: String,
    val rssi: String,
    val status: String
)

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

    val cacheManager = remember { LocalCacheManager(context) }

    // Seed mock data for demonstration if empty
    var kitsList by remember {
        val cached = cacheManager.getHardwareKits()
        if (cached.isEmpty()) {
            val defaults = listOf(
                HardwareKit(
                    kitId = "PG-KIT-00045",
                    gatewayId = "ESP32-0045",
                    serialNumber = "PGESP0045",
                    firmwareVersion = "1.2.0",
                    lifecycleStatus = "Available",
                    hasTempSensor = true,
                    hasHumidSensor = true,
                    hasAmmoniaSensor = true,
                    hasSoundSensor = true,
                    hasCameraSensor = true,
                    farmerName = "",
                    farmName = "",
                    lastCommunication = "Offline (No data logged)"
                ),
                HardwareKit(
                    kitId = "PG-KIT-00021",
                    gatewayId = "ESP32-0021",
                    serialNumber = "PGESP0021",
                    firmwareVersion = "1.1.5",
                    lifecycleStatus = "Active",
                    hasTempSensor = true,
                    hasHumidSensor = true,
                    hasAmmoniaSensor = true,
                    hasSoundSensor = true,
                    hasCameraSensor = false,
                    farmerName = "Joe Patterson",
                    farmName = "Shed #4 (Broilers)",
                    lastCommunication = "Active (10 seconds ago)"
                ),
                HardwareKit(
                    kitId = "PG-KIT-00012",
                    gatewayId = "ESP32-0012",
                    serialNumber = "PGESP0012",
                    firmwareVersion = "1.0.8",
                    lifecycleStatus = "Maintenance",
                    hasTempSensor = true,
                    hasHumidSensor = true,
                    hasAmmoniaSensor = false,
                    hasSoundSensor = true,
                    hasCameraSensor = false,
                    farmerName = "Joe Patterson",
                    farmName = "Shed #2 (Breeders)",
                    lastCommunication = "Offline (5 days ago)"
                )
            )
            cacheManager.saveHardwareKits(defaults)
            mutableStateOf(defaults)
        } else {
            mutableStateOf(cached)
        }
    }

    var selectedTab by remember { mutableStateOf(0) }

    // Dialog state variables
    var showAddKitDialog by remember { mutableStateOf(false) }
    var activeAssignKit by remember { mutableStateOf<HardwareKit?>(null) }
    var activeFirmwareKit by remember { mutableStateOf<HardwareKit?>(null) }
    var activeReplaceKit by remember { mutableStateOf<HardwareKit?>(null) }

    // Search & Filter state
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }

    val nodes = listOf(
        IoTNode("Node #4A (Temp/Humid)", "98%", "-54 dBm", "ONLINE"),
        IoTNode("Node #4B (Ammonia)", "94%", "-62 dBm", "ONLINE"),
        IoTNode("Node #4C (Sound Mic)", "85%", "-59 dBm", "ONLINE")
    )

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
                    Text(
                        text = "Superintendent Console",
                        style = Typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
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

            // Tabs Layout (Health vs Kits)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = GreenPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = GreenPrimary
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Health & Approvals", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Device Kit Management", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Renders selected tab screen
            when (selectedTab) {
                0 -> {
                    // IoT Node Health and approvals list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
                    ) {
                        // System KPI Cards
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1.5f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = "IoT Gateways Status", style = Typography.labelMedium, color = TextMedium)
                                        Text(
                                            text = "6 / 6 Active",
                                            style = Typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDark
                                        )
                                        Text(text = "All systems reporting OK", style = Typography.labelMedium, color = GreenPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(text = "Server Sync Latency", style = Typography.labelMedium, color = TextMedium)
                                        Text(
                                            text = "45 ms",
                                            style = Typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDark
                                        )
                                        Text(text = "Live Broker connection", style = Typography.labelMedium, color = GreenPrimary)
                                    }
                                }
                            }
                        }

                        // Pending Approvals Widget
                        item {
                            Text(
                                text = "Pending Staff Approvals",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    StaffApprovalRow(
                                        name = "Alex Mercer",
                                        requestRole = "Farmer Assistant",
                                        email = "alex.m@farmsecure.net"
                                    )
                                    Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                                    StaffApprovalRow(
                                        name = "Dr. Linda Croft",
                                        requestRole = "Consulting Veterinarian",
                                        email = "linda.c@poultryhealth.org"
                                    )
                                }
                            }
                        }

                        // Node health list
                        item {
                            Text(
                                text = "IoT Shed Wireless Node Health",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextDark,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(nodes.size) { index ->
                            val node = nodes[index]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(GreenLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Router,
                                                contentDescription = "IoT Node",
                                                tint = GreenPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = node.id,
                                                style = Typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = TextDark
                                            )
                                            Text(
                                                text = "RF Strength: ${node.rssi} • Batt: ${node.battery}",
                                                style = Typography.labelMedium,
                                                color = TextMedium
                                            )
                                        }
                                    }
                                    Text(
                                        text = node.status,
                                        style = Typography.labelMedium,
                                        color = GreenPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
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
                                placeholder = { Text("Search by Kit / Device ID...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    focusedLabelColor = GreenPrimary
                                )
                            )

                            // Simple lifecycle filter dropdown menu trigger
                            var showFilterMenu by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { showFilterMenu = true },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (statusFilter != "All") GreenPrimary else DividerColor),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (statusFilter != "All") GreenPrimary else TextMedium
                                    )
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filter")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(statusFilter)
                                }
                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false }
                                ) {
                                    val filters = listOf("All", "Manufactured", "Available", "Assigned to Farmer", "Installed", "Active", "Maintenance", "Retired")
                                    filters.forEach { filterOpt ->
                                        DropdownMenuItem(
                                            text = { Text(filterOpt) },
                                            onClick = {
                                                statusFilter = filterOpt
                                                showFilterMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Filter and search computation
                        val filteredKits = kitsList.filter { kit ->
                            val matchesSearch = kit.kitId.contains(searchQuery, ignoreCase = true) ||
                                    kit.gatewayId.contains(searchQuery, ignoreCase = true) ||
                                    kit.serialNumber.contains(searchQuery, ignoreCase = true)
                            val matchesFilter = statusFilter == "All" || kit.lifecycleStatus == statusFilter
                            matchesSearch && matchesFilter
                        }

                        if (filteredKits.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No device kits registered matching criteria.",
                                    color = TextMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(filteredKits) { kit ->
                                    AdminHardwareKitCard(
                                        kit = kit,
                                        onAssignClick = { activeAssignKit = kit },
                                        onUnassignClick = {
                                            // Unassign action
                                            val updatedList = kitsList.map { k ->
                                                if (k.kitId == kit.kitId) {
                                                    k.copy(
                                                        lifecycleStatus = "Available",
                                                        farmerName = "",
                                                        farmName = ""
                                                    )
                                                } else k
                                            }
                                            kitsList = updatedList
                                            cacheManager.saveHardwareKits(updatedList)
                                            Toast.makeText(context, "Kit ${kit.kitId} unassigned from farmer.", Toast.LENGTH_SHORT).show()
                                        },
                                        onMaintenanceClick = {
                                            val updatedList = kitsList.map { k ->
                                                if (k.kitId == kit.kitId) {
                                                    k.copy(lifecycleStatus = "Maintenance")
                                                } else k
                                            }
                                            kitsList = updatedList
                                            cacheManager.saveHardwareKits(updatedList)
                                            Toast.makeText(context, "Kit ${kit.kitId} marked in Maintenance status.", Toast.LENGTH_SHORT).show()
                                        },
                                        onRetireClick = {
                                            val updatedList = kitsList.map { k ->
                                                if (k.kitId == kit.kitId) {
                                                    k.copy(lifecycleStatus = "Retired")
                                                } else k
                                            }
                                            kitsList = updatedList
                                            cacheManager.saveHardwareKits(updatedList)
                                            Toast.makeText(context, "Kit ${kit.kitId} retired successfully.", Toast.LENGTH_SHORT).show()
                                        },
                                        onFirmwareClick = { activeFirmwareKit = kit },
                                        onReplaceClick = { activeReplaceKit = kit }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Kit Dialog Layout
        if (showAddKitDialog) {
            AddKitDialog(
                onDismiss = { showAddKitDialog = false },
                onAddKit = { newKit ->
                    val updated = kitsList.toMutableList().apply { add(newKit) }
                    kitsList = updated
                    cacheManager.saveHardwareKits(updated)
                    showAddKitDialog = false
                    Toast.makeText(context, "Successfully registered Device Kit ${newKit.kitId}!", Toast.LENGTH_SHORT).show()
                },
                nextSuggestedId = "PG-KIT-000${kitsList.size + 46}"
            )
        }

        // Assign to Farmer Dialog Layout
        activeAssignKit?.let { kit ->
            AssignKitDialog(
                kit = kit,
                onDismiss = { activeAssignKit = null },
                onAssign = { farmer, farm ->
                    val updatedList = kitsList.map { k ->
                        if (k.kitId == kit.kitId) {
                            k.copy(
                                farmerName = farmer,
                                farmName = farm,
                                lifecycleStatus = "Active"
                            )
                        } else k
                    }
                    kitsList = updatedList
                    cacheManager.saveHardwareKits(updatedList)
                    activeAssignKit = null
                    Toast.makeText(context, "Kit ${kit.kitId} assigned to $farmer at $farm.", Toast.LENGTH_SHORT).show()
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
                    activeReplaceKit = null
                    Toast.makeText(context, "Kit ${kit.kitId} hardware replaced with ESP32 node $newDeviceId.", Toast.LENGTH_SHORT).show()
                }
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
    nextSuggestedId: String
) {
    var kitId by remember { mutableStateOf(nextSuggestedId) }
    var deviceId by remember { mutableStateOf("ESP32-00" + nextSuggestedId.takeLast(2)) }
    var serialNumber by remember { mutableStateOf("PGESP0" + nextSuggestedId.takeLast(2)) }
    var firmwareVersion by remember { mutableStateOf("1.2.0") }

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

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    .heightIn(max = 400.dp)
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
                        lastCommunication = "Offline (Warehouse Inventory)"
                    )
                    onAddKit(kit)
                }
            ) {
                Text("Register Kit", color = Color.White)
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
    onDismiss: () -> Unit,
    onAssign: (String, String) -> Unit
) {
    var farmerName by remember { mutableStateOf("") }
    var farmName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AssignmentInd, contentDescription = "Assign", tint = GreenPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Assign Kit to Farm", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Target Kit: ${kit.kitId}", style = Typography.bodyMedium, color = TextMedium)

                OutlinedTextField(
                    value = farmerName,
                    onValueChange = { farmerName = it },
                    label = { Text("Farmer Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = farmName,
                    onValueChange = { farmName = it },
                    label = { Text("Farm / Shed Identifier") },
                    singleLine = true,
                    placeholder = { Text("e.g. Shed #4 Broilers") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                onClick = {
                    if (farmerName.isBlank() || farmName.isBlank()) return@Button
                    onAssign(farmerName, farmName)
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
