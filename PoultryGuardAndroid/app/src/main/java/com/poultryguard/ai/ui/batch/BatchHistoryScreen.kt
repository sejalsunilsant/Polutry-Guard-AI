package com.poultryguard.ai.ui.batch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poultryguard.ai.data.model.Batch
import com.poultryguard.ai.data.model.BatchStatus
import com.poultryguard.ai.data.model.ageDays
import com.poultryguard.ai.data.repository.BatchRepository
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.data.cache.AppDatabase
import com.poultryguard.ai.ui.theme.*
import com.poultryguard.ai.ui.localization.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cacheManager = remember { LocalCacheManager(context.applicationContext) }
    val db = remember { AppDatabase.getDatabase(context.applicationContext) }
    val batchRepository = remember { BatchRepository(context) }
    var historyList by remember { mutableStateOf(emptyList<Batch>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val user = cacheManager.getCachedUserProfile()
        val farmerProfile = user?.email?.let { db.farmerProfileDao().getFarmerByEmail(it) }
        val farmerId = farmerProfile?.id ?: user?.uid ?: ""
        
        val result = batchRepository.getAllBatches(farmerId)
        result.onSuccess { list ->
            historyList = list
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource("batch_history"), fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = GreenPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = GreenPrimary
                )
            } else if (historyList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "No History",
                        tint = TextMedium,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No batch records found.",
                        color = TextMedium,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(historyList) { batch ->
                        BatchHistoryItem(batch = batch)
                    }
                }
            }
        }
    }
}

@Composable
fun BatchHistoryItem(batch: Batch, modifier: Modifier = Modifier) {
    val statusColor = when (batch.status) {
        BatchStatus.ACTIVE -> GreenPrimary
        BatchStatus.SOLD -> Color(0xFF008080) // Teal
        BatchStatus.CLOSED -> TextMedium
    }
    
    val statusBg = when (batch.status) {
        BatchStatus.ACTIVE -> GreenLight
        BatchStatus.SOLD -> Color(0xFFE0F2F1)
        BatchStatus.CLOSED -> AppBackground
    }

    val mortRate = if (batch.initialCount > 0) {
        ((batch.initialCount - batch.currentCount).toFloat() / batch.initialCount * 100).coerceAtLeast(0f)
    } else 0f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = batch.id,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                
                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = batch.status.name,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${stringResource("breed")}: ${batch.breed} • ${stringResource("days_active")}: ${batch.ageDays}",
                fontSize = 14.sp,
                color = TextMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = AppBackground, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource("initial_count"),
                        style = Typography.labelMedium,
                        color = TextMedium
                    )
                    Text(
                        text = batch.initialCount.toString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
                Column {
                    Text(
                        text = stringResource("current_count"),
                        style = Typography.labelMedium,
                        color = TextMedium
                    )
                    Text(
                        text = batch.currentCount.toString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
                Column {
                    Text(
                        text = stringResource("mortality_rate"),
                        style = Typography.labelMedium,
                        color = TextMedium
                    )
                    Text(
                        text = "%.2f%%".format(mortRate),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mortRate > 10.0f) AlertRed else AlertOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            // Date info
            Text(
                text = "${stringResource("start_date")}: ${batch.startDate}" + 
                       (batch.endDate?.let { " • ${stringResource("end_date")}: $it" } ?: ""),
                fontSize = 12.sp,
                color = TextMedium
            )
        }
    }
}
