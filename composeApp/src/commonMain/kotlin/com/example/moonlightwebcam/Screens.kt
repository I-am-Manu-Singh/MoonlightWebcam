@file:OptIn(ExperimentalResourceApi::class)

package com.example.moonlightwebcam

// removed isAdbEnabled import
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moonlightwebcam.composeapp.generated.resources.Res
import moonlightwebcam.composeapp.generated.resources.moonlight_logo
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@Composable
fun HomeScreen(cameraManager: CameraManager, streamingServer: StreamingServer) {
        // Poll for IP Address
        // Poll for Connection Status
        var ipAddress by remember { mutableStateOf<String?>(null) }
        var networkName by remember { mutableStateOf<String?>(null) }
        var isUsbConnected by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
                while (true) {
                        ipAddress = PlatformUtils.getIpAddress()
                        networkName = PlatformUtils.getNetworkName()
                        isUsbConnected = PlatformUtils.isUsbConnected()
                        delay(300)
                }
        }

        val cameraState by cameraManager.cameraState.collectAsState()
        val theme = LocalAppTheme.current
        var isUsbMode by remember { mutableStateOf(false) }
        val clipboardManager = LocalClipboardManager.current

        // Logic for availability
        val isNetworkAvailable = ipAddress != null && ipAddress != "127.0.0.1" && ipAddress != "::1"
        val canStart = isUsbMode || isNetworkAvailable

        val snackbarHostState = remember { SnackbarHostState() }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscape = maxWidth > maxHeight
                val scrollState = rememberScrollState()

                // Fix: Use movableContentOf to preserve the Preview state (and Surface) across
                // configuration changes (Landscape <-> Portrait) which change the layout structure.
                val previewContent = remember {
                        movableContentOf {
                                HomePreviewSection(
                                        cameraManager,
                                        cameraState,
                                        theme,
                                        canStart,
                                        isUsbMode,
                                        isUsbConnected,
                                        isNetworkAvailable
                                )
                        }
                }

                if (isLandscape) {
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .verticalScroll(scrollState)
                                                .padding(horizontal = 24.dp)
                        ) {
                                Spacer(Modifier.height(60.dp))
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                        // Column 1: Preview (Wider)
                                        Column(modifier = Modifier.weight(0.9f)) {
                                                AppHeader(theme)
                                                Spacer(Modifier.height(24.dp))
                                                previewContent()
                                        }

                                        // Column 2: Controls & Status
                                        Column(modifier = Modifier.weight(1.1f)) {
                                                Spacer(Modifier.height(12.dp))
                                                HomeControlsSection(
                                                        isUsbMode = isUsbMode,
                                                        onModeChange = { isUsbMode = it },
                                                        theme = theme,
                                                        canStart = canStart,
                                                        isNetworkAvailable = isNetworkAvailable,
                                                        isUsbConnected = isUsbConnected,
                                                        cameraState = cameraState,
                                                        cameraManager = cameraManager,
                                                        streamingServer = streamingServer,
                                                        ipAddress = ipAddress,
                                                        networkName = networkName,
                                                        clipboardManager = clipboardManager,
                                                        snackbarHostState = snackbarHostState
                                                )
                                                Spacer(Modifier.height(12.dp))
                                        }
                                }
                        }
                } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .verticalScroll(scrollState)
                                                        .padding(horizontal = 24.dp)
                                ) {
                                        Spacer(Modifier.height(90.dp)) // Clear Global Top Bar

                                        // Header (Moved from Sticky)
                                        AppHeader(theme)
                                        previewContent()
                                        Spacer(Modifier.height(12.dp))
                                        HomeControlsSection(
                                                isUsbMode = isUsbMode,
                                                onModeChange = { isUsbMode = it },
                                                theme = theme,
                                                canStart = canStart,
                                                isNetworkAvailable = isNetworkAvailable,
                                                isUsbConnected = isUsbConnected,
                                                cameraState = cameraState,
                                                cameraManager = cameraManager,
                                                streamingServer = streamingServer,
                                                ipAddress = ipAddress,
                                                networkName = networkName,
                                                clipboardManager = clipboardManager,
                                                snackbarHostState = snackbarHostState
                                        )
                                        Spacer(Modifier.height(100.dp)) // Bottom padding
                                }
                        }
                }

                SnackbarHost(
                        hostState = snackbarHostState,
                        modifier =
                                Modifier.align(Alignment.BottomCenter)
                                        .padding(
                                                bottom = 110.dp
                                        ) // Clear floating bottom bar (~80dp) + margin
                ) { data ->
                        // Custom "Glass" Snackbar for better visual integration
                        Box(
                                modifier =
                                        Modifier.padding(horizontal = 16.dp)
                                                .shadow(8.dp, RoundedCornerShape(16.dp))
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                        Brush.verticalGradient(
                                                                listOf(
                                                                        Color(0xFF333333)
                                                                                .copy(
                                                                                        alpha =
                                                                                                0.95f
                                                                                ),
                                                                        Color(0xFF111111)
                                                                                .copy(alpha = 0.98f)
                                                                )
                                                        )
                                                )
                                                .border(
                                                        1.dp,
                                                        Brush.linearGradient(
                                                                listOf(
                                                                        Color.White.copy(
                                                                                alpha = 0.3f
                                                                        ),
                                                                        Color.White.copy(
                                                                                alpha = 0.1f
                                                                        )
                                                                )
                                                        ),
                                                        RoundedCornerShape(16.dp)
                                                )
                                                .clickable { data.dismiss() }
                                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                                Text(
                                        text = data.message,
                                        style =
                                                MaterialTheme.typography.body2.copy(
                                                        fontWeight = FontWeight.Medium
                                                ),
                                        color = Color.White
                                )
                        }
                }
        }
}

