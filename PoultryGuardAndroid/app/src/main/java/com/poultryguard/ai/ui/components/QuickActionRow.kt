package com.poultryguard.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDamage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poultryguard.ai.ui.theme.*

@Composable
fun QuickActionRow(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Environmental Controls",
            style = Typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val fanState = remember { mutableStateOf(true) }
            val heaterState = remember { mutableStateOf(false) }
            val sprinklerState = remember { mutableStateOf(false) }
            val lightState = remember { mutableStateOf(true) }

            DeviceToggleCard(
                name = "Exhaust Fans",
                icon = Icons.Default.Air,
                isActive = fanState.value,
                onToggle = { fanState.value = !fanState.value },
                activeColor = GreenPrimary,
                modifier = Modifier.weight(1f)
            )

            DeviceToggleCard(
                name = "Brooder Heater",
                icon = Icons.Default.LocalFireDepartment,
                isActive = heaterState.value,
                onToggle = { heaterState.value = !heaterState.value },
                activeColor = AlertOrange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sprinklerState = remember { mutableStateOf(false) }
            val lightState = remember { mutableStateOf(true) }

            DeviceToggleCard(
                name = "Cooling Misters",
                icon = Icons.Default.WaterDamage,
                isActive = sprinklerState.value,
                onToggle = { sprinklerState.value = !sprinklerState.value },
                activeColor = BlueSecondary,
                modifier = Modifier.weight(1f)
            )

            DeviceToggleCard(
                name = "Shed Lights",
                icon = Icons.Default.Lightbulb,
                isActive = lightState.value,
                onToggle = { lightState.value = !lightState.value },
                activeColor = Color(0xFFFFB300),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DeviceToggleCard(
    name: String,
    icon: ImageVector,
    isActive: Boolean,
    onToggle: () -> Unit,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isActive) activeColor.copy(alpha = 0.08f) else CardSurface
    val borderCol = if (isActive) activeColor.copy(alpha = 0.3f) else DividerColor

    Card(
        onClick = onToggle,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(borderCol)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Background circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor.copy(alpha = 0.15f) else AppBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = if (isActive) activeColor else TextMedium,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = name,
                    style = Typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    maxLines = 1
                )
                Text(
                    text = if (isActive) "Active" else "Idle",
                    style = Typography.labelMedium,
                    color = if (isActive) activeColor else TextMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
