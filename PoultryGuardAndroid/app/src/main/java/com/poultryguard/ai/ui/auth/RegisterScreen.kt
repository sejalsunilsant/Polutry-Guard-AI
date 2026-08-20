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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
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

    // Farm Information fields for Farmers
    var farmName by remember { mutableStateOf("") }
    var farmLocation by remember { mutableStateOf("") }
    var totalSheds by remember { mutableStateOf("") }
    var floorSpace by remember { mutableStateOf("") }

    // Veterinarian fields
    var phone by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var qualification by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }

    // Field-level Error States
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var specialtyError by remember { mutableStateOf<String?>(null) }
    var qualificationError by remember { mutableStateOf<String?>(null) }
    var licenseError by remember { mutableStateOf<String?>(null) }
    var experienceError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    
    // Farmer-specific field errors
    var farmNameError by remember { mutableStateOf<String?>(null) }
    var farmLocationError by remember { mutableStateOf<String?>(null) }
    var totalShedsError by remember { mutableStateOf<String?>(null) }
    var floorSpaceError by remember { mutableStateOf<String?>(null) }

    var validationError by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    fun validateInputs(): Boolean {
        var isValid = true
        
        nameError = null
        emailError = null
        phoneError = null
        locationError = null
        specialtyError = null
        qualificationError = null
        licenseError = null
        experienceError = null
        passwordError = null
        confirmPasswordError = null
        farmNameError = null
        farmLocationError = null
        totalShedsError = null
        floorSpaceError = null
        validationError = null

        // Name Validation
        if (name.isBlank()) {
            nameError = "Full Name is required."
            isValid = false
        }

        // Email Validation
        val emailPattern = android.util.Patterns.EMAIL_ADDRESS
        if (email.isBlank()) {
            emailError = "Email Address is required."
            isValid = false
        } else if (!emailPattern.matcher(email.trim()).matches()) {
            emailError = "Please enter a valid email address."
            isValid = false
        }

        // Password Validation
        if (password.isEmpty()) {
            passwordError = "Password is required."
            isValid = false
        } else if (password.length < 6) {
            passwordError = "Password must be at least 6 characters."
            isValid = false
        }

        // Confirm Password Validation
        if (confirmPassword.isEmpty()) {
            confirmPasswordError = "Please confirm your password."
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordError = "Passwords do not match."
            isValid = false
        }

        // Role Specific Validation
        if (selectedRole == UserRole.FARMER) {
            if (farmName.isBlank()) {
                farmNameError = "Farm Name is required."
                isValid = false
            }
            if (farmLocation.isBlank()) {
                farmLocationError = "Farm Location is required."
                isValid = false
            }
            if (totalSheds.isNotBlank() && totalSheds.toIntOrNull() == null) {
                totalShedsError = "Sheds count must be an integer number."
                isValid = false
            }
            if (floorSpace.isNotBlank() && floorSpace.toIntOrNull() == null) {
                floorSpaceError = "Floor space must be an integer number."
                isValid = false
            }
        } else if (selectedRole == UserRole.VETERINARIAN) {
            // Phone Validation (Indian phone format validation)
            val cleanPhone = phone.trim().replace("\\s".toRegex(), "")
            val indianPhoneRegex = Regex("^(\\+91)?[6-9]\\d{9}$")
            if (phone.isBlank()) {
                phoneError = "Phone Number is required."
                isValid = false
            } else if (!indianPhoneRegex.matches(cleanPhone)) {
                phoneError = "Please enter a valid Indian phone number."
                isValid = false
            }

            // Location
            if (location.isBlank()) {
                locationError = "Location / City is required."
                isValid = false
            }

            // Specialty
            if (specialty.isBlank()) {
                specialtyError = "Specialty is required."
                isValid = false
            }

            // Qualification
            if (qualification.isBlank()) {
                qualificationError = "Qualification is required."
                isValid = false
            }

            // License Number
            if (licenseNumber.isBlank()) {
                licenseError = "Veterinary License Number is required."
                isValid = false
            }

            // Experience
            val expInt = experience.toIntOrNull()
            if (experience.isBlank()) {
                experienceError = "Years of Experience is required."
                isValid = false
            } else if (expInt == null || expInt < 0) {
                experienceError = "Experience must be a positive integer number of years."
                isValid = false
            }
        }

        if (!isValid) {
            validationError = "Please correct the highlighted errors."
        }
        return isValid
    }

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
                        // Role Selector Dropdown (changes layout structure dynamically)
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = when (selectedRole) {
                                    UserRole.FARMER -> "Farmer (Monitor & Toggles)"
                                    UserRole.VETERINARIAN -> "Veterinarian (Acoustic Logs & Health)"
                                    UserRole.ADMIN -> "Admin (Configure Systems & Sheds)"
                                    UserRole.SUPER_ADMIN -> "Super Admin (Full System Control)"
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
                                        validationError = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Veterinarian (Health Specialist)") },
                                    onClick = {
                                        selectedRole = UserRole.VETERINARIAN
                                        dropdownExpanded = false
                                        validationError = null
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (selectedRole == UserRole.FARMER) {
                            // 1. Account Details Section
                            Text(
                                text = "1. Account Information",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                            
                            // Full Name
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it; nameError = null },
                                label = { Text("Full Name") },
                                isError = nameError != null,
                                supportingText = nameError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name", tint = GreenPrimary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Email
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it; emailError = null },
                                label = { Text("Email Address") },
                                isError = emailError != null,
                                supportingText = emailError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email", tint = GreenPrimary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Password
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; passwordError = null },
                                label = { Text("Password (min 6 chars)") },
                                isError = passwordError != null,
                                supportingText = passwordError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password", tint = GreenPrimary) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Confirm Password
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; confirmPasswordError = null },
                                label = { Text("Confirm Password") },
                                isError = confirmPasswordError != null,
                                supportingText = confirmPasswordError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Confirm", tint = GreenPrimary) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // 2. Farm Information Section
                            Text(
                                text = "2. Farm Information",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )

                            OutlinedTextField(
                                value = farmName,
                                onValueChange = { farmName = it; farmNameError = null },
                                label = { Text("Farm Name") },
                                isError = farmNameError != null,
                                supportingText = farmNameError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Home, contentDescription = "Farm Name", tint = GreenPrimary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = farmLocation,
                                onValueChange = { farmLocation = it; farmLocationError = null },
                                label = { Text("Farm Location") },
                                isError = farmLocationError != null,
                                supportingText = farmLocationError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Place, contentDescription = "Location", tint = GreenPrimary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = totalSheds,
                                onValueChange = { totalSheds = it; totalShedsError = null },
                                label = { Text("Total Sheds / Barns") },
                                isError = totalShedsError != null,
                                supportingText = totalShedsError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Apps, contentDescription = "Sheds", tint = GreenPrimary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = floorSpace,
                                onValueChange = { floorSpace = it; floorSpaceError = null },
                                label = { Text("Floor Space (Sq. Ft.)") },
                                isError = floorSpaceError != null,
                                supportingText = floorSpaceError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Crop, contentDescription = "Floor Space", tint = GreenPrimary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Veterinarians Registration Form

                            // 1. Account Details Section
                            Text(
                                text = "1. Account Information",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it; emailError = null },
                                label = { Text("Email Address") },
                                isError = emailError != null,
                                supportingText = emailError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email", tint = GreenPrimary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; passwordError = null },
                                label = { Text("Password (min 6 chars)") },
                                isError = passwordError != null,
                                supportingText = passwordError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password", tint = GreenPrimary) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; confirmPasswordError = null },
                                label = { Text("Confirm Password") },
                                isError = confirmPasswordError != null,
                                supportingText = confirmPasswordError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Confirm", tint = GreenPrimary) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // 2. Personal Information Section
                            Text(
                                text = "2. Personal Information",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it; nameError = null },
                                label = { Text("Full Name") },
                                isError = nameError != null,
                                supportingText = nameError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name", tint = GreenPrimary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it; phoneError = null },
                                label = { Text("Phone Number") },
                                isError = phoneError != null,
                                supportingText = phoneError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone", tint = GreenPrimary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it; locationError = null },
                                label = { Text("Location / City") },
                                isError = locationError != null,
                                supportingText = locationError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Place, contentDescription = "Location", tint = GreenPrimary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = photoUrl,
                                onValueChange = { photoUrl = it },
                                label = { Text("Profile Photo URL (Optional)") },
                                leadingIcon = { Icon(Icons.Default.Face, contentDescription = "Profile Photo", tint = GreenPrimary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // 3. Professional Information Section
                            Text(
                                text = "3. Professional Information",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )

                            OutlinedTextField(
                                value = specialty,
                                onValueChange = { specialty = it; specialtyError = null },
                                label = { Text("Specialty") },
                                isError = specialtyError != null,
                                supportingText = specialtyError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Work, contentDescription = "Specialty", tint = GreenPrimary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = qualification,
                                onValueChange = { qualification = it; qualificationError = null },
                                label = { Text("Qualification") },
                                isError = qualificationError != null,
                                supportingText = qualificationError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.School, contentDescription = "Qualification", tint = GreenPrimary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = licenseNumber,
                                onValueChange = { licenseNumber = it; licenseError = null },
                                label = { Text("Veterinary License Number") },
                                isError = licenseError != null,
                                supportingText = licenseError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = "License Number", tint = GreenPrimary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = experience,
                                onValueChange = { experience = it; experienceError = null },
                                label = { Text("Years of Experience") },
                                isError = experienceError != null,
                                supportingText = experienceError?.let { { Text(it) } },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = "Experience", tint = GreenPrimary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

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
                                if (validateInputs()) {
                                    if (selectedRole == UserRole.FARMER) {
                                        viewModel.register(
                                            name = name,
                                            email = email,
                                            password = password,
                                            role = selectedRole,
                                            farmName = farmName,
                                            farmLocation = farmLocation,
                                            totalSheds = totalSheds.toIntOrNull() ?: 0,
                                            floorSpaceSqFt = floorSpace.toIntOrNull() ?: 0
                                        )
                                    } else {
                                        viewModel.register(
                                            name = name,
                                            email = email,
                                            password = password,
                                            role = selectedRole,
                                            phone = phone,
                                            location = location,
                                            photoUrl = photoUrl,
                                            specialty = specialty,
                                            qualification = qualification,
                                            licenseNumber = licenseNumber,
                                            experience = experience.toIntOrNull() ?: 0
                                        )
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