@Composable
fun HomePreviewSection(
        cameraManager: CameraManager,
        cameraState: CameraState,
        theme: AppTheme,
        canStart: Boolean,
        isUsbMode: Boolean,
        isUsbConnected: Boolean,
        isNetworkAvailable: Boolean
) {
        GlassBox(modifier = Modifier.fillMaxWidth()) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .aspectRatio(1.33f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black)
                ) {
                        if (cameraState.isStreaming) {

                                // ===== Desktop focus fix =====
                                val previewFocusRequester = remember { FocusRequester() }

                                // Focus ring state
                                var showFocusRing by remember { mutableStateOf(false) }
                                var focusTapOffset by remember { mutableStateOf(Offset.Zero) }
                                val focusRingAlpha = remember { Animatable(0f) }
                                val focusRingScale = remember { Animatable(1.5f) }

                                // Focus ring animation
                                LaunchedEffect(showFocusRing) {
                                        if (showFocusRing) {
                                                focusRingAlpha.snapTo(1f)
                                                focusRingScale.snapTo(1.5f)
                                                focusRingScale.animateTo(
                                                        1f,
                                                        animationSpec = tween(300)
                                                )
                                                delay(500)
                                                focusRingAlpha.animateTo(
                                                        0f,
                                                        animationSpec = tween(300)
                                                )
                                                showFocusRing = false
                                        }
                                }

                                // ===== Preview + interaction layer =====
                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .focusRequester(previewFocusRequester)
                                                        .focusable()
                                                        .pointerInput(Unit) {
                                                                detectTapGestures { offset ->
                                                                        val x =
                                                                                offset.x /
                                                                                        size.width
                                                                        val y =
                                                                                offset.y /
                                                                                        size.height
                                                                        cameraManager.focusAt(x, y)
                                                                        focusTapOffset = offset
                                                                        showFocusRing = true
                                                                }
                                                        }
                                ) {
                                        if (cameraState.currentLens == CameraLens.SCREEN) {
                                                // Screen Cast Preview Placeholder
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxSize()
                                                                        .background(
                                                                                Color(0xFF101010)
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Column(
                                                                horizontalAlignment =
                                                                        Alignment.CenterHorizontally
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.CastConnected,
                                                                        contentDescription = null,
                                                                        tint = theme.primary,
                                                                        modifier =
                                                                                Modifier.size(48.dp)
                                                                )
                                                                Spacer(Modifier.height(16.dp))
                                                                Text(
                                                                        "Screen Casting Active",
                                                                        color = Color.White,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .h6
                                                                )
                                                                Spacer(Modifier.height(8.dp))
                                                                Text(
                                                                        "Everything on your screen is being streamed",
                                                                        color =
                                                                                Color.White.copy(
                                                                                        alpha = 0.6f
                                                                                ),
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .caption
                                                                )
                                                        }
                                                }
                                        } else {
                                                CameraPreview(
                                                        modifier = Modifier.fillMaxSize(),
                                                        cameraManager = cameraManager
                                                )
                                        }

                                        // Focus ring (pure drawing, no focus involvement)
                                        if (focusRingAlpha.value > 0f) {
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                        drawCircle(
                                                                color =
                                                                        theme.primary.copy(
                                                                                alpha =
                                                                                        focusRingAlpha
                                                                                                .value
                                                                        ),
                                                                radius =
                                                                        40.dp.toPx() *
                                                                                focusRingScale
                                                                                        .value,
                                                                center = focusTapOffset,
                                                                style = Stroke(width = 2.dp.toPx())
                                                        )
                                                }
                                        }
                                }

                                // ===== Re-request focus AFTER lens changes =====
                                LaunchedEffect(cameraState.currentLens) {
                                        withFrameNanos {}
                                        previewFocusRequester.requestFocus()
                                }

                                // ===== Lens switch overlay =====
                                Box(
                                        modifier =
                                                Modifier.align(Alignment.BottomStart).padding(12.dp)
                                ) {
                                        // Replacement for IconButton (Lens Switcher)
                                        Box(
                                                modifier =
                                                        Modifier.background(
                                                                        Color.Black.copy(
                                                                                alpha = 0.6f
                                                                        ),
                                                                        CircleShape
                                                                )
                                                                .size(40.dp)
                                                                .clickable {
                                                                        val lenses =
                                                                                cameraState
                                                                                        .availableLenses
                                                                                        .filter {
                                                                                                it !=
                                                                                                        "SCREEN"
                                                                                        }
                                                                                        .mapNotNull {
                                                                                                runCatching {
                                                                                                                CameraLens
                                                                                                                        .valueOf(
                                                                                                                                it
                                                                                                                        )
                                                                                                        }
                                                                                                        .getOrNull()
                                                                                        }
                                                                        if (lenses.isNotEmpty()) {
                                                                                val currentIndex =
                                                                                        lenses.indexOf(
                                                                                                cameraState
                                                                                                        .currentLens
                                                                                        )
                                                                                val nextIndex =
                                                                                        (currentIndex +
                                                                                                1) %
                                                                                                lenses.size
                                                                                cameraManager
                                                                                        .switchCamera(
                                                                                                lenses[
                                                                                                        nextIndex]
                                                                                        )
                                                                        }
                                                                },
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector =
                                                                when (cameraState.currentLens) {
                                                                        CameraLens.FRONT ->
                                                                                Icons.Filled.Face
                                                                        CameraLens.BACK ->
                                                                                Icons.Filled.Camera
                                                                        CameraLens.ULTRA_WIDE ->
                                                                                Icons.Filled
                                                                                        .Landscape
                                                                        CameraLens.TELEPHOTO ->
                                                                                Icons.Filled.ZoomIn
                                                                        CameraLens.SCREEN ->
                                                                                Icons.Filled.Cast
                                                                },
                                                        contentDescription = "Switch Camera",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }
                                }

                                // ===== Shutter Button Overlay =====
                                Box(
                                        modifier =
                                                Modifier.align(Alignment.BottomCenter)
                                                        .padding(12.dp)
                                ) {
                                        // Replacement for FloatingActionButton
                                        Box(
                                                modifier =
                                                        Modifier.size(56.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.White)
                                                                .clickable {
                                                                        cameraManager.takePicture()
                                                                },
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        Icons.Filled.CameraAlt,
                                                        contentDescription = "Take Picture",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }
                                }

                                // ===== Mute toggle overlay =====
                                val audioManager by DependencyHolder.audioManager.collectAsState()
                                val isMuted =
                                        audioManager?.isMuted?.collectAsState()?.value ?: false

                                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                                        // Replacement for IconButton (Mute Toggle)
                                        Box(
                                                modifier =
                                                        Modifier.background(
                                                                        Color.Black.copy(
                                                                                alpha = 0.6f
                                                                        ),
                                                                        CircleShape
                                                                )
                                                                .size(40.dp)
                                                                .clickable {
                                                                        audioManager?.setMute(
                                                                                !isMuted
                                                                        )
                                                                },
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        if (isMuted) Icons.Default.MicOff
                                                        else Icons.Default.Mic,
                                                        contentDescription = "Mute Toggle",
                                                        tint =
                                                                if (isMuted) Color.Red
                                                                else Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }
                                }
                        } else {

                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                val (icon, message) =
                                                        when {
                                                                // ===== SERVER NOT RUNNING (HIGHEST
                                                                // PRIORITY) =====
                                                                !cameraState.isStreaming ->
                                                                        Icons.Default.Visibility to
                                                                                "Preview Paused"

                                                                // ===== AUTO CLOSE COUNTDOWN (HIGH
                                                                // PRIORITY) =====
                                                                cameraState.autoCloseRemaining !=
                                                                        null ->
                                                                        Icons.Default.Timer to
                                                                                "Waiting for client… (Auto-close in ${cameraState.autoCloseRemaining}s)"

                                                                // ===== USB MODE (ONLY WHEN
                                                                // STREAMING) =====
                                                                isUsbMode && !isUsbConnected ->
                                                                        Icons.Outlined.Usb to
                                                                                "Connect USB cable"
                                                                isUsbMode &&
                                                                        !PlatformUtils
                                                                                .isAdbEnabled() ->
                                                                        Icons.Outlined.Usb to
                                                                                "Enable USB debugging"
                                                                isUsbMode -> {
                                                                        if (PlatformUtils
                                                                                        .isAndroid()
                                                                        ) {
                                                                                Icons.Filled
                                                                                        .Android to
                                                                                        "Run: adb forward tcp:8080 tcp:8080"
                                                                        } else {
                                                                                Icons.Filled
                                                                                        .PhoneIphone to
                                                                                        "Run: iproxy 8080 8080"
                                                                        }
                                                                }

                                                                // ===== WIFI MODE (ONLY WHEN
                                                                // STREAMING) =====
                                                                !isUsbMode && !isNetworkAvailable ->
                                                                        Icons.Default.WifiOff to
                                                                                "No Network"

                                                                // ===== STREAMING BUT WAITING =====
                                                                else -> {
                                                                        Icons.Default.Visibility to
                                                                                "Waiting for client…"
                                                                }
                                                        }

                                                Icon(
                                                        icon,
                                                        contentDescription = null,
                                                        tint =
                                                                theme.textPrimary.copy(
                                                                        alpha = 0.35f
                                                                ),
                                                        modifier = Modifier.size(48.dp)
                                                )

                                                Spacer(Modifier.height(8.dp))

                                                Text(
                                                        message,
                                                        color = theme.textPrimary.copy(alpha = 0.6f)
                                                )
                                        }
                                }
                        }

                        // ===== Status badge =====
                        Row(
                                modifier =
                                        Modifier.align(Alignment.TopStart)
                                                .padding(12.dp)
                                                .background(
                                                        Color.Black.copy(alpha = 0.6f),
                                                        RoundedCornerShape(50)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Box(
                                        modifier =
                                                Modifier.size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                if (cameraState.isStreaming)
                                                                        Color.Green
                                                                else Color.Red
                                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                        if (cameraState.isStreaming) "LIVE" else "OFFLINE",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        // ===== PIN badge =====
                        if (cameraState.isStreaming) {
                                val pin by DependencyHolder.pin.collectAsState()

                                Row(
                                        modifier =
                                                Modifier.align(Alignment.TopEnd)
                                                        .padding(12.dp)
                                                        .background(
                                                                Color.Black.copy(alpha = 0.6f),
                                                                RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(
                                                                horizontal = 10.dp,
                                                                vertical = 4.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Icon(
                                                Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = Color.Yellow,
                                                modifier = Modifier.size(12.dp)
                                        )

                                        Spacer(Modifier.width(6.dp))

                                        Text(
                                                text = "PIN: $pin",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        }
                }
        }
}

@Composable
fun AppLogo(modifier: Modifier = Modifier) {
        Image(
                painter = painterResource(Res.drawable.moonlight_logo),
                contentDescription = "App Logo",
                modifier = modifier,
                contentScale = ContentScale.Fit
        )
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun AppHeader(theme: AppTheme) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
                Box(
                        modifier =
                                Modifier.size(50.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                                Brush.linearGradient(
                                                        listOf(theme.primary, theme.secondary)
                                                )
                                        )
                                        .padding(6.dp)
                ) {
                        AppLogo(
                                modifier = Modifier.fillMaxSize(),
                                //                        tint = Color.White
                                )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                        "Moonlight",
                        style = MaterialTheme.typography.h5.copy(fontWeight = FontWeight.Bold),
                        color = theme.textPrimary
                )
                Text(
                        "Webcam",
                        style = MaterialTheme.typography.h5.copy(fontWeight = FontWeight.Light),
                        color = theme.textSecondary
                )
        }
}

@Composable
fun HomeControlsSection(
        isUsbMode: Boolean,
        onModeChange: (Boolean) -> Unit,
        theme: AppTheme,
        canStart: Boolean,
        isNetworkAvailable: Boolean,
        isUsbConnected: Boolean,
        cameraState: CameraState,
        cameraManager: CameraManager,
        streamingServer: StreamingServer,
        ipAddress: String?,
        networkName: String?,
        clipboardManager: ClipboardManager,
        snackbarHostState: SnackbarHostState
) {
        val displayIp = if (isUsbMode) "127.0.0.1" else ipAddress
        val httpUrl: String? =
                if (cameraState.isStreaming && displayIp != null) "http://$displayIp:8080" else null

        val shakeAnim = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

                /* ── Top Level Mode Switch (Webcam vs Screen) ── */
                val isScreen = cameraState.currentLens == CameraLens.SCREEN
                /* ── Main Mode Selection (Webcam vs Cast) ── */
                Row(
                        modifier =
                                Modifier.widthIn(max = 280.dp)
                                        .align(Alignment.CenterHorizontally)
                                        .border(
                                                1.dp,
                                                theme.textPrimary.copy(alpha = 0.15f),
                                                RoundedCornerShape(28.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                        val webcamSelected = !isScreen

                        TabButton(
                                text = "WEBCAM",
                                isSelected = webcamSelected,
                                onClick = {
                                        if (isScreen) cameraManager.switchCamera(CameraLens.BACK)
                                },
                                activeColor = theme.primary,
                                modifier = Modifier.weight(1f)
                        )

                        val screenSelected = isScreen

                        TabButton(
                                text = "CAST",
                                isSelected = screenSelected,
                                onClick = {
                                        if (!isScreen) cameraManager.switchCamera(CameraLens.SCREEN)
                                },
                                activeColor = theme.primary,
                                modifier = Modifier.weight(1f)
                        )
                }
                Spacer(Modifier.height(8.dp))

                /* ── Connection Type (Wi-Fi vs USB) ── */
                Row(
                        modifier =
                                Modifier.wrapContentWidth() // makes second row compact
                                        .align(
                                                Alignment.CenterHorizontally
                                        ) // centers it under the first row
                                        .border(
                                                1.dp,
                                                theme.textPrimary.copy(alpha = 0.12f),
                                                RoundedCornerShape(28.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                        TabButton(
                                text = "Wi-Fi",
                                isSelected = !isUsbMode,
                                onClick = { onModeChange(false) },
                                activeColor = theme.secondary
                        )

                        TabButton(
                                text = "USB",
                                isSelected = isUsbMode,
                                onClick = { onModeChange(true) },
                                activeColor = theme.secondary
                        )
                }

                if (PlatformUtils.isAndroid()) {
                        // 1. Accessibility for Screen Control
                        if (isScreen) {
                                Spacer(Modifier.height(8.dp))
                                // Replacement for OutlinedButton (Accessibility)
                                Box(
                                        modifier =
                                                Modifier.height(36.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .border(
                                                                BorderStroke(
                                                                        1.dp,
                                                                        theme.secondary.copy(
                                                                                alpha = 0.5f
                                                                        )
                                                                ),
                                                                RoundedCornerShape(16.dp)
                                                        )
                                                        .clickable {
                                                                PlatformUtils
                                                                        .openAccessibilitySettings()
                                                        }
                                                        .padding(
                                                                horizontal = 12.dp,
                                                                vertical = 6.dp
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                        Icons.Default.Settings,
                                                        null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = theme.secondary
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                        "Enable Remote Input",
                                                        fontSize = 12.sp,
                                                        color = theme.secondary
                                                )
                                        }
                                }
                        }

                        // 2. USB Debugging for USB Mode
                        if (isUsbMode && !PlatformUtils.isAdbEnabled()) {
                                Spacer(Modifier.height(8.dp))
                                // Replacement for OutlinedButton (Developer Settings)
                                Box(
                                        modifier =
                                                Modifier.height(36.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .border(
                                                                BorderStroke(
                                                                        1.dp,
                                                                        theme.primary.copy(
                                                                                alpha = 0.5f
                                                                        )
                                                                ),
                                                                RoundedCornerShape(16.dp)
                                                        )
                                                        .clickable {
                                                                PlatformUtils
                                                                        .openDeveloperSettings()
                                                        }
                                                        .padding(
                                                                horizontal = 12.dp,
                                                                vertical = 6.dp
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                        Icons.Default.Build,
                                                        null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = theme.primary
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                        "Enable USB Debugging",
                                                        fontSize = 12.sp,
                                                        color = theme.primary
                                                )
                                        }
                                }
                        }
                }

                Spacer(Modifier.height(8.dp))

                /* ── Control + Status ── */
                GlassBox(modifier = Modifier.fillMaxWidth()) {
                        Column {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        val canStart =
                                                if (isUsbMode) isUsbConnected
                                                else isNetworkAvailable
                                        val isBlocked = !cameraState.isStreaming && !canStart

                                        LaunchedEffect(
                                                isNetworkAvailable,
                                                isUsbMode,
                                                isUsbConnected
                                        ) {
                                                if (!isUsbMode &&
                                                                !isNetworkAvailable &&
                                                                cameraState.isStreaming
                                                ) {
                                                        cameraManager.stopCamera()
                                                        streamingServer.stop()
                                                }
                                                if (isUsbMode &&
                                                                !isUsbConnected &&
                                                                cameraState.isStreaming
                                                ) {
                                                        cameraManager.stopCamera()
                                                        streamingServer.stop()
                                                }
                                        }

                                        fun triggerShake() {
                                                if (shakeAnim.isRunning) return
                                                coroutineScope.launch {
                                                        shakeAnim.snapTo(0f)
                                                        repeat(3) {
                                                                shakeAnim.animateTo(-6f, tween(40))
                                                                shakeAnim.animateTo(6f, tween(40))
                                                        }
                                                        shakeAnim.animateTo(0f)
                                                }
                                        }

                                        val bgColor =
                                                when {
                                                        cameraState.isStreaming ->
                                                                Color.Red.copy(alpha = 0.18f)
                                                        isBlocked -> Color.Gray.copy(alpha = 0.25f)
                                                        else -> theme.primary.copy(alpha = 0.18f)
                                                }

                                        val iconTint =
                                                when {
                                                        cameraState.isStreaming -> Color.Red
                                                        isBlocked ->
                                                                theme.textSecondary.copy(
                                                                        alpha = 0.5f
                                                                )
                                                        else -> theme.primary
                                                }

                                        // Replacement for IconButton (Start/Stop)
                                        Box(
                                                modifier =
                                                        Modifier.size(56.dp)
                                                                .graphicsLayer {
                                                                        translationX =
                                                                                shakeAnim.value
                                                                        alpha =
                                                                                if (isBlocked) 0.55f
                                                                                else 1f
                                                                }
                                                                .background(
                                                                        bgColor,
                                                                        RoundedCornerShape(16.dp)
                                                                )
                                                                .clickable {
                                                                        when {
                                                                                cameraState
                                                                                        .isStreaming -> {
                                                                                        cameraManager
                                                                                                .stopCamera()
                                                                                        streamingServer
                                                                                                .stop()
                                                                                }
                                                                                isBlocked ->
                                                                                        triggerShake()
                                                                                else -> {
                                                                                        cameraManager
                                                                                                .startCamera()
                                                                                        streamingServer
                                                                                                .start()
                                                                                }
                                                                        }
                                                                },
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        if (cameraState.isStreaming)
                                                                Icons.Default.Stop
                                                        else Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = iconTint,
                                                        modifier = Modifier.size(28.dp)
                                                )
                                        }

                                        Spacer(Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                                val (statusHint, statusColor) =
                                                        when {
                                                                !cameraState.isStreaming ->
                                                                        "Server stopped" to
                                                                                theme.textSecondary
                                                                !isUsbMode && !isNetworkAvailable ->
                                                                        "${networkName ?: "Wi-Fi"} unavailable" to
                                                                                Color.Red.copy(
                                                                                        alpha =
                                                                                                0.85f
                                                                                )
                                                                isUsbMode && !isUsbConnected ->
                                                                        "Connect USB cable first" to
                                                                                Color.Red.copy(
                                                                                        alpha =
                                                                                                0.85f
                                                                                )
                                                                cameraState.isClientConnected ->
                                                                        "Connected to ${cameraState.clientName ?: "Device"}" to
                                                                                Color.Green
                                                                isUsbMode ->
                                                                        "USB connected" to
                                                                                theme.textPrimary
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.7f
                                                                                        )
                                                                else ->
                                                                        "Waiting for client…" to
                                                                                theme.textSecondary
                                                        }

                                                Text(
                                                        statusHint,
                                                        style = MaterialTheme.typography.h6,
                                                        color = statusColor
                                                )

                                                Spacer(Modifier.height(12.dp))

                                                val connectionText =
                                                        when {
                                                                !cameraState.isStreaming ->
                                                                        "Server stopped"
                                                                cameraState.isClientConnected ->
                                                                        when (cameraState
                                                                                        .streamingInterface
                                                                        ) {
                                                                                StreamingInterface
                                                                                        .USB ->
                                                                                        "Connected to: ${cameraState.clientName ?: "USB Client"}"
                                                                                StreamingInterface
                                                                                        .WIFI ->
                                                                                        "Connected to: ${cameraState.clientName ?: "Wi-Fi Client"}"
                                                                                else ->
                                                                                        "Connected to: ${cameraState.clientName ?: "Client"}"
                                                                        }
                                                                cameraState.autoCloseRemaining !=
                                                                        null ->
                                                                        "Auto-close in ${cameraState.autoCloseRemaining}s"
                                                                isUsbMode ->
                                                                        "Waiting for USB client…"
                                                                else -> "Ready to connect"
                                                        }

                                                Text(
                                                        connectionText,
                                                        style = MaterialTheme.typography.caption,
                                                        color =
                                                                if (cameraState.isClientConnected)
                                                                        Color.Green
                                                                else theme.textSecondary
                                                )

                                                if (!isUsbMode &&
                                                                !cameraState.isClientConnected &&
                                                                isNetworkAvailable
                                                ) {
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                                "Ensure firewall allows Port 8080",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .caption.copy(
                                                                                fontSize = 10.sp
                                                                        ),
                                                                color =
                                                                        theme.textSecondary.copy(
                                                                                alpha = 0.6f
                                                                        )
                                                        )
                                                }
                                        }
                                }

                                /* ── Connection Info Container ── */
                                val isScreen = cameraState.currentLens == CameraLens.SCREEN
                                if (httpUrl != null || isScreen) {

                                        Spacer(Modifier.height(12.dp))

                                        val usbCommand =
                                                if (isUsbMode &&
                                                                isUsbConnected &&
                                                                cameraState.isStreaming
                                                ) {
                                                        when {
                                                                PlatformUtils.isAndroid() ->
                                                                        "adb forward tcp:8080 tcp:8080"
                                                                PlatformUtils.isIos() ->
                                                                        "iproxy 8080 8080"
                                                                else -> null
                                                        }
                                                } else null

                                        val displayLabel =
                                                if (isScreen) "Screen Cast" else "Webcam Stream"
                                        val displayIcon =
                                                if (isScreen) Icons.Default.CastConnected
                                                else Icons.Filled.Wifi

                                        Column(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(
                                                                        theme.textPrimary.copy(
                                                                                alpha = 0.06f
                                                                        )
                                                                )
                                                                .padding(
                                                                        horizontal = 12.dp,
                                                                        vertical = 10.dp
                                                                ),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                                ConnectionStatusRow(
                                                        icon = displayIcon,
                                                        label = displayLabel,
                                                        status =
                                                                if (cameraState.isPaused) {
                                                                        "Paused"
                                                                } else if (cameraState
                                                                                .isClientConnected
                                                                ) {
                                                                        "Connected to: ${cameraState.clientName ?: "Desktop"}"
                                                                } else {
                                                                        httpUrl
                                                                                ?: if (isScreen)
                                                                                        "Waiting for client..."
                                                                                else "Ready"
                                                                },
                                                        theme = theme,
                                                        isStreaming = cameraState.isStreaming,
                                                        isPaused = cameraState.isPaused,
                                                        onTogglePause = {
                                                                cameraManager.togglePause()
                                                        },
                                                        onToggleStream = {
                                                                if (isScreen) {
                                                                        if (cameraState.isStreaming
                                                                        ) {
                                                                                PlatformUtils
                                                                                        .stopService()
                                                                                // cameraManager.stopCamera()
                                                                                // Keep server
                                                                                // running for
                                                                                // persistent
                                                                                // connection
                                                                                // streamingServer.stop()
                                                                        } else {
                                                                                // Ensure server is
                                                                                // running
                                                                                streamingServer
                                                                                        .start()
                                                                                cameraManager
                                                                                        .startCamera()
                                                                        }
                                                                } else {
                                                                        if (cameraState.isStreaming
                                                                        ) {
                                                                                PlatformUtils
                                                                                        .stopService()
                                                                                // Camera and Server
                                                                                // will be stopped
                                                                                // by the Service or
                                                                                // CameraManager
                                                                                // cleanup
                                                                        } else {
                                                                                cameraManager
                                                                                        .startCamera()
                                                                        }
                                                                }
                                                        },
                                                        copyUrl = httpUrl,
                                                        onCopy =
                                                                if (httpUrl != null) {
                                                                        {
                                                                                clipboardManager
                                                                                        .setText(
                                                                                                AnnotatedString(
                                                                                                        httpUrl
                                                                                                )
                                                                                        )
                                                                        }
                                                                } else null
                                                )

                                                if (usbCommand != null) {
                                                        Divider(
                                                                color =
                                                                        theme.textPrimary.copy(
                                                                                alpha = 0.12f
                                                                        )
                                                        )
                                                        ConnectionCommandRow(
                                                                command = usbCommand,
                                                                theme = theme,
                                                                onCopy = {
                                                                        clipboardManager.setText(
                                                                                AnnotatedString(
                                                                                        usbCommand
                                                                                )
                                                                        )
                                                                }
                                                        )
                                                }
                                        }
                                }

                                Spacer(Modifier.height(10.dp))

                                val showUsbWarning = isUsbMode && !isUsbConnected
                                val showWifiWarning = !isUsbMode && !isNetworkAvailable

                                if (showUsbWarning || showWifiWarning) {
                                        val (icon, message) =
                                                if (showUsbWarning)
                                                        Icons.Default.DeveloperMode to
                                                                "Connect USB cable and enable USB debugging."
                                                else
                                                        Icons.Default.WifiOff to
                                                                "Connect to Wi-Fi or switch to " +
                                                                        "Mobile Data."

                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .background(
                                                                        Color.Red.copy(
                                                                                alpha = 0.2f
                                                                        ),
                                                                        RoundedCornerShape(12.dp)
                                                                )
                                                                .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        icon,
                                                        null,
                                                        tint = Color.Red,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                        message,
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.caption
                                                )
                                        }

                                        Spacer(Modifier.height(16.dp))
                                }
                        }
                }
        }
}

