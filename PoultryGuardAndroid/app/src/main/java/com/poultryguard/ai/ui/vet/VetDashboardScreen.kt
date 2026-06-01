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
import androidx.compose.runtime.Composable
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
import com.poultryguard.ai.ui.theme.*

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
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
                                color = GreenPrimary,
                                topLeft = Offset(20.dp.toPx(), 20.dp.toPx()),
                                size = Size(shedWidth, shedHeight)
                            )

                            // Shed #2 (Healthy)
                            drawRect(
                                color = GreenPrimary,
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
                                color = GreenPrimary,
                                topLeft = Offset(110.dp.toPx(), 80.dp.toPx()),
                                size = Size(shedWidth, shedHeight)
                            )
                            drawRect(
                                color = GreenPrimary,
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
                            MapLegendItem("Healthy", GreenPrimary)
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
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BlueSecondary)
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = "SMS", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SMS Alert", fontSize = 13.sp, color = Color.White)
                            }

                            Button(
                                onClick = { triggerEmail() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = "Email", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Email Sweep", fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
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
                    else -> GreenPrimary
                }
                val statusBg = when (anomaly.severity) {
                    "CRITICAL" -> Color(0xFFFFEBEE)
                    "ATTENTION" -> Color(0xFFFFF3E0)
                    else -> GreenLight
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
                            statusColor = GreenPrimary
                        )
                        Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                        VaccineRow(
                            day = "Day 14",
                            name = "Infectious Bursal (Gumboro) Vaccine",
                            status = "COMPLETED",
                            statusColor = GreenPrimary
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
