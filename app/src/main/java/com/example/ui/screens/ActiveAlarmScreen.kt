package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.alarm.AlarmAudioPlayer
import com.example.data.model.AlarmSoundOption
import com.example.ui.components.ActakeWordmark
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentCoralBorder
import com.example.ui.theme.AccentCoralSoft
import com.example.ui.theme.AccentDanger
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.BgCard
import com.example.ui.theme.BgObsidian
import com.example.ui.theme.BgWarmTint
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.ActiveVerificationTarget
import com.example.viewmodel.RandomizedTargetUiState
import com.example.viewmodel.VerificationUiState
import kotlinx.coroutines.delay
import java.util.Locale

private const val TAG = "ActiveAlarmScreen"

@Composable
fun ActiveAlarmScreen(
    target: ActiveVerificationTarget,
    randomizedTargetState: RandomizedTargetUiState,
    verificationState: VerificationUiState,
    strictnessEnabled: Boolean,
    onRerollTarget: () -> Unit,
    onRetryPreviewImage: () -> Unit = {},
    onCaptureFrame: (Bitmap) -> Unit,
    onRetryReset: () -> Unit,
    onExitCancelled: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Captured photo awaiting explicit Retake or Confirm & Verify
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Countdown timer (starts at 5 minutes = 300s) and elapsed duration
    var secondsRemaining by remember { mutableIntStateOf(300) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var alarmMuted by remember { mutableStateOf(false) }

    val audioPlayer = remember(context) { AlarmAudioPlayer(context) }
    var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var showStrictExitDialog by remember { mutableStateOf(false) }
    var showExpandedReferenceDialog by remember { mutableStateOf(false) }

    fun exitLiveCam() {
        audioPlayer.stop()
        try {
            boundCameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding camera on exit", e)
        }
        onExitCancelled()
    }

    LaunchedEffect(alarmMuted, verificationState, capturedBitmap) {
        val soundOption = AlarmSoundOption.fromLabel(target.alarmSound)
        if (!alarmMuted && capturedBitmap == null && verificationState is VerificationUiState.Ready) {
            audioPlayer.startAlarmLoop(soundOption, scope)
        } else {
            audioPlayer.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
            try {
                boundCameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining -= 1
            elapsedSeconds += 1
        }
    }

    // Hard System Back Lock: live cam exits ONLY on explicit Back button click or when turning off
    // the alarm via verified AI proof — never on accidental back swipes or outside taps.
    BackHandler(enabled = true) {
        // Intentionally locked to prevent accidental live camera / alarm dismissal
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val isBusy = verificationState is VerificationUiState.Scanning ||
        verificationState is VerificationUiState.VerifyingOracle

    val activeTargetLabel = (randomizedTargetState as? RandomizedTargetUiState.Ready)
        ?.target?.targetObject ?: target.title

    fun captureLivePhotoForReview() {
        if (isBusy || capturedBitmap != null) return
        audioPlayer.stop()
        val executor = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val rawBitmap = image.toBitmap()
                        val rotation = image.imageInfo.rotationDegrees
                        val rotated = if (rotation != 0) {
                            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                            Bitmap.createBitmap(
                                rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                            )
                        } else {
                            rawBitmap
                        }
                        capturedBitmap = rotated
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode camera frame", e)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Camera capture failed", exception)
                }
            }
        )
    }

    val minutes = secondsRemaining / 60
    val secs = secondsRemaining % 60
    val formattedCountdown = String.format(Locale.US, "%02d:%02d", minutes, secs)
    val rewardPoints = if (target.tokenSymbol == "SKR") 75 else 50

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgWarmTint)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Status Bar: Back button + actake + LIVE PROOF + Stake at risk + mute
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BgCard,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isBusy) {
                                if (strictnessEnabled) {
                                    showStrictExitDialog = true
                                } else {
                                    exitLiveCam()
                                }
                            }
                            .testTag("alarm_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    ActakeWordmark(fontSize = 22.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.active_alarm_live_proof),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "$%.2f", target.stakeAmount),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = { alarmMuted = !alarmMuted },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .testTag("alarm_mute_toggle")
                    ) {
                        Icon(
                            imageVector = if (alarmMuted) Icons.AutoMirrored.Filled.VolumeOff
                            else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (alarmMuted) "Unmute alarm" else "Mute alarm",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Floating AI Randomized Target + Runtime AI-Generated Visual Reference Preview Card
            // Locked to the exact item initially displayed in the alarm pop-up
            RandomizedTargetReferenceCard(
                randomizedTargetState = randomizedTargetState,
                rewardPoints = rewardPoints,
                formattedCountdown = formattedCountdown,
                canReroll = false,
                onRerollTarget = onRerollTarget,
                onRetryPreviewImage = onRetryPreviewImage,
                onExpandReference = { showExpandedReferenceDialog = true }
            )

            // Full-Bleed 32dp-Rounded CameraX Live Viewfinder OR Frozen Review Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(BgObsidian)
            ) {
                val previewBmp = capturedBitmap
                if (previewBmp != null) {
                    Image(
                        bitmap = previewBmp.asImageBitmap(),
                        contentDescription = "Captured habit proof preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Subtle scrim to keep bottom controls crisp
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.22f))
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = BgObsidian.copy(alpha = 0.78f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 14.dp)
                    ) {
                        Text(
                            text = when (verificationState) {
                                is VerificationUiState.Ready -> "PROOF CAPTURED • CONFIRM OR RETAKE"
                                is VerificationUiState.Scanning -> "VERIFYING WITH GEMINI VISION AI"
                                is VerificationUiState.VerifyingOracle -> "SIGNING ED25519 ATTESTATION"
                                is VerificationUiState.Rejected -> "VERIFICATION REJECTED"
                                is VerificationUiState.Error -> "VERIFICATION ERROR"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                } else if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    boundCameraProvider = cameraProvider
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageCapture
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "CameraX bind failed", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Animated Targeting Reticle Overlay (live viewfinder only)
                    CameraTargetingReticle(
                        isScanning = isBusy,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(176.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgObsidian.copy(alpha = 0.76f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 204.dp)
                    ) {
                        Text(
                            text = "Point camera at $activeTargetLabel",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Camera Access Required for Anti-Spoof Proof",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCoral),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Enable Camera", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Bottom Frosted Control Panel: Shutter OR Retake/Confirm OR Scanning/Error
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = BgObsidian.copy(alpha = 0.84f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    when (verificationState) {
                        is VerificationUiState.Scanning, is VerificationUiState.VerifyingOracle -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = AccentEmerald,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.size(38.dp)
                                )
                                Column {
                                    Text(
                                        text = when (verificationState) {
                                            is VerificationUiState.VerifyingOracle ->
                                                "Matched ${verificationState.confidencePct}% ${verificationState.detectedLabel} • Attesting..."
                                            is VerificationUiState.Scanning ->
                                                verificationState.statusText
                                            else -> "Verifying..."
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Gemini Vision anti-spoof + Ed25519 oracle verification",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.72f)
                                    )
                                }
                            }
                        }

                        is VerificationUiState.Rejected -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Rejected (${verificationState.confidencePct}%): ${verificationState.reason}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFFCA5A5)
                                )
                                Button(
                                    onClick = {
                                        capturedBitmap = null
                                        onRetryReset()
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCoral),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("retry_capture_button")
                                ) {
                                    Text("Retake Live Proof", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        is VerificationUiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = verificationState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFFCA5A5)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            capturedBitmap = null
                                            onRetryReset()
                                        },
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text("Retake", fontWeight = FontWeight.Bold)
                                    }
                                    val bmpForRetry = capturedBitmap
                                    if (bmpForRetry != null) {
                                        Button(
                                            onClick = {
                                                onRetryReset()
                                                onCaptureFrame(bmpForRetry)
                                            },
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentCoral),
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .height(48.dp)
                                        ) {
                                            Text("Retry Verify", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        VerificationUiState.Ready -> {
                            val bmpToConfirm = capturedBitmap
                            if (bmpToConfirm != null) {
                                // Explicit Retake & Confirm Workflow
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Confirm live proof of $activeTargetLabel?",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                capturedBitmap = null
                                                onRetryReset()
                                            },
                                            shape = RoundedCornerShape(50),
                                            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.65f)),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .testTag("camera_retake_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.camera_retake),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Button(
                                            onClick = { onCaptureFrame(bmpToConfirm) },
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentEmerald,
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .weight(1.35f)
                                                .height(50.dp)
                                                .testTag("camera_confirm_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.camera_confirm),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Live Shutter Capture Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.active_alarm_capture_proof),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Snap live photo, then Retake or Confirm",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.72f)
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = AccentCoral,
                                        shadowElevation = 6.dp,
                                        modifier = Modifier
                                            .size(62.dp)
                                            .border(3.dp, Color.White, CircleShape)
                                            .clip(CircleShape)
                                            .clickable(enabled = hasCameraPermission) {
                                                captureLivePhotoForReview()
                                            }
                                            .testTag("camera_shutter_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.CameraAlt,
                                                contentDescription = stringResource(R.string.cd_camera_shutter),
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Compact Telemetry Card (Duration & Verification rings)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = BgCard,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timePct = ((secondsRemaining / 300f) * 100).toInt().coerceIn(0, 100)
                    val verifyPct = when (verificationState) {
                        is VerificationUiState.Ready -> if (capturedBitmap != null) 45 else 15
                        is VerificationUiState.Scanning -> 70
                        is VerificationUiState.VerifyingOracle -> 95
                        is VerificationUiState.Rejected -> verificationState.confidencePct
                        is VerificationUiState.Error -> 0
                    }
                    val verifyLabel = when (verificationState) {
                        is VerificationUiState.Ready -> if (capturedBitmap != null) "Review" else "Live"
                        is VerificationUiState.Scanning -> "Vision AI"
                        is VerificationUiState.VerifyingOracle -> "Escrow Tx"
                        is VerificationUiState.Rejected -> "Retry"
                        is VerificationUiState.Error -> "Error"
                    }

                    TelemetryCompactPill(
                        label = "Elapsed ${elapsedSeconds}s",
                        percent = timePct
                    )
                    TelemetryCompactPill(
                        label = "Proof • $verifyLabel",
                        percent = verifyPct
                    )
                }
            }
        }
    }

    if (showStrictExitDialog) {
        AlertDialog(
            onDismissRequest = { showStrictExitDialog = false },
            containerColor = BgCard,
            title = {
                Text(
                    text = "Exit Live Camera?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Strict Mode is active. Turning off the alarm and protecting your $${"%.2f".format(Locale.US, target.stakeAmount)} ${target.tokenSymbol} stake requires verified live photo proof.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStrictExitDialog = false
                        exitLiveCam()
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentDanger,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_exit_live_cam_button")
                ) {
                    Text("Exit Live Cam", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStrictExitDialog = false },
                    modifier = Modifier.testTag("stay_verify_live_cam_button")
                ) {
                    Text(
                        text = "Stay & Verify",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }

    // Expanded Full-Size AI-Generated Target Image + Capture Description Modal
    val readyState = randomizedTargetState as? RandomizedTargetUiState.Ready
    if (showExpandedReferenceDialog && readyState != null) {
        AlertDialog(
            onDismissRequest = { showExpandedReferenceDialog = false },
            containerColor = BgCard,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Snap: ${readyState.target.targetObject}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "AI-Generated Visual Reference to Turn Off Alarm",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCoral,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val bmp = readyState.generatedPreviewBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "AI generated visual reference for ${readyState.target.targetObject}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(20.dp))
                        )
                    }
                    Text(
                        text = readyState.target.visualCue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Anti-Cheat: ${readyState.target.antiCheatRule}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showExpandedReferenceDialog = false },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BgObsidian,
                        contentColor = Color.White
                    )
                ) {
                    Text("Got It • Snap Now", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun RandomizedTargetReferenceCard(
    randomizedTargetState: RandomizedTargetUiState,
    rewardPoints: Int,
    formattedCountdown: String,
    canReroll: Boolean,
    onRerollTarget: () -> Unit,
    onRetryPreviewImage: () -> Unit,
    onExpandReference: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AccentCoralSoft,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, AccentCoralBorder, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left 72x72dp Runtime AI-Generated Reference Thumbnail (Zero static mock fallback)
            val ready = randomizedTargetState as? RandomizedTargetUiState.Ready
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = ready != null) {
                        if (ready?.customPreviewError != null) {
                            onRetryPreviewImage()
                        } else if (ready?.generatedPreviewBitmap != null) {
                            onExpandReference()
                        }
                    }
                    .testTag("reference_preview_thumbnail")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    when (randomizedTargetState) {
                        is RandomizedTargetUiState.Loading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = AccentCoral,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Rolling",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        is RandomizedTargetUiState.Error -> {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "Target error",
                                tint = AccentDanger,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        is RandomizedTargetUiState.Ready -> {
                            val generatedBmp = randomizedTargetState.generatedPreviewBitmap
                            when {
                                generatedBmp != null -> {
                                    Image(
                                        bitmap = generatedBmp.asImageBitmap(),
                                        contentDescription = "AI generated image of ${randomizedTargetState.target.targetObject}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                randomizedTargetState.isGeneratingCustomPreview -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = AccentCoral,
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = "AI img...",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = AccentCoral,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                else -> {
                                    // Visible error with 1-tap retry (strictly no fake static fallback)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Retry AI reference image",
                                            tint = AccentDanger,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Retry AI",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentDanger
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Center Target Title + Full Capture Description
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                when (randomizedTargetState) {
                    is RandomizedTargetUiState.Loading -> {
                        Text(
                            text = "Rolling AI alarm target...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Generating randomized item & AI picture",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    is RandomizedTargetUiState.Error -> {
                        Text(
                            text = "AI Target Roll Failed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentDanger
                        )
                        Text(
                            text = randomizedTargetState.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    is RandomizedTargetUiState.Ready -> {
                        Text(
                            text = "Snap: ${randomizedTargetState.target.targetObject}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = randomizedTargetState.target.visualCue,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val statusHint = when {
                            randomizedTargetState.isGeneratingCustomPreview -> "Generating AI photo • +$rewardPoints \$ACT"
                            randomizedTargetState.generatedPreviewBitmap != null -> "Tap photo to enlarge • +$rewardPoints \$ACT"
                            randomizedTargetState.customPreviewError != null -> "Image error (tap box to retry)"
                            else -> "+$rewardPoints \$ACT on verification"
                        }
                        Text(
                            text = statusHint,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (randomizedTargetState.customPreviewError != null) AccentDanger else AccentCoral,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right Countdown + Re-roll Action
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formattedCountdown,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFeatureSettings = "tnum"
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "left",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    if (canReroll) {
                        IconButton(
                            onClick = onRerollTarget,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("reroll_target_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Roll a new AI target",
                                tint = AccentCoral,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraTargetingReticle(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "reticle_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reticle_scale"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = w * 0.16f
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        val color = if (isScanning) AccentEmerald else Color.White.copy(alpha = 0.88f)

        // Four HUD corner brackets
        drawLine(color, Offset(0f, corner), Offset(0f, 0f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, 0f), Offset(corner, 0f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(w - corner, 0f), Offset(w, 0f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(w, 0f), Offset(w, corner), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, h - corner), Offset(0f, h), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, h), Offset(corner, h), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(w - corner, h), Offset(w, h), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(w, h - corner), Offset(w, h), strokeWidth = stroke.width, cap = StrokeCap.Round)

        val r = (w * 0.28f) * pulse
        drawCircle(
            color = color.copy(alpha = 0.85f),
            radius = r,
            style = Stroke(width = 2.dp.toPx())
        )
        drawLine(color, Offset(center.x - r - 12f, center.y), Offset(center.x - r + 14f, center.y), strokeWidth = 2.dp.toPx())
        drawLine(color, Offset(center.x + r - 14f, center.y), Offset(center.x + r + 12f, center.y), strokeWidth = 2.dp.toPx())
        drawLine(color, Offset(center.x, center.y - r - 12f), Offset(center.x, center.y - r + 14f), strokeWidth = 2.dp.toPx())
        drawLine(color, Offset(center.x, center.y + r - 14f), Offset(center.x, center.y + r + 12f), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun TelemetryCompactPill(
    label: String,
    percent: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                color = AccentCoral,
                trackColor = AccentCoralSoft,
                strokeWidth = 3.dp,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum"
                ),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFeatureSettings = "tnum"
            ),
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}