@Composable
fun ControlsScreen(
        cameraManager: CameraManager,
        currentTheme: AppTheme,
        onThemeChange: (AppTheme) -> Unit
) {
        val cameraState by cameraManager.cameraState.collectAsState()
        val theme = LocalAppTheme.current

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscape = maxWidth > maxHeight
                val scrollState = rememberScrollState()
                if (isLandscape) {
                        val leftScroll = rememberScrollState()
                        val rightScroll = rememberScrollState()

                        Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                // ── LEFT COLUMN ─────────────────────────────
                                Column(modifier = Modifier.weight(1f).verticalScroll(leftScroll)) {
                                        Spacer(Modifier.height(100.dp)) // ✅ now scrolls
                                        ThemeSection(currentTheme, onThemeChange, theme)
                                        Spacer(Modifier.height(16.dp))
                                        LensSection(cameraManager, cameraState, theme)
                                        Spacer(Modifier.height(24.dp))
                                }

                                // ── RIGHT COLUMN ────────────────────────────
                                Column(modifier = Modifier.weight(1f).verticalScroll(rightScroll)) {
                                        Spacer(Modifier.height(100.dp)) // ✅ now scrolls
                                        VideoSettingsSection(cameraManager, cameraState, theme)
                                        Spacer(Modifier.height(16.dp))
                                        CameraAdjustmentsSection(cameraManager, cameraState, theme)
                                        Spacer(Modifier.height(24.dp))
                                }
                        }
                } else {
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(horizontal = 24.dp)
                                                .verticalScroll(scrollState),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                        ) {
                                Spacer(Modifier.height(120.dp))
                                ThemeSection(currentTheme, onThemeChange, theme)
                                Spacer(Modifier.height(16.dp))
                                LensSection(cameraManager, cameraState, theme)
                                Spacer(Modifier.height(16.dp))
                                VideoSettingsSection(cameraManager, cameraState, theme)
                                Spacer(Modifier.height(16.dp))
                                CameraAdjustmentsSection(cameraManager, cameraState, theme)
                                Spacer(Modifier.height(120.dp))
                        }
                }
        }
}

