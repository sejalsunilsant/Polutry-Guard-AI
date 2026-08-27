package com.poultryguard.ai.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.data.model.UserRole
import com.poultryguard.ai.data.repository.AuthRepository
import com.poultryguard.ai.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Authenticated(val user: UserProfile) : AuthUiState
    object Unauthenticated : AuthUiState
    data class Error(val message: String) : AuthUiState
    object PasswordResetSent : AuthUiState
    data class PendingApproval(val message: String) : AuthUiState
    data class RejectedApproval(val reason: String) : AuthUiState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AuthRepository = SupabaseAuthRepository(application.applicationContext)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkCurrentUser()
    }

    fun isSimulatedMode(): Boolean {
        return repository.isSimulatedMode()
    }

    fun checkCurrentUser() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            var currentUser = repository.getCurrentUser()
            if (currentUser != null) {
                try {
                    repository.fetchUserContext(currentUser.uid)
                    currentUser = repository.getCurrentUser() ?: currentUser
                } catch (e: Exception) {
                    // Ignore errors during offline startup checks
                }
                _uiState.value = AuthUiState.Authenticated(currentUser)
            } else {
                _uiState.value = AuthUiState.Unauthenticated
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Email and Password fields cannot be empty.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.login(email, password)
            result.fold(
                onSuccess = { userProfile ->
                    if (userProfile.approvalStatus == "PENDING_APPROVAL") {
                        _uiState.value = AuthUiState.PendingApproval("Your account is pending admin approval. Please wait for review.")
                    } else if (userProfile.approvalStatus == "REJECTED") {
                        _uiState.value = AuthUiState.RejectedApproval(userProfile.rejectionReason ?: "Rejection details not specified.")
                    } else {
                        try {
                            repository.fetchUserContext(userProfile.uid)
                        } catch (e: Exception) {
                            // Ignore errors during login context fetch
                        }
                        val updatedUser = repository.getCurrentUser() ?: userProfile
                        _uiState.value = AuthUiState.Authenticated(updatedUser)
                    }
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Failed logging in."
                    if (errorMsg.contains("PENDING_APPROVAL")) {
                        _uiState.value = AuthUiState.PendingApproval("Your account is pending admin approval. Please wait for review.")
                    } else if (errorMsg.contains("REJECTED")) {
                        val reason = errorMsg.substringAfter("Reason: ").substringBefore("||").trim()
                        _uiState.value = AuthUiState.RejectedApproval(reason.ifBlank { "Rejection details not specified." })
                    } else {
                        _uiState.value = AuthUiState.Error(errorMsg.substringBefore("||"))
                    }
                }
            )
        }
    }

    fun register(
        name: String,
        email: String,
        password: String,
        role: UserRole,
        // Farm Info:
        farmName: String = "",
        farmLocation: String = "",
        totalSheds: Int = 4,
        floorSpaceSqFt: Int = 24000,
        // Veterinarian Info:
        phone: String = "",
        location: String = "",
        photoUrl: String = "",
        specialty: String = "",
        qualification: String = "",
        licenseNumber: String = "",
        experience: Int = 0,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("All fields are required.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.register(
                name = name,
                email = email,
                password = password,
                role = role,
                farmName = farmName,
                farmLocation = farmLocation,
                totalSheds = totalSheds,
                floorSpaceSqFt = floorSpaceSqFt,
                phone = phone,
                specialty = specialty,
                location = location,
                photoUrl = photoUrl,
                licenseNumber = licenseNumber,
                qualification = qualification,
                experience = experience,
                latitude = latitude,
                longitude = longitude
            )
            result.fold(
                onSuccess = { userProfile ->
                    try {
                        val db = com.poultryguard.ai.data.cache.AppDatabase.getDatabase(getApplication())
                        if (role == UserRole.FARMER) {
                            val newFarmer = com.poultryguard.ai.data.model.FarmerProfile(
                                id = userProfile.uid.ifBlank { "farmer_" + System.currentTimeMillis() },
                                name = name,
                                email = email,
                                phone = "",
                                accountStatus = "Active",
                                lastActive = "Just now",
                                isOnline = true,
                                farmName = farmName,
                                farmLocation = farmLocation,
                                latitude = latitude,
                                longitude = longitude,
                                totalSheds = totalSheds,
                                floorSpaceSqFt = floorSpaceSqFt,
                                deviceId = "",
                                deviceSerial = "",
                                firmwareVersion = "",
                                activeBatchId = "",
                                activeBatchStartDate = "",
                                chickAgeDays = 0,
                                feedConsumedKg = 0.0f,
                                mortalitiesCount = 0,
                                openDiseaseAlertsCount = 0
                            )
                            db.farmerProfileDao().insert(newFarmer)
                        } else if (role == UserRole.VETERINARIAN) {
                            val newVet = com.poultryguard.ai.data.model.Veterinarian(
                                id = userProfile.uid.ifBlank { "vet_" + System.currentTimeMillis() },
                                name = name,
                                specialty = specialty,
                                phone = phone,
                                email = email,
                                location = location,
                                photoUrl = photoUrl,
                                availability = "Available",
                                verificationStatus = "PENDING",
                                licenseNumber = licenseNumber,
                                qualification = qualification,
                                experience = experience
                            )
                            db.vetDao().insert(newVet)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    if (userProfile.approvalStatus == "PENDING_APPROVAL") {
                        _uiState.value = AuthUiState.PendingApproval("Your registration was successful. Please wait for an administrator to approve your account.")
                    } else {
                        _uiState.value = AuthUiState.Authenticated(userProfile)
                    }
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Registration failed."
                    _uiState.value = AuthUiState.Error(errorMsg.substringBefore("||"))
                }
            )
        }
    }

    fun forgotPassword(email: String) {
        if (email.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your email to proceed.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.forgotPassword(email)
            result.fold(
                onSuccess = {
                    _uiState.value = AuthUiState.PasswordResetSent
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Failed sending reset link.")
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.logout()
            _uiState.value = AuthUiState.Unauthenticated
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
