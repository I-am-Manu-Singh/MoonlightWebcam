package com.example.moonlightwebcam

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

// --- Theme Colors ---
val GlassBlack = Color(0xFF121212)
val NeonPurple = Color(0xFFBB86FC)
val NeonCyan = Color(0xFF03DAC6)
val GlassWhite = Color.White.copy(alpha = 0.1f)
val GlassBorder = Color.White.copy(alpha = 0.2f)

val LocalHazeState = compositionLocalOf { HazeState() }

// --- Theme Definitions ---
enum class AppTheme(
        val title: String,

        // Surfaces
        val primary: Color,
        val secondary: Color,
        val backgroundStart: Color,
        val backgroundEnd: Color,

        // Text
        val textPrimary: Color,
        val textSecondary: Color,

        // Button / content colors
        val onPrimary: Color,
        val onSecondary: Color
) {
        Neon(
                title = "Cyberpunk Neon",
                primary = Color(0xFFB69CFF),
                secondary = Color(0xFF00E5FF),
                backgroundStart = Color(0xFF0B061F),
                backgroundEnd = Color(0xFF140B3A),
                textPrimary = Color(0xFFEDE9FE),
                textSecondary = Color(0xFFB3E5FC),
                onPrimary = Color(0xFF140B3A), // Dark text on neon violet
                onSecondary = Color(0xFF003B44)
        ),
        Ocean(
                title = "Pacific Blue",
                primary = Color(0xFF4FC3F7),
                secondary = Color(0xFF18FFFF),
                backgroundStart = Color(0xFF001F2D),
                backgroundEnd = Color(0xFF003B55),
                textPrimary = Color(0xFFE0F7FA),
                textSecondary = Color(0xFFB2EBF2),
                onPrimary = Color(0xFF001F2D),
                onSecondary = Color(0xFF003B55)
        ),
        Sunset(
                title = "Golden Hour",
                primary = Color(0xFFFFB74D),
                secondary = Color(0xFFFF8A65),
                backgroundStart = Color(0xFF3E0F0A),
                backgroundEnd = Color(0xFF7A1C10),
                textPrimary = Color(0xFFFFF3E0),
                textSecondary = Color(0xFFFFCCBC),
                onPrimary = Color(0xFF3E0F0A),
                onSecondary = Color(0xFF4A130D)
        ),
        White(
                title = "Arctic Frost",
                primary = Color(0xFF4F46E5),
                secondary = Color(0xFF06B6D4),
                backgroundStart = Color(0xFFF2F4F8),
                backgroundEnd = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF0F172A),
                textSecondary = Color(0xFF475569),
                onPrimary = Color(0xFFFFFFFF),
                onSecondary = Color(0xFF003344)
        ),
        Dark(
                title = "Midnight Void",

                // Selected / active elements (now clearly red)
                primary = Color(0xFFDC2626), // Strong but controlled red
                secondary = Color(0xFFEF4444), // Accent red (lighter)

                // Background with subtle red warmth
                backgroundStart = Color(0xFF140B0B),
                backgroundEnd = Color(0xFF080303),

                // Text (pure readable white hierarchy)
                textPrimary = Color(0xFFFFFFFF), // Main text = white
                textSecondary = Color(0xFFCBD5E1), // Neutral light gray (not red)

                // Content on selected surfaces
                onPrimary = Color(0xFFFFFFFF), // White text/icons on red buttons
                onSecondary = Color(0xFF450A0A)
        ),
        Forest(
                title = "Emerald Forest",
                primary = Color(0xFF86EFAC),
                secondary = Color(0xFF4ADE80),
                backgroundStart = Color(0xFF052E16),
                backgroundEnd = Color(0xFF064E3B),
                textPrimary = Color(0xFFECFDF5),
                textSecondary = Color(0xFFBBF7D0),
                onPrimary = Color(0xFF052E16),
                onSecondary = Color(0xFF022C22)
        ),
        Berry(
                title = "Royal Berry",
                primary = Color(0xFFF472B6),
                secondary = Color(0xFFC084FC),
                backgroundStart = Color(0xFF2E0249),
                backgroundEnd = Color(0xFF3B0764),
                textPrimary = Color(0xFFFAE8FF),
                textSecondary = Color(0xFFE9D5FF),
                onPrimary = Color(0xFF2E0249),
                onSecondary = Color(0xFF24003A)
        )
}

