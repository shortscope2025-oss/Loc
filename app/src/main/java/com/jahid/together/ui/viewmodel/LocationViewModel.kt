package com.jahid.together.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jahid.together.data.model.AppNotification
import com.jahid.together.data.model.HomeLocation
import com.jahid.together.data.model.LiveLocation
import com.jahid.together.data.repository.HomeRepository
import com.jahid.together.data.repository.LocationRepository
import com.jahid.together.service.GeofenceDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationViewModel(
    private val locationRepo: LocationRepository = LocationRepository(),
    private val homeRepo: HomeRepository = HomeRepository()
) : ViewModel() {

    private val _myLocation = MutableStateFlow<LiveLocation?>(null)
    val myLocation: StateFlow<LiveLocation?> = _myLocation.asStateFlow()

    private val _partnerLocation = MutableStateFlow<LiveLocation?>(null)
    val partnerLocation: StateFlow<LiveLocation?> = _partnerLocation.asStateFlow()

    private val _homeLocation = MutableStateFlow<HomeLocation?>(null)
    val homeLocation: StateFlow<HomeLocation?> = _homeLocation.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _distanceBetweenPartnersMeters = MutableStateFlow<Double?>(null)
    val distanceBetweenPartnersMeters: StateFlow<Double?> = _distanceBetweenPartnersMeters.asStateFlow()

    private val _partnerDistanceFromHomeMeters = MutableStateFlow<Double?>(null)
    val partnerDistanceFromHomeMeters: StateFlow<Double?> = _partnerDistanceFromHomeMeters.asStateFlow()

    private val _myDistanceFromHomeMeters = MutableStateFlow<Double?>(null)
    val myDistanceFromHomeMeters: StateFlow<Double?> = _myDistanceFromHomeMeters.asStateFlow()

    private var partnerLocationJob: Job? = null
    private var notificationJob: Job? = null

    fun setMyLocation(loc: LiveLocation) {
        _myLocation.value = loc
        recalculateDistances()
    }

    fun setHomeLocation(home: HomeLocation?) {
        _homeLocation.value = home
        recalculateDistances()
    }

    fun startObservingPartner(pairId: String, partnerUid: String, myUid: String) {
        partnerLocationJob?.cancel()
        notificationJob?.cancel()

        if (pairId.isNotBlank() && partnerUid.isNotBlank()) {
            partnerLocationJob = viewModelScope.launch {
                locationRepo.observePartnerLocation(pairId, partnerUid).collect { loc ->
                    _partnerLocation.value = loc
                    recalculateDistances()
                }
            }
        }

        if (myUid.isNotBlank()) {
            notificationJob = viewModelScope.launch {
                locationRepo.observeNotifications(myUid).collect { list ->
                    _notifications.value = list
                }
            }
        }
    }

    private fun recalculateDistances() {
        val my = _myLocation.value
        val partner = _partnerLocation.value
        val home = _homeLocation.value

        if (my != null && partner != null && my.isValid() && partner.isValid()) {
            _distanceBetweenPartnersMeters.value = GeofenceDetector.calculateDistance(
                my.latitude, my.longitude,
                partner.latitude, partner.longitude
            )
        } else {
            _distanceBetweenPartnersMeters.value = null
        }

        if (partner != null && home != null && partner.isValid() && home.isValid()) {
            _partnerDistanceFromHomeMeters.value = GeofenceDetector.calculateDistance(
                partner.latitude, partner.longitude,
                home.latitude, home.longitude
            )
        } else {
            _partnerDistanceFromHomeMeters.value = null
        }

        if (my != null && home != null && my.isValid() && home.isValid()) {
            _myDistanceFromHomeMeters.value = GeofenceDetector.calculateDistance(
                my.latitude, my.longitude,
                home.latitude, home.longitude
            )
        } else {
            _myDistanceFromHomeMeters.value = null
        }
    }

    fun saveHomeLocation(pairId: String, home: HomeLocation, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = homeRepo.setHomeLocation(pairId, home)
            if (result.isSuccess) {
                _homeLocation.value = home
                recalculateDistances()
            }
            onComplete(result)
        }
    }

    fun resetHomeLocation(pairId: String, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = homeRepo.resetHomeLocation(pairId)
            if (result.isSuccess) {
                _homeLocation.value = null
                recalculateDistances()
            }
            onComplete(result)
        }
    }
}
