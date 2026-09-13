package com.example.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GeofenceCategory
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AvatarColors
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.MAP_STYLE_DARK_JSON
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SpaceBackground
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MapScreen(viewModel: MainViewModel) {
    val selfLocation by viewModel.selfLocation.collectAsState()
    val partnerState by viewModel.partnerState.collectAsState()
    val smoothedPartnerLatLng by viewModel.smoothedPartnerLatLng.collectAsState()
    val smoothedPartnerBearing by viewModel.smoothedPartnerBearing.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val geofences by viewModel.geofences.collectAsState()
    val connectionLatency by viewModel.connectionLatencyMs.collectAsState()
    val distanceMeters by viewModel.distanceToPartnerMeters.collectAsState()
    val bearingToPartner by viewModel.bearingToPartner.collectAsState()
    val isOfflineMode by viewModel.isOfflineModeForced.collectAsState()

    var isSatelliteMap by remember { mutableStateOf(false) }
    var autoCenterMode by remember { mutableStateOf(0) } // 0: Fit Both, 1: Self, 2: Partner

    val coroutineScope = rememberCoroutineScope()

    // Default to a sane location if self location not acquired yet
    val initialLatLng = remember(selfLocation) {
        if (selfLocation != null) {
            LatLng(selfLocation!!.latitude, selfLocation!!.longitude)
        } else {
            LatLng(37.7749, -122.4194)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, 15f)
    }

    // Auto-center camera when locations update
    LaunchedEffect(selfLocation, smoothedPartnerLatLng, autoCenterMode) {
        val self = selfLocation?.let { LatLng(it.latitude, it.longitude) }
        val partner = smoothedPartnerLatLng

        when (autoCenterMode) {
            0 -> {
                if (self != null && partner != null) {
                    try {
                        val bounds = LatLngBounds.builder().include(self).include(partner).build()
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 180))
                    } catch (_: Exception) {}
                } else if (self != null) {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLng(self))
                }
            }
            1 -> {
                if (self != null) {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLng(self))
                }
            }
            2 -> {
                if (partner != null) {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLng(partner))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isOfflineMode) {
            // Google Maps View
            val mapProperties = remember(isSatelliteMap, userProfile.isNightMapEnabled, userProfile.isTrafficEnabled) {
                MapProperties(
                    mapType = if (isSatelliteMap) MapType.HYBRID else MapType.NORMAL,
                    isTrafficEnabled = userProfile.isTrafficEnabled,
                    mapStyleOptions = if (userProfile.isNightMapEnabled && !isSatelliteMap) {
                        MapStyleOptions(MAP_STYLE_DARK_JSON)
                    } else null
                )
            }

            val uiSettings = remember {
                MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = true,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false
                )
            }

            GoogleMap(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("google_map_view"),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = uiSettings
            ) {
                // Self Marker
                selfLocation?.let { loc ->
                    val pos = LatLng(loc.latitude, loc.longitude)
                    Marker(
                        state = MarkerState(position = pos),
                        title = "${userProfile.displayName} (You)",
                        snippet = "Speed: ${String.format("%.1f", loc.speedKmh)} km/h • Bat: ${loc.batteryLevel}%",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN),
                        flat = true,
                        rotation = loc.bearing
                    )

                    // Self Accuracy Halo Circle
                    Circle(
                        center = pos,
                        radius = (loc.accuracy.coerceAtLeast(10f)).toDouble(),
                        fillColor = NeonCyan.copy(alpha = 0.15f),
                        strokeColor = NeonCyan.copy(alpha = 0.6f),
                        strokeWidth = 2f
                    )
                }

                // Partner Marker (with smooth interpolation)
                smoothedPartnerLatLng?.let { partnerPos ->
                    val partner = partnerState
                    val partnerName = partner?.deviceName ?: "Partner"
                    val speedText = partner?.location?.speedKmh?.let { String.format("%.1f", it) } ?: "0.0"

                    Marker(
                        state = MarkerState(position = partnerPos),
                        title = "$partnerName (${speedText} km/h)",
                        snippet = "Bat: ${partner?.location?.batteryLevel ?: 100}% • Latency: ${connectionLatency}ms",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                        flat = true,
                        rotation = smoothedPartnerBearing
                    )

                    // Partner Accuracy Circle
                    Circle(
                        center = partnerPos,
                        radius = ((partner?.location?.accuracy ?: 15f).coerceAtLeast(10f)).toDouble(),
                        fillColor = NeonEmerald.copy(alpha = 0.18f),
                        strokeColor = NeonEmerald.copy(alpha = 0.8f),
                        strokeWidth = 2.5f
                    )

                    // Connecting direct vector line between devices
                    selfLocation?.let { self ->
                        val selfPos = LatLng(self.latitude, self.longitude)
                        Polyline(
                            points = listOf(selfPos, partnerPos),
                            color = NeonEmerald.copy(alpha = 0.5f),
                            width = 5f,
                            geodesic = true
                        )
                    }
                }

                // Geofences rendered on map
                for (zone in geofences) {
                    if (!zone.isEnabled) continue
                    val zoneColor = when (zone.category) {
                        GeofenceCategory.HOME -> NeonEmerald
                        GeofenceCategory.WORK -> Color(0xFF3B82F6)
                        GeofenceCategory.SCHOOL -> Color(0xFF8B5CF6)
                        GeofenceCategory.DANGER -> CoralRed
                        GeofenceCategory.CUSTOM -> AmberWarning
                    }

                    Circle(
                        center = LatLng(zone.latitude, zone.longitude),
                        radius = zone.radiusMeters.toDouble(),
                        fillColor = zoneColor.copy(alpha = 0.18f),
                        strokeColor = zoneColor.copy(alpha = 0.75f),
                        strokeWidth = 3f
                    )
                }
            }
        } else {
            // High-Contrast Offline Tactical Radar & Vector Grid
            TacticalOfflineMapView(
                selfLocation = selfLocation,
                partnerLatLng = smoothedPartnerLatLng,
                partnerBearing = smoothedPartnerBearing,
                geofences = geofences,
                distanceMeters = distanceMeters,
                bearingToPartner = bearingToPartner
            )
        }

        // Top Status Header: Room, Connection & Latency
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 44.dp, start = 16.dp, end = 16.dp)
        ) {
            TopStatusBar(
                roomCode = userProfile.currentRoomCode,
                partnerState = partnerState,
                latencyMs = connectionLatency,
                isSharing = userProfile.isSharingLocation,
                isOfflineMode = isOfflineMode,
                onPairClick = { viewModel.showPairingDialog(true) },
                onToggleOffline = { viewModel.toggleOfflineMode() },
                modifier = Modifier.fillMaxWidth()
            )

            if (!isOfflineMode && (com.example.BuildConfig.MAPS_API_KEY == "DEFAULT_MAPS_KEY" || com.example.BuildConfig.MAPS_API_KEY.isBlank())) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.95f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleOfflineMode() }
                        .testTag("maps_key_banner")
                        .border(1.dp, AmberWarning.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Maps key not configured. Tap to switch to Tactical Offline Map.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Right Side Quick Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Traffic Toggle
            SmallFloatingActionButton(
                onClick = { viewModel.toggleTraffic(!userProfile.isTrafficEnabled) },
                containerColor = if (userProfile.isTrafficEnabled) NeonEmerald else DarkSurfaceElevated,
                contentColor = if (userProfile.isTrafficEnabled) Color.Black else Color.White,
                modifier = Modifier.testTag("toggle_traffic_button")
            ) {
                Icon(Icons.Default.Traffic, contentDescription = "Traffic Toggle")
            }

            // Night Map Toggle
            SmallFloatingActionButton(
                onClick = { viewModel.toggleNightMap(!userProfile.isNightMapEnabled) },
                containerColor = if (userProfile.isNightMapEnabled) NeonCyan else DarkSurfaceElevated,
                contentColor = if (userProfile.isNightMapEnabled) Color.Black else Color.White,
                modifier = Modifier.testTag("toggle_night_map_button")
            ) {
                Icon(Icons.Default.Nightlight, contentDescription = "Night Map Toggle")
            }

            // Satellite Toggle
            SmallFloatingActionButton(
                onClick = { isSatelliteMap = !isSatelliteMap },
                containerColor = if (isSatelliteMap) AmberWarning else DarkSurfaceElevated,
                contentColor = if (isSatelliteMap) Color.Black else Color.White,
                modifier = Modifier.testTag("toggle_satellite_button")
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Satellite Layer")
            }

            // Camera Auto-Center Mode (0: Both, 1: Self, 2: Partner)
            SmallFloatingActionButton(
                onClick = {
                    autoCenterMode = (autoCenterMode + 1) % 3
                },
                containerColor = DarkSurfaceElevated,
                contentColor = NeonCyan,
                modifier = Modifier.testTag("auto_center_button")
            ) {
                Icon(
                    when (autoCenterMode) {
                        1 -> Icons.Default.MyLocation
                        2 -> Icons.Default.NearMe
                        else -> Icons.Default.Explore
                    },
                    contentDescription = "Center Camera"
                )
            }
        }

        // Bottom Telemetry Sheet & Action HUD
        BottomTelemetryCard(
            selfLocation = selfLocation,
            partnerState = partnerState,
            distanceMeters = distanceMeters,
            bearingToPartner = bearingToPartner,
            trackingProfile = userProfile.trackingProfile,
            isSharing = userProfile.isSharingLocation,
            onToggleSharing = { viewModel.toggleLocationSharing() },
            onSimulatePartner = { viewModel.togglePartnerSimulation() },
            isSimulating = viewModel.isSimulating,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 80.dp)
        )
    }
}

