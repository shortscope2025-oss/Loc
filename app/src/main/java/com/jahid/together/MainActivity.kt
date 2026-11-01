package com.jahid.together

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.jahid.together.data.model.LiveLocation
import com.jahid.together.service.TogetherLocationService
import com.jahid.together.ui.screens.*
import com.jahid.together.ui.theme.TogetherTheme
import com.jahid.together.ui.viewmodel.AuthUiState
import com.jahid.together.ui.viewmodel.AuthViewModel
import com.jahid.together.ui.viewmodel.LocationViewModel
import com.jahid.together.ui.viewmodel.PairViewModel

enum class Screen {
    SPLASH,
    LOGIN,
    REGISTER,
    PROFILE_SETUP,
    PAIRING,
    HOME_SETUP,
    MAIN_MAP,
    PARTNER_DETAILS,
    NOTIFICATIONS,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TogetherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TogetherAppNavigator()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocationOnce(onLocationReady: (LiveLocation) -> Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            val fused = LocationServices.getFusedLocationProviderClient(this)
            fused.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    onLocationReady(
                        LiveLocation(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracy = loc.accuracy,
                            speed = if (loc.hasSpeed()) loc.speed else 0f,
                            altitude = loc.altitude,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun TogetherAppNavigator() {
        val authViewModel: AuthViewModel = viewModel()
        val pairViewModel: PairViewModel = viewModel()
        val locationViewModel: LocationViewModel = viewModel()

        val authState by authViewModel.uiState.collectAsState()
        val currentProfile by authViewModel.currentUserProfile.collectAsState()
        val activePair by pairViewModel.activePair.collectAsState()
        val partnerProfile by pairViewModel.partnerProfile.collectAsState()

        val myLocation by locationViewModel.myLocation.collectAsState()
        val partnerLocation by locationViewModel.partnerLocation.collectAsState()
        val homeLocation by locationViewModel.homeLocation.collectAsState()
        val notifications by locationViewModel.notifications.collectAsState()
        val distancePartners by locationViewModel.distanceBetweenPartnersMeters.collectAsState()
        val partnerDistanceHome by locationViewModel.partnerDistanceFromHomeMeters.collectAsState()

        var currentScreen by remember { mutableStateOf(Screen.SPLASH) }

        // Runtime Permissions Launcher
        val permissionsLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            if (fine) {
                fetchCurrentLocationOnce { loc ->
                    locationViewModel.setMyLocation(loc)
                }
            }
        }

        LaunchedEffect(Unit) {
            val neededPermissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionsLauncher.launch(neededPermissions.toTypedArray())
        }

        // React to Authentication and Pairing State
        LaunchedEffect(currentProfile, activePair) {
            val profile = currentProfile
            if (profile != null) {
                if (profile.pairId.isNotBlank()) {
                    pairViewModel.monitorPair(profile.pairId, profile.partnerUid)
                    locationViewModel.startObservingPartner(profile.pairId, profile.partnerUid, profile.uid)

                    // Start Background Location Service
                    TogetherLocationService.startService(
                        this@MainActivity,
                        profile.pairId,
                        profile.uid,
                        profile.partnerUid,
                        activePair?.homeLocation
                    )

                    if (activePair?.homeLocation != null) {
                        locationViewModel.setHomeLocation(activePair?.homeLocation)
                    }

                    if (currentScreen == Screen.SPLASH || currentScreen == Screen.LOGIN || currentScreen == Screen.REGISTER || currentScreen == Screen.PAIRING) {
                        currentScreen = Screen.MAIN_MAP
                    }
                } else {
                    if (currentScreen == Screen.SPLASH || currentScreen == Screen.LOGIN || currentScreen == Screen.REGISTER) {
                        currentScreen = Screen.PAIRING
                    }
                }
            } else if (currentScreen != Screen.SPLASH && currentScreen != Screen.REGISTER) {
                currentScreen = Screen.LOGIN
            }
        }

        // Navigation state machine
        when (currentScreen) {
            Screen.SPLASH -> {
                SplashScreen(
                    onTimeout = {
                        val profile = currentProfile
                        currentScreen = when {
                            profile == null -> Screen.LOGIN
                            profile.pairId.isBlank() -> Screen.PAIRING
                            else -> Screen.MAIN_MAP
                        }
                    }
                )
            }

            Screen.LOGIN -> {
                LoginScreen(
                    authViewModel = authViewModel,
                    onNavigateToRegister = { currentScreen = Screen.REGISTER },
                    onNavigateToForgot = { /* handled in ViewModel or alert */ }
                )
            }

            Screen.REGISTER -> {
                BackHandler { currentScreen = Screen.LOGIN }
                RegisterScreen(
                    authViewModel = authViewModel,
                    onNavigateToLogin = { currentScreen = Screen.LOGIN }
                )
            }

            Screen.PROFILE_SETUP -> {
                BackHandler { currentScreen = Screen.SETTINGS }
                ProfileSetupScreen(
                    authViewModel = authViewModel,
                    onFinished = {
                        val profile = currentProfile
                        currentScreen = if (profile?.pairId?.isNotBlank() == true) Screen.MAIN_MAP else Screen.PAIRING
                    }
                )
            }

            Screen.PAIRING -> {
                val profile = currentProfile
                if (profile != null) {
                    PairingScreen(
                        currentUser = profile,
                        pairViewModel = pairViewModel,
                        onPairSuccess = {
                            currentScreen = Screen.MAIN_MAP
                        }
                    )
                }
            }

            Screen.HOME_SETUP -> {
                BackHandler { currentScreen = Screen.MAIN_MAP }
                HomeLocationSetupScreen(
                    pairId = currentProfile?.pairId ?: "",
                    currentLocation = myLocation,
                    existingHome = homeLocation,
                    locationViewModel = locationViewModel,
                    onSaved = { currentScreen = Screen.MAIN_MAP }
                )
            }

            Screen.MAIN_MAP -> {
                val profile = currentProfile
                if (profile != null) {
                    MainLiveMapScreen(
                        currentUser = profile,
                        partnerUser = partnerProfile,
                        myLocation = myLocation,
                        partnerLocation = partnerLocation,
                        homeLocation = homeLocation,
                        distancePartners = distancePartners,
                        partnerDistanceHome = partnerDistanceHome,
                        authViewModel = authViewModel,
                        onNavigateToPartnerDetails = { currentScreen = Screen.PARTNER_DETAILS },
                        onNavigateToNotifications = { currentScreen = Screen.NOTIFICATIONS },
                        onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                        onNavigateToHomeSetup = { currentScreen = Screen.HOME_SETUP }
                    )
                }
            }

            Screen.PARTNER_DETAILS -> {
                BackHandler { currentScreen = Screen.MAIN_MAP }
                PartnerDetailsScreen(
                    partnerUser = partnerProfile,
                    partnerLocation = partnerLocation,
                    partnerDistanceHome = partnerDistanceHome,
                    distanceBetween = distancePartners,
                    homeLocation = homeLocation,
                    onBack = { currentScreen = Screen.MAIN_MAP }
                )
            }

            Screen.NOTIFICATIONS -> {
                BackHandler { currentScreen = Screen.MAIN_MAP }
                NotificationHistoryScreen(
                    notifications = notifications,
                    onBack = { currentScreen = Screen.MAIN_MAP }
                )
            }

            Screen.SETTINGS -> {
                BackHandler { currentScreen = Screen.MAIN_MAP }
                val profile = currentProfile
                if (profile != null) {
                    SettingsPrivacyScreen(
                        currentUser = profile,
                        partnerUser = partnerProfile,
                        authViewModel = authViewModel,
                        pairViewModel = pairViewModel,
                        onNavigateToHomeSetup = { currentScreen = Screen.HOME_SETUP },
                        onBack = { currentScreen = Screen.MAIN_MAP }
                    )
                }
            }
        }
    }
}