@Composable
fun ThemeSection(currentTheme: AppTheme, onThemeChange: (AppTheme) -> Unit, theme: AppTheme) {
        GlassBox(modifier = Modifier.fillMaxWidth()) {
                Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Filled.Palette,
                                        "Theme",
                                        tint = theme.secondary,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text("Appearance", color = theme.secondary)
                        }
                        Spacer(Modifier.height(16.dp))

                        val scrollState = rememberScrollState()
                        val density = LocalDensity.current

                        // Auto-scroll to current theme
                        LaunchedEffect(currentTheme) {
                                val themeIndex = AppTheme.entries.indexOf(currentTheme)
                                if (themeIndex >= 0) {
                                        // Item width (110.dp) + spacing (16.dp) = 126.dp
                                        val itemSize = with(density) { 126.dp.toPx() }
                                        val targetScroll = (themeIndex * itemSize).toInt()
                                        scrollState.animateScrollTo(targetScroll)
                                }
                        }

                        Row(
                                modifier = Modifier.horizontalScroll(scrollState),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                AppTheme.entries.forEach { t ->
                                        val isSelected = currentTheme == t

                                        // ---- Animations ----
                                        val scale by
                                                animateFloatAsState(
                                                        targetValue = if (isSelected) 1.15f else 1f,
                                                        label = "ThemeScale"
                                                )

                                        val borderColor by
                                                animateColorAsState(
                                                        targetValue =
                                                                if (isSelected) t.primary
                                                                else
                                                                        theme.textPrimary.copy(
                                                                                alpha = 0.2f
                                                                        ),
                                                        label = "ThemeBorder"
                                                )

                                        Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier =
                                                        Modifier.width(
                                                                110.dp
                                                        ) // <- fixed width = even spacing
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(
                                                                                width = 96.dp,
                                                                                height = 64.dp
                                                                        )
                                                                        .scale(scale)
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        16.dp
                                                                                )
                                                                        )
                                                                        // Actual theme background
                                                                        // preview
                                                                        .background(
                                                                                Brush.verticalGradient(
                                                                                        colors =
                                                                                                listOf(
                                                                                                        t.backgroundStart,
                                                                                                        t.backgroundEnd
                                                                                                )
                                                                                )
                                                                        )
                                                                        .border(
                                                                                width = 2.dp,
                                                                                color = borderColor,
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                16.dp
                                                                                        )
                                                                        )
                                                                        .clickable {
                                                                                onThemeChange(t)
                                                                        }
                                                ) {

                                                        // Accent strip showing primary → secondary
                                                        // intent
                                                        Box(
                                                                modifier =
                                                                        Modifier.align(
                                                                                        Alignment
                                                                                                .BottomCenter
                                                                                )
                                                                                .fillMaxWidth()
                                                                                .height(
                                                                                        22.dp
                                                                                ) // <-- taller
                                                                                // bottom section
                                                                                .clip(
                                                                                        RoundedCornerShape(
                                                                                                bottomStart =
                                                                                                        16.dp,
                                                                                                bottomEnd =
                                                                                                        16.dp
                                                                                        )
                                                                                )
                                                                                .background(
                                                                                        Brush.horizontalGradient(
                                                                                                colors =
                                                                                                        listOf(
                                                                                                                t.primary,
                                                                                                                t.secondary
                                                                                                        )
                                                                                        )
                                                                                )
                                                        )
                                                }

                                                Spacer(Modifier.height(6.dp))

                                                // Fixed height label = no layout jumping
                                                Box(
                                                        modifier = Modifier.height(28.dp),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Text(
                                                                text = t.title,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .caption,
                                                                color =
                                                                        if (isSelected)
                                                                                theme.textPrimary
                                                                        else
                                                                                theme.textPrimary
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.6f
                                                                                        ),
                                                                textAlign = TextAlign.Center
                                                        )
                                                }
                                        }
                                }
                        }
                }
        }
}

