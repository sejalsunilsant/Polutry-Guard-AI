package com.poultryguard.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import com.poultryguard.ai.ui.theme.*

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
