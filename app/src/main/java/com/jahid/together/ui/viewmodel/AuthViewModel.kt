package com.jahid.together.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jahid.together.data.model.UserProfile
import com.jahid.together.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Authenticated(val profile: UserProfile) : AuthUiState
    data class Error(val message: String) : AuthUiState
    data class Message(val text: String) : AuthUiState
}

class AuthViewModel(
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    init {
        checkSession()
    }

    fun checkSession() {
        val uid = authRepo.currentUserId
        if (uid != null) {
            viewModelScope.launch {
                authRepo.getUserProfile(uid).onSuccess { profile ->
                    _currentUserProfile.value = profile
                    if (profile != null) {
                        _uiState.value = AuthUiState.Authenticated(profile)
                    }
                }.onFailure {
                    _uiState.value = AuthUiState.Idle
                }
            }
        } else {
            _uiState.value = AuthUiState.Idle
        }
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("ইমেইল এবং পাসওয়ার্ড পূরণ করুন।")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepo.login(email, pass).onSuccess { profile ->
                _currentUserProfile.value = profile
                _uiState.value = AuthUiState.Authenticated(profile)
            }.onFailure { e ->
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "লগইন ব্যর্থ হয়েছে।")
            }
        }
    }

    fun register(email: String, pass: String, confirmPass: String, displayName: String) {
        if (email.isBlank() || pass.isBlank() || displayName.isBlank()) {
            _uiState.value = AuthUiState.Error("সকল তথ্য সঠিকভাবে পূরণ করুন।")
            return
        }
        if (pass.length < 6) {
            _uiState.value = AuthUiState.Error("পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।")
            return
        }
        if (pass != confirmPass) {
            _uiState.value = AuthUiState.Error("পাসওয়ার্ড মেলেনি। আবার যাচাই করুন।")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepo.register(email, pass, displayName).onSuccess { profile ->
                _currentUserProfile.value = profile
                _uiState.value = AuthUiState.Authenticated(profile)
            }.onFailure { e ->
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "রেজিস্ট্রেশন ব্যর্থ হয়েছে।")
            }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _uiState.value = AuthUiState.Error("ইমেইল ঠিকানা লিখুন।")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepo.resetPassword(email).onSuccess {
                _uiState.value = AuthUiState.Message("পাসওয়ার্ড রিসেট লিংক আপনার ইমেইলে পাঠানো হয়েছে।")
            }.onFailure { e ->
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "রিসেট লিংক পাঠানো যায়নি।")
            }
        }
    }

    fun updateProfile(name: String, bitmap: Bitmap?) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepo.updateProfile(name, bitmap).onSuccess {
                checkSession()
                _uiState.value = AuthUiState.Message("প্রোফাইল সফলভাবে আপডেট হয়েছে।")
            }.onFailure { e ->
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "প্রোফাইল আপডেট ব্যর্থ হয়েছে।")
            }
        }
    }

    fun toggleLocationSharing(enabled: Boolean) {
        viewModelScope.launch {
            authRepo.toggleLocationSharing(enabled)
            val current = _currentUserProfile.value
            if (current != null) {
                _currentUserProfile.value = current.copy(isSharingLocation = enabled)
            }
        }
    }

    fun logout() {
        authRepo.logout()
        _currentUserProfile.value = null
        _uiState.value = AuthUiState.Idle
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepo.deleteAccount().onSuccess {
                _currentUserProfile.value = null
                _uiState.value = AuthUiState.Idle
            }.onFailure { e ->
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "অ্যাকাউন্ট ডিলিট করা যায়নি।")
            }
        }
    }

    fun clearMessage() {
        val current = _currentUserProfile.value
        _uiState.value = if (current != null) AuthUiState.Authenticated(current) else AuthUiState.Idle
    }
}