@Composable
fun LensSection(cameraManager: CameraManager, cameraState: CameraState, theme: AppTheme) {
        GlassBox(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                        // Dynamic Lens Selection
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Filled.Cameraswitch,
                                        null,
                                        tint = theme.secondary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Lens Selection", color = theme.secondary)
                        }
                        Spacer(Modifier.height(16.dp))

                        // Screen Mirroring Toggle
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clickable {
                                                        val newLens =
                                                                if (cameraState.currentLens ==
                                                                                CameraLens.SCREEN
                                                                )
                                                                        CameraLens.BACK
                                                                else CameraLens.SCREEN
                                                        cameraManager.switchCamera(newLens)
                                                }
                                                .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                        Icons.Default.CastConnected,
                                        null,
                                        tint = theme.secondary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                "Screen Mirroring",
                                                color = theme.textPrimary,
                                                style = MaterialTheme.typography.body1
                                        )
                                        Text(
                                                "Cast your screen",
                                                color = theme.textSecondary,
                                                style = MaterialTheme.typography.caption
                                        )
                                }
                                // Replacement for Switch (Screen Mirroring)
                                val isChecked = cameraState.currentLens == CameraLens.SCREEN
                                Box(
                                        modifier =
                                                Modifier.size(50.dp, 30.dp)
                                                        .clip(RoundedCornerShape(15.dp))
                                                        .background(
                                                                if (isChecked) theme.primary
                                                                else
                                                                        theme.textSecondary.copy(
                                                                                alpha = 0.3f
                                                                        )
                                                        )
                                                        .clickable {
                                                                val newLens =
                                                                        if (isChecked)
                                                                                CameraLens.BACK
                                                                        else CameraLens.SCREEN
                                                                cameraManager.switchCamera(newLens)
                                                        }
                                                        .padding(2.dp),
                                        contentAlignment =
                                                if (isChecked) Alignment.CenterEnd
                                                else Alignment.CenterStart
                                ) {
                                        Box(
                                                modifier =
                                                        Modifier.size(26.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.White)
                                        )
                                }
                        }
                        Spacer(Modifier.height(16.dp))

                        // Grid of Lenses (excluding SCREEN)
                        val chunkedLenses =
                                cameraState.availableLenses.filter { it != "SCREEN" }.chunked(2)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                chunkedLenses.forEach { rowLenses ->
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                                rowLenses.forEach { lensName ->
                                                        // Replacement for Button (Lens Selection)
                                                        val lens =
                                                                runCatching {
                                                                        CameraLens.valueOf(lensName)
                                                                }
                                                                        .getOrElse {
                                                                                CameraLens.BACK
                                                                        }
                                                        val isSelected =
                                                                cameraState.currentLens == lens

                                                        Box(
                                                                modifier =
                                                                        Modifier.weight(1f)
                                                                                .height(
                                                                                        IntrinsicSize
                                                                                                .Min
                                                                                )
                                                                                .clip(
                                                                                        RoundedCornerShape(
                                                                                                12.dp
                                                                                        )
                                                                                )
                                                                                .background(
                                                                                        if (isSelected
                                                                                        )
                                                                                                theme.primary
                                                                                        else
                                                                                                theme.textPrimary
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.1f
                                                                                                        )
                                                                                )
                                                                                .clickable {
                                                                                        cameraManager
                                                                                                .switchCamera(
                                                                                                        lens
                                                                                                )
                                                                                }
                                                                                .padding(8.dp),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth(),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {

                                                                        // ICON LEFT
                                                                        Icon(
                                                                                imageVector =
                                                                                        when (lens
                                                                                        ) {
                                                                                                CameraLens
                                                                                                        .FRONT ->
                                                                                                        Icons.Filled
                                                                                                                .Face
                                                                                                CameraLens
                                                                                                        .BACK ->
                                                                                                        Icons.Filled
                                                                                                                .Camera
                                                                                                CameraLens
                                                                                                        .ULTRA_WIDE ->
                                                                                                        Icons.Filled
                                                                                                                .Landscape
                                                                                                CameraLens
                                                                                                        .TELEPHOTO ->
                                                                                                        Icons.Filled
                                                                                                                .ZoomIn
                                                                                                CameraLens
                                                                                                        .SCREEN ->
                                                                                                        Icons.Filled
                                                                                                                .Cast
                                                                                        },
                                                                                contentDescription =
                                                                                        null,
                                                                                tint =
                                                                                        if (isSelected
                                                                                        )
                                                                                                Color.White
                                                                                        else
                                                                                                theme.textSecondary,
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                        start =
                                                                                                                6.dp
                                                                                                ) // ← the fix you wanted
                                                                                                .size(
                                                                                                        20.dp
                                                                                                )
                                                                                                .align(
                                                                                                        Alignment
                                                                                                                .CenterVertically
                                                                                                )
                                                                        )

                                                                        // CENTER TEXT
                                                                        // COLUMN (REAL FIX)
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .padding(
                                                                                                        start =
                                                                                                                8.dp
                                                                                                ),
                                                                                contentAlignment =
                                                                                        Alignment
                                                                                                .Center
                                                                        ) {
                                                                                Column(
                                                                                        horizontalAlignment =
                                                                                                Alignment
                                                                                                        .CenterHorizontally
                                                                                ) {
                                                                                        Text(
                                                                                                text =
                                                                                                        when (lens
                                                                                                        ) {
                                                                                                                CameraLens
                                                                                                                        .FRONT ->
                                                                                                                        "Front"
                                                                                                                CameraLens
                                                                                                                        .BACK ->
                                                                                                                        "Main"
                                                                                                                CameraLens
                                                                                                                        .ULTRA_WIDE ->
                                                                                                                        "Ultrawide"
                                                                                                                CameraLens
                                                                                                                        .TELEPHOTO ->
                                                                                                                        "Telephoto"
                                                                                                                CameraLens
                                                                                                                        .SCREEN ->
                                                                                                                        "Screen"
                                                                                                        },
                                                                                                style =
                                                                                                        MaterialTheme
                                                                                                                .typography
                                                                                                                .caption,
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Bold
                                                                                        )

                                                                                        Text(
                                                                                                text =
                                                                                                        when (lens
                                                                                                        ) {
                                                                                                                CameraLens
                                                                                                                        .FRONT ->
                                                                                                                        "1.0x"
                                                                                                                CameraLens
                                                                                                                        .BACK ->
                                                                                                                        "1.0x"
                                                                                                                CameraLens
                                                                                                                        .ULTRA_WIDE ->
                                                                                                                        "0.5x"
                                                                                                                CameraLens
                                                                                                                        .TELEPHOTO ->
                                                                                                                        "2.0x"
                                                                                                                CameraLens
                                                                                                                        .SCREEN ->
                                                                                                                        "Fit"
                                                                                                        },
                                                                                                style =
                                                                                                        MaterialTheme
                                                                                                                .typography
                                                                                                                .caption,
                                                                                                fontSize =
                                                                                                        10.sp,
                                                                                                color =
                                                                                                        if (isSelected
                                                                                                        )
                                                                                                                Color.White
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.8f
                                                                                                                        )
                                                                                                        else
                                                                                                                theme.textSecondary
                                                                                        )
                                                                                }
                                                                        }

                                                                        // RIGHT-SIDE
                                                                        // INVISIBLE SPACER
                                                                        // to keep text
                                                                        // centered
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                20.dp
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                }
                                        }

                                        if (rowLenses.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                        }
                                }
                        }
                }
        }
}

@Composable
fun VideoSettingsSection(cameraManager: CameraManager, cameraState: CameraState, theme: AppTheme) {
        GlassBox {
                Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Filled.Equalizer,
                                        null,
                                        tint = theme.secondary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Quality Control", color = theme.secondary)
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                        ) {
                                Row(
                                        modifier =
                                                Modifier.wrapContentWidth()
                                                        .background(
                                                                theme.textPrimary.copy(
                                                                        alpha = 0.05f
                                                                ),
                                                                RoundedCornerShape(28.dp)
                                                        )
                                                        .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                        val isStream = cameraState.streamMode == StreamMode.STREAM

                                        TabButton(
                                                "Camera",
                                                !isStream,
                                                { cameraManager.setStreamMode(StreamMode.CAMERA) },
                                                theme.primary
                                        )

                                        TabButton(
                                                "Stream",
                                                isStream,
                                                { cameraManager.setStreamMode(StreamMode.STREAM) },
                                                theme.primary
                                        )
                                }
                        }
                        Spacer(Modifier.height(16.dp))

                        // Resolution (Always show, applies to current context)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Filled.AspectRatio,
                                        null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                        "Resolution",
                                        color = theme.textPrimary,
                                        style = MaterialTheme.typography.caption
                                )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                cameraState.availableResolutions.forEach { cameraRes ->
                                        // Replacement for Button (Resolution)
                                        Box(
                                                modifier =
                                                        Modifier.height(36.dp)
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(
                                                                        if (cameraRes ==
                                                                                        cameraState
                                                                                                .currentResolution
                                                                        )
                                                                                theme.primary
                                                                        else
                                                                                theme.textPrimary
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.1f
                                                                                        )
                                                                )
                                                                .clickable {
                                                                        cameraManager.setResolution(
                                                                                cameraRes
                                                                        )
                                                                }
                                                                .padding(
                                                                        horizontal = 16.dp,
                                                                        vertical = 8.dp
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Text(
                                                        cameraRes.label,
                                                        style = MaterialTheme.typography.caption,
                                                        color =
                                                                if (cameraRes ==
                                                                                cameraState
                                                                                        .currentResolution
                                                                )
                                                                        Color.White
                                                                else theme.textPrimary
                                                )
                                        }
                                }
                        }
                        Spacer(Modifier.height(16.dp))

                        if (cameraState.streamMode == StreamMode.STREAM) {
                                // FPS (Stream Only)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                Icons.Filled.Speed,
                                                null,
                                                tint = theme.primary,
                                                modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                                "FPS",
                                                color = theme.textPrimary,
                                                style = MaterialTheme.typography.caption
                                        )
                                }

                                Spacer(Modifier.height(8.dp))
                                Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        cameraState.availableFps.forEach { frameRate ->
                                                // Replacement for Button (FPS)
                                                Box(
                                                        modifier =
                                                                Modifier.height(36.dp)
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        12.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                if (frameRate ==
                                                                                                cameraState
                                                                                                        .currentFps
                                                                                )
                                                                                        theme.primary
                                                                                else
                                                                                        theme.textPrimary
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.1f
                                                                                                )
                                                                        )
                                                                        .clickable {
                                                                                cameraManager
                                                                                        .setFps(
                                                                                                frameRate
                                                                                        )
                                                                        }
                                                                        .padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 8.dp
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Text(
                                                                "$frameRate FPS",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .caption,
                                                                color =
                                                                        if (frameRate ==
                                                                                        cameraState
                                                                                                .currentFps
                                                                        )
                                                                                Color.White
                                                                        else theme.textPrimary
                                                        )
                                                }
                                        }
                                }
                        } else {
                                // JPEG Quality (Camera Only)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                Icons.Filled.HighQuality,
                                                null,
                                                tint = theme.primary,
                                                modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                                "Quality: ${cameraState.jpegQuality}",
                                                color = theme.textSecondary
                                        )
                                }

                                ExpressiveSlider(
                                        value = cameraState.jpegQuality.toFloat(),
                                        onValueChange = {
                                                cameraManager.setJpegQuality(it.toInt())
                                        },
                                        valueRange = 10f..100f,
                                        steps = 90,
                                        thumbColor = theme.primary
                                )
                        }
                }
        }
}

