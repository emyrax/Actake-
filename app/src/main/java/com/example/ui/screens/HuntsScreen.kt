package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.DefaultGeoHuntZones
import com.example.data.model.GeoHuntCheckpoint
import com.example.data.model.UserProfile
import com.example.ui.components.ActakeMascotImage
import com.example.ui.components.ActakeWordmark
import com.example.ui.components.AvatarCluster
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentCoralSoft
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldSoft
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgObsidian
import com.example.ui.theme.BgSurfaceSoft
import com.example.ui.theme.DividerSubtle
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.LiveGpsTelemetry
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun HuntsScreen(
    profile: UserProfile?,
    selectedZone: GeoHuntCheckpoint,
    gpsTelemetry: LiveGpsTelemetry,
    distanceMeters: Double?,
    onSelectZone: (GeoHuntCheckpoint) -> Unit,
    onUpdateGpsLocation: (Double, Double) -> Unit,
    onGpsPermissionResult: (Boolean) -> Unit,
    onSnapGeoProof: (GeoHuntCheckpoint, Double?) -> Unit
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        onGpsPermissionResult(granted)
    }

    // Live FusedLocationProviderClient updates when permission is granted
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            onDispose { }
        } else {
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    onUpdateGpsLocation(loc.latitude, loc.longitude)
                }
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateDistanceMeters(2f)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        onUpdateGpsLocation(loc.latitude, loc.longitude)
                    }
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            onDispose {
                client.removeLocationUpdates(callback)
            }
        }
    }

    // Live session elapsed timer
    var elapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedZone.id) {
        elapsedSec = 0
        while (true) {
            delay(1000L)
            elapsedSec += 1
        }
    }
    val elapsedFormatted = String.format(
        Locale.US,
        "%02d:%02d:%02d",
        elapsedSec / 3600,
        (elapsedSec % 3600) / 60,
        elapsedSec % 60
    )

    val insideZone = distanceMeters != null && distanceMeters <= selectedZone.radiusMeters
    val distanceLabel = when {
        !hasLocationPermission -> "Enable GPS"
        distanceMeters == null -> "Acquiring GPS..."
        insideZone -> "${distanceMeters.toInt()}m • INSIDE ZONE"
        else -> "${distanceMeters.toInt()}m away"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCanvas),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 12.dp,
                bottom = 112.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: actake + GEO-HUNT badge + streak & USDC
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        ActakeWordmark(fontSize = 26.sp)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = AccentCoralSoft,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.hunts_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AccentCoral,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "🔥 ${profile?.streakCount ?: 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = String.format(Locale.US, "$%.2f USDC", profile?.usdcBalance ?: 150.0),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                }
            }

            // Interactive Apple-style Monochrome Vector Map with Live Geofence Radar
            item {
                TacticalGeoHuntMapCard(
                    selectedZone = selectedZone,
                    distanceLabel = distanceLabel,
                    insideZone = insideZone,
                    hasPermission = hasLocationPermission,
                    gpsTelemetry = gpsTelemetry,
                    elapsedFormatted = elapsedFormatted,
                    onRequestPermission = {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                )
            }

            // Category Pills (Running | Campus Hunt | Gym Check-in)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DefaultGeoHuntZones.forEach { zone ->
                        val active = zone.id == selectedZone.id
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) AccentCoralSoft else BgCard,
                            shadowElevation = if (active) 3.dp else 1.dp,
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = if (active) AccentCoral else DividerSubtle,
                                    shape = RoundedCornerShape(50)
                                )
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectZone(zone) }
                                .testTag("hunt_zone_${zone.id}")
                        ) {
                            Text(
                                text = zone.category,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (active) AccentCoral else TextPrimary,
                                maxLines = 1,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                    .align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }

            // Dark Obsidian Telemetry & Geofence Status Card
            item {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = BgObsidian,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = elapsedFormatted,
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Live Hunt Session",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (insideZone) Icons.Filled.CheckCircle
                                        else Icons.Filled.GpsFixed,
                                        contentDescription = null,
                                        tint = if (insideZone) AccentEmerald else AccentAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Geofence Check:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.72f)
                                        )
                                        Text(
                                            text = if (insideZone) "INSIDE ZONE ✓" else distanceLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (insideZone) AccentEmerald else AccentAmber
                                        )
                                    }
                                }
                            }
                        }

                        // Dual progress bars
                        val gpsProgress = when {
                            insideZone -> 1f
                            distanceMeters != null -> (1f - (distanceMeters.toFloat() / 250f)).coerceIn(0.15f, 0.95f)
                            else -> 0.25f
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinearProgressIndicator(
                                progress = { gpsProgress },
                                color = AccentEmerald,
                                trackColor = Color.White.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                            Text(
                                text = "${(gpsProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Hunt Pool Card + Snap Geo-Proof CTA
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = BgCard,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "🎯 Actake Hunt",
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentCoral,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedZone.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Radius ${selectedZone.radiusMeters.toInt()}m • ${selectedZone.activeHunters} hunters active",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format(Locale.US, "$%.0f", selectedZone.potAmount),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Pool",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (!hasLocationPermission) {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        } else {
                            onSnapGeoProof(selectedZone, distanceMeters)
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (insideZone || !hasLocationPermission) AccentCoral else BgObsidian,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("snap_geo_proof_button")
                ) {
                    Icon(
                        imageVector = if (hasLocationPermission) Icons.Filled.CameraAlt else Icons.Filled.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            !hasLocationPermission -> "Enable Live GPS to Unlock Hunt"
                            insideZone -> stringResource(R.string.hunts_snap_cta)
                            else -> "Verify Geo-Proof (${distanceMeters?.toInt() ?: "--"}m)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TacticalGeoHuntMapCard(
    selectedZone: GeoHuntCheckpoint,
    distanceLabel: String,
    insideZone: Boolean,
    hasPermission: Boolean,
    gpsTelemetry: LiveGpsTelemetry,
    elapsedFormatted: String,
    onRequestPermission: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "radar_pulse")
    val pulseRadius by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_r"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFFECEEF3),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(276.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Monochrome Apple-style topographical roads & geofence radar Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Soft pastel terrain zones
                drawCircle(
                    color = Color(0xFFD9EEDB).copy(alpha = 0.55f),
                    radius = w * 0.36f,
                    center = Offset(w * 0.78f, h * 0.32f)
                )
                drawCircle(
                    color = Color(0xFFE0E4FB).copy(alpha = 0.55f),
                    radius = w * 0.28f,
                    center = Offset(w * 0.22f, h * 0.78f)
                )

                // Stylized street grid
                val roadStroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                val roadColor = Color.White.copy(alpha = 0.92f)
                drawLine(roadColor, Offset(0f, h * 0.38f), Offset(w, h * 0.22f), strokeWidth = roadStroke.width)
                drawLine(roadColor, Offset(w * 0.12f, h), Offset(w * 0.78f, 0f), strokeWidth = roadStroke.width)
                drawLine(roadColor, Offset(w * 0.28f, 0f), Offset(w * 0.68f, h), strokeWidth = 5.dp.toPx())
                drawLine(roadColor, Offset(0f, h * 0.74f), Offset(w, h * 0.62f), strokeWidth = 5.dp.toPx())

                // Walking route path from user pin to target checkpoint
                val routePath = Path().apply {
                    moveTo(w * 0.30f, h * 0.48f)
                    lineTo(w * 0.52f, h * 0.34f)
                    lineTo(w * 0.64f, h * 0.58f)
                }
                drawPath(
                    path = routePath,
                    color = BgObsidian.copy(alpha = 0.75f),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                )

                // Pulsing target geofence zone
                val zoneCenter = Offset(w * 0.30f, h * 0.48f)
                val zoneColor = if (insideZone) AccentEmerald else AccentCoral
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(zoneColor.copy(alpha = 0.28f), Color.Transparent),
                        center = zoneCenter,
                        radius = 66.dp.toPx() * pulseRadius
                    ),
                    radius = 66.dp.toPx() * pulseRadius,
                    center = zoneCenter
                )
                drawCircle(
                    color = zoneColor.copy(alpha = 0.18f),
                    radius = 44.dp.toPx(),
                    center = zoneCenter
                )
                drawCircle(
                    color = zoneColor,
                    radius = 44.dp.toPx(),
                    center = zoneCenter,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Floating Zone Distance Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = BgCard.copy(alpha = 0.94f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = if (insideZone) AccentEmerald else AccentCoral,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${selectedZone.category} • $distanceLabel",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // Floating Squad Presence Avatars
            Surface(
                shape = RoundedCornerShape(50),
                color = BgCard.copy(alpha = 0.94f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
            ) {
                Box(modifier = Modifier.padding(start = 6.dp, end = 0.dp, top = 4.dp, bottom = 4.dp)) {
                    AvatarCluster(extraCount = selectedZone.activeHunters)
                }
            }

            // Actake Mascot Runner Marker in Center-Right
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 74.dp, top = 16.dp)
            ) {
                ActakeMascotImage(size = 64.dp)
            }

            // Bottom-Left GPS Coordinates Pill or Enable GPS button
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BgCard.copy(alpha = 0.92f),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .clickable(enabled = !hasPermission, onClick = onRequestPermission)
            ) {
                Text(
                    text = if (gpsTelemetry.latitude != null && gpsTelemetry.longitude != null) {
                        String.format(
                            Locale.US,
                            "GPS %.4f, %.4f",
                            gpsTelemetry.latitude,
                            gpsTelemetry.longitude
                        )
                    } else if (!hasPermission) {
                        "Tap to enable GPS"
                    } else {
                        "Locking satellites..."
                    },
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            // Bottom-Right Floating Elapsed Time Chip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BgCard.copy(alpha = 0.94f),
                shadowElevation = 5.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = elapsedFormatted,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Elapsed Time",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
