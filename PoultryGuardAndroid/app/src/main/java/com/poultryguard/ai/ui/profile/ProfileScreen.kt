package com.poultryguard.ai.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.ui.theme.*

@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    onLogout: () -> Unit,
    vetRepository: com.poultryguard.ai.data.repository.VetRepository,
    onNavigateToHardwareConfig: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val veterinariansState = vetRepository.getVeterinariansFlow().collectAsState(initial = emptyList())
    val veterinarians = veterinariansState.value
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Card Header
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(GreenPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val firstLetter = if (userProfile.name.isNotEmpty()) userProfile.name.take(1).uppercase() else "F"
                        Text(
                            text = firstLetter,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = userProfile.name,
                        style = Typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Primary Owner & Operator • Shed 1-6",
                        style = Typography.bodyMedium,
                        color = TextMedium
                    )
                }
            }

            // Contact Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Contact Details",
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ContactItem(
                            icon = Icons.Default.Email,
                            label = "Email Address",
                            value = userProfile.email
                        )
                        Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                        ContactItem(
                            icon = Icons.Default.Phone,
                            label = "SMS Alarm Number",
                            value = "+1 (555) 382-7492"
                        )
                        Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                        ContactItem(
                            icon = Icons.Default.LocationOn,
                            label = "Geographical Region",
                            value = "Midwest Broiler Belt, Sect-4"
                        )
                    }
                }
            }

            // Quick App Actions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Security & Integrations",
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ProfileSettingRow(
                            icon = Icons.Default.Security,
                            title = "IoT Gateway Cryptographic Keys",
                            subtitle = "Authorized firmware connection",
                            onClick = onNavigateToHardwareConfig
                        )
                        Divider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                        ProfileSettingRow(
                            icon = Icons.Default.Person,
                            title = "Sub-Farmer Access Logs",
                            subtitle = "Manage permissions for shift handlers"
                        )
                    }
                }
            }

            // Health Mentors Section
            item {
                Text(
                    text = "Health Mentors",
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (veterinarians.isEmpty()) {
                item {
                    Text(
                        text = "No registered veterinarians found.",
                        style = Typography.bodyMedium,
                        color = TextMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(veterinarians.size) { index ->
                    val vet = veterinarians[index]
                    val statusColor = when (vet.availability) {
                        "Available" -> Color(0xFF4CAF50)
                        "Busy" -> AlertOrange
                        else -> AlertRed
                    }
                    val context = LocalContext.current

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(GreenPrimary.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val initials = if (vet.name.startsWith("Dr. ")) {
                                    vet.name.substring(4).take(2).uppercase()
                                } else {
                                    vet.name.take(2).uppercase()
                                }
                                Text(
                                    text = initials,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vet.name,
                                    style = Typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Text(
                                    text = vet.specialty,
                                    fontSize = 11.sp,
                                    color = TextMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                    )
                                    Text(
                                        text = vet.availability,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                    Text(
                                        text = "• ${vet.location}",
                                        fontSize = 10.sp,
                                        color = TextMedium
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${vet.phone}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(GreenPrimary.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Call veterinarian",
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Flask API Configuration Card
            item {
                val context = LocalContext.current
                val cacheManager = remember { LocalCacheManager(context) }
                var urlText by remember { mutableStateOf(cacheManager.getApiBaseUrl()) }
                var isEditing by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Flask API Configuration",
                            style = Typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        if (isEditing) {
                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                label = { Text("Base API URL") },
                                placeholder = { Text("e.g. http://10.0.2.2:5000/") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    unfocusedBorderColor = DividerColor,
                                    focusedLabelColor = GreenPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    urlText = cacheManager.getApiBaseUrl()
                                    isEditing = false
                                }) {
                                    Text("Cancel", color = TextMedium)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        cacheManager.saveApiBaseUrl(urlText)
                                        // Refresh the text view in case formatting added trailing slash
                                        urlText = cacheManager.getApiBaseUrl()
                                        isEditing = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Save", color = Color.White)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Base Server URL",
                                        style = Typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = urlText,
                                        style = Typography.bodyMedium,
                                        color = TextMedium
                                    )
                                }
                                TextButton(
                                    onClick = { isEditing = true }
                                ) {
                                    Text("Edit", color = GreenPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Clean, red-themed Sign Out Button
            item {
                Button(
                    onClick = onLogout,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Sign Out Account",
                        color = AlertRed,
                        style = Typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ContactItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AppBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = GreenPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = Typography.labelMedium,
                color = TextMedium
            )
            Text(
                text = value,
                style = Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }
    }
}

@Composable
fun ProfileSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = BlueSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = subtitle,
                    style = Typography.bodyMedium,
                    color = TextMedium
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = TextMedium
        )
    }
}
