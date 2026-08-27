package com.poultryguard.ai.ui.batch

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poultryguard.ai.ui.dashboard.DashboardViewModel
import com.poultryguard.ai.ui.theme.*
import com.poultryguard.ai.ui.localization.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartBatchScreen(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var initialCountStr by remember { mutableStateOf("") }
    var selectedBreed by remember { mutableStateOf("Broiler") }
    var startDateStr by remember {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        mutableStateOf(today)
    }

    var dropdownExpanded by remember { mutableStateOf(false) }
    val breeds = listOf("Broiler", "Layer", "Cobb 500", "Ross 308", "Cobb 700")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource("start_batch"), fontWeight = FontWeight.Bold, color = TextDark) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Initial Count Input
                    OutlinedTextField(
                        value = initialCountStr,
                        onValueChange = { initialCountStr = it },
                        label = { Text(stringResource("initial_count")) },
                        placeholder = { Text("e.g. 5000") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary,
                            focusedLabelColor = GreenPrimary
                        ),
                        singleLine = true
                    )

                    // Breed Dropdown Selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedBreed,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource("breed")) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { dropdownExpanded = !dropdownExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Breed",
                                        tint = GreenPrimary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                focusedLabelColor = GreenPrimary
                            )
                        )
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(CardSurface)
                        ) {
                            breeds.forEach { breed ->
                                DropdownMenuItem(
                                    text = { Text(breed, color = TextDark) },
                                    onClick = {
                                        selectedBreed = breed
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Start Date Input
                    OutlinedTextField(
                        value = startDateStr,
                        onValueChange = { startDateStr = it },
                        label = { Text(stringResource("start_date") + " (yyyy-MM-dd)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Date",
                                tint = GreenPrimary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary,
                            focusedLabelColor = GreenPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Start Batch Submit Button
                    Button(
                        onClick = {
                            val count = initialCountStr.toIntOrNull()
                            if (count == null || count <= 0) {
                                Toast.makeText(context, "Please enter a valid bird count greater than 0", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            viewModel.startNewBatch(
                                startDate = startDateStr,
                                initialCount = count,
                                breed = selectedBreed,
                                onSuccess = {
                                    Toast.makeText(context, "Batch started successfully!", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = stringResource("start_batch"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