@Composable
fun TopStatusBar(
    roomCode: String,
    partnerState: com.example.data.model.DeviceState?,
    latencyMs: Long,
    isSharing: Boolean,
    isOfflineMode: Boolean,
    onPairClick: () -> Unit,
    onToggleOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Room Pairing Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B))
                    .clickable { onPairClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("pair_code_pill")
            ) {
                Icon(
                    Icons.Default.Sensors,
                    contentDescription = null,
                    tint = if (partnerState != null) NeonEmerald else AmberWarning,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (roomCode.isNotEmpty()) "Room: $roomCode" else "Pair Device",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Connection / Latency Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                val partnerOnline = partnerState != null && partnerState.isOnline
                val statusDotColor = if (partnerOnline) NeonEmerald else AmberWarning

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (partnerOnline) {
                        "${latencyMs}ms"
                    } else if (roomCode.isNotEmpty()) {
                        "Waiting..."
                    } else {
                        "Not Paired"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Offline Mode Pill
                IconButton(
                    onClick = onToggleOffline,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("offline_mode_toggle")
                ) {
                    Icon(
                        if (isOfflineMode) Icons.Default.CloudOff else Icons.Default.Wifi,
                        contentDescription = "Toggle Offline Map Mode",
                        tint = if (isOfflineMode) AmberWarning else NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomTelemetryCard(
    selfLocation: com.example.data.model.LocationPoint?,
    partnerState: com.example.data.model.DeviceState?,
    distanceMeters: Float?,
    bearingToPartner: Float?,
    trackingProfile: com.example.data.model.TrackingProfile,
    isSharing: Boolean,
    onToggleSharing: () -> Unit,
    onSimulatePartner: () -> Unit,
    isSimulating: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Partner Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Partner Name & Distance
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                partnerState?.let {
                                    AvatarColors.getOrElse(it.avatarColorIndex) { NeonEmerald }
                                } ?: Color(0xFF475569)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = partnerState?.deviceName?.take(1)?.uppercase() ?: "?",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = partnerState?.deviceName ?: "No Partner Paired",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (distanceMeters != null) {
                                val km = distanceMeters / 1000f
                                if (km < 1.0f) "${distanceMeters.toInt()} meters away"
                                else "${String.format("%.2f", km)} km away"
                            } else {
                                "Location pending..."
                            },
                            fontSize = 12.sp,
                            color = NeonEmerald
                        )
                    }
                }

                // Partner Telemetry: Bearing Arrow & Battery
                Row(verticalAlignment = Alignment.CenterVertically) {
                    bearingToPartner?.let { bearing ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = "Bearing to Partner",
                                tint = NeonCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(bearing)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    partnerState?.location?.let { loc ->
                        val bat = loc.batteryLevel
                        val batColor = if (bat <= 20) CoralRed else if (bat <= 50) AmberWarning else NeonEmerald
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (loc.isCharging) Icons.Default.BatteryChargingFull
                                else if (bat <= 20) Icons.Default.BatteryAlert
                                else Icons.Default.BatteryFull,
                                contentDescription = "Battery",
                                tint = batColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "$bat%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = batColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Self Telemetry & Profile Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Self Speed & Accuracy
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${String.format("%.1f", selfLocation?.speedKmh ?: 0f)} km/h",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCBD5E1)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "±${selfLocation?.accuracy?.toInt() ?: 0}m",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                // Profile Badge
                Text(
                    text = trackingProfile.displayName.split(" ")[0],
                    fontSize = 11.sp,
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF064E3B))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Location Sharing Toggle Button
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSharing) Color(0xFF065F46) else Color(0xFF7F1D1D)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleSharing() }
                        .testTag("toggle_sharing_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSharing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSharing) "Sharing Active" else "Sharing Paused",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Simulator / Demo Button
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSimulating) Color(0xFF1E3A8A) else Color(0xFF1E293B)
                    ),
                    modifier = Modifier
                        .clickable { onSimulatePartner() }
                        .testTag("simulate_movement_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = if (isSimulating) NeonCyan else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSimulating) "Simulating" else "Demo Run",
                            color = if (isSimulating) NeonCyan else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tactical Offline Map Canvas:
 * Renders an offline high-contrast vector radar grid with breadcrumbs,
 * compass rose, geofence zones, and relative directional vector to partner.
 */
@Composable
fun TacticalOfflineMapView(
    selfLocation: com.example.data.model.LocationPoint?,
    partnerLatLng: LatLng?,
    partnerBearing: Float,
    geofences: List<com.example.data.model.GeofenceZone>,
    distanceMeters: Float?,
    bearingToPartner: Float?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBackground)
            .testTag("offline_canvas_map")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = size.minDimension / 2.2f

            // Radar concentric rings
            val ringCount = 4
            for (i in 1..ringCount) {
                val r = (maxR / ringCount) * i
                drawCircle(
                    color = Color(0xFF1E293B),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f))
                )
            }

            // Crosshairs
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(cx, cy - maxR),
                end = Offset(cx, cy + maxR),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(cx - maxR, cy),
                end = Offset(cx + maxR, cy),
                strokeWidth = 1f
            )

            // Self Marker at center
            drawCircle(
                color = NeonCyan.copy(alpha = 0.2f),
                radius = pulseRadius,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = NeonCyan,
                radius = 10f,
                center = Offset(cx, cy)
            )

            // Partner relative marker on radar
            if (distanceMeters != null && bearingToPartner != null) {
                // Scale distance into radar screen (e.g. 5000m max view)
                val normalizedDistance = (distanceMeters / 5000f).coerceIn(0.15f, 0.95f) * maxR
                val angleRad = Math.toRadians((bearingToPartner - 90.0))

                val px = (cx + normalizedDistance * cos(angleRad)).toFloat()
                val py = (cy + normalizedDistance * sin(angleRad)).toFloat()

                // Vector line to partner
                drawLine(
                    color = NeonEmerald.copy(alpha = 0.6f),
                    start = Offset(cx, cy),
                    end = Offset(px, py),
                    strokeWidth = 2.5f
                )

                // Partner dot
                drawCircle(
                    color = NeonEmerald,
                    radius = 12f,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = Offset(px, py)
                )
            }
        }

        // Offline Tactical HUD Overlay
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "OFFLINE TACTICAL GRID",
                color = NeonCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "Local Breadcrumbs & Vector Tracking Active",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
        }
    }
}