@Composable
fun CameraAdjustmentsSection(
        cameraManager: CameraManager,
        cameraState: CameraState,
        theme: AppTheme
) {
        GlassBox {
                Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Filled.Tune,
                                        null,
                                        tint = theme.secondary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Camera Control", color = theme.secondary)
                        }
                        Spacer(Modifier.height(16.dp))

                        // Zoom
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Filled.ZoomIn,
                                        null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                        "Zoom: ${(cameraState.zoomRatio * 10).toInt() / 10f}x",
                                        color = theme.textPrimary
                                )
                        }

                        ExpressiveSlider(
                                value = cameraState.zoomRatio,
                                onValueChange = { cameraManager.setZoom(it) },
                                valueRange = 1f..cameraState.maxZoom,
                                steps = ((cameraState.maxZoom - 1f) / 0.1f).toInt(),
                                thumbColor = theme.primary
                        )
                        Spacer(Modifier.height(16.dp))

                        // Exposure
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Filled.Settings,
                                        null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                        "Exposure: ${cameraState.exposureIndex}",
                                        color = theme.textPrimary
                                )
                        }

                        ExpressiveSlider(
                                value = cameraState.exposureIndex.toFloat(),
                                onValueChange = { cameraManager.setExposure(it.toInt()) },
                                valueRange =
                                        cameraState.minExposure.toFloat()..cameraState.maxExposure
                                                        .toFloat(),
                                steps = 20,
                                thumbColor = theme.primary
                        )
                        Spacer(Modifier.height(16.dp))

                        // Brightness
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Filled.BrightnessAuto,
                                        null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                        "Brightness: ${cameraState.brightness}",
                                        color = theme.textPrimary
                                )
                        }
                        ExpressiveSlider(
                                value = cameraState.brightness.toFloat(),
                                onValueChange = { cameraManager.setBrightness(it.toInt()) },
                                valueRange = -100f..100f,
                                steps = 19, // Every 10 units
                                thumbColor = theme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        // Contast
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Filled.Contrast,
                                        null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Contrast: ${cameraState.contrast}", color = theme.textPrimary)
                        }
                        ExpressiveSlider(
                                value = cameraState.contrast.toFloat(),
                                onValueChange = { cameraManager.setContrast(it.toInt()) },
                                valueRange = -100f..100f,
                                steps = 19, // Every 10 units
                                thumbColor = theme.primary
                        )

                        Spacer(Modifier.height(16.dp))

                        // Flash & Portrait Controls (Expressive Switch)
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                                // Flash
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                Icons.Filled.FlashOn,
                                                null,
                                                tint = theme.primary,
                                                modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text("Flash", color = theme.textPrimary)
                                }
                                ExpressiveSwitch(
                                        checked = cameraState.isFlashEnabled,
                                        onCheckedChange = { cameraManager.setFlash(it) },
                                        thumbColor = theme.primary
                                )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                                // Portrait Blur
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                Icons.Filled.BlurOn,
                                                null,
                                                tint = theme.primary,
                                                modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text("Portrait Blur", color = theme.textPrimary)
                                }
                                ExpressiveSwitch(
                                        checked = cameraState.isBokehEnabled,
                                        onCheckedChange = { cameraManager.setBokeh(it) },
                                        thumbColor = theme.primary
                                )
                        }
                }
        }
}

@Composable
fun InfoScreen(
        cameraManager: CameraManager,
        currentTheme: AppTheme,
        onThemeChange: (AppTheme) -> Unit
) {
        val theme = LocalAppTheme.current

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscape = maxWidth > maxHeight
                val scrollState = rememberScrollState()

                if (isLandscape) {

                        Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {

                                // LEFT PANE
                                Column(
                                        modifier = Modifier.weight(1f).padding(vertical = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                ) {
                                        Spacer(Modifier.height(70.dp))
                                        InfoBrandingSection(theme)
                                }

                                // RIGHT PANE
                                Box(
                                        modifier =
                                                Modifier.weight(1f)
                                                        .fillMaxHeight()
                                                        .padding(top = 90.dp)
                                                        .clipToBounds()
                                ) {
                                        GlassBox(modifier = Modifier.fillMaxSize()) {
                                                Column(
                                                        modifier =
                                                                Modifier.verticalScroll(
                                                                                rememberScrollState()
                                                                        )
                                                                        .padding(24.dp)
                                                ) { InfoFeaturesSection(theme) }
                                        }
                                }
                        }
                } else {
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(horizontal = 24.dp)
                                                .verticalScroll(scrollState),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                        ) {
                                Spacer(Modifier.height(120.dp))
                                InfoBrandingSection(theme)
                                Spacer(Modifier.height(12.dp))
                                Divider(
                                        color = theme.textPrimary.copy(alpha = 0.12f),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                GlassBox {
                                        Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(8.dp)
                                        ) {
                                                Spacer(Modifier.height(12.dp))
                                                InfoFeaturesSection(theme)
                                        }
                                }
                        }
                }
        }
}

@Composable
fun InfoBrandingSection(theme: AppTheme) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.wrapContentHeight().fillMaxWidth()
        ) {
                Box(
                        modifier =
                                Modifier.size(100.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                                Brush.linearGradient(
                                                        listOf(theme.primary, theme.secondary)
                                                )
                                        )
                                        .padding(6.dp)
                ) {
                        AppLogo(
                                modifier = Modifier.fillMaxSize(),
                                //                        tint = Color.White
                                )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                        Text(
                                "Moonlight",
                                style =
                                        MaterialTheme.typography.h5.copy(
                                                fontWeight = FontWeight.Bold
                                        ),
                                color = theme.textPrimary
                        )
                        Text(
                                "Webcam",
                                style =
                                        MaterialTheme.typography.h5.copy(
                                                fontWeight = FontWeight.Light
                                        ),
                                color = theme.textSecondary
                        )
                }

                Text(
                        "Pro-Grade Streaming Studio",
                        color = theme.primary,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                        InfoBadge("Version", "1.4.0", theme.primary)
                        InfoBadge("Build", "2026.01.11", theme.secondary)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        val buttonHeight = 52.dp

                        // Replacement for Button (Share App)
                        Box(
                                modifier =
                                        Modifier.weight(1f)
                                                .height(buttonHeight)
                                                .shadow(8.dp, RoundedCornerShape(16.dp))
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(theme.primary)
                                                .clickable {
                                                        PlatformUtils.shareText(
                                                                "Stream smarter with MoonlightWebcam! Get it at https://moonlight.webcam"
                                                        )
                                                },
                                contentAlignment = Alignment.Center
                        ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                Icons.Filled.Share,
                                                null,
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.White
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                                "Share App",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                        )
                                }
                        }

                        // Replacement for OutlinedButton (Rate App)
                        Box(
                                modifier =
                                        Modifier.weight(1f)
                                                .height(buttonHeight)
                                                .clip(RoundedCornerShape(16.dp))
                                                .border(
                                                        BorderStroke(
                                                                2.dp,
                                                                theme.textPrimary.copy(alpha = 0.2f)
                                                        ),
                                                        RoundedCornerShape(16.dp)
                                                )
                                                .clickable {
                                                        PlatformUtils.openUrl(
                                                                "https://moonlight.webcam/rate"
                                                        )
                                                },
                                contentAlignment = Alignment.Center
                        ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                Icons.Filled.Star,
                                                null,
                                                modifier = Modifier.size(18.dp),
                                                tint = theme.secondary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                                "Rate App",
                                                fontWeight = FontWeight.Bold,
                                                color = theme.textPrimary
                                        )
                                }
                        }
                }
                Spacer(Modifier.height(22.dp))
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                ) {
                        Text(
                                "Crafted with ❤️ for Open-Source by Manu Singh",
                                style = MaterialTheme.typography.caption,
                                color = theme.textSecondary.copy(alpha = 0.6f)
                        )
                }
        }
}

@Composable
fun InfoFeaturesSection(theme: AppTheme) {
        Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                Icons.AutoMirrored.Filled.FeaturedPlayList,
                                null,
                                tint = theme.secondary,
                                modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Standard Features", color = theme.secondary)
                }
                Spacer(Modifier.height(6.dp))
                BulletPoint("4K Ultra HD @ 60 FPS Support")
                BulletPoint("High-Fidelity Audio Streaming (44.1kHz)")
                BulletPoint("Ultra-Low Latency (< 50ms over USB)")
                BulletPoint("Multi-Lens Support (Ultra Wide, Telephoto)")
                BulletPoint("Screen Sharing (Cast your screen to PC)")
                BulletPoint("Universal Desktop Client (Win/Mac)")
                BulletPoint("Bi-Directional Manual Controls")
        }
}

@Composable
fun BulletPoint(text: String) {
        val theme = LocalAppTheme.current
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                        "•",
                        color = theme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.h6
                )
                Text(
                        text,
                        color = theme.textSecondary,
                        style = MaterialTheme.typography.body2,
                        lineHeight = 20.sp
                )
        }
}

@Composable
fun InfoBadge(label: String, value: String, color: Color) {
        val theme = LocalAppTheme.current
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.caption, color = theme.textSecondary)
                Text(
                        value,
                        style = MaterialTheme.typography.body1,
                        color = color,
                        fontWeight = FontWeight.Black
                )
        }
}

@Composable
fun SetupGuideScreen() {
        val theme = LocalAppTheme.current
        var activeMode by remember { mutableStateOf(GuideMode.WIFI) }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscape = maxWidth > maxHeight
                val scrollState = rememberScrollState()

                if (isLandscape) {
                        val leftScroll = rememberScrollState()
                        val rightScroll = rememberScrollState()

                        Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                                // ── LEFT COLUMN: Tutorial Steps ─────────────────
                                Column(modifier = Modifier.weight(1f).verticalScroll(leftScroll)) {
                                        Spacer(Modifier.height(100.dp))
                                        SetupStepsSection(activeMode, { activeMode = it }, theme)
                                        Spacer(Modifier.height(24.dp))
                                }

                                // ── RIGHT COLUMN: Desktop Promo ─────────────────
                                Column(modifier = Modifier.weight(1f).verticalScroll(rightScroll)) {
                                        Spacer(Modifier.height(100.dp))
                                        DesktopPromoSection(theme)
                                        Spacer(Modifier.height(24.dp))
                                }
                        }
                } else {
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(horizontal = 24.dp)
                                                .verticalScroll(scrollState),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                        ) {
                                Spacer(Modifier.height(120.dp))

                                SetupStepsSection(activeMode, { activeMode = it }, theme)

                                Spacer(Modifier.height(32.dp))

                                DesktopPromoSection(theme)
                        }
                }
        }
}

enum class GuideMode {
        WIFI,
        USB,
        CAST
}

