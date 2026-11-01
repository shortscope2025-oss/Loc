package com.jahid.together.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.rememberCameraPositionState
import com.jahid.together.data.model.AppNotification
import com.jahid.together.data.model.HomeLocation
import com.jahid.together.data.model.LiveLocation
import com.jahid.together.data.model.UserProfile
import com.jahid.together.ui.components.*
import com.jahid.together.ui.theme.*
import com.jahid.together.ui.viewmodel.AuthViewModel
import com.jahid.together.ui.viewmodel.LocationViewModel
import com.jahid.together.ui.viewmodel.PairViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLiveMapScreen(
    currentUser: UserProfile,
    partnerUser: UserProfile?,
    myLocation: LiveLocation?,
    partnerLocation: LiveLocation?,
    homeLocation: HomeLocation?,
    distancePartners: Double?,
    partnerDistanceHome: Double?,
    authViewModel: AuthViewModel,
    onNavigateToPartnerDetails: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHomeSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentMapType by remember { mutableStateOf(MapType.NORMAL) }

    val defaultPos = remember {
        when {
            partnerLocation != null && partnerLocation.isValid() -> LatLng(partnerLocation.latitude, partnerLocation.longitude)
            myLocation != null && myLocation.isValid() -> LatLng(myLocation.latitude, myLocation.longitude)
            else -> LatLng(23.8103, 90.4125)
        }
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPos, 15f)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Google Maps Compose: Real-time Partner Live Location & Couple Geofence
        TogetherGoogleMapView(
            myLocation = myLocation,
            partnerLocation = partnerLocation,
            homeLocation = homeLocation,
            myName = currentUser.displayName,
            partnerName = partnerUser?.displayName ?: "আমার প্রিয়",
            partnerPhotoBase64 = partnerUser?.photoBase64 ?: "",
            cameraPositionState = cameraPositionState,
            mapType = currentMapType
        )

        // Top Header Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onNavigateToPartnerDetails() }
                ) {
                    ProfileAvatar(
                        photoBase64 = partnerUser?.photoBase64 ?: "",
                        displayName = partnerUser?.displayName ?: "❤️",
                        size = 46.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = partnerUser?.displayName ?: "সঙ্গী খুঁজছি...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(
                                isOnline = partnerLocation?.isOnline ?: false,
                                isMoving = partnerLocation?.isMoving ?: false
                            )
                            if (partnerLocation != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                BatteryBadge(
                                    batteryPct = partnerLocation.batteryLevel,
                                    isCharging = partnerLocation.isCharging
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateToNotifications) {
                        BadgedBox(badge = {}) {
                            Icon(Icons.Default.Notifications, contentDescription = "নোটিফিকেশন", tint = RosePrimary)
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "সেটিংস", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Floating Action Buttons for quick navigation on Google Maps
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Map Type Toggle (Normal / Satellite)
            SmallFloatingActionButton(
                onClick = {
                    currentMapType = if (currentMapType == MapType.NORMAL) MapType.SATELLITE else MapType.NORMAL
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = if (currentMapType == MapType.NORMAL) Icons.Default.Layers else Icons.Default.Map,
                    contentDescription = "ম্যাপ ধরন পরিবর্তন",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Recenter on Partner
            FloatingActionButton(
                onClick = {
                    if (partnerLocation != null && partnerLocation.isValid()) {
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(partnerLocation.latitude, partnerLocation.longitude),
                                    16.5f
                                )
                            )
                        }
                        Toast.makeText(context, "সঙ্গীর রিয়েল-টাইম অবস্থান ❤️", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "সঙ্গীর অবস্থান অপেক্ষা করছে...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = RosePrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "সঙ্গীর অবস্থান")
            }

            // Recenter on Home
            FloatingActionButton(
                onClick = {
                    if (homeLocation == null || !homeLocation.isValid()) {
                        onNavigateToHomeSetup()
                    } else {
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(homeLocation.latitude, homeLocation.longitude),
                                    16f
                                )
                            )
                        }
                        Toast.makeText(context, "বাড়ি কেন্দ্রে স্থাপন করা হচ্ছে 🏡", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFFF59E0B),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Home, contentDescription = "বাড়ি কেন্দ্র")
            }

            // Recenter on My Location
            FloatingActionButton(
                onClick = {
                    if (myLocation != null && myLocation.isValid()) {
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(myLocation.latitude, myLocation.longitude),
                                    16.5f
                                )
                            )
                        }
                        Toast.makeText(context, "আমার অবস্থান কেন্দ্র করা হচ্ছে", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "আমার অবস্থান অপেক্ষা করছে...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF2563EB),
                shape = CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "আমার অবস্থান")
            }
        }

        // Bottom Info Card
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Distances summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "আমাদের দূরত্ব",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = distancePartners?.let { BengaliFormatter.formatDistance(it) } ?: "তথ্য আসছে...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = RosePrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "বাড়ি থেকে সঙ্গীর দূরত্ব",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = partnerDistanceHome?.let { BengaliFormatter.formatDistance(it) }
                                ?: if (homeLocation == null) "বাড়ি সেট করুন" else "গণনা হচ্ছে...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = CoralSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = partnerLocation?.let { "আপডেট: ${BengaliFormatter.formatElapsedTime(it.timestamp)}" } ?: "আপডেট অপেক্ষা করছে",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (homeLocation == null || !homeLocation.isValid()) {
                        TextButton(onClick = onNavigateToHomeSetup) {
                            Text("বাড়ি সেট করুন 🏡", color = RosePrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = StatusOnline, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("৫০০ মি. সুরক্ষিত", fontSize = 12.sp, color = StatusOnline, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDetailsScreen(
    partnerUser: UserProfile?,
    partnerLocation: LiveLocation?,
    partnerDistanceHome: Double?,
    distanceBetween: Double?,
    homeLocation: HomeLocation?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("সঙ্গীর তথ্য ও অবস্থা ❤️", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "ফিরে যান")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileAvatar(
                photoBase64 = partnerUser?.photoBase64 ?: "",
                displayName = partnerUser?.displayName ?: "❤️",
                size = 96.dp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = partnerUser?.displayName ?: "জীবনসঙ্গী",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = RosePrimary
            )
            Text(
                text = partnerUser?.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    isOnline = partnerLocation?.isOnline ?: false,
                    isMoving = partnerLocation?.isMoving ?: false
                )
                if (partnerLocation != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    BatteryBadge(
                        batteryPct = partnerLocation.batteryLevel,
                        isCharging = partnerLocation.isCharging
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TogetherCard {
                Text(
                    text = "রিয়েল-টাইম মেট্রিক্স",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RosePrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                MetricRow(label = "আপনার থেকে দূরত্ব", value = distanceBetween?.let { BengaliFormatter.formatDistance(it) } ?: "অজানা")
                MetricRow(label = "বাড়ি থেকে দূরত্ব", value = partnerDistanceHome?.let { BengaliFormatter.formatDistance(it) } ?: "বাড়ি সেট নেই")
                MetricRow(label = "বর্তমান গতি", value = partnerLocation?.let { "${BengaliFormatter.toBengaliDigits(String.format("%.1f", it.speed * 3.6))} কিমি/ঘণ্টা" } ?: "০ কিমি/ঘণ্টা")
                MetricRow(label = "জিপিএস নির্ভুলতা", value = partnerLocation?.let { "± ${BengaliFormatter.toBengaliDigits(it.accuracy.toInt())} মিটার" } ?: "অজানা")
                MetricRow(label = "সর্বশেষ আপডেট", value = partnerLocation?.let { BengaliFormatter.formatElapsedTime(it.timestamp) } ?: "পাওয়া যায়নি")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TogetherCard {
                Text(
                    text = "গৃহ নিরাপত্তা অবস্থা",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RosePrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (homeLocation != null && homeLocation.isValid()) {
                    Text(
                        text = "বাড়ি: ${homeLocation.address}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val statusText = if ((partnerDistanceHome ?: 999.0) <= 500.0) {
                        "বর্তমানে বাড়ির ৫০০ মিটার নিরাপদ বৃত্তের ভেতরে আছেন 🏡"
                    } else {
                        "বর্তমানে বাড়ির ৫০০ মিটার বৃত্তের বাইরে আছেন"
                    }
                    Text(text = statusText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                } else {
                    Text(
                        text = "বাড়ি এখনও সেট করা হয়নি। বাড়ির অবস্থান নির্ধারণ করুন।",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    notifications: List<AppNotification>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("বিজ্ঞপ্তি ইতিহাস 🔔", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "ফিরে যান")
                    }
                }
            )
        }
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(RosePrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔔", fontSize = 36.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "এখনও কোনো বিজ্ঞপ্তি নেই",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "সঙ্গী ৫০০ মিটারে প্রবেশ বা ত্যাগ করলে নোটিফিকেশন এখানে জমা হবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id.ifBlank { it.timestamp.toString() } }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            val iconColor = when (item.type) {
                                "ENTRY_500" -> StatusOnline
                                "APPROACHING_100" -> RosePrimary
                                "EXIT_500" -> CoralSecondary
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(iconColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.type == "EXIT_500") Icons.Default.DirectionsWalk else Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.body,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = BengaliFormatter.formatElapsedTime(item.timestamp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPrivacyScreen(
    currentUser: UserProfile,
    partnerUser: UserProfile?,
    authViewModel: AuthViewModel,
    pairViewModel: PairViewModel,
    onNavigateToHomeSetup: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showUnpairDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("গোপনীয়তা ও সেটিংস", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "ফিরে যান")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TogetherCard {
                Text(
                    text = "অবস্থান শেয়ারিং নিয়ন্ত্রণ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RosePrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("লাইভ অবস্থান শেয়ারিং", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            "বন্ধ করলে সঙ্গী আপনার বর্তমান অবস্থান দেখতে পারবেন না।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = currentUser.isSharingLocation,
                        onCheckedChange = { authViewModel.toggleLocationSharing(it) }
                    )
                }
            }

            TogetherCard {
                Text(
                    text = "গৃহ অবস্থান পরিচালনা",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RosePrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "বাড়ির অবস্থান পরিবর্তন বা আপডেট করুন। নতুন অবস্থান স্বয়ংক্রিয়ভাবে উভয়ের জন্য প্রয়োগ হবে।",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onNavigateToHomeSetup,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = RosePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("বাড়ির অবস্থান আপডেট করুন 🏡", color = RosePrimary)
                }
            }

            TogetherCard {
                Text(
                    text = "অ্যাকাউন্ট ও যুগল বিচ্ছিন্নকরণ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showUnpairDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralSecondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("সঙ্গীর সংযোগ বিচ্ছিন্ন করুন (Unpair)")
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("অ্যাকাউন্ট মুছে ফেলুন (Delete Account)")
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = { authViewModel.logout() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("লগআউট করুন", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            TogetherCard {
                Text("Together ❤️ সম্পর্কে", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("ভার্সন ১.০ • দুজনের নিরাপদ সঙ্গ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ওপেনস্ট্রিটম্যাপ ও ফায়ারবেস দ্বারা সুরক্ষিত", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showUnpairDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairDialog = false },
            title = { Text("সঙ্গীর সংযোগ বিচ্ছিন্ন করবেন?") },
            text = { Text("সংযোগ বিচ্ছিন্ন করলে উভয়ের লাইভ অবস্থান শেয়ারিং তাৎক্ষণিকভাবে বন্ধ হয়ে যাবে।") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pairViewModel.unpair(currentUser.pairId, currentUser.uid, currentUser.partnerUid)
                        showUnpairDialog = false
                    }
                ) {
                    Text("বিচ্ছিন্ন করুন", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("অ্যাকাউন্ট সম্পূর্ণ মুছে ফেলবেন?") },
            text = { Text("আপনার প্রোফাইল, অবস্থান তথ্য ও নোটিফিকেশন স্থায়ীভাবে মুছে যাবে। এই প্রক্রিয়াটি পুনরায় ফিরিয়ে আনা সম্ভব নয়।") },
            confirmButton = {
                TextButton(
                    onClick = {
                        authViewModel.deleteAccount()
                        showDeleteAccountDialog = false
                    }
                ) {
                    Text("স্থায়ীভাবে মুছুন", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
