package com.poultryguard.ai.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poultryguard.ai.ui.theme.*

data class IoTNode(
    val id: String,
    val battery: String,
    val rssi: String,
    val status: String
)

@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nodes = listOf(
        IoTNode("Node #4A (Temp/Humid)", "98%", "-54 dBm", "ONLINE"),
        IoTNode("Node #4B (Ammonia)", "94%", "-62 dBm", "ONLINE"),
        IoTNode("Node #4C (Sound Mic)", "85%", "-59 dBm", "ONLINE")
    )

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "6 / 6 Active",
                                    style = Typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                            }
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

            // Active Staff Requests Widget
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

            // IoT Telemetry Node Health list
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
}

@Composable
fun StaffApprovalRow(
    name: String,
    requestRole: String,
    email: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = name,
                style = Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                text = "Requests: $requestRole • $email",
                style = Typography.labelMedium,
                color = TextMedium
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(GreenLight)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Approve",
                    tint = GreenPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEBEE))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Reject",
                    tint = AlertRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