@Composable
fun SetupStepsSection(activeMode: GuideMode, onModeChange: (GuideMode) -> Unit, theme: AppTheme) {
        GlassBox {
                Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.AutoMirrored.Filled.Help,
                                        null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                        "Tutorial",
                                        style = MaterialTheme.typography.h6,
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                        Spacer(Modifier.height(12.dp))
                        // ── Mode Toggle ─────────────────────────────────────────────
                        Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                        ) {
                                Row(
                                        modifier =
                                                Modifier.wrapContentWidth()
                                                        .background(
                                                                theme.textPrimary.copy(
                                                                        alpha = 0.05f
                                                                ),
                                                                RoundedCornerShape(28.dp)
                                                        )
                                                        .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                        TabButton(
                                                "Wi-Fi",
                                                activeMode == GuideMode.WIFI,
                                                { onModeChange(GuideMode.WIFI) },
                                                theme.primary
                                        )

                                        TabButton(
                                                " USB ",
                                                activeMode == GuideMode.USB,
                                                { onModeChange(GuideMode.USB) },
                                                theme.primary
                                        )

                                        TabButton(
                                                "Cast",
                                                activeMode == GuideMode.CAST,
                                                { onModeChange(GuideMode.CAST) },
                                                theme.primary
                                        )
                                }
                        }

                        // ── Platform + command ─────────────────────────────────────
                        val isAndroid = PlatformUtils.isAndroid()
                        val deviceName = if (isAndroid) "phone" else "iPhone"

                        Column(modifier = Modifier.padding(8.dp)) {
                                when (activeMode) {
                                        GuideMode.WIFI -> {
                                                TutorialStep(
                                                        "1",
                                                        "Wi-Fi Sync",
                                                        "Ensure your $deviceName and PC are on the same Wi-Fi network (preferably 5 GHz).",
                                                        Icons.Filled.Wifi
                                                )

                                                StepDivider()

                                                TutorialStep(
                                                        "2",
                                                        "Launch Desktop Client",
                                                        "Open MoonlightWebcam on your PC. The " +
                                                                "device" +
                                                                " " +
                                                                "should auto-discover.",
                                                        Icons.Filled.Info
                                                )

                                                StepDivider()

                                                TutorialStep(
                                                        "3",
                                                        "Start Streaming",
                                                        "Tap 'Play' to start the camera stream.",
                                                        Icons.Filled.Videocam
                                                )
                                        }
                                        GuideMode.USB -> {
                                                val clipboardManager = LocalClipboardManager.current
                                                val usbCommand =
                                                        if (isAndroid)
                                                                "adb forward tcp:8080 tcp:8080"
                                                        else "iproxy 8080 8080"

                                                TutorialStep(
                                                        number = "1",
                                                        title =
                                                                if (isAndroid) "USB Debugging"
                                                                else "Trust Computer",
                                                        desc =
                                                                if (isAndroid)
                                                                        "Connect USB and enable 'USB Debugging' in Developer Options."
                                                                else
                                                                        "Connect via Lightning/USB-C cable. Tap 'Trust' on iPhone if prompted.",
                                                        icon = Icons.Outlined.Usb
                                                )

                                                StepDivider()

                                                TutorialStep(
                                                        number = "2",
                                                        title =
                                                                if (isAndroid) "ADB Forward"
                                                                else "USB Tunnel",
                                                        desc =
                                                                "Run this command on your computer to establish connection.",
                                                        icon =
                                                                if (isAndroid) Icons.Filled.Android
                                                                else Icons.Filled.PhoneIphone,
                                                        command = usbCommand,
                                                        onCopy = {
                                                                clipboardManager.setText(
                                                                        AnnotatedString(usbCommand)
                                                                )
                                                        }
                                                )

                                                StepDivider()

                                                TutorialStep(
                                                        number = "3",
                                                        title = "Local Connect",
                                                        desc =
                                                                "In Desktop Client, enable USB Mode to connect via localhost.",
                                                        icon = Icons.Filled.Link
                                                )
                                        }
                                        GuideMode.CAST -> {
                                                TutorialStep(
                                                        "1",
                                                        "Connect Client",
                                                        "Connect via Wi-Fi or USB first. Ensure the Desktop Client is ready.",
                                                        Icons.Filled.Link
                                                )

                                                StepDivider()

                                                TutorialStep(
                                                        "2",
                                                        "Remote Control",
                                                        if (isAndroid)
                                                                "For mouse/keyboard control, " +
                                                                        "enable 'MoonlightClient " +
                                                                        "Input' in Accessibility Settings."
                                                        else
                                                                "Remote input is currently limited on iOS.",
                                                        Icons.Default.Settings
                                                )

                                                StepDivider()

                                                TutorialStep(
                                                        "3",
                                                        "Start Broadcast",
                                                        "Tap 'Start Stream' and accept the scaling/recording permissions.",
                                                        Icons.Default.PlayArrow
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun DesktopPromoSection(theme: AppTheme) {
        Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                backgroundColor = theme.primary.copy(alpha = 0.15f),
                elevation = 0.dp
        ) {
                Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Default.DesktopMac,
                                        null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                        "Desktop Client",
                                        style = MaterialTheme.typography.h6,
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                                "Unlock pro features like Virtual Camera, Background Removal, and Multi-Cam sync with our desktop companion.",
                                style = MaterialTheme.typography.body2,
                                color = theme.textSecondary,
                                lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(24.dp))
                        // Replacement for Button (Download Desktop)
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(theme.primary)
                                                .clickable {
                                                        PlatformUtils.openUrl(
                                                                "https://moonlight.webcam/download"
                                                        )
                                                },
                                contentAlignment = Alignment.Center
                        ) {
                                Text(
                                        "Download for Win / Mac",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                )
                        }
                }
        }
}

@Composable
fun StepDivider() {
        val theme = LocalAppTheme.current
        Divider(
                modifier = Modifier.padding(start = 56.dp, top = 8.dp, bottom = 8.dp),
                color = theme.textPrimary.copy(alpha = 0.05f)
        )
}

@Composable
fun TutorialStep(
        number: String,
        title: String,
        desc: String,
        icon: ImageVector,
        command: String? = null,
        onCopy: (() -> Unit)? = null
) {
        val theme = LocalAppTheme.current

        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(12.dp)) {

                // Icon box
                Box(
                        modifier =
                                Modifier.size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(theme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = theme.primary, modifier = Modifier.size(24.dp)) }

                Spacer(Modifier.width(16.dp))

                // Content
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                title,
                                style = MaterialTheme.typography.subtitle1,
                                color = theme.textPrimary,
                                fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                                desc,
                                style = MaterialTheme.typography.body2,
                                color = theme.textSecondary.copy(alpha = 0.8f),
                                lineHeight = 18.sp
                        )

                        // Command box
                        if (command != null && onCopy != null) {
                                Spacer(Modifier.height(8.dp))
                                ConnectionCommandRow(
                                        command = command,
                                        theme = theme,
                                        onCopy = onCopy
                                )
                        }
                }
        }
}

