package com.poultryguard.ai.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.data.model.UserRole
import com.poultryguard.ai.data.repository.AuthRepository
import com.poultryguard.ai.data.repository.FirebaseAuthRepository
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
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AuthRepository = FirebaseAuthRepository(application.applicationContext)

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
            val currentUser = repository.getCurrentUser()
            if (currentUser != null) {
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
                    _uiState.value = AuthUiState.Authenticated(userProfile)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Failed logging in.")
                }
            )
        }
    }

    fun register(name: String, email: String, password: String, role: UserRole) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("All fields are required.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repository.register(name, email, password, role)
            result.fold(
                onSuccess = { userProfile ->
                    _uiState.value = AuthUiState.Authenticated(userProfile)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Registration failed.")
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
