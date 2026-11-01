package com.jahid.together.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jahid.together.data.model.InviteCode
import com.jahid.together.data.model.PairInfo
import com.jahid.together.data.model.UserProfile
import com.jahid.together.data.repository.PairRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PairUiStatus {
    object Idle : PairUiStatus
    object Loading : PairUiStatus
    data class CodeGenerated(val invite: InviteCode) : PairUiStatus
    data class PairedSuccess(val pair: PairInfo) : PairUiStatus
    data class Error(val message: String) : PairUiStatus
    data class Message(val text: String) : PairUiStatus
}

class PairViewModel(
    private val pairRepo: PairRepository = PairRepository()
) : ViewModel() {

    private val _pairStatus = MutableStateFlow<PairUiStatus>(PairUiStatus.Idle)
    val pairStatus: StateFlow<PairUiStatus> = _pairStatus.asStateFlow()

    private val _activePair = MutableStateFlow<PairInfo?>(null)
    val activePair: StateFlow<PairInfo?> = _activePair.asStateFlow()

    private val _partnerProfile = MutableStateFlow<UserProfile?>(null)
    val partnerProfile: StateFlow<UserProfile?> = _partnerProfile.asStateFlow()

    private var pairJob: Job? = null
    private var partnerJob: Job? = null

    fun monitorPair(pairId: String, partnerUid: String) {
        pairJob?.cancel()
        partnerJob?.cancel()

        if (pairId.isNotBlank()) {
            pairJob = viewModelScope.launch {
                pairRepo.observePairInfo(pairId).collect { info ->
                    _activePair.value = info
                }
            }
        } else {
            _activePair.value = null
        }

        if (partnerUid.isNotBlank()) {
            partnerJob = viewModelScope.launch {
                pairRepo.observePartnerProfile(partnerUid).collect { profile ->
                    _partnerProfile.value = profile
                }
            }
        } else {
            _partnerProfile.value = null
        }
    }

    fun createInvite(currentUser: UserProfile) {
        viewModelScope.launch {
            _pairStatus.value = PairUiStatus.Loading
            pairRepo.createInviteCode(currentUser).onSuccess { invite ->
                _pairStatus.value = PairUiStatus.CodeGenerated(invite)
            }.onFailure { e ->
                _pairStatus.value = PairUiStatus.Error(e.localizedMessage ?: "কোড তৈরি ব্যর্থ হয়েছে।")
            }
        }
    }

    fun joinInvite(currentUser: UserProfile, code: String) {
        viewModelScope.launch {
            _pairStatus.value = PairUiStatus.Loading
            pairRepo.joinWithInviteCode(currentUser, code).onSuccess { pair ->
                _activePair.value = pair
                _pairStatus.value = PairUiStatus.PairedSuccess(pair)
            }.onFailure { e ->
                _pairStatus.value = PairUiStatus.Error(e.localizedMessage ?: "যুক্ত হওয়া সম্ভব হয়নি।")
            }
        }
    }

    fun unpair(pairId: String, currentUid: String, partnerUid: String) {
        viewModelScope.launch {
            _pairStatus.value = PairUiStatus.Loading
            pairRepo.unpair(pairId, currentUid, partnerUid).onSuccess {
                _activePair.value = null
                _partnerProfile.value = null
                _pairStatus.value = PairUiStatus.Message("সঙ্গীর সাথে সংযোগ বিচ্ছিন্ন করা হয়েছে।")
            }.onFailure { e ->
                _pairStatus.value = PairUiStatus.Error(e.localizedMessage ?: "সংযোগ বিচ্ছিন্ন করা যায়নি।")
            }
        }
    }

    fun resetStatus() {
        _pairStatus.value = PairUiStatus.Idle
    }
}