@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
        val theme = LocalAppTheme.current
        var startAnimation by remember { mutableStateOf(false) }

        val alphaAnim by
                animateFloatAsState(
                        targetValue = if (startAnimation) 1f else 0f,
                        animationSpec = tween(durationMillis = 1000)
                )

        LaunchedEffect(Unit) {
                startAnimation = true
                delay(2000)
                onAnimationFinished()
        }

        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(
                                        Brush.verticalGradient(
                                                listOf(theme.backgroundStart, theme.backgroundEnd)
                                        )
                                ),
                contentAlignment = Alignment.Center
        ) {
                // Liquid background STILL gets full space
                Box(modifier = Modifier.fillMaxSize()) { // <<–– FIX
                        LiquidBackground(theme) {
                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                ) {
                                        Box(
                                                modifier =
                                                        Modifier.size(100.dp)
                                                                .clip(RoundedCornerShape(22.dp))
                                                                .background(
                                                                        Brush.linearGradient(
                                                                                listOf(
                                                                                        theme.primary,
                                                                                        theme.secondary
                                                                                )
                                                                        )
                                                                )
                                                                .padding(6.dp)
                                        ) {
                                                AppLogo(
                                                        modifier = Modifier.fillMaxSize(),
                                                        //
                                                        //              tint = Color.White
                                                        )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                        ) {
                                                Text(
                                                        "Moonlight",
                                                        style =
                                                                MaterialTheme.typography.h5.copy(
                                                                        fontWeight = FontWeight.Bold
                                                                ),
                                                        color = theme.textPrimary
                                                )
                                                Text(
                                                        "Webcam",
                                                        style =
                                                                MaterialTheme.typography.h5.copy(
                                                                        fontWeight =
                                                                                FontWeight.Light
                                                                ),
                                                        color = theme.textSecondary
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun IntroScreen(onFinished: () -> Unit) {
        val theme = LocalAppTheme.current
        val density = LocalDensity.current

        data class IntroPage(
                val title: String,
                val icon: ImageVector?,
                val description: String,
                val tooltip: String? = null
        )

        val pages = remember {
                listOf(
                        IntroPage(
                                "Welcome to Moonlight",
                                null, // Use AppLogo
                                "Transform your phone into a professional 4K webcam with AI-powered features."
                        ),
                        IntroPage(
                                "High Quality",
                                Icons.Default.Videocam,
                                "Stream pristine 4K video over Wi-Fi or USB with ultra-low latency (< 80ms).",
                                "Low Latency"
                        ),
                        IntroPage(
                                "Full Control",
                                Icons.Default.Settings,
                                "Take manual control of Focus, Zoom, ISO, and Shutter Speed for the cinematic look.",
                                "Pro Controls"
                        ),
                        IntroPage(
                                "Desktop Companion",
                                Icons.Default.Computer,
                                "Pair with the Moonlight Desktop Client for remote control and advanced effects."
                        ),
                        IntroPage(
                                "Cross Platform",
                                Icons.Default.PhoneAndroid,
                                "Works seamlessly on Android, iOS, Windows and macOS"
                        ),
                        IntroPage(
                                "Screen Share",
                                Icons.Default.Cast,
                                "Shares your screen seamlessly on various devices",
                                "NEW FEATURE"
                        )
                )
        }

        var currentPage by remember { mutableStateOf(0) }

        // Particle System State
        val particles = remember {
                List(25) {
                        Particle(
                                x = (0..1000).random() / 1000f,
                                y = (0..1000).random() / 1000f,
                                radius = (2..5).random().toFloat(),
                                speedX = (-3..3).random() / 10000f,
                                speedY = (-3..3).random() / 10000f,
                                alpha = (20..60).random() / 100f
                        )
                }
        }

        Box(modifier = Modifier.fillMaxSize().background(theme.backgroundStart)) {
                // 1. Motion Graphics Background
                ParticleCanvas(theme, particles)

                // 2. Animated Content Pager
                AnimatedContent(
                        targetState = currentPage,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                                (slideInHorizontally { width -> width / 2 } +
                                                fadeIn() +
                                                scaleIn(initialScale = 0.9f))
                                        .togetherWith(
                                                slideOutHorizontally { width -> -width / 2 } +
                                                        fadeOut() +
                                                        scaleOut(targetScale = 0.95f)
                                        )
                        }
                ) { targetPage ->
                        Column(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                val page = pages[targetPage]

                                // Staggered Animation States
                                var iconVisible by remember { mutableStateOf(false) }
                                LaunchedEffect(targetPage) { iconVisible = true }

                                val iconScale by
                                        animateFloatAsState(
                                                targetValue = if (iconVisible) 1f else 0.5f,
                                                animationSpec =
                                                        spring(
                                                                dampingRatio =
                                                                        Spring.DampingRatioMediumBouncy
                                                        )
                                        )
                                val iconRotation by
                                        animateFloatAsState(
                                                targetValue = if (iconVisible) 0f else -15f,
                                                animationSpec = tween(500)
                                        )

                                // Icon Container
                                Box(contentAlignment = Alignment.TopEnd) {
                                        GlassBox(
                                                modifier =
                                                        Modifier.graphicsLayer {
                                                                scaleX = iconScale
                                                                scaleY = iconScale
                                                                rotationZ = iconRotation
                                                        }
                                        ) {
                                                Box(
                                                        modifier = Modifier.padding(40.dp),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        if (page.icon != null) {
                                                                Icon(
                                                                        page.icon,
                                                                        contentDescription = null,
                                                                        modifier =
                                                                                Modifier.size(
                                                                                        140.dp
                                                                                ),
                                                                        tint = theme.textPrimary
                                                                )
                                                        } else {
                                                                AppLogo(
                                                                        modifier =
                                                                                Modifier.size(
                                                                                        140.dp
                                                                                )
                                                                )
                                                        }
                                                }
                                        }

                                        // Tooltip Overlay
                                        if (page.tooltip != null) {
                                                androidx.compose.animation.AnimatedVisibility(
                                                        visible = iconVisible,
                                                        enter =
                                                                slideInVertically { -40 } +
                                                                        fadeIn(),
                                                        modifier =
                                                                Modifier.offset(
                                                                        x = 12.dp,
                                                                        y = (-12).dp
                                                                )
                                                ) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.background(
                                                                                        theme.primary,
                                                                                        RoundedCornerShape(
                                                                                                8.dp
                                                                                        )
                                                                                )
                                                                                .padding(
                                                                                        horizontal =
                                                                                                10.dp,
                                                                                        vertical =
                                                                                                5.dp
                                                                                )
                                                        ) {
                                                                Text(
                                                                        page.tooltip,
                                                                        color = Color.White,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .overline,
                                                                        fontWeight = FontWeight.Bold
                                                                )
                                                        }
                                                }
                                        }
                                }

                                Spacer(Modifier.height(48.dp))

                                // Text Content
                                Text(
                                        page.title,
                                        style = MaterialTheme.typography.h4,
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                        page.description,
                                        style = MaterialTheme.typography.body1,
                                        color = theme.textSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        lineHeight = 24.sp
                                )
                        }
                } // End AnimatedContent

                // 3. Navigation Controls (Fixed at Bottom)
                Box(
                        modifier =
                                Modifier.align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(bottom = 60.dp, start = 32.dp, end = 32.dp)
                ) {
                        // Indicators
                        Row(
                                modifier = Modifier.align(Alignment.CenterStart),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                pages.indices.forEach { index ->
                                        val isSelected = currentPage == index
                                        val width by
                                                animateDpAsState(if (isSelected) 32.dp else 8.dp)
                                        val color by
                                                animateColorAsState(
                                                        if (isSelected) theme.primary
                                                        else theme.textPrimary.copy(alpha = 0.2f)
                                                )

                                        Box(
                                                modifier =
                                                        Modifier.height(8.dp)
                                                                .width(width)
                                                                .clip(CircleShape)
                                                                .background(color)
                                        )
                                }
                        }

                        // Next/Finish Button
                        val isLastPage = currentPage == pages.lastIndex

                        // Replacement for FloatingActionButton to avoid iOS IrLinkageError
                        Box(
                                modifier =
                                        Modifier.align(Alignment.CenterEnd)
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(theme.primary)
                                                .clickable {
                                                        if (currentPage < pages.lastIndex) {
                                                                currentPage++
                                                        } else {
                                                                onFinished()
                                                        }
                                                },
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector =
                                                if (isLastPage) Icons.Default.Check
                                                else Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White
                                )
                        }
                }
        }
}

@Composable
fun InfoRow(label: String, value: String) {
        val theme = LocalAppTheme.current
        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
                Text(label, color = theme.secondary, style = MaterialTheme.typography.subtitle1)
                Text(
                        value,
                        color = theme.textPrimary,
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Bold
                )
        }
}

@Composable
fun ConnectionStatusRow(
        icon: ImageVector,
        label: String,
        status: String,
        theme: AppTheme,
        // Actions
        // Actions
        isStreaming: Boolean,
        isPaused: Boolean = false,
        onToggleStream: () -> Unit,
        onTogglePause: () -> Unit = {},
        copyUrl: String? = null,
        onCopy: (() -> Unit)? = null
) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 1. Icon & Label Section
                Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = theme.secondary,
                        modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = label,
                                style =
                                        MaterialTheme.typography.caption.copy(
                                                fontWeight = FontWeight.Bold
                                        ),
                                color = theme.secondary
                        )
                        Text(
                                text = status,
                                style = MaterialTheme.typography.caption.copy(fontSize = 11.sp),
                                color = theme.textPrimary,
                                maxLines = 1,
                        )
                }

                // 2. Action Buttons Section
                // Wrapper Row for buttons
                Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Pause/Resume Button
                        if (isStreaming) {
                                Box(
                                        contentAlignment = Alignment.Center,
                                        modifier =
                                                Modifier.size(24.dp) // Maintain size
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                                theme.primary.copy(alpha = 0.2f)
                                                        )
                                                        .clickable(onClick = onTogglePause)
                                ) {
                                        Icon(
                                                imageVector =
                                                        if (isPaused) Icons.Default.PlayArrow
                                                        else Icons.Default.Pause,
                                                contentDescription =
                                                        if (isPaused) "Resume" else "Pause",
                                                tint = theme.primary,
                                                modifier = Modifier.size(16.dp)
                                        )
                                }
                        }
                        //
                        //                        // Stop/Start Button
                        //                        Box(
                        //                                contentAlignment = Alignment.Center,
                        //                                modifier =
                        //                                        Modifier.size(24.dp)
                        //
                        // .clip(RoundedCornerShape(8.dp))
                        //                                                .background(
                        //                                                        color =
                        //                                                                if
                        // (isStreaming)
                        //
                        // Color.Red.copy(alpha = 0.2f)
                        //                                                                else
                        //
                        // theme.primary.copy(
                        //
                        //      alpha = 0.2f
                        //                                                                        )
                        //                                                )
                        //                                                .clickable(onClick =
                        // onToggleStream)
                        //                        ) {
                        //                                Icon(
                        //                                        imageVector =
                        //                                                if (isStreaming)
                        // Icons.Default.Stop
                        //                                                else
                        // Icons.Default.PlayArrow,
                        //                                        contentDescription = if
                        // (isStreaming) "Stop" else "Start",
                        //                                        tint = if (isStreaming) Color.Red
                        // else theme.primary,
                        //                                        modifier = Modifier.size(16.dp)
                        //                                )
                        //                        }

                        // Copy Button (Optional)
                        if (copyUrl != null && onCopy != null) {
                                Box(
                                        contentAlignment = Alignment.Center,
                                        modifier =
                                                Modifier.size(24.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                                color =
                                                                        theme.primary.copy(
                                                                                alpha = 0.15f
                                                                        )
                                                        )
                                                        .clickable(onClick = onCopy)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy URL",
                                                tint = theme.primary,
                                                modifier = Modifier.size(16.dp)
                                        )
                                }
                        }
                }

                // Right Margin
                Spacer(Modifier.width(4.dp))
        }
}

@Composable
fun ConnectionCommandRow(command: String, theme: AppTheme, onCopy: () -> Unit) {
        val isAndroid = PlatformUtils.isAndroid()

        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(
                        imageVector =
                                if (isAndroid) Icons.Filled.Android else Icons.Filled.PhoneIphone,
                        contentDescription = "Platform",
                        tint = theme.secondary,
                        modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(12.dp))

                Text(
                        text = command,
                        style = MaterialTheme.typography.caption.copy(fontSize = 11.sp),
                        color = theme.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                )

                Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                                Modifier.size(24.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(color = theme.primary.copy(alpha = 0.15f))
                                        .clickable(onClick = onCopy)
                ) {
                        Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Command",
                                tint = theme.primary,
                                modifier = Modifier.size(16.dp)
                        )
                }

                // Right Margin (4dp)
                Spacer(Modifier.width(4.dp))
        }
}

data class Particle(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speedX: Float,
        val speedY: Float,
        val alpha: Float
)

@Composable
fun ParticleCanvas(theme: AppTheme, particles: List<Particle>) {
        var trigger by remember { mutableStateOf(0L) }

        LaunchedEffect(Unit) {
                while (true) {
                        withFrameNanos { time ->
                                particles.forEach { p ->
                                        p.x += p.speedX
                                        p.y += p.speedY
                                        if (p.x > 1f) p.x = 0f else if (p.x < 0f) p.x = 1f
                                        if (p.y > 1f) p.y = 0f else if (p.y < 0f) p.y = 1f
                                }
                                trigger = time
                        }
                }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
                trigger // Force redraw
                val w = size.width
                val h = size.height

                particles.forEach { p ->
                        drawCircle(
                                brush =
                                        Brush.radialGradient(
                                                colors =
                                                        listOf(
                                                                theme.primary.copy(
                                                                        alpha = p.alpha * 0.5f
                                                                ),
                                                                Color.Transparent
                                                        ),
                                                center = Offset(p.x * w, p.y * h),
                                                radius = p.radius * 4.dp.toPx()
                                        ),
                                radius = p.radius * 4.dp.toPx(),
                                center = Offset(p.x * w, p.y * h)
                        )
                }
        }
}
