package com.jahid.together.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.jahid.together.data.model.HomeLocation
import com.jahid.together.data.model.LiveLocation

class MapBridge {
    var onMapReadyListener: (() -> Unit)? = null

    @JavascriptInterface
    fun onMapLoaded() {
        onMapReadyListener?.invoke()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TogetherMapView(
    myLocation: LiveLocation?,
    partnerLocation: LiveLocation?,
    homeLocation: HomeLocation?,
    myName: String,
    partnerName: String,
    partnerPhotoBase64: String,
    modifier: Modifier = Modifier,
    onMapReady: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val bridge = remember { MapBridge() }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            addJavascriptInterface(bridge, "AndroidBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onMapReady?.invoke()
                }
            }
        }
    }

    // HTML template featuring OpenStreetMap tiles + Leaflet + romantic couple markers & 500m home circle
    val htmlContent = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    background-color: #FFF1F2;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                }
                .partner-marker {
                    background: #E11D48;
                    border: 3px solid #FFFFFF;
                    border-radius: 50%;
                    box-shadow: 0 4px 12px rgba(225, 29, 72, 0.5);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: white;
                    font-weight: bold;
                    font-size: 14px;
                    width: 44px;
                    height: 44px;
                    animation: pulse-ring 2s infinite;
                }
                .my-marker {
                    background: #3B82F6;
                    border: 3px solid #FFFFFF;
                    border-radius: 50%;
                    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.5);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: white;
                    font-weight: bold;
                    font-size: 13px;
                    width: 38px;
                    height: 38px;
                }
                .home-marker {
                    background: #F59E0B;
                    border: 3px solid #FFFFFF;
                    border-radius: 50%;
                    box-shadow: 0 4px 12px rgba(245, 158, 11, 0.5);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 20px;
                    width: 42px;
                    height: 42px;
                }
                @keyframes pulse-ring {
                    0% { box-shadow: 0 0 0 0 rgba(225, 29, 72, 0.7); }
                    70% { box-shadow: 0 0 0 14px rgba(225, 29, 72, 0); }
                    100% { box-shadow: 0 0 0 0 rgba(225, 29, 72, 0); }
                }
                .custom-popup .leaflet-popup-content-wrapper {
                    border-radius: 12px;
                    box-shadow: 0 4px 14px rgba(0,0,0,0.15);
                    font-size: 13px;
                    font-weight: 500;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map', {
                    zoomControl: false,
                    attributionControl: false
                }).setView([23.8103, 90.4125], 14);

                L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 19
                }).addTo(map);

                var myMarker = null;
                var partnerMarker = null;
                var homeMarker = null;
                var homeCircle500 = null;
                var homeCircle100 = null;

                function updateMyLocation(lat, lng, name) {
                    if (!lat || !lng) return;
                    var icon = L.divIcon({
                        className: 'my-marker',
                        html: '<span>আমি</span>',
                        iconSize: [38, 38],
                        iconAnchor: [19, 19]
                    });
                    if (myMarker) {
                        myMarker.setLatLng([lat, lng]);
                    } else {
                        myMarker = L.marker([lat, lng], {icon: icon}).addTo(map);
                        myMarker.bindPopup('<b>' + (name || 'আমি') + '</b><br>আমার বর্তমান অবস্থান');
                    }
                }

                function updatePartnerLocation(lat, lng, name, battery, photoBase64) {
                    if (!lat || !lng) return;
                    var inner = photoBase64 ? '<img src="data:image/jpeg;base64,' + photoBase64 + '" style="width:38px;height:38px;border-radius:50%;object-fit:cover;"/>' : '<span>❤️</span>';
                    var icon = L.divIcon({
                        className: 'partner-marker',
                        html: inner,
                        iconSize: [44, 44],
                        iconAnchor: [22, 22]
                    });
                    if (partnerMarker) {
                        partnerMarker.setLatLng([lat, lng]);
                        partnerMarker.setIcon(icon);
                    } else {
                        partnerMarker = L.marker([lat, lng], {icon: icon}).addTo(map);
                    }
                    partnerMarker.bindPopup('<b>' + (name || 'কলিজা') + ' ❤️</b><br>ব্যাটারি: ' + (battery || 100) + '%');
                }

                function updateHome(lat, lng, radius) {
                    if (!lat || !lng) return;
                    var icon = L.divIcon({
                        className: 'home-marker',
                        html: '<span>🏡</span>',
                        iconSize: [42, 42],
                        iconAnchor: [21, 21]
                    });
                    if (homeMarker) {
                        homeMarker.setLatLng([lat, lng]);
                    } else {
                        homeMarker = L.marker([lat, lng], {icon: icon}).addTo(map);
                        homeMarker.bindPopup('<b>আমাদের প্রিয় বাড়ি 🏡</b><br>৫০০ মিটার নিরাপদ বলয়');
                    }

                    if (homeCircle500) {
                        homeCircle500.setLatLng([lat, lng]);
                        homeCircle500.setRadius(radius || 500);
                    } else {
                        homeCircle500 = L.circle([lat, lng], {
                            radius: radius || 500,
                            color: '#E11D48',
                            weight: 2,
                            fillColor: '#FDA4AF',
                            fillOpacity: 0.25
                        }).addTo(map);
                    }

                    if (homeCircle100) {
                        homeCircle100.setLatLng([lat, lng]);
                    } else {
                        homeCircle100 = L.circle([lat, lng], {
                            radius: 100,
                            color: '#F43F5E',
                            weight: 2,
                            dashArray: '4, 4',
                            fillColor: '#E11D48',
                            fillOpacity: 0.35
                        }).addTo(map);
                    }
                }

                function centerOn(lat, lng, zoom) {
                    if (lat && lng) {
                        map.flyTo([lat, lng], zoom || 16, { duration: 1.2 });
                    }
                }

                function fitBoth(lat1, lng1, lat2, lng2) {
                    if (lat1 && lng1 && lat2 && lng2) {
                        var bounds = L.latLngBounds([[lat1, lng1], [lat2, lng2]]);
                        map.fitBounds(bounds, { padding: [60, 60], maxZoom: 17 });
                    }
                }

                if (window.AndroidBridge && window.AndroidBridge.onMapLoaded) {
                    window.AndroidBridge.onMapLoaded();
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    LaunchedEffect(Unit) {
        webView.loadDataWithBaseURL("https://openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
    }

    // Reactively update markers via JavaScript
    LaunchedEffect(myLocation) {
        myLocation?.let {
            if (it.isValid()) {
                webView.evaluateJavascript("updateMyLocation(${it.latitude}, ${it.longitude}, '$myName');", null)
            }
        }
    }

    LaunchedEffect(partnerLocation, partnerPhotoBase64) {
        partnerLocation?.let {
            if (it.isValid()) {
                val cleanPhoto = partnerPhotoBase64.replace("\n", "").replace("\r", "")
                webView.evaluateJavascript(
                    "updatePartnerLocation(${it.latitude}, ${it.longitude}, '$partnerName', ${it.batteryLevel}, '$cleanPhoto');",
                    null
                )
            }
        }
    }

    LaunchedEffect(homeLocation) {
        homeLocation?.let {
            if (it.isValid()) {
                webView.evaluateJavascript("updateHome(${it.latitude}, ${it.longitude}, ${it.radiusMeters});", null)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
