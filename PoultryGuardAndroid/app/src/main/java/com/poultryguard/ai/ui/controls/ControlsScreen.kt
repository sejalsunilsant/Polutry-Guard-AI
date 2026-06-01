package com.poultryguard.ai.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poultryguard.ai.ui.theme.*

@Composable
fun ControlsScreen(modifier: Modifier = Modifier) {
    var fanTempThreshold by remember { mutableStateOf(26f) }
    var brooderTempThreshold by remember { mutableStateOf(21f) }
    var automatedMistingEnabled by remember { mutableStateOf(true) }
    var lightningDuration by remember { mutableStateOf(16f) }

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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Controls",
                        tint = GreenPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hardware Adjustments",
                        style = Typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = GreenLight.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = GreenPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Modifications are automatically sent to IoT Gateway for Shed #4.",
                            style = Typography.bodyMedium,
                            color = GreenDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Exhaust Fan Threshold
            item {
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
                            Text(
                                text = "Exhaust Fans Start Point",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "${fanTempThreshold.toInt()}°C",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                        }
                        Text(
                            text = "Fans will kick on immediately once target broiler house exceeds this temperature.",
                            style = Typography.bodyMedium,
                            color = TextMedium,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        Slider(
                            value = fanTempThreshold,
                            onValueChange = { fanTempThreshold = it },
                            valueRange = 20f..35f,
                            colors = SliderDefaults.colors(
                                thumbColor = GreenPrimary,
                                activeTrackColor = GreenPrimary
                            )
                        )
                    }
                }
            }

            // Brooder Heater Threshold
            item {
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
                            Text(
                                text = "Heaters Cut-in Level",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "${brooderTempThreshold.toInt()}°C",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = AlertOrange
                            )
                        }
                        Text(
                            text = "Brooder heaters ignite if ambient thermometer dips below this to keep chicks warm.",
                            style = Typography.bodyMedium,
                            color = TextMedium,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        Slider(
                            value = brooderTempThreshold,
                            onValueChange = { brooderTempThreshold = it },
                            valueRange = 15f..28f,
                            colors = SliderDefaults.colors(
                                thumbColor = AlertOrange,
                                activeTrackColor = AlertOrange
                            )
                        )
                    }
                }
            }

            // Misting Toggles
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automated Cooling Misters",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "AI triggers fine mist nozzle spray during heat stress warning ranges.",
                                style = Typography.bodyMedium,
                                color = TextMedium
                            )
                        }
                        Switch(
                            checked = automatedMistingEnabled,
                            onCheckedChange = { automatedMistingEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BlueSecondary
                            )
                        )
                    }
                }
            }

            // Lighting Period
            item {
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
                            Text(
                                text = "Daily Photo-Period Hours",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "${lightningDuration.toInt()} Hours",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = BlueSecondary
                            )
                        }
                        Text(
                            text = "Light timeline schedules for optimal feeding cycles (Default 16 hours for broilers).",
                            style = Typography.bodyMedium,
                            color = TextMedium,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        Slider(
                            value = lightningDuration,
                            onValueChange = { lightningDuration = it },
                            valueRange = 8f..24f,
                            colors = SliderDefaults.colors(
                                thumbColor = BlueSecondary,
                                activeTrackColor = BlueSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}