val LocalAppTheme = compositionLocalOf { AppTheme.Neon }

@Composable
fun App(cameraManager: CameraManager, streamingServer: StreamingServer) {
        val savedThemeName = remember { PlatformUtils.getString("APP_THEME", AppTheme.Neon.name) }
        var currentTheme by remember {
                mutableStateOf(
                        AppTheme.values().find { it.name == savedThemeName } ?: AppTheme.Neon
                )
        }
        var currentScreen by remember { mutableStateOf(0) }
        var isDimmed by remember { mutableStateOf(false) }
        var barsVisible by remember { mutableStateOf(true) }
        val barOffset by
                animateDpAsState(
                        targetValue = if (barsVisible) 0.dp else 200.dp,
                        animationSpec = tween(300)
                )
        var isLocked by remember { mutableStateOf(false) }
        var showSplash by remember { mutableStateOf(true) }
        var showIntro by remember {
                mutableStateOf(!PlatformUtils.getBoolean("INTRO_SHOWN", false))
        }

        val cameraState by cameraManager.cameraState.collectAsState()

        // Reset bars when switching screens
        LaunchedEffect(currentScreen) { barsVisible = true }

        // Global Scroll Detection
        val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                        // accumulator for hysteresis
                        var scrollAccumulator = 0f

                        override fun onPreScroll(
                                available: Offset,
                                source: NestedScrollSource
                        ): Offset {
                                val delta = available.y

                                // Reset accumulator if direction changes drastically
                                if ((scrollAccumulator < 0 && delta > 0) ||
                                                (scrollAccumulator > 0 && delta < 0)
                                ) {
                                        scrollAccumulator = 0f
                                }

                                scrollAccumulator += delta

                                // Scroll Down -> Hide (Accumulate -100px)
                                if (barsVisible && scrollAccumulator < -100f) {
                                        barsVisible = false
                                        scrollAccumulator = 0f
                                }

                                // Scroll Up -> Show (Accumulate +30px) - easier to show than hide
                                if (!barsVisible && scrollAccumulator > 30f) {
                                        barsVisible = true
                                        scrollAccumulator = 0f
                                }
                                return Offset.Zero
                        }

                        override fun onPostScroll(
                                consumed: Offset,
                                available: Offset,
                                source: NestedScrollSource
                        ): Offset {
                                // If we hit the bottom (tried to scroll down but couldn't), show
                                // bars
                                if (available.y < 0f) {
                                        barsVisible = true
                                }
                                return Offset.Zero
                        }
                }
        }

        LaunchedEffect(Unit) { PlatformUtils.requestNotificationPermission() }

        CompositionLocalProvider(LocalAppTheme provides currentTheme) {
                val colors =
                        if (currentTheme == AppTheme.White) {
                                lightColors(
                                        primary = currentTheme.primary,
                                        secondary = currentTheme.secondary,
                                        background = Color.Transparent,
                                        surface = Color.Transparent,
                                        onBackground = currentTheme.textPrimary,
                                        onSurface = currentTheme.textPrimary
                                )
                        } else {
                                darkColors(
                                        primary = currentTheme.primary,
                                        secondary = currentTheme.secondary,
                                        background = Color.Transparent,
                                        surface = Color.Transparent,
                                        onBackground = currentTheme.textPrimary,
                                        onSurface = currentTheme.textPrimary
                                )
                        }
                val isDarkIcons = currentTheme == AppTheme.White
                LaunchedEffect(currentTheme) { PlatformUtils.setStatusBarColor(isDarkIcons) }

                MaterialTheme(colors = colors) {
                        if (showSplash) {
                                SplashScreen { showSplash = false }
                        } else if (showIntro) {
                                IntroScreen {
                                        PlatformUtils.saveBoolean("INTRO_SHOWN", true)
                                        showIntro = false
                                }
                        } else {
                                val scaffoldState = rememberScaffoldState()
                                val scope = rememberCoroutineScope()

                                // Connection Notification
                                LaunchedEffect(cameraState.isClientConnected) {
                                        if (cameraState.isClientConnected) {
                                                scaffoldState.snackbarHostState.showSnackbar(
                                                        "Client Connected: ${cameraState.clientName ?: "Unknown"}",
                                                        duration = SnackbarDuration.Short
                                                )
                                        } else if (cameraState.isStreaming &&
                                                        !cameraState.isClientConnected
                                        ) {
                                                // scaffoldState.snackbarHostState.showSnackbar("Client Disconnected", duration = SnackbarDuration.Short)
                                        }
                                }

                                val hazeState = remember { HazeState() }

                                CompositionLocalProvider(LocalHazeState provides hazeState) {
                                        BoxWithConstraints(
                                                modifier =
                                                        Modifier.fillMaxSize()
                                                                .nestedScroll(
                                                                        nestedScrollConnection
                                                                )
                                        ) {
                                                val isLandscape = maxWidth > maxHeight

                                                // Wrapper Box to provide BoxScope for align
                                                // (SideRail)
                                                Box(Modifier.fillMaxSize()) {

                                                        // 1. Background Source (Haze)
                                                        Box(
                                                                modifier =
                                                                        Modifier.fillMaxSize()
                                                                                .haze(hazeState)
                                                        ) { LiquidBackground(currentTheme) {} }

                                                        // 2. Screen Content (Sinks)
                                                        Box(
                                                                modifier =
                                                                        Modifier.fillMaxSize()
                                                                                .padding(
                                                                                        start =
                                                                                                if (isLandscape
                                                                                                )
                                                                                                        80.dp
                                                                                                else
                                                                                                        0.dp
                                                                                )
                                                        ) {
                                                                // LAYER 0: Home Screen (Always
                                                                // Alive)
                                                                // Keeps Preview/Stream active.
                                                                // Alpha hides the UI buttons, but
                                                                // SurfaceView ensures Preview stays
                                                                // visible
                                                                // behind Controls (Pro/Overlay
                                                                // effect).
                                                                val homeAlpha by
                                                                        animateFloatAsState(
                                                                                if (currentScreen ==
                                                                                                0
                                                                                )
                                                                                        1f
                                                                                else 0f
                                                                        )
                                                                Box(
                                                                        modifier =
                                                                                Modifier.fillMaxSize()
                                                                                        .graphicsLayer {
                                                                                                alpha =
                                                                                                        homeAlpha
                                                                                        }
                                                                ) {
                                                                        HomeScreen(
                                                                                cameraManager,
                                                                                streamingServer
                                                                        )
                                                                }

                                                                // LAYER 1: Overlays (Controls,
                                                                // Guide, Info)
                                                                if (currentScreen != 0) {
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.fillMaxSize()
                                                                        ) {
                                                                                when (currentScreen
                                                                                ) {
                                                                                        1 ->
                                                                                                ControlsScreen(
                                                                                                        cameraManager,
                                                                                                        currentTheme
                                                                                                ) {
                                                                                                        currentTheme =
                                                                                                                it
                                                                                                        PlatformUtils
                                                                                                                .saveString(
                                                                                                                        "APP_THEME",
                                                                                                                        it.name
                                                                                                                )
                                                                                                }
                                                                                        2 ->
                                                                                                SetupGuideScreen()
                                                                                        3 ->
                                                                                                InfoScreen(
                                                                                                        cameraManager,
                                                                                                        currentTheme
                                                                                                ) {
                                                                                                        currentTheme =
                                                                                                                it
                                                                                                        PlatformUtils
                                                                                                                .saveString(
                                                                                                                        "APP_THEME",
                                                                                                                        it.name
                                                                                                                )
                                                                                                }
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }

                                                // 2. Floating Top Bar Header
                                                if (!isDimmed) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .zIndex(30f)
                                                        ) {
                                                                // Background Gradient
                                                                Box(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .height(
                                                                                                100.dp
                                                                                        )
                                                                                        .background(
                                                                                                Brush.verticalGradient(
                                                                                                        colors =
                                                                                                                listOf(
                                                                                                                        currentTheme
                                                                                                                                .backgroundStart
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                1.0f
                                                                                                                                ),
                                                                                                                        currentTheme
                                                                                                                                .backgroundStart
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                0.0f
                                                                                                                                )
                                                                                                                )
                                                                                                )
                                                                                        )
                                                                )

                                                                // Content Row (Title + Actions)
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .statusBarsPadding()
                                                                                        .padding(
                                                                                                horizontal =
                                                                                                        24.dp,
                                                                                                vertical =
                                                                                                        4.dp
                                                                                        )
                                                                                        .height(
                                                                                                48.dp
                                                                                        ),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .SpaceBetween,
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        // Title
                                                                        val titles =
                                                                                listOf(
                                                                                        "Home",
                                                                                        "Controls",
                                                                                        "Setup Guide",
                                                                                        "App Info"
                                                                                )
                                                                        Column {
                                                                                Text(
                                                                                        titles[
                                                                                                currentScreen],
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .subtitle1,
                                                                                        color =
                                                                                                currentTheme
                                                                                                        .textPrimary,
                                                                                        fontWeight =
                                                                                                FontWeight
                                                                                                        .Bold
                                                                                )
                                                                                // Display IP if
                                                                                // streaming
                                                                                val ip by
                                                                                        remember(
                                                                                                currentScreen
                                                                                        ) {
                                                                                                mutableStateOf(
                                                                                                        PlatformUtils
                                                                                                                .getIpAddress()
                                                                                                                ?: "Unknown"
                                                                                                )
                                                                                        }
                                                                                Text(
                                                                                        "IP: $ip",
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .caption,
                                                                                        color =
                                                                                                currentTheme
                                                                                                        .textPrimary
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.7f
                                                                                                        )
                                                                                )
                                                                        }

                                                                        // Right Side Actions (Dim /
                                                                        // Lock)
                                                                        Row(
                                                                                horizontalArrangement =
                                                                                        Arrangement
                                                                                                .spacedBy(
                                                                                                        16.dp
                                                                                                )
                                                                        ) {
                                                                                // Replacement for
                                                                                // IconButton (Dim)
                                                                                Box(
                                                                                        modifier =
                                                                                                Modifier.clip(
                                                                                                                RoundedCornerShape(
                                                                                                                        12.dp
                                                                                                                )
                                                                                                        )
                                                                                                        .background(
                                                                                                                currentTheme
                                                                                                                        .textPrimary
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.1f
                                                                                                                        )
                                                                                                        )
                                                                                                        .size(
                                                                                                                40.dp
                                                                                                        )
                                                                                                        .clickable {
                                                                                                                isDimmed =
                                                                                                                        !isDimmed
                                                                                                        },
                                                                                        contentAlignment =
                                                                                                Alignment
                                                                                                        .Center
                                                                                ) {
                                                                                        EyeIcon(
                                                                                                "Dim",
                                                                                                isDimmed,
                                                                                                currentTheme
                                                                                                        .textPrimary
                                                                                        )
                                                                                }

                                                                                // Replacement for
                                                                                // IconButton (Lock)
                                                                                Box(
                                                                                        modifier =
                                                                                                Modifier.clip(
                                                                                                                RoundedCornerShape(
                                                                                                                        12.dp
                                                                                                                )
                                                                                                        )
                                                                                                        .background(
                                                                                                                currentTheme
                                                                                                                        .textPrimary
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.1f
                                                                                                                        )
                                                                                                        )
                                                                                                        .size(
                                                                                                                40.dp
                                                                                                        )
                                                                                                        .clickable {
                                                                                                                isLocked =
                                                                                                                        !isLocked
                                                                                                        },
                                                                                        contentAlignment =
                                                                                                Alignment
                                                                                                        .Center
                                                                                ) {
                                                                                        Icon(
                                                                                                if (isLocked
                                                                                                )
                                                                                                        Icons.Filled
                                                                                                                .Lock
                                                                                                else
                                                                                                        Icons.Outlined
                                                                                                                .Lock,
                                                                                                "Lock",
                                                                                                tint =
                                                                                                        currentTheme
                                                                                                                .textPrimary
                                                                                        )
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }
                                                // (LiquidBackground closed above)
                                                // Add a wrapper Box inside BoxWithConstraints
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                        // 3. Floating Navigation (Bottom or Side)
                                                        if (!isDimmed) {
                                                                val navItems =
                                                                        listOf(
                                                                                "Home" to
                                                                                        Icons.Default
                                                                                                .Visibility,
                                                                                "Controls" to
                                                                                        Icons.Default
                                                                                                .Settings,
                                                                                "Guide" to
                                                                                        Icons.AutoMirrored
                                                                                                .Filled
                                                                                                .List,
                                                                                "Info" to
                                                                                        Icons.Default
                                                                                                .Info
                                                                        )

                                                                if (isLandscape) {
                                                                        // FLOATING SIDE RAIL
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.align(
                                                                                                        Alignment
                                                                                                                .CenterStart
                                                                                                )
                                                                                                .padding(
                                                                                                        start =
                                                                                                                8.dp
                                                                                                )
                                                                                                .width(
                                                                                                        64.dp
                                                                                                )
                                                                                                .height(
                                                                                                        290.dp
                                                                                                )
                                                                                                .clip(
                                                                                                        RoundedCornerShape(
                                                                                                                50
                                                                                                        )
                                                                                                )
                                                                                                .hazeChild(
                                                                                                        state =
                                                                                                                LocalHazeState
                                                                                                                        .current,
                                                                                                        shape =
                                                                                                                RoundedCornerShape(
                                                                                                                        50
                                                                                                                ),
                                                                                                        HazeStyle(
                                                                                                                tint =
                                                                                                                        if (currentTheme ==
                                                                                                                                        AppTheme.White
                                                                                                                        )
                                                                                                                                HazeTint(
                                                                                                                                        Color.White
                                                                                                                                                .copy(
                                                                                                                                                        alpha =
                                                                                                                                                                0.4f
                                                                                                                                                )
                                                                                                                                )
                                                                                                                        else
                                                                                                                                HazeTint(
                                                                                                                                        Color.White
                                                                                                                                                .copy(
                                                                                                                                                        alpha =
                                                                                                                                                                0.15f
                                                                                                                                                )
                                                                                                                                ),
                                                                                                                blurRadius =
                                                                                                                        25.dp,
                                                                                                                backgroundColor =
                                                                                                                        if (currentTheme ==
                                                                                                                                        AppTheme.White
                                                                                                                        )
                                                                                                                                Color.White
                                                                                                                                        .copy(
                                                                                                                                                alpha =
                                                                                                                                                        0.85f
                                                                                                                                        )
                                                                                                                        else
                                                                                                                                Color.White
                                                                                                                                        .copy(
                                                                                                                                                alpha =
                                                                                                                                                        0.25f
                                                                                                                                        )
                                                                                                        )
                                                                                                )
                                                                                                .border(
                                                                                                        1.dp,
                                                                                                        Brush.linearGradient(
                                                                                                                listOf(
                                                                                                                        currentTheme
                                                                                                                                .textPrimary
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                if (currentTheme ==
                                                                                                                                                                AppTheme.White
                                                                                                                                                )
                                                                                                                                                        0.2f
                                                                                                                                                else
                                                                                                                                                        0.3f
                                                                                                                                ),
                                                                                                                        currentTheme
                                                                                                                                .textPrimary
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                0.05f
                                                                                                                                )
                                                                                                                )
                                                                                                        ),
                                                                                                        RoundedCornerShape(
                                                                                                                50
                                                                                                        )
                                                                                                )
                                                                                                .padding(
                                                                                                        vertical =
                                                                                                                8.dp
                                                                                                ),
                                                                                contentAlignment =
                                                                                        Alignment
                                                                                                .Center
                                                                        ) {
                                                                                Column(
                                                                                        verticalArrangement =
                                                                                                Arrangement
                                                                                                        .spacedBy(
                                                                                                                6.dp
                                                                                                        ), // spacing between buttons
                                                                                        horizontalAlignment =
                                                                                                Alignment
                                                                                                        .CenterHorizontally,
                                                                                        modifier =
                                                                                                Modifier.fillMaxHeight()
                                                                                ) {
                                                                                        navItems
                                                                                                .forEachIndexed {
                                                                                                        index,
                                                                                                        item
                                                                                                        ->
                                                                                                        val isSelected =
                                                                                                                currentScreen ==
                                                                                                                        index

                                                                                                        Box(
                                                                                                                modifier =
                                                                                                                        Modifier.width(
                                                                                                                                        48.dp
                                                                                                                                ) // narrower
                                                                                                                                .height(
                                                                                                                                        65.dp
                                                                                                                                ) // taller
                                                                                                                                .clip(
                                                                                                                                        RoundedCornerShape(
                                                                                                                                                26.dp
                                                                                                                                        )
                                                                                                                                ) // taller shape needs higher radius
                                                                                                                                .background(
                                                                                                                                        if (isSelected
                                                                                                                                        )
                                                                                                                                                currentTheme
                                                                                                                                                        .textPrimary
                                                                                                                                                        .copy(
                                                                                                                                                                alpha =
                                                                                                                                                                        0.12f
                                                                                                                                                        )
                                                                                                                                        else
                                                                                                                                                Color.Transparent
                                                                                                                                )
                                                                                                                                .clickable {
                                                                                                                                        if (!isLocked
                                                                                                                                        )
                                                                                                                                                currentScreen =
                                                                                                                                                        index
                                                                                                                                },
                                                                                                                contentAlignment =
                                                                                                                        Alignment
                                                                                                                                .Center
                                                                                                        ) {
                                                                                                                Icon(
                                                                                                                        item.second,
                                                                                                                        contentDescription =
                                                                                                                                item.first,
                                                                                                                        tint =
                                                                                                                                if (isSelected
                                                                                                                                )
                                                                                                                                        currentTheme
                                                                                                                                                .primary
                                                                                                                                else
                                                                                                                                        currentTheme
                                                                                                                                                .textPrimary
                                                                                                                                                .copy(
                                                                                                                                                        alpha =
                                                                                                                                                                0.5f
                                                                                                                                                ),
                                                                                                                        modifier =
                                                                                                                                Modifier.size(
                                                                                                                                        24.dp
                                                                                                                                )
                                                                                                                )
                                                                                                        }
                                                                                                }
                                                                                }
                                                                        }
                                                                } else {
                                                                        // FLOATING BOTTOM BAR
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.align(
                                                                                                        Alignment
                                                                                                                .BottomCenter
                                                                                                )
                                                                                                .navigationBarsPadding()
                                                                                                .fillMaxWidth()
                                                                                                .padding(
                                                                                                        bottom =
                                                                                                                16.dp,
                                                                                                        start =
                                                                                                                16.dp,
                                                                                                        end =
                                                                                                                16.dp
                                                                                                ),
                                                                                contentAlignment =
                                                                                        Alignment
                                                                                                .BottomCenter
                                                                        ) {
                                                                                Box(
                                                                                        modifier =
                                                                                                Modifier.offset(
                                                                                                        y =
                                                                                                                barOffset
                                                                                                )
                                                                                ) {
                                                                                        Row(
                                                                                                modifier =
                                                                                                        Modifier.widthIn(
                                                                                                                        max =
                                                                                                                                330.dp
                                                                                                                )
                                                                                                                .height(
                                                                                                                        64.dp
                                                                                                                )
                                                                                                                .clip(
                                                                                                                        RoundedCornerShape(
                                                                                                                                50
                                                                                                                        )
                                                                                                                )
                                                                                                                .hazeChild(
                                                                                                                        LocalHazeState
                                                                                                                                .current,
                                                                                                                        HazeStyle(
                                                                                                                                tint =
                                                                                                                                        if (currentTheme ==
                                                                                                                                                        AppTheme.White
                                                                                                                                        )
                                                                                                                                                HazeTint(
                                                                                                                                                        Color.White
                                                                                                                                                                .copy(
                                                                                                                                                                        alpha =
                                                                                                                                                                                0.4f
                                                                                                                                                                )
                                                                                                                                                )
                                                                                                                                        else
                                                                                                                                                HazeTint(
                                                                                                                                                        Color.White
                                                                                                                                                                .copy(
                                                                                                                                                                        alpha =
                                                                                                                                                                                0.15f
                                                                                                                                                                )
                                                                                                                                                ),
                                                                                                                                blurRadius =
                                                                                                                                        25.dp,
                                                                                                                                backgroundColor =
                                                                                                                                        if (currentTheme ==
                                                                                                                                                        AppTheme.White
                                                                                                                                        )
                                                                                                                                                Color.White
                                                                                                                                                        .copy(
                                                                                                                                                                alpha =
                                                                                                                                                                        0.85f
                                                                                                                                                        )
                                                                                                                                        else
                                                                                                                                                Color.White
                                                                                                                                                        .copy(
                                                                                                                                                                alpha =
                                                                                                                                                                        0.25f
                                                                                                                                                        )
                                                                                                                        )
                                                                                                                )
                                                                                                                .border(
                                                                                                                        1.dp,
                                                                                                                        Brush.linearGradient(
                                                                                                                                listOf(
                                                                                                                                        currentTheme
                                                                                                                                                .textPrimary
                                                                                                                                                .copy(
                                                                                                                                                        alpha =
                                                                                                                                                                if (currentTheme ==
                                                                                                                                                                                AppTheme.White
                                                                                                                                                                )
                                                                                                                                                                        0.2f
                                                                                                                                                                else
                                                                                                                                                                        0.3f
                                                                                                                                                ),
                                                                                                                                        currentTheme
                                                                                                                                                .textPrimary
                                                                                                                                                .copy(
                                                                                                                                                        alpha =
                                                                                                                                                                if (currentTheme ==
                                                                                                                                                                                AppTheme.White
                                                                                                                                                                )
                                                                                                                                                                        0.05f
                                                                                                                                                                else
                                                                                                                                                                        0.1f
                                                                                                                                                )
                                                                                                                                )
                                                                                                                        ),
                                                                                                                        RoundedCornerShape(
                                                                                                                                50
                                                                                                                        )
                                                                                                                )
                                                                                                                .padding(
                                                                                                                        horizontal =
                                                                                                                                8.dp
                                                                                                                ),
                                                                                                horizontalArrangement =
                                                                                                        Arrangement
                                                                                                                .SpaceBetween,
                                                                                                verticalAlignment =
                                                                                                        Alignment
                                                                                                                .CenterVertically
                                                                                        ) {
                                                                                                navItems
                                                                                                        .forEachIndexed {
                                                                                                                index,
                                                                                                                item
                                                                                                                ->
                                                                                                                val isSelected =
                                                                                                                        currentScreen ==
                                                                                                                                index

                                                                                                                Box(
                                                                                                                        modifier =
                                                                                                                                Modifier.weight(
                                                                                                                                                1f
                                                                                                                                        )
                                                                                                                                        .height(
                                                                                                                                                48.dp
                                                                                                                                        )
                                                                                                                                        .padding(
                                                                                                                                                horizontal =
                                                                                                                                                        6.dp
                                                                                                                                        ) // 👈 spacing between items
                                                                                                                                        .clip(
                                                                                                                                                RoundedCornerShape(
                                                                                                                                                        22.dp
                                                                                                                                                )
                                                                                                                                        )
                                                                                                                                        .background(
                                                                                                                                                if (isSelected
                                                                                                                                                )
                                                                                                                                                        currentTheme
                                                                                                                                                                .textPrimary
                                                                                                                                                                .copy(
                                                                                                                                                                        alpha =
                                                                                                                                                                                0.12f
                                                                                                                                                                )
                                                                                                                                                else
                                                                                                                                                        Color.Transparent
                                                                                                                                        )
                                                                                                                                        .clickable {
                                                                                                                                                if (!isLocked
                                                                                                                                                )
                                                                                                                                                        currentScreen =
                                                                                                                                                                index
                                                                                                                                        },
                                                                                                                        contentAlignment =
                                                                                                                                Alignment
                                                                                                                                        .Center
                                                                                                                ) {
                                                                                                                        Icon(
                                                                                                                                item.second,
                                                                                                                                contentDescription =
                                                                                                                                        item.first,
                                                                                                                                tint =
                                                                                                                                        if (isSelected
                                                                                                                                        )
                                                                                                                                                currentTheme
                                                                                                                                                        .primary
                                                                                                                                        else
                                                                                                                                                currentTheme
                                                                                                                                                        .textPrimary
                                                                                                                                                        .copy(
                                                                                                                                                                alpha =
                                                                                                                                                                        0.5f
                                                                                                                                                        ),
                                                                                                                                modifier =
                                                                                                                                        Modifier.size(
                                                                                                                                                24.dp
                                                                                                                                        )
                                                                                                                        )
                                                                                                                }
                                                                                                        }
                                                                                        }
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }

                                                if (isLocked) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.fillMaxSize()
                                                                                .background(
                                                                                        Color.Black
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.5f
                                                                                                )
                                                                                )
                                                                                // Intercept
                                                                                // clicks
                                                                                .clickable(
                                                                                        enabled =
                                                                                                true,
                                                                                        onClick = {}
                                                                                )
                                                        ) {
                                                                // Locked Toast
                                                                Box(
                                                                        modifier =
                                                                                Modifier.align(
                                                                                                Alignment
                                                                                                        .TopCenter
                                                                                        )
                                                                                        .padding(
                                                                                                top =
                                                                                                        100.dp
                                                                                        ) // Lowered
                                                                                        // to not
                                                                                        // overlap
                                                                                        // title
                                                                                        .background(
                                                                                                Color.Black
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.8f
                                                                                                        ),
                                                                                                RoundedCornerShape(
                                                                                                        50
                                                                                                )
                                                                                        )
                                                                                        .padding(
                                                                                                16.dp
                                                                                        )
                                                                ) {
                                                                        Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically
                                                                        ) {
                                                                                Icon(
                                                                                        Icons.Filled
                                                                                                .Lock,
                                                                                        null,
                                                                                        tint =
                                                                                                Color.White
                                                                                )
                                                                                Spacer(
                                                                                        Modifier.width(
                                                                                                8.dp
                                                                                        )
                                                                                )
                                                                                Text(
                                                                                        "Press and Hold to Unlock",
                                                                                        color =
                                                                                                Color.White,
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .caption
                                                                                )
                                                                        }
                                                                }

                                                                // Center Lock Button for
                                                                // unlocking
                                                                // (Long
                                                                // Press)
                                                                Box(
                                                                        modifier =
                                                                                Modifier.align(
                                                                                                Alignment
                                                                                                        .Center
                                                                                        )
                                                                                        .size(80.dp)
                                                                                        .clip(
                                                                                                RoundedCornerShape(
                                                                                                        50
                                                                                                )
                                                                                        )
                                                                                        .background(
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.1f
                                                                                                        )
                                                                                        )
                                                                                        .pointerInput(
                                                                                                Unit
                                                                                        ) {
                                                                                                detectTapGestures(
                                                                                                        onLongPress = {
                                                                                                                isLocked =
                                                                                                                        false
                                                                                                        }
                                                                                                )
                                                                                        },
                                                                        contentAlignment =
                                                                                Alignment.Center
                                                                ) {
                                                                        Icon(
                                                                                Icons.Filled.Lock,
                                                                                null,
                                                                                tint = Color.White,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                32.dp
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                }

                                                // Old Buttons Removed (Moved to Top Header)

                                                if (isDimmed) {
                                                        // Hide System bars when dimmed
                                                        LaunchedEffect(isDimmed) {
                                                                PlatformUtils.setFullscreen(
                                                                        isDimmed
                                                                )
                                                        }
                                                        Box(
                                                                modifier =
                                                                        Modifier.fillMaxSize()
                                                                                .background(
                                                                                        Color.Black
                                                                                )
                                                                                .clickable {
                                                                                        isDimmed =
                                                                                                false
                                                                                        PlatformUtils
                                                                                                .setFullscreen(
                                                                                                        false
                                                                                                )
                                                                                }
                                                        ) {
                                                                if (cameraState.isStreaming) {
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.align(
                                                                                                        Alignment
                                                                                                                .TopEnd
                                                                                                )
                                                                                                .padding(
                                                                                                        32.dp
                                                                                                )
                                                                                                .size(
                                                                                                        12.dp
                                                                                                )
                                                                                                .clip(
                                                                                                        RoundedCornerShape(
                                                                                                                50
                                                                                                        )
                                                                                                )
                                                                                                .background(
                                                                                                        Color.Green
                                                                                                )
                                                                        )
                                                                }
                                                        }
                                                }
                                        } // Closes the new wrapper Box
                                } // Closes BoxWithConstraints
                        } // Closes LiquidBackground
                } // Closes AppTheme
        } // Closes App
} // Closes Surface
