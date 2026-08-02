package com.poultryguard.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import com.poultryguard.ai.ui.theme.*

data class MonthlyHealthDataPoint(
    val dateStr: String,
    val healthRate: Float, // e.g. 99.85f (%)
    val deathCount: Int,
    val diseaseCases: Int,
    val temperature: Float,
    val humidity: Float,
    val ammonia: Float,
    val sound: Float
)

// 1D Line Chart plotting daily broiler wellness ratios
@Composable
fun FlockHealthLineChart(
    weeklyRates: List<Float>, // health percentages e.g. [100f, 99.98f, 99.95f, 99.95f, 99.92f, 99.88f, 99.88f]
    modifier: Modifier = Modifier
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "1D Broiler Wellness Index",
                style = Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                text = "Flock health rate (%) over the last 7 days",
                style = Typography.labelMedium,
                color = TextMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.Transparent)
            ) {
                val paddingLeft = 40.dp.toPx()
                val paddingBottom = 20.dp.toPx()
                val chartWidth = size.width - paddingLeft
                val chartHeight = size.height - paddingBottom

                val maxRate = 100f
                val minRate = 99.8f
                val range = maxRate - minRate

                // Draw Grid Lines (Y axis milestones)
                val gridLines = 4
                for (i in 0..gridLines) {
                    val yVal = minRate + (range * i / gridLines)
                    val yPos = chartHeight - (chartHeight * i / gridLines)
                    drawLine(
                        color = DividerColor.copy(alpha = 0.5f),
                        start = Offset(paddingLeft, yPos),
                        end = Offset(size.width, yPos),
                        strokeWidth = 1f
                    )
                }

                // Trace Health Line Path
                val points = weeklyRates.mapIndexed { index, rate ->
                    val x = paddingLeft + (chartWidth * index / (weeklyRates.size - 1))
                    val normalizedRate = (rate - minRate) / range
                    val y = chartHeight - (chartHeight * normalizedRate)
                    Offset(x, y)
                }

                val linePath = Path().apply {
                    if (points.isNotEmpty()) {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            // Smooth cubic spline curves
                            cubicTo(
                                (prev.x + curr.x) / 2f, prev.y,
                                (prev.x + curr.x) / 2f, curr.y,
                                curr.x, curr.y
                            )
                        }
                    }
                }

                // Shaded gradient fill underneath health path
                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(points.last().x, chartHeight)
                    lineTo(points.first().x, chartHeight)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(GreenPrimary.copy(alpha = 0.25f), Color.Transparent)
                    )
                )

                drawPath(
                    path = linePath,
                    color = GreenPrimary,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Plot glowing telemetry points
                points.forEachIndexed { index, point ->
                    drawCircle(
                        color = GreenPrimary,
                        radius = 4.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = point
                    )
                }
            }

            // Days Legend Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { day ->
                    Text(
                        text = day,
                        fontSize = 10.sp,
                        color = TextMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// 2D Scatter Plot cross-correlating Ammonia and Temperature Swings
@Composable
fun AmmoniaTempScatterPlot(
    scatterPoints: List<Pair<Float, Float>>, // list of Pair(temp, ammonia)
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "2D Gas & Heat Correlation",
                style = Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                text = "Ammonia Gas (ppm) vs Temperature (°C) daily scatter plot",
                style = Typography.labelMedium,
                color = TextMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color.Transparent)
            ) {
                val paddingLeft = 40.dp.toPx()
                val paddingBottom = 20.dp.toPx()
                val chartWidth = size.width - paddingLeft
                val chartHeight = size.height - paddingBottom

                val maxTemp = 34f
                val minTemp = 20f
                val maxAmmonia = 40f
                val minAmmonia = 0f

                // Draw Ammonia Threshold Dotted Danger Line (20 ppm limit)
                val ammoniaLimitY = chartHeight - (chartHeight * (20f - minAmmonia) / (maxAmmonia - minAmmonia))
                drawLine(
                    color = AlertOrange.copy(alpha = 0.5f),
                    start = Offset(paddingLeft, ammoniaLimitY),
                    end = Offset(size.width, ammoniaLimitY),
                    strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Draw Temp Warning Dotted Danger Line (28°C limit)
                val tempLimitX = paddingLeft + (chartWidth * (28f - minTemp) / (maxTemp - minTemp))
                drawLine(
                    color = AlertOrange.copy(alpha = 0.5f),
                    start = Offset(tempLimitX, 0f),
                    end = Offset(tempLimitX, chartHeight),
                    strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Plot Y axis indicators (Ammonia)
                val ySteps = 4
                for (i in 0..ySteps) {
                    val ammoniaVal = minAmmonia + ((maxAmmonia - minAmmonia) * i / ySteps)
                    val yPos = chartHeight - (chartHeight * i / ySteps)
                    drawLine(
                        color = DividerColor.copy(alpha = 0.4f),
                        start = Offset(paddingLeft, yPos),
                        end = Offset(size.width, yPos),
                        strokeWidth = 1f
                    )
                }

                // Plot 2D scatter points
                scatterPoints.forEach { (temp, ammonia) ->
                    val x = paddingLeft + (chartWidth * (temp - minTemp) / (maxTemp - minTemp))
                    val y = chartHeight - (chartHeight * (ammonia - minAmmonia) / (maxAmmonia - minAmmonia))

                    // If point falls inside high-risk quadrant (Temp > 28 and Ammonia > 20) -> Red Alert
                    val isDanger = temp >= 28f || ammonia >= 20f
                    val dotColor = if (temp >= 28f && ammonia >= 20f) AlertRed 
                                   else if (isDanger) AlertOrange 
                                   else GreenPrimary

                    // Draw glowing highlight rings
                    drawCircle(
                        color = dotColor.copy(alpha = 0.25f),
                        radius = 8.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = dotColor,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 1.5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }

            // Legend indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Y-Axis: Ammonia (0-40 ppm)", fontSize = 9.sp, color = TextMedium, fontWeight = FontWeight.Bold)
                Text(text = "X-Axis: Temp (20°C - 34°C)", fontSize = 9.sp, color = TextMedium, fontWeight = FontWeight.Bold)
                
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AlertRed))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Danger Quadrant", fontSize = 8.sp, color = TextMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyHealthReportChart(
    dataPoints: List<MonthlyHealthDataPoint>,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) return

    val weeks = dataPoints.map { it.dateStr }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly Health Report",
                style = Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                text = "Overall health rate (%) vs date (Tap points for details)",
                style = Typography.labelMedium,
                color = TextMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .onSizeChanged { chartSize = it }
                    .pointerInput(dataPoints) {
                        detectTapGestures { offset ->
                            val paddingLeft = 40.dp.toPx()
                            val chartWidth = chartSize.width - paddingLeft
                            if (dataPoints.size > 1 && chartWidth > 0) {
                                var closestIndex = 0
                                var minDistance = Float.MAX_VALUE
                                for (i in dataPoints.indices) {
                                    val x = paddingLeft + (chartWidth * i / (dataPoints.size - 1))
                                    val dist = abs(offset.x - x)
                                    if (dist < minDistance) {
                                        minDistance = dist
                                        closestIndex = i
                                    }
                                }
                                selectedIndex = if (selectedIndex == closestIndex) null else closestIndex
                            } else if (dataPoints.size == 1) {
                                selectedIndex = if (selectedIndex == 0) null else 0
                            }
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                    val paddingLeft = 40.dp.toPx()
                    val paddingBottom = 20.dp.toPx()
                    val chartWidth = size.width - paddingLeft
                    val chartHeight = size.height - paddingBottom

                    val maxRate = 100f
                    val minRate = (dataPoints.minOfOrNull { it.healthRate } ?: 95f).coerceAtMost(99.0f) - 0.5f
                    val range = maxRate - minRate

                    // Draw Grid Lines (Y axis milestones)
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val yPos = chartHeight - (chartHeight * i / gridLines)
                        drawLine(
                            color = DividerColor.copy(alpha = 0.5f),
                            start = Offset(paddingLeft, yPos),
                            end = Offset(size.width, yPos),
                            strokeWidth = 1f
                        )
                    }

                    // Calculate point positions
                    val points = dataPoints.mapIndexed { index, point ->
                        val x = if (dataPoints.size > 1) {
                            paddingLeft + (chartWidth * index / (dataPoints.size - 1))
                        } else {
                            paddingLeft + chartWidth / 2f
                        }
                        val normalizedRate = if (range > 0f) {
                            (point.healthRate - minRate) / range
                        } else {
                            1f
                        }
                        val y = chartHeight - (chartHeight * normalizedRate.coerceIn(0f, 1f))
                        Offset(x, y)
                    }

                    // Draw vertical dashed guideline if a point is selected
                    selectedIndex?.let { index ->
                        if (index in points.indices) {
                            val selectedPoint = points[index]
                            drawLine(
                                color = TextMedium.copy(alpha = 0.6f),
                                start = Offset(selectedPoint.x, 0f),
                                end = Offset(selectedPoint.x, chartHeight),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                    }

                    // Draw Line Path
                    val linePath = Path().apply {
                        if (points.isNotEmpty()) {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val curr = points[i]
                                cubicTo(
                                    (prev.x + curr.x) / 2f, prev.y,
                                    (prev.x + curr.x) / 2f, curr.y,
                                    curr.x, curr.y
                                )
                            }
                        }
                    }

                    if (points.isNotEmpty()) {
                        // Shaded gradient fill underneath path
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(points.last().x, chartHeight)
                            lineTo(points.first().x, chartHeight)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(GreenPrimary.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = GreenPrimary,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Draw points
                        points.forEachIndexed { index, point ->
                            val isSelected = index == selectedIndex
                            val dotColor = if (isSelected) AlertOrange else GreenPrimary
                            val outerRadius = if (isSelected) 6.dp.toPx() else 4.dp.toPx()
                            val innerRadius = if (isSelected) 3.dp.toPx() else 2.dp.toPx()

                            drawCircle(
                                color = dotColor,
                                radius = outerRadius,
                                center = point
                            )
                            drawCircle(
                                color = Color.White,
                                radius = innerRadius,
                                center = point
                            )
                        }
                    }
                }

                // Render Tooltip Card Overlay near selected point
                selectedIndex?.let { index ->
                    if (index in dataPoints.indices) {
                        val point = dataPoints[index]
                        
                        // Calculate positions in DP safely
                        val paddingLeftPx = with(density) { 40.dp.toPx() }
                        val chartWidthPx = chartSize.width - paddingLeftPx
                        val chartHeightPx = chartSize.height - with(density) { 20.dp.toPx() }
                        
                        val maxRate = 100f
                        val minRate = (dataPoints.minOfOrNull { it.healthRate } ?: 95f).coerceAtMost(99.0f) - 0.5f
                        val range = maxRate - minRate
                        
                        val xPx = if (dataPoints.size > 1) {
                            paddingLeftPx + (chartWidthPx * index / (dataPoints.size - 1))
                        } else {
                            paddingLeftPx + chartWidthPx / 2f
                        }
                        
                        val normalizedRate = if (range > 0f) {
                            (point.healthRate - minRate) / range
                        } else {
                            1f
                        }
                        val yPx = chartHeightPx - (chartHeightPx * normalizedRate.coerceIn(0f, 1f))
                        
                        val xDpValue = with(density) { xPx.toFloat().toDp().value }
                        val yDpValue = with(density) { yPx.toFloat().toDp().value }
                        val chartWidthDpValue = with(density) { chartSize.width.toFloat().toDp().value }
                        
                        // Tooltip dimensions: width 210.dp, height approx 100.dp
                        val tooltipWidthValue = 210f
                        val tooltipHeightValue = 100f
                        
                        val xOffsetValue = (xDpValue - tooltipWidthValue / 2f).coerceIn(8f, chartWidthDpValue - tooltipWidthValue - 8f)
                        // Place it above the point if possible, otherwise below it
                        val yOffsetValue = if (yDpValue - tooltipHeightValue > 4f) {
                            yDpValue - tooltipHeightValue - 6f
                        } else {
                            yDpValue + 8f
                        }

                        Card(
                            modifier = Modifier
                                .width(210.dp)
                                .offset(x = xOffsetValue.dp, y = yOffsetValue.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            border = BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = point.dateStr,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                                androidx.compose.material3.HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = "Health: ${"%.2f%%".format(point.healthRate)}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDark
                                        )
                                        Text(
                                            text = "Deaths: ${point.deathCount}",
                                            fontSize = 9.sp,
                                            color = TextMedium
                                        )
                                        Text(
                                            text = "Disease: ${point.diseaseCases} cases",
                                            fontSize = 9.sp,
                                            color = TextMedium
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1.5f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "Temp: ${"%.1f".format(point.temperature)}°C",
                                            fontSize = 9.sp,
                                            color = TextMedium
                                        )
                                        Text(
                                            text = "Humid: ${"%.1f".format(point.humidity)}%",
                                            fontSize = 9.sp,
                                            color = TextMedium
                                        )
                                        Text(
                                            text = "Ammonia: ${"%.1f".format(point.ammonia)} ppm",
                                            fontSize = 9.sp,
                                            color = TextMedium
                                        )
                                        Text(
                                            text = "Sound: ${"%.1f".format(point.sound)} dB",
                                            fontSize = 9.sp,
                                            color = TextMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Weeks/Dates Legend Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weeks.forEach { week ->
                    Text(
                        text = week,
                        fontSize = 10.sp,
                        color = TextMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Unified Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GreenPrimary))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Overall Health Rate", fontSize = 10.sp, color = TextMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
