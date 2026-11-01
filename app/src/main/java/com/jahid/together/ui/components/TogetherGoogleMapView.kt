package com.jahid.together.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.jahid.together.data.model.HomeLocation
import com.jahid.together.data.model.LiveLocation
import com.jahid.together.ui.theme.RosePrimary
import com.jahid.together.ui.theme.RosePrimaryContainer
import kotlinx.coroutines.launch

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun TogetherGoogleMapView(
    myLocation: LiveLocation?,
    partnerLocation: LiveLocation?,
    homeLocation: HomeLocation?,
    myName: String,
    partnerName: String,
    partnerPhotoBase64: String,
    cameraPositionState: CameraPositionState,
    mapType: MapType = MapType.NORMAL,
    modifier: Modifier = Modifier,
    onMapLoaded: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    // Default center (Dhaka, Bangladesh fallback if coordinates not yet available)
    val defaultPos = remember { LatLng(23.8103, 90.4125) }

    val partnerLatLng = remember(partnerLocation?.latitude, partnerLocation?.longitude) {
        if (partnerLocation != null && partnerLocation.isValid()) {
            LatLng(partnerLocation.latitude, partnerLocation.longitude)
        } else null
    }

    val myLatLng = remember(myLocation?.latitude, myLocation?.longitude) {
        if (myLocation != null && myLocation.isValid()) {
            LatLng(myLocation.latitude, myLocation.longitude)
        } else null
    }

    val homeLatLng = remember(homeLocation?.latitude, homeLocation?.longitude) {
        if (homeLocation != null && homeLocation.isValid()) {
            LatLng(homeLocation.latitude, homeLocation.longitude)
        } else null
    }

    // Camera auto-initialization when partner or my location first becomes available
    var hasCenteredInitially by remember { mutableStateOf(false) }
    LaunchedEffect(partnerLatLng, myLatLng) {
        if (!hasCenteredInitially) {
            val target = partnerLatLng ?: myLatLng
            if (target != null) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 15.5f)
                hasCenteredInitially = true
            }
        }
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = true,
            zoomGesturesEnabled = true
        )
    }

    val properties = remember(mapType) {
        MapProperties(
            mapType = mapType,
            isMyLocationEnabled = false // We draw custom romantic couple avatar marker instead
        )
    }

    // Decode partner photo bitmap once
    val partnerBitmap = remember(partnerPhotoBase64) {
        if (partnerPhotoBase64.isNotBlank()) {
            try {
                val decoded = Base64.decode(partnerPhotoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = uiSettings,
        onMapLoaded = { onMapLoaded?.invoke() }
    ) {
        // 1. Home 500-Meter Geofence Circle
        if (homeLatLng != null && homeLocation != null && homeLocation.isValid()) {
            Circle(
                center = homeLatLng,
                radius = homeLocation.radiusMeters,
                strokeColor = Color(0xFFE11D48),
                strokeWidth = 4f,
                fillColor = Color(0x26E11D48),
                zIndex = 1f
            )

            // Inner 100-meter proximity circle
            Circle(
                center = homeLatLng,
                radius = 100.0,
                strokeColor = Color(0xFFF43F5E),
                strokeWidth = 3f,
                fillColor = Color(0x35E11D48),
                zIndex = 1.1f
            )

            // Home Marker
            MarkerComposable(
                state = rememberMarkerState(position = homeLatLng),
                title = "আমাদের প্রিয় বাড়ি 🏡",
                snippet = "৫০০ মিটার নিরাপদ বলয়"
            ) {
                HomeMarkerComposable()
            }
        }

        // 2. User's Own Location Marker
        if (myLatLng != null) {
            MarkerComposable(
                state = rememberMarkerState(position = myLatLng),
                title = myName.ifBlank { "আমি" },
                snippet = "আমার বর্তমান অবস্থান"
            ) {
                MyMarkerComposable(myName = myName)
            }
        }

        // 3. Partner's Real-time Location Marker (High Fidelity Romantic Avatar)
        if (partnerLatLng != null && partnerLocation != null) {
            MarkerComposable(
                state = rememberMarkerState(position = partnerLatLng),
                title = "${partnerName.ifBlank { "জীবনসঙ্গী" }} ❤️",
                snippet = "ব্যাটারি: ${partnerLocation.batteryLevel}% • গতি: ${String.format("%.1f", partnerLocation.speed * 3.6)} কিমি/ঘণ্টা",
                zIndex = 10f
            ) {
                PartnerMarkerComposable(
                    name = partnerName,
                    batteryLevel = partnerLocation.batteryLevel,
                    isMoving = partnerLocation.isMoving,
                    photoBitmap = partnerBitmap
                )
            }
        }
    }
}

@Composable
private fun PartnerMarkerComposable(
    name: String,
    batteryLevel: Int,
    isMoving: Boolean,
    photoBitmap: Bitmap?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        // Romantic Floating Badge above avatar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFE11D48),
            shadowElevation = 6.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = name.ifBlank { "কলিজা" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Avatar container with pulsing crimson ring
        Box(
            modifier = Modifier
                .size(54.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(3.5.dp, Color(0xFFE11D48), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap.asImageBitmap(),
                    contentDescription = "$name ছবি",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = "❤️",
                    fontSize = 24.sp
                )
            }
        }

        // Pointer pin arrow
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(6.dp)
                .background(Color(0xFFE11D48), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
        )
    }
}

@Composable
private fun MyMarkerComposable(myName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF2563EB),
            shadowElevation = 4.dp
        ) {
            Text(
                text = "আমি",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(3.dp, Color(0xFF2563EB), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HomeMarkerComposable() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFFF59E0B))
                .border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🏡", fontSize = 22.sp)
        }
    }
}
