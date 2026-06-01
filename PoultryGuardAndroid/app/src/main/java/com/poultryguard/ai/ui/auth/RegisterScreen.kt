package com.poultryguard.ai.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poultryguard.ai.data.model.UserRole
import com.poultryguard.ai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.FARMER) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var validationError by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Scaffold(containerColor = AppBackground) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GreenLight.copy(alpha = 0.5f), AppBackground)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header
                Text(
                    text = "Create Account",
                    style = Typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = "Select your agricultural role and enroll",
                    style = Typography.bodyMedium,
                    color = TextMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Inputs Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Full Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name", tint = GreenPrimary) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                focusedLabelColor = GreenPrimary
                            )
                        )

                        // Email
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email", tint = GreenPrimary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                focusedLabelColor = GreenPrimary
                            )
                        )

                        // Role Selector Dropdown
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = when (selectedRole) {
                                    UserRole.FARMER -> "Farmer (Monitor & Toggles)"
                                    UserRole.VETERINARIAN -> "Veterinarian (Acoustic Logs & Health)"
                                    UserRole.ADMIN -> "Admin (Configure Systems & Sheds)"
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Authorized Role") },
                                leadingIcon = { Icon(Icons.Default.Work, contentDescription = "Role", tint = GreenPrimary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    focusedLabelColor = GreenPrimary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Farmer (Farm Operator)") },
                                    onClick = {
                                        selectedRole = UserRole.FARMER
                                        dropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Veterinarian (Health Specialist)") },
                                    onClick = {
                                        selectedRole = UserRole.VETERINARIAN
                                        dropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Admin (System Superintendent)") },
                                    onClick = {
                                        selectedRole = UserRole.ADMIN
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }

                        // Password
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password (min 6 chars)") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password", tint = GreenPrimary) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                focusedLabelColor = GreenPrimary
                            )
                        )

                        // Confirm Password
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Confirm", tint = GreenPrimary) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GreenPrimary,
                                focusedLabelColor = GreenPrimary
                            )
                        )

                        // Validation or API Error Messaging
                        val currentError = validationError ?: (uiState as? AuthUiState.Error)?.message
                        if (currentError != null) {
                            Text(
                                text = currentError,
                                color = AlertRed,
                                style = Typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Submit Button
                        Button(
                            onClick = {
                                validationError = null
                                when {
                                    name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                                        validationError = "Please fill in all requested fields."
                                    }
                                    password.length < 6 -> {
                                        validationError = "Password must be at least 6 characters."
                                    }
                                    password != confirmPassword -> {
                                        validationError = "Passwords do not match."
                                    }
                                    else -> {
                                        viewModel.register(name, email, password, selectedRole)
                                    }
                                }
                            },
                            enabled = uiState !is AuthUiState.Loading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) {
                            if (uiState is AuthUiState.Loading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text(
                                    text = "Register",
                                    style = Typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Log In Link
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Already have an account? ",
                        style = Typography.bodyMedium,
                        color = TextMedium
                    )
                    Text(
                        text = "Sign In",
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary,
                        modifier = Modifier.clickable { onNavigateToLogin() }
                    )
                }
            }
        }
    }
}
