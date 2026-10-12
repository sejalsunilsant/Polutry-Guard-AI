package com.poultryguard.ai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poultryguard.ai.data.model.SensorReading
import com.poultryguard.ai.data.model.SensorStatus
import com.poultryguard.ai.ui.theme.*

@Composable
fun SensorCard(
    reading: SensorReading,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (reading.status) {
        SensorStatus.IDEAL -> GreenPrimary
        SensorStatus.WARNING -> AlertOrange
        SensorStatus.CRITICAL -> AlertRed
    }

    val statusBg = when (reading.status) {
        SensorStatus.IDEAL -> GreenLight
        SensorStatus.WARNING -> Color(0xFFFFF3E0)
        SensorStatus.CRITICAL -> Color(0xFFFFEBEE)
    }

    val statusLabel = when (reading.status) {
        SensorStatus.IDEAL -> "Ideal"
        SensorStatus.WARNING -> "Warning"
        SensorStatus.CRITICAL -> "Critical"
    }

    val icon: ImageVector = when (reading.id) {
        "temperature" -> Icons.Default.Thermostat
        "humidity" -> Icons.Default.Opacity
        "ammonia" -> Icons.Default.Science
        "sound" -> Icons.Default.Hearing
        else -> Icons.Default.Thermostat
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Left color stripe indicating status
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(statusColor)
                    .align(Alignment.CenterStart)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header: Icon on left, Status Pill on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(statusBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = reading.name,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Status Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(statusBg)
                            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            style = Typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Sensor Name (Full card width to prevent clipping "Temperature" / "Humidity")
                Text(
                    text = reading.name,
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                // Core Value Display
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = if (reading.value != null) "%.1f".format(reading.value) else "--",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        lineHeight = 34.sp
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = reading.unit,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMedium,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}
