package com.example.moonlightwebcam.desktop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.Toolkit
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

// --- Aesthetics ---
val NeonPurple = Color(0xFFBB86FC)
val DarkBackground = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val ControlBackground = Color(0xCC000000) // Translucent Black

data class DiscoveredDevice(
        val serviceName: String,
        val name: String,
        val ip: String,
        val port: Int,
        val pin: String = ""
)

enum class StreamingInterface {
    NONE,
    WIFI,
    USB
}

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
            primary = Color(0xFFDC2626), // Strong but controlled red
            secondary = Color(0xFFEF4444),
            backgroundStart = Color(0xFF140B0B),
            backgroundEnd = Color(0xFF080303),
            textPrimary = Color(0xFFFFFFFF), // Main text = white
            textSecondary = Color(0xFFCBD5E1), // Neutral light gray (not red)
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

fun main() = application {
    var isWindowVisible by remember { mutableStateOf(true) }
    val windowState =
            rememberWindowState(
                    placement = WindowPlacement.Floating,
                    position = WindowPosition.Aligned(Alignment.Center),
                    size = DpSize(900.dp, 600.dp)
            )
    var windowTitle by remember { mutableStateOf("MoonlightWebcam") }
    var currentTheme by remember { mutableStateOf(AppTheme.Dark) }

    if (isWindowVisible) {
        Window(
                onCloseRequest = { isWindowVisible = false },
                state = windowState,
                title = windowTitle
        ) {
            val scaffoldState = rememberScaffoldState()

            MaterialTheme(
                    colors =
                            if (currentTheme == AppTheme.White) {
                                lightColors(
                                        primary = currentTheme.primary,
                                        secondary = currentTheme.secondary,
                                        background = currentTheme.backgroundStart,
                                        surface = Color.White,
                                        onBackground = currentTheme.textPrimary,
                                        onSurface = currentTheme.textPrimary
                                )
                            } else {
                                darkColors(
                                        primary = currentTheme.primary,
                                        secondary = currentTheme.secondary,
                                        background = currentTheme.backgroundStart,
                                        surface = Color(0xFF1E1E1E),
                                        onBackground = currentTheme.textPrimary,
                                        onSurface = currentTheme.textPrimary
                                )
                            }
            ) {
                Scaffold(scaffoldState = scaffoldState) { paddingValues ->
                    Box(Modifier.padding(paddingValues)) {
                        MainApp(
                                onTitleChange = { windowTitle = it },
                                currentTheme = currentTheme,
                                onThemeChange = { currentTheme = it },
                                scaffoldState = scaffoldState
                        )
                    }
                }
            }
        }
    }

    // TRAY ICON
    Tray(
            icon = painterResource("tray_icon.png"),
            tooltip = "Moonlight Webcam",

            // LEFT CLICK → show/hide
            onAction = { isWindowVisible = !isWindowVisible },

            // RIGHT CLICK MENU
            menu = {
                Item(if (isWindowVisible) "Hide" else "Show") { isWindowVisible = !isWindowVisible }
                Separator()
                Item("Quit") { exitApplication() }
            }
    )
}

@Composable
fun MainApp(
        onTitleChange: (String) -> Unit,
        currentTheme: AppTheme,
        onThemeChange: (AppTheme) -> Unit,
        scaffoldState: ScaffoldState
) {
    var connectionUrl by remember { mutableStateOf<String?>(null) }
    var debugText by remember { mutableStateOf("Ready") }
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var cameraStatus by remember { mutableStateOf<CameraStatus?>(null) }
    var targetDeviceName by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var initialInfoTab by remember { mutableStateOf(0) }

    // Audio Level State for Visualizer
    var audioLevel by remember { mutableStateOf(0f) }

    // Saved IP State for switching modes
    var savedIpParts by remember { mutableStateOf<List<String>?>(null) }
    var savedPort by remember { mutableStateOf<String?>(null) }

    // Discovery State
    var discoveredDevices by remember { mutableStateOf<List<DiscoveredDevice>>(emptyList()) }

    // Start Discovery
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Find valid local IP for Multicast
                val bindAddress =
                        NetworkInterface.getNetworkInterfaces()
                                .asSequence()
                                .flatMap { it.inetAddresses.asSequence() }
                                .filter {
                                    it is Inet4Address &&
                                            !it.isLoopbackAddress &&
                                            it.isSiteLocalAddress
                                }
                                .firstOrNull()
                                ?: InetAddress.getLocalHost()

                println("Discovery: Binding to ${bindAddress.hostAddress}")
                val jmdns = JmDNS.create(bindAddress)
                jmdns.addServiceListener(
                        "_moonlight-webcam._tcp.local.",
                        object : ServiceListener {
                            override fun serviceAdded(event: ServiceEvent) {
                                jmdns.requestServiceInfo(event.type, event.name)
                            }

                            override fun serviceRemoved(event: ServiceEvent) {
                                val name = event.name
                                discoveredDevices =
                                        discoveredDevices.filter { it.serviceName != name }
                            }

                            override fun serviceResolved(event: ServiceEvent) {
                                val info = event.info
                                val addresses = info.inet4Addresses
                                if (addresses.isNotEmpty()) {
                                    val ip = addresses[0].hostAddress
                                    val port = info.port
                                    val pin = info.getPropertyString("pin") ?: ""
                                    val deviceName = info.getPropertyString("name") ?: event.name

                                    val device =
                                            DiscoveredDevice(
                                                    serviceName = event.name ?: "Unknown",
                                                    name = deviceName
                                                                    ?: event.name
                                                                            ?: "Unknown Device",
                                                    ip = ip,
                                                    port = port,
                                                    pin = pin
                                            )
                                    discoveredDevices =
                                            discoveredDevices.filter {
                                                it.serviceName != event.name
                                            } + device
                                }
                            }
                        }
                )
            } catch (e: Exception) {
                debugText = "Discovery Error: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    // IP State
    var o1 by remember { mutableStateOf("192") }
    var o2 by remember { mutableStateOf("168") }
    var o3 by remember { mutableStateOf("1") }
    var o4 by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8080") }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var p3 by remember { mutableStateOf("") }
    var p4 by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    val colors =
            if (currentTheme == AppTheme.White) {
                lightColors(
                        primary = currentTheme.primary,
                        secondary = currentTheme.secondary,
                        background = currentTheme.backgroundStart,
                        surface = Color.White,
                        onBackground = currentTheme.textPrimary,
                        onSurface = currentTheme.textPrimary
                )
            } else {
                darkColors(
                        primary = currentTheme.primary,
                        secondary = currentTheme.secondary,
                        background = currentTheme.backgroundStart,
                        surface = Color(0xFF1E1E1E),
                        onBackground = currentTheme.textPrimary,
                        onSurface = currentTheme.textPrimary
                )
            }

    var isUsbMode by remember { mutableStateOf(false) }

    var connectedDeviceName by remember { mutableStateOf<String?>(null) }

    // Track previous status for notifications
    var previousStatus by remember { mutableStateOf<CameraStatus?>(null) }

    // Update Window Title
    LaunchedEffect(connectionUrl) {
        // User requested to remove connected device name from title bar
        onTitleChange("MoonlightWebcam")
        if (connectionUrl == null) {
            connectedDeviceName = null
        }
    }

    // Status Change Notifier
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(cameraStatus) {
        val prev = previousStatus
        val curr = cameraStatus
        if (prev != null && curr != null) {
            if (prev.isStreaming && !curr.isStreaming) {
                // Stream Stopped
                val reason = if (curr.isClientConnected) "Stopped by user" else "Disconnected"

                // Clear PIN components
                p1 = ""
                p2 = ""
                p3 = ""
                p4 = ""

                snackbarHostState.showSnackbar(
                        "Streaming Stopped: $reason",
                        duration = SnackbarDuration.Short
                )
            } else if (!prev.isStreaming && curr.isStreaming) {
                snackbarHostState.showSnackbar(
                        "Streaming Started",
                        duration = SnackbarDuration.Short
                )
            }
        }
        previousStatus = curr
    }

    MaterialTheme(colors = colors) {
        Box(Modifier.fillMaxSize()) {

            // Background Content (Connection Screen or Stream)
            // applying blur and zoom when info/overlay is open
            val isOverlayOpen = showInfo
            val blurRadius by animateDpAsState(if (isOverlayOpen) 16.dp else 0.dp)
            val scale by animateFloatAsState(if (isOverlayOpen) 1.05f else 1f)

            Box(modifier = Modifier.fillMaxSize().scale(scale).blur(blurRadius)) {
                if (connectionUrl == null) {
                    ConnectionScreen(
                            o1,
                            { o1 = it },
                            o2,
                            { o2 = it },
                            o3,
                            { o3 = it },
                            o4,
                            { o4 = it },
                            port,
                            { port = it },
                            onConnect = {
                                val ip = if (isUsbMode) "127.0.0.1" else "$o1.$o2.$o3.$o4"
                                val pin = p1 + p2 + p3 + p4
                                if (ip.split(".").all { it.isNotEmpty() }) {
                                    val hostname =
                                            try {
                                                InetAddress.getLocalHost().hostName
                                            } catch (e: Exception) {
                                                "Desktop"
                                            }
                                    val pinParam = if (pin.isNotEmpty()) "?pin=$pin" else "?pin="
                                    connectionUrl =
                                            "http://$ip:$port/video$pinParam&clientName=$hostname"

                                    // Try to find name if matched in discovered list, else use IP
                                    targetDeviceName =
                                            discoveredDevices.find { it.ip == ip }?.name ?: ip
                                }
                            },
                            onInfo = { showInfo = true },
                            currentTheme = currentTheme,
                            onThemeChange = { onThemeChange(it) },
                            p1 = p1,
                            onP1 = { p1 = it },
                            p2 = p2,
                            onP2 = { p2 = it },
                            p3 = p3,
                            onP3 = { p3 = it },
                            p4 = p4,
                            onP4 = { p4 = it },
                            isUsbMode = isUsbMode,
                            onUsbModeChange = {
                                isUsbMode = it
                                if (it) {
                                    o1 = "127"
                                    o2 = "0"
                                    o3 = "0"
                                    o4 = "1"
                                }
                            }
                    )
                } else {
                    StreamView(
                            frame = currentFrame,
                            audioLevel = audioLevel,
                            debugText = debugText,
                            deviceName = targetDeviceName,
                            status = cameraStatus,
                            currentTheme = currentTheme,
                            onThemeChange = { onThemeChange(it) },
                            onDisconnect = { connectionUrl = null },
                            onCommand = { map ->
                                val baseUrl = connectionUrl!!.substringBefore("/video")
                                val pin = p1 + p2 + p3 + p4
                                // Inject PIN into command parameters
                                val secureMap = map.toMutableMap()
                                if (pin.isNotEmpty()) secureMap["pin"] = pin

                                scope.launch(Dispatchers.IO) {
                                    sendControlCommand(baseUrl, secureMap)
                                }
                            },
                            onSnapshot = {
                                scope.launch(Dispatchers.IO) {
                                    val baseUrl = connectionUrl!!.substringBefore("/video")
                                    val pin = p1 + p2 + p3 + p4
                                    val params = mapOf("pin" to pin)
                                    val file = downloadSnapshot(baseUrl, params)
                                    withContext(Dispatchers.Main) {
                                        if (file != null) {
                                            snackbarHostState.showSnackbar("Saved: ${file.name}")
                                            // Play sound
                                            try {
                                                Toolkit.getDefaultToolkit().beep()
                                            } catch (e: Exception) {
                                                // Ignore
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("Snapshot failed")
                                        }
                                    }
                                }
                            }
                    )
                }
            }

            // Overlay Content
            if (showInfo) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                    DesktopInfoScreen(
                            initialTab = initialInfoTab,
                            onBack = { showInfo = false },
                            currentTheme = currentTheme,
                            onThemeChange = { newTheme -> onThemeChange(newTheme) }
                    )
                }
            }

            LaunchedEffect(connectionUrl) {
                if (connectionUrl != null) {
                    snackbarHostState.showSnackbar(
                            "Connecting...",
                            duration = SnackbarDuration.Short
                    )
                    // 1. Status Loop
                    launch(Dispatchers.IO) {
                        val pin = p1 + p2 + p3 + p4
                        var failureCount = 0
                        while (connectionUrl != null) {
                            try {
                                val baseUrl = connectionUrl!!.substringBefore("/video")
                                val status = fetchCameraStatus(baseUrl, pin)
                                withContext(Dispatchers.Main) {
                                    cameraStatus = status
                                    if (!status?.deviceName.isNullOrEmpty()) {
                                        targetDeviceName = status!!.deviceName
                                    }
                                }
                                failureCount = 0
                            } catch (e: Exception) {
                                failureCount++
                                if (failureCount >= 3) {
                                    withContext(Dispatchers.Main) {
                                        if (connectionUrl != null) {
                                            debugText = "Connection lost (Heartbeat)"
                                            snackbarHostState.showSnackbar("Connection lost")
                                            connectionUrl = null
                                        }
                                    }
                                    break
                                }
                            }
                            delay(2000)
                        }
                    }
                    // 2. Video Loop - Persistent
                    launch(Dispatchers.IO) {
                        var retryCount = 0
                        while (connectionUrl != null) {
                            var conn: HttpURLConnection? = null
                            try {
                                // Append clientName to URL
                                val separator = if (connectionUrl!!.contains("?")) "&" else "?"
                                val fullUrl = "$connectionUrl${separator}clientName=Desktop"

                                val url = URL(fullUrl)
                                conn = url.openConnection() as HttpURLConnection
                                conn.connectTimeout = 5000
                                conn.readTimeout = 15000
                                conn.useCaches = false

                                val stream = conn.inputStream
                                val dataInputStream = DataInputStream(stream)

                                withContext(Dispatchers.Main) {
                                    debugText = "Connected"
                                    retryCount = 0 // Reset retry count on success
                                }

                                while (connectionUrl != null) {
                                    // Simplified MJPEG parsing
                                    var contentLength = -1
                                    var line: String
                                    while (true) {
                                        line = dataInputStream.readLine() ?: break
                                        if (line.startsWith("Content-Length:")) {
                                            contentLength = line.substringAfter(":").trim().toInt()
                                        }
                                        if (line.isEmpty() && contentLength != -1) break
                                    }

                                    if (contentLength != -1) {
                                        val bytes = ByteArray(contentLength)
                                        dataInputStream.readFully(bytes)
                                        val image = Image.makeFromEncoded(bytes)
                                        val bitmap = image.toComposeImageBitmap()
                                        withContext(Dispatchers.Main) { currentFrame = bitmap }
                                    } else {
                                        // Header parsing failed or stream ended
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                // Stream failed (EOF, Timeout, Refused).
                                retryCount++
                                withContext(Dispatchers.Main) {
                                    if (retryCount > 10) {
                                        // Max retries exceeded - Go back to connection screen
                                        debugText = "Connection Timed Out"
                                        snackbarHostState.showSnackbar("Connection Timed Out")
                                        connectionUrl = null
                                    } else {
                                        debugText = "Waiting for stream... ($retryCount/10)"
                                        currentFrame = null // Clear frozen frame
                                    }
                                }
                                delay(1000)
                            } finally {
                                conn?.disconnect()
                            }
                            delay(500) // Retry delay
                        }
                    }

                    // 3. Audio Loop - Persistent
                    launch(Dispatchers.IO) {
                        while (connectionUrl != null) {
                            try {
                                // Prevent mic audio leakage in Cast Mode
                                if (cameraStatus?.lens == "SCREEN") {
                                    delay(1000)
                                    continue
                                }

                                val pin = p1 + p2 + p3 + p4
                                connectToAudio(connectionUrl!!, pin) { level -> audioLevel = level }
                            } catch (e: Exception) {
                                // Audio failed. Wait and retry.
                                delay(2000)
                            }
                            delay(1000)
                        }
                    }
                }
            }
            SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
fun DesktopInfoScreen(
        initialTab: Int = 0,
        onBack: () -> Unit,
        currentTheme: AppTheme,
        onThemeChange: (AppTheme) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    val tabs =
            listOf(
                    "USB Setup",
                    "Wi-Fi",
                    "Screen Cast",
                    "Common Issues",
                    "Firewall",
                    "Desktop App",
                    "About"
            )
    val backdropAlpha = if (MaterialTheme.colors.isLight) 0.12f else 0.25f
    // Backdrop
    Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
    ) {
        // ─── Top Right Actions ────────────────────────────────────────────────
        Row(
                Modifier.align(Alignment.TopEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // THEME TOGGLE (In Stream View)
            IconButton(
                    onClick = {
                        val themes = AppTheme.values()
                        val nextIndex = (themes.indexOf(currentTheme) + 1) % themes.size
                        onThemeChange(themes[nextIndex])
                    },
                    modifier = Modifier.background(ControlBackground, CircleShape).size(48.dp)
            ) { Icon(Icons.Filled.Palette, "Theme", tint = currentTheme.primary) }
        }

        Card(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                backgroundColor = MaterialTheme.colors.surface,
                modifier =
                        Modifier.width(900.dp).heightIn(min = 500.dp, max = 700.dp).padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                Icons.Filled.Info,
                                "Infos",
                                tint = MaterialTheme.colors.primary,
                                modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                                "Guide & Info",
                                style = MaterialTheme.typography.h5,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onSurface
                        )
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colors.onSurface)
                    }
                }

                // Tabs with scroll support
                ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        backgroundColor = Color.Transparent,
                        contentColor = MaterialTheme.colors.primary,
                        edgePadding = 24.dp,
                        divider = {
                            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
                        }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                            title,
                                            style = MaterialTheme.typography.button,
                                            fontWeight =
                                                    if (selectedTab == index) FontWeight.ExtraBold
                                                    else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                    )
                                },
                                selectedContentColor = MaterialTheme.colors.primary,
                                unselectedContentColor =
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                // Content with proper scroll management to prevent "Constraints" crash
                Column(
                        modifier =
                                Modifier.weight(1f) // Fill remaining space in Card correctly
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(24.dp)
                ) {
                    when (selectedTab) {
                        0 -> UsbGuideTabContent()
                        1 -> WiFiTabContent()
                        2 -> ScreenCastTabContent()
                        3 -> TroubleshootingTabContent()
                        4 -> FirewallTabContent()
                        5 -> DesktopAppTabContent()
                        6 -> AboutTabContent()
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenCastTabContent() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Default.Cast,
                    null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                    "Screen Cast Mode",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.primary
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
                "Mirror your phone screen to your desktop in real-time. Useful for demonstrations, gaming/recording, or using your phone apps on a larger screen.",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(24.dp))

        Text("Features", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TipItem(Icons.Default.TouchApp, "View your phone screen with low latency.")
            TipItem(
                    Icons.Default.Mouse,
                    "Input Control: Coming Soon (Control phone with Mouse/Keyboard)."
            )
            TipItem(Icons.Default.Hd, "High Quality mirroring over USB or 5GHz Wi-Fi.")
        }
    }
}

@Composable
fun AboutTabContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {

        // App icon
        Icon(
                Icons.Filled.Videocam,
                contentDescription = null,
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(80.dp)
        )

        Spacer(Modifier.height(16.dp))

        // App name
        Text("MoonlightWebcam", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)

        Text(
                "v1.3.0 • Desktop Client",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(24.dp))

        // Description
        Text(
                "MoonlightWebcam transforms your phone’s high-quality camera into a professional webcam for your computer. " +
                        "Connect wirelessly for flexibility or use USB for ultra-low latency and maximum stability.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(24.dp))

        // Tips header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                    "Tips for best experience",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
            )
        }

        Spacer(Modifier.height(12.dp))

        // Tips list
        Column(modifier = Modifier.padding(horizontal = 32.dp)) {
            TipItem(
                    icon = Icons.Default.Wifi,
                    text = "Use 5GHz Wi-Fi for better performance in wireless mode."
            )
            TipItem(
                    icon = Icons.Default.Router,
                    text = "Ensure both devices are on the exact same network."
            )
            TipItem(
                    icon = Icons.Default.Usb,
                    text = "Switch to USB mode if you experience lag or interference."
            )
        }
    }
}

@Composable
fun UsbGuideTabContent() {
    Column(modifier = Modifier.fillMaxWidth()) {

        // Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Default.Usb,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                    "USB Wired Connection",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.primary
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
                "For the lowest latency, highest 60FPS quality, and reliability, use a wired USB connection. This method bypasses Wi-Fi interference entirely.",
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f)
        )

        Spacer(Modifier.height(24.dp))

        // Android Section
        AddPlatformGuide(
                title = "Android",
                icon = Icons.Default.Android,
                description =
                        "1. Enable Developer Options & USB Debugging on your phone.\n" +
                                "2. Connect phone via USB.\n" +
                                "3. Install ADB (Android Debug Bridge).\n" +
                                "4. Run this command in your terminal:",
                command = "adb forward tcp:8080 tcp:8080"
        )

        Spacer(Modifier.height(24.dp))

        // iOS Section
        AddPlatformGuide(
                title = "iOS",
                Icons.Default.PhoneIphone,
                description =
                        "1. Install libimobiledevice (Mac: `brew install libimobiledevice`).\n" +
                                "2. Connect iPhone via Lightning / USB-C.\n" +
                                "3. Trust the computer on your iPhone.\n" +
                                "4. Run this command:",
                command = "iproxy 8080 8080"
        )

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colors.secondary,
                    modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                    "Once the command is running, switch the Connection Mode to USB on the main screen and click Connect.",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun TipItem(icon: ImageVector, text: String) {
    Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colors.secondary,
                modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
                text,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f)
        )
    }
}

@Composable
fun AddPlatformGuide(title: String, icon: ImageVector, description: String, command: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        Text(
                description,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f),
                lineHeight = 18.sp
        )

        Spacer(Modifier.height(8.dp))

        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                        Modifier.background(
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
                                        RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
        ) {
            Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colors.secondary,
                    modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(command, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
    }
}

@Composable
fun TroubleshootingTabContent() {
    Column(modifier = Modifier.fillMaxWidth()) {

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                    "Common Issues",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.primary
            )
        }

        Spacer(Modifier.height(16.dp))

        GuideItem(
                title = "App shows 'Connected' but no video",
                description =
                        "This usually means the PIN is correct but video packets are blocked. Firewalls or antivirus software are the most common cause.",
                icon = Icons.Default.VisibilityOff
        )

        GuideItem(
                title = "Cannot find device",
                description =
                        "Ensure both devices are on the same Wi-Fi network. Some routers isolate 2.4GHz and 5GHz bands. Guest networks often block device discovery.",
                icon = Icons.Default.WifiOff
        )

        GuideItem(
                title = "Laggy or stuttering video",
                description =
                        "Switch to 5GHz Wi-Fi or USB mode. Bluetooth devices and crowded networks can interfere with streaming performance.",
                icon = Icons.Default.Speed
        )
    }
}

@Composable
fun FirewallTabContent() {
    Column(modifier = Modifier.fillMaxWidth()) {

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                    "Firewall Configuration",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.primary
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colors.secondary,
                    modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                    "Firewalls are the #1 cause of connection issues.",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
                "You must allow this application (Java / java.exe) to communicate on private and public networks. Required ports must not be blocked.",
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f)
        )

        Spacer(Modifier.height(16.dp))

        GuideItem(
                title = "Windows",
                description =
                        "Allow the app through Windows Defender Firewall. Ensure TCP port 8080 and UDP port 5353 (device discovery) are open.",
                icon = Icons.Default.DesktopWindows
        )

        GuideItem(
                title = "macOS",
                description =
                        "Go to System Settings → Network → Firewall. Turn off 'Block all incoming connections' and allow signed applications.",
                icon = Icons.Default.LaptopMac
        )
    }
}

@Composable
fun WiFiTabContent() {
    Column {
        GuideItem(
                "Use 5GHz Wi-Fi",
                "For low latency and high quality, always connect both devices to a 5GHz Wi-Fi network. 2.4GHz is often too congested.",
                Icons.Filled.Wifi
        )
        Spacer(Modifier.height(16.dp))
        GuideItem(
                "Signal Strength",
                "Keep your phone and computer close to the router. Walls heavily impact 5GHz signal.",
                Icons.Filled.SignalWifi4Bar
        )
        Spacer(Modifier.height(16.dp))
        GuideItem(
                "Channel Interference",
                "If stuttering persists, try changing your router's Wi-Fi channel to a less congested one (uses WiFi Analyzer app to check).",
                Icons.Filled.Warning
        )
    }
}

@Composable
fun DesktopAppTabContent() {
    Column {
        GuideItem(
                "Shortcuts",
                "• Space: Switch Camera / Controls\n• F: Toggle Fullscreen (if supported)\n• Esc: Exit",
                Icons.Filled.Keyboard
        )
        Spacer(Modifier.height(16.dp))
        GuideItem(
                "Input Injection",
                "In 'Screen Cast' mode, your mouse clicks and keyboard input are sent to the Android device. Ensure the accessibility service is enabled on the phone.",
                Icons.Filled.Mouse
        )
        Spacer(Modifier.height(16.dp))
        GuideItem(
                "Connection",
                "Use USB for the absolute best performance (lowest latency). Wi-Fi is great for convenience but depends heavily on your router.",
                Icons.Filled.Usb
        )
    }
}

@Composable
fun MeetingAppsTabContent() {
    Column(modifier = Modifier.fillMaxWidth()) {

        // Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Default.VideoCall,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                    "Using with Zoom, Teams & Meet",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.primary
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
                "Since MoonlightWebcam streams raw video over the network, you need a virtual " +
                        "camera " +
                        "driver to feed it into meeting apps like Zoom, Microsoft Teams, or Google Meet.",
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f)
        )

        Spacer(Modifier.height(24.dp))

        // OBS Section Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colors.secondary,
                    modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Recommended Method: OBS Studio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(Modifier.height(12.dp))

        GuideStep(1, "Install OBS Studio (free & open source).")
        GuideStep(2, "In OBS, add a Browser Source or VLC Video Source.")
        GuideStep(
                3,
                "Set the source URL to your MoonlightWebcam stream (e.g. http://192.168.1" +
                        ".50:8080/video)."
        )
        GuideStep(4, "Right-click the preview → Transform → Fit to Screen.")
        GuideStep(5, "Click Start Virtual Camera in the OBS controls.")
        GuideStep(6, "In Zoom, Teams, or Meet, select OBS Virtual Camera as your video input.")

        Spacer(Modifier.height(16.dp))

        // Info card
        Card(
                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
                elevation = 0.dp
        ) {
            Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colors.secondary,
                        modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                        "Bonus: OBS unlocks overlays, filters, scene switching, and full control over your webcam feed.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun GuideItem(title: String, description: String, icon: ImageVector = Icons.Default.Warning) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colors.secondary,
                    modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
        }

        Spacer(Modifier.height(4.dp))

        Text(
                description,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f),
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 26.dp)
        )
    }
}

@Composable
fun GuideStep(step: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 6.dp)) {
        Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colors.secondary,
                modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
                "$step. $text",
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                lineHeight = 18.sp
        )
    }
}

@Composable
fun UsbCommandRow(icon: ImageVector, title: String, command: String, onCopy: () -> Unit) {
    Column(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(
                                    MaterialTheme.colors.primary.copy(alpha = 0.06f),
                                    RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colors.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
        }

        Spacer(Modifier.height(6.dp))

        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                        Modifier.background(
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
                                        RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                    Icons.Default.Terminal,
                    null,
                    tint = MaterialTheme.colors.secondary,
                    modifier = Modifier.size(14.dp)
            )

            Spacer(Modifier.width(6.dp))

            Text(
                    command,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onCopy, modifier = Modifier.size(26.dp)) {
                Icon(
                        Icons.Default.ContentCopy,
                        null,
                        tint = MaterialTheme.colors.secondary,
                        modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun ConnectionScreen(
        o1: String,
        onO1: (String) -> Unit,
        o2: String,
        onO2: (String) -> Unit,
        o3: String,
        onO3: (String) -> Unit,
        o4: String,
        onO4: (String) -> Unit,
        port: String,
        onPort: (String) -> Unit,
        onConnect: () -> Unit,
        onInfo: () -> Unit,
        currentTheme: AppTheme,
        onThemeChange: (AppTheme) -> Unit,
        p1: String,
        onP1: (String) -> Unit,
        p2: String,
        onP2: (String) -> Unit,
        p3: String,
        onP3: (String) -> Unit,
        p4: String,
        onP4: (String) -> Unit,
        isUsbMode: Boolean,
        onUsbModeChange: (Boolean) -> Unit
) {
    val fr1 = remember { FocusRequester() }
    val fr2 = remember { FocusRequester() }
    val fr3 = remember { FocusRequester() }
    val fr4 = remember { FocusRequester() }
    val pf1 = remember { FocusRequester() }
    val pf2 = remember { FocusRequester() }
    val pf3 = remember { FocusRequester() }
    val pf4 = remember { FocusRequester() }
    // Handlers (define once, above UI)
    val o1Handler: (String) -> Unit = if (!isUsbMode) onO1 else { _ -> }
    val o2Handler: (String) -> Unit = if (!isUsbMode) onO2 else { _ -> }
    val o3Handler: (String) -> Unit = if (!isUsbMode) onO3 else { _ -> }
    val o4Handler: (String) -> Unit = if (!isUsbMode) onO4 else { _ -> }
    val clipboardManager = LocalClipboardManager.current
    Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
    ) {

        // ─── Top Right Actions ────────────────────────────────────────────────
        Row(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            IconButton(
                    onClick = {
                        val themes = AppTheme.values()
                        val next = (themes.indexOf(currentTheme) + 1) % themes.size
                        onThemeChange(themes[next])
                    }
            ) {
                Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onInfo) {
                Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        // ─── Main Card ────────────────────────────────────────────────────────
        Card(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                backgroundColor = MaterialTheme.colors.surface,
                modifier = Modifier.width(450.dp)
        ) {
            Column(
                    modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(64.dp)
                )

                Spacer(Modifier.height(24.dp))

                Text(
                        "MoonlightWebcam",
                        style = MaterialTheme.typography.h5,
                        fontWeight = FontWeight.Bold
                )

                Text(
                        "Remote Viewer",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )

                Spacer(Modifier.height(32.dp))

                // ─── Mode Switch ───────────────────────────────────────────────
                val selectedTabIndex = if (isUsbMode) 1 else 0

                TabRow(
                        selectedTabIndex = selectedTabIndex,
                        backgroundColor = MaterialTheme.colors.surface,
                        contentColor = MaterialTheme.colors.primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                    modifier =
                                            Modifier.tabIndicatorOffset(
                                                    tabPositions[selectedTabIndex]
                                            ),
                                    color = MaterialTheme.colors.primary
                            )
                        },
                        modifier =
                                Modifier.clip(RoundedCornerShape(12.dp))
                                        .background(
                                                MaterialTheme.colors.onSurface.copy(alpha = 0.05f)
                                        )
                ) {
                    Tab(
                            selected = !isUsbMode,
                            onClick = { onUsbModeChange(false) },
                            text = { Text("Wi-Fi", fontWeight = FontWeight.SemiBold) }
                    )

                    Tab(
                            selected = isUsbMode,
                            onClick = { onUsbModeChange(true) },
                            text = { Text("USB", fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(Modifier.height(24.dp))

                var showHelpDialog by remember { mutableStateOf(false) }

                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                            if (isUsbMode) "LOCALHOST (USB)" else "DEVICE IP ADDRESS",
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.primary,
                            modifier = Modifier.weight(1f)
                    )

                    IconButton(
                            onClick = { showHelpDialog = true },
                            modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                                Icons.AutoMirrored.Filled.Help,
                                contentDescription = "Help",
                                tint = MaterialTheme.colors.secondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (showHelpDialog) {
                    androidx.compose.material.AlertDialog(
                            onDismissRequest = { showHelpDialog = false },
                            shape = RoundedCornerShape(20.dp),
                            title = {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                            if (isUsbMode) "USB Connection Setup"
                                            else "Remote Control Access",
                                            fontWeight = FontWeight.Bold
                                    )

                                    Box(
                                            modifier =
                                                    Modifier.size(32.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Red)
                                                            .clickable { showHelpDialog = false },
                                            contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                                    // ───────── USB MODE ─────────
                                    if (isUsbMode) {
                                        Text(
                                                "Enable USB debugging (Android) or trust the device (iOS).",
                                                color =
                                                        MaterialTheme.colors.onSurface.copy(
                                                                alpha = 0.8f
                                                        )
                                        )

                                        UsbCommandRow(
                                                icon = Icons.Default.Android,
                                                title = "Android (ADB)",
                                                command = "adb forward tcp:8080 tcp:8080",
                                                onCopy = {
                                                    clipboardManager.setText(
                                                            AnnotatedString(
                                                                    "adb forward tcp:8080 tcp:8080"
                                                            )
                                                    )
                                                }
                                        )

                                        UsbCommandRow(
                                                icon = Icons.Default.PhoneIphone,
                                                title = "iOS (iproxy)",
                                                command = "iproxy 8080 8080",
                                                onCopy = {
                                                    clipboardManager.setText(
                                                            AnnotatedString("iproxy 8080 8080")
                                                    )
                                                }
                                        )
                                    }

                                    // ───────── NON-USB (ACCESSIBILITY) ─────────
                                    else {
                                        Text(
                                                "Required for mouse & keyboard input control.",
                                                color =
                                                        MaterialTheme.colors.onSurface.copy(
                                                                alpha = 0.8f
                                                        )
                                        )

                                        Text(
                                                "Enable 'Moonlight Input' in Accessibility Settings on Android.",
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colors.secondary
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showHelpDialog = false }) { Text("Close") }
                            },
                            backgroundColor = MaterialTheme.colors.surface,
                            contentColor = MaterialTheme.colors.onSurface
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ─── Connection Row ───────────────────────────────────────────
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // IP + Port Container

                    Box(
                            modifier =
                                    Modifier.weight(1f)
                                            .alpha(if (isUsbMode) 0.5f else 1f)
                                            .background(
                                                    MaterialTheme.colors.onSurface.copy(
                                                            alpha = 0.05f
                                                    ),
                                                    RoundedCornerShape(16.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            IpField(
                                    value = o1,
                                    onValueChange = o1Handler,
                                    focusRequester = fr1,
                                    modifier = Modifier.width(72.dp),
                                    onNext = { fr2.requestFocus() }
                            )

                            Dot()

                            IpField(
                                    value = o2,
                                    onValueChange = o2Handler,
                                    focusRequester = fr2,
                                    modifier = Modifier.width(72.dp),
                                    onNext = { fr3.requestFocus() },
                                    onPrev = { fr1.requestFocus() }
                            )

                            Dot()

                            IpField(
                                    value = o3,
                                    onValueChange = o3Handler,
                                    focusRequester = fr3,
                                    modifier = Modifier.width(72.dp),
                                    onNext = { fr4.requestFocus() },
                                    onPrev = { fr2.requestFocus() }
                            )

                            Dot()

                            IpField(
                                    value = o4,
                                    onValueChange = o4Handler,
                                    focusRequester = fr4,
                                    modifier = Modifier.width(72.dp),
                                    onPrev = { fr3.requestFocus() }
                            )

                            Text(
                                    ":",
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )

                            BasicTextField(
                                    value = port,
                                    onValueChange = {
                                        if (it.all(Char::isDigit) && it.length <= 5) {
                                            onPort(it)
                                        }
                                    },
                                    modifier =
                                            Modifier.width(72.dp) // slightly wider
                                                    // than before
                                                    .height(40.dp), // match IP field
                                    // height
                                    textStyle =
                                            TextStyle(
                                                    color = MaterialTheme.colors.onSurface,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                            ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                                    decorationBox = { innerTextField ->
                                        Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                        ) { innerTextField() }
                                    }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                Text(
                        "ENTER PIN",
                        style = MaterialTheme.typography.overline,
                        color = MaterialTheme.colors.primary
                )

                Spacer(Modifier.height(8.dp))

                Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    PinDigitField(
                            value = p1,
                            onValueChange = onP1,
                            focusRequester = pf1,
                            onNext = { pf2.requestFocus() },
                            onBack = {},
                            isFocus = true
                    )

                    PinDigitField(
                            value = p2,
                            onValueChange = onP2,
                            focusRequester = pf2,
                            onNext = { pf3.requestFocus() },
                            onBack = { pf1.requestFocus() },
                            isFocus = false
                    )

                    PinDigitField(
                            value = p3,
                            onValueChange = onP3,
                            focusRequester = pf3,
                            onNext = { pf4.requestFocus() },
                            onBack = { pf2.requestFocus() },
                            isFocus = false
                    )

                    PinDigitField(
                            value = p4,
                            onValueChange = onP4,
                            focusRequester = pf4,
                            onNext = {},
                            onBack = { pf3.requestFocus() },
                            isFocus = false
                    )
                }

                Spacer(Modifier.height(16.dp))
                IconButton(
                        onClick = onConnect,
                        modifier =
                                Modifier.size(56.dp)
                                        .align(Alignment.CenterHorizontally)
                                        .background(MaterialTheme.colors.primary, CircleShape)
                ) { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White) }
            }
        }
    }
}

@Composable
fun StreamView(
        frame: ImageBitmap?,
        audioLevel: Float,
        debugText: String,
        deviceName: String?,
        status: CameraStatus?,
        currentTheme: AppTheme,
        onThemeChange: (AppTheme) -> Unit,
        onDisconnect: () -> Unit,
        onCommand: (params: Map<String, String>) -> Unit,
        onSnapshot: () -> Unit
) {
    // Client-Side States
    var rotation by remember { mutableStateOf(0f) }
    var isMirrored by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    // Tap Feedback State
    var tapLocation by remember { mutableStateOf<Offset?>(null) }
    var isFlashing by remember { mutableStateOf(false) }

    // Focus requester to capture keyboard input
    val focusRequester = remember { FocusRequester() }
    val isScreenCast = status?.lens == "SCREEN"

    LaunchedEffect(isFlashing) {
        if (isFlashing) {
            delay(100)
            isFlashing = false
        }
    }

    // Auto-hide feedback
    LaunchedEffect(tapLocation) {
        if (tapLocation != null) {
            delay(1000)
            tapLocation = null
        }
    }

    // Audio Logic: Mute if in Screen Cast mode preventing mic leakage
    val effectiveAudioLevel = if (isScreenCast) 0f else audioLevel

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(
                                    Brush.verticalGradient(
                                            colors =
                                                    listOf(
                                                            currentTheme.backgroundStart,
                                                            currentTheme.backgroundEnd
                                                    )
                                    )
                            ),
            contentAlignment = Alignment.Center
    ) {
        if (frame != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize().rotate(rotation)) {
                    val flipVal = if (isMirrored) -1f else 1f
                    // If rotated 90/270, the X-axis is
                    // vertical, so we must
                    // flip Y to mirror horizontally on
                    // screen
                    val isPortraitRot = rotation == 90f || rotation == 270f
                    val sx = if (isPortraitRot) 1f else flipVal
                    val sy = if (isPortraitRot) flipVal else 1f

                    val safeFrameW = frame.width.toFloat().coerceAtLeast(1f)
                    val safeFrameH = frame.height.toFloat().coerceAtLeast(1f)
                    val safeSizeW = size.width.coerceAtLeast(1f)
                    val safeSizeH = size.height.coerceAtLeast(1f)

                    val scale = min(safeSizeW / safeFrameW, safeSizeH / safeFrameH)
                    val targetW = frame.width * scale
                    val targetH = frame.height * scale
                    val offsetX = (size.width - targetW) / 2
                    val offsetY = (size.height - targetH) / 2

                    // DEBUG: Confirm we are entering
                    // draw phase
                    println(
                            "StreamView: Canvas Size: ${size.width}x${size.height}, Frame: ${frame.width}x${frame.height}"
                    )

                    translate(left = offsetX, top = offsetY) {
                        withTransform({
                            // Center pivot for
                            // mirroring
                            translate(targetW / 2f, targetH / 2f)
                            scale(sx, sy)
                            translate(-targetW / 2f, -targetH / 2f)
                        }) {
                            drawImage(
                                    image = frame,
                                    dstSize = IntSize(targetW.toInt(), targetH.toInt())
                            )
                        }
                    }

                    // GRID OVERLAY
                    if (showGrid) {
                        val stroke = 1.dp.toPx()
                        val color = Color.White.copy(alpha = 0.3f)
                        // Vertical Thirds
                        drawLine(
                                color,
                                Offset(offsetX + targetW / 3, offsetY),
                                Offset(offsetX + targetW / 3, offsetY + targetH),
                                stroke
                        )
                        drawLine(
                                color,
                                Offset(offsetX + 2 * targetW / 3, offsetY),
                                Offset(offsetX + 2 * targetW / 3, offsetY + targetH),
                                stroke
                        )
                        // Horizontal Thirds
                        drawLine(
                                color,
                                Offset(offsetX, offsetY + targetH / 3),
                                Offset(offsetX + targetW, offsetY + targetH / 3),
                                stroke
                        )
                        drawLine(
                                color,
                                Offset(offsetX, offsetY + 2 * targetH / 3),
                                Offset(offsetX + targetW, offsetY + 2 * targetH / 3),
                                stroke
                        )
                    }
                }

                // Audio Status Overlay (Top-Left)
                if (status != null) {
                    Box(
                            modifier =
                                    Modifier.align(Alignment.TopStart)
                                            .padding(16.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                    if (status.isAudioMuted) Icons.Default.MicOff
                                    else Icons.Default.Mic,
                                    contentDescription = "Audio Status",
                                    tint = if (status.isAudioMuted) Color.Red else Color.White,
                                    modifier = Modifier.size(24.dp)
                            )
                            if (!status.isAudioMuted) {
                                Spacer(Modifier.width(8.dp))
                                // Simple
                                // Visualizer Bar
                                Box(
                                        Modifier.width(60.dp)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color.Gray.copy(0.3f))
                                ) {
                                    Box(
                                            Modifier.fillMaxWidth(effectiveAudioLevel)
                                                    .fillMaxHeight()
                                                    .background(Color.Green)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Request focus for keyboard input on logic change
            LaunchedEffect(isScreenCast) { if (isScreenCast) focusRequester.requestFocus() }

            Box(
                    Modifier.fillMaxSize()
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { event ->
                                if (isScreenCast && event.type == KeyEventType.KeyDown) {
                                    val textToSend =
                                            if (event.key == Key.Backspace) "\b"
                                            else if (event.utf16CodePoint in 32..126)
                                                    event.utf16CodePoint.toChar().toString()
                                            else null

                                    if (textToSend != null) {
                                        onCommand(
                                                mapOf("inputType" to "text", "text" to textToSend)
                                        )
                                        return@onKeyEvent true
                                    }
                                }
                                false
                            }
                            .pointerInput(frame, rotation, isMirrored, status?.lens) {
                                detectTapGestures { offset ->
                                    val isScreenCast = status?.lens == "SCREEN"
                                    // Removed: if (isScreenCast) return@detectTapGestures

                                    // frame is guaranteed non-null here due to parent capture?
                                    // Actually it's passed as ImageBitmap, which is non-null?
                                    // No, let's check the argument. 'imageBitmap'.
                                    // Ah, inside detectTapGestures 'frame' might be capturing the
                                    // outer
                                    // variable?
                                    // Wait, StreamView takes 'imageBitmap'.
                                    // The code uses 'frame' which likely refers to 'imageBitmap'
                                    // arg...
                                    // wait.
                                    // In Step 7159 snippet:
                                    // fun StreamView(imageBitmap: ImageBitmap, ...
                                    // ...
                                    // Box(Modifier...pointerInput(frame...)
                                    // The snippet I see uses 'frame'. Is 'frame' the argument name?
                                    // In Step 7159 snippet, the argument is 'imageBitmap'.
                                    // BUT inside I see `if (frame != null)`.
                                    // This suggests I might have renamed the argument in StreamView
                                    // or
                                    // 'frame' is a captured outer variable.
                                    // Let's check the StreamView definition again.
                                    // In Step 7159: `fun StreamView(imageBitmap: ImageBitmap...`
                                    // But lines 1971: `pointerInput(frame...`
                                    // This means 'frame' is likely NOT the argument 'imageBitmap'.
                                    // It's probably borrowing from outer scope?
                                    // Wait, StreamView is a Composable.
                                    // If `imageBitmap` is the argument, I should use `imageBitmap`.
                                    // Let's assume 'frame' was a typo in my previous reading or it
                                    // is
                                    // indeed capturing something.
                                    // However, let's just make it check 'imageBitmap' if that's the
                                    // argument.
                                    // Actually, I should probably stick to `imageBitmap`.

                                    // Ensure frame is valid before handling taps
                                    if (frame != null) {
                                        val isPortraitRot = rotation == 90f || rotation == 270f
                                        // Calculate correct scale depending on rotation
                                        val scale =
                                                min(
                                                        size.width /
                                                                (if (isPortraitRot) frame.height
                                                                        else frame.width)
                                                                        .toFloat(),
                                                        size.height /
                                                                (if (isPortraitRot) frame.width
                                                                        else frame.height)
                                                                        .toFloat()
                                                )

                                        val targetW =
                                                (if (isPortraitRot) frame.height else frame.width) *
                                                        scale
                                        val targetH =
                                                (if (isPortraitRot) frame.width else frame.height) *
                                                        scale

                                        val offsetX = (size.width - targetW) / 2
                                        val offsetY = (size.height - targetH) / 2

                                        // Check if tap is within displayed image
                                        if (offset.x in offsetX..(offsetX + targetW) &&
                                                        offset.y in offsetY..(offsetY + targetH)
                                        ) {
                                            val normX = (offset.x - offsetX) / targetW
                                            val normY = (offset.y - offsetY) / targetH

                                            if (isScreenCast) {
                                                // Remote Input Injection
                                                onCommand(
                                                        mapOf(
                                                                "inputType" to "mouse",
                                                                "action" to "tap",
                                                                "x" to normX.toString(),
                                                                "y" to normY.toString()
                                                        )
                                                )

                                                // No explicit visual feedback for remote taps
                                                // (white screen
                                                // is distracting)
                                                // tapLocation = offset // Optional: show ring? User
                                                // asked
                                                // to remove it.
                                            } else {
                                                // Camera Focus Logic
                                                tapLocation = offset
                                                isFlashing = true // Visual feedback

                                                // Calculate focus point in
                                                // 0..1 relative to image
                                                // rotation (Camera sensor
                                                // orientation)
                                                // This is tricky: we want
                                                // (0,0) to be top-left of
                                                // the SENSOR.
                                                // Usually, we just pass
                                                // screen-relative coords
                                                // and let Android handle
                                                // metering areas,
                                                // but here we are manual.
                                                // Let's just pass the
                                                // visual X/Y (0..1) and let
                                                // Server rotate if needed.

                                                onCommand(
                                                        mapOf(
                                                                "focusX" to normX.toString(),
                                                                "focusY" to normY.toString()
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
            )

            if (isFlashing) {
                // Subtle flash only, not opaque white
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.3f)))
            }
        } else {
            // Show Loading / Waiting State if Connected but no FRAME
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = NeonPurple)
                Spacer(Modifier.height(16.dp))
                Text(
                        "Waiting for Video...",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.subtitle1
                )
            }
        }

        // Focus Reticle Overlay
        tapLocation?.let { loc ->
            Canvas(
                    modifier =
                            Modifier.offset(
                                            x = with(LocalDensity.current) { (loc.x - 32).toDp() },
                                            y = with(LocalDensity.current) { (loc.y - 32).toDp() }
                                    )
                                    .size(64.dp)
            ) {
                val stroke = Stroke(width = 3.dp.toPx())
                drawCircle(Color.Cyan, style = stroke)
                // Crosshair
                drawLine(
                        Color.Cyan.copy(alpha = 0.5f),
                        Offset(size.width / 2, 0f),
                        Offset(size.width / 2, size.height),
                        strokeWidth = 2.dp.toPx()
                )
                drawLine(
                        Color.Cyan.copy(alpha = 0.5f),
                        Offset(0f, size.height / 2),
                        Offset(size.width, size.height / 2),
                        strokeWidth = 2.dp.toPx()
                )
            }
        }

        // CONTROL SIDEBAR
        if (showControls && status != null) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().padding(16.dp)) {
                ModernControlsPanel(
                        status = status,
                        theme = currentTheme,
                        onCommand = onCommand,
                        isMirrored = isMirrored,
                        onMirrorChange = { isMirrored = it },
                        showGrid = showGrid,
                        onGridChange = { showGrid = it },
                        onSnapshot = {
                            isFlashing = true
                            onSnapshot()
                        }
                )
            }
        }

        // TOOLBAR (Top Right)
        Row(
                Modifier.align(Alignment.TopEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // THEME TOGGLE
            IconButton(
                    onClick = {
                        val themes = AppTheme.values()
                        val nextIndex = (themes.indexOf(currentTheme) + 1) % themes.size
                        onThemeChange(themes[nextIndex])
                    },
                    modifier = Modifier.background(ControlBackground, CircleShape).size(48.dp)
            ) { Icon(Icons.Filled.Palette, "Theme", tint = currentTheme.primary) }

            // TOGGLE CONTROLS
            IconButton(
                    onClick = { showControls = !showControls },
                    modifier = Modifier.background(ControlBackground, CircleShape).size(48.dp)
            ) {
                Icon(
                        if (showControls) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                        "Toggle Controls",
                        tint = Color.White
                )
            }

            // ROTATE
            IconButton(
                    onClick = { rotation = (rotation + 90f) % 360f },
                    modifier = Modifier.background(ControlBackground, CircleShape).size(48.dp)
            ) { Icon(Icons.Filled.Refresh, "Rotate", tint = Color.White) }

            // DISCONNECT
            IconButton(
                    onClick = onDisconnect,
                    modifier =
                            Modifier.background(Color.Red.copy(alpha = 0.8f), CircleShape)
                                    .size(48.dp)
            ) { Icon(Icons.Filled.Close, "Disconnect", tint = Color.White) }
        }

        // Debug Info / Device Name
        // FIXED: Show device name properly
        val overlayText =
                if (!deviceName.isNullOrEmpty()) "Connected to: $deviceName"
                else if (debugText.isNotEmpty()) debugText else "Connected"

        Text(
                text = overlayText,
                color = Color.Green.copy(alpha = 0.7f),
                style = MaterialTheme.typography.caption,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModernControlsPanel(
        status: CameraStatus,
        theme: AppTheme,
        onCommand: (Map<String, String>) -> Unit,
        onSnapshot: () -> Unit,
        isMirrored: Boolean,
        onMirrorChange: (Boolean) -> Unit,
        showGrid: Boolean,
        onGridChange: (Boolean) -> Unit
) {
    // Show camera controls unless we are in Screen Cast mode
    val isCameraMode = status.streamMode != "SCREEN" && status.lens != "SCREEN"

    Card(
            modifier = Modifier.width(300.dp).fillMaxHeight().padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = theme.backgroundStart.copy(alpha = 0.95f),
            elevation = 8.dp
    ) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(
                    "Controls",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
            )
            Spacer(Modifier.height(16.dp))

            // Screen Mirroring Toggle
            val isScreenMode = status.lens == "SCREEN"
            Card(
                    backgroundColor =
                            if (isScreenMode) NeonPurple.copy(alpha = 0.15f)
                            else theme.textPrimary.copy(0.05f),
                    shape = RoundedCornerShape(12.dp),
                    modifier =
                            Modifier.fillMaxWidth().clickable {
                                if (isScreenMode) {
                                    onCommand(mapOf("lens" to "BACK"))
                                } else {
                                    onCommand(mapOf("lens" to "SCREEN"))
                                }
                            },
                    elevation = 0.dp
            ) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            if (isScreenMode) Icons.Default.CastConnected else Icons.Default.Cast,
                            null,
                            tint = if (isScreenMode) NeonPurple else theme.textSecondary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                                "Screen Mirroring",
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textPrimary
                        )
                        Text(
                                if (isScreenMode) "Active - Tap to Stop" else "Tap to Start",
                                style = MaterialTheme.typography.caption,
                                color = theme.textSecondary
                        )
                    }
                }
            }

            if (!isScreenMode) {
                Spacer(Modifier.height(20.dp))

                // ─── LENSES ───
                Text(
                        "LENSES",
                        style = MaterialTheme.typography.overline,
                        color = theme.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                // Only show lens if NOT in "SCREEN" stream mode
                val lenses = status.availableLenses.filter { it != "SCREEN" }
                FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (lenses.isNotEmpty()) {
                        lenses.forEach { lens ->
                            val selected = status.lens == lens
                            val label =
                                    when (lens) {
                                        "BACK" -> "Wide"
                                        "FRONT" -> "Selfie"
                                        "ULTRA_WIDE" -> "Ultra Wide"
                                        "TELEPHOTO" -> "Telephoto"
                                        else -> lens.lowercase().replaceFirstChar { it.uppercase() }
                                    }

                            CompactChoiceChip(
                                    label = label,
                                    selected = selected,
                                    theme = theme,
                                    onClick = { onCommand(mapOf("lens" to lens)) }
                            )
                        }
                    } else {
                        Text("No lenses available", color = theme.textSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ─── VIDEO TOOLS ───
                Text(
                        "TOOLS",
                        style = MaterialTheme.typography.overline,
                        color = theme.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactControlChip(
                            label = "Mirror",
                            icon = Icons.AutoMirrored.Filled.CompareArrows,
                            checked = isMirrored,
                            theme = theme,
                            onClick = { onMirrorChange(!isMirrored) }
                    )

                    CompactControlChip(
                            label = "Grid",
                            icon = Icons.Default.GridOn,
                            checked = showGrid,
                            theme = theme,
                            onClick = { onGridChange(!showGrid) }
                    )

                    if (!isCameraMode) {
                        var useMic by remember { mutableStateOf(false) }
                        CompactControlChip(
                                label = if (useMic) "Mic Audio" else "Dev Audio",
                                icon = if (useMic) Icons.Default.Mic else Icons.Default.Speaker,
                                checked = useMic,
                                theme = theme,
                                onClick = {
                                    useMic = !useMic
                                    onCommand(
                                            mapOf(
                                                    "audioSource" to
                                                            if (useMic) "mic" else "internal"
                                            )
                                    )
                                }
                        )
                    }

                    if (isCameraMode) {
                        CompactControlChip(
                                label = "Portrait",
                                icon = Icons.Default.Person,
                                checked = status.isBokehEnabled,
                                theme = theme,
                                onClick = {
                                    onCommand(mapOf("bokeh" to (!status.isBokehEnabled).toString()))
                                }
                        )

                        CompactControlChip(
                                label = "Flash",
                                icon =
                                        if (status.isFlashEnabled) Icons.Default.FlashOn
                                        else Icons.Default.FlashOff,
                                checked = status.isFlashEnabled,
                                theme = theme,
                                onClick = {
                                    onCommand(mapOf("flash" to (!status.isFlashEnabled).toString()))
                                }
                        )
                    }

                    CompactControlChip(
                            label = "Photo",
                            icon = Icons.Default.CameraAlt,
                            checked = false,
                            theme = theme,
                            onClick = onSnapshot
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ─── ADJUSTMENTS (Sliders) ───
                if (isCameraMode) {
                    Text(
                            "ADJUSTMENTS",
                            style = MaterialTheme.typography.overline,
                            color = theme.textSecondary
                    )
                    Spacer(Modifier.height(8.dp))

                    // Zoom
                    if (status.maxZoom > 1f) {
                        AdjustmentSlider(
                                "Zoom",
                                status.zoom,
                                status.minZoom,
                                status.maxZoom,
                                theme
                        ) { onCommand(mapOf("zoom" to it.toString())) }
                    }

                    // Exposure
                    AdjustmentSlider(
                            "Exposure",
                            status.exposure.toFloat(),
                            status.minExposure.toFloat(),
                            status.maxExposure.toFloat(),
                            theme
                    ) { onCommand(mapOf("exposure" to it.toInt().toString())) }
                }

                Spacer(Modifier.height(20.dp))

                // ─── PICTURE SETTINGS ───
                Text(
                        "PICTURE",
                        style = MaterialTheme.typography.overline,
                        color = theme.textSecondary
                )
                Spacer(Modifier.height(8.dp))

                AdjustmentSlider("Contrast", status.contrast.toFloat(), -100f, 100f, theme) {
                    onCommand(mapOf("contrast" to it.toInt().toString()))
                }

                AdjustmentSlider("Brightness", status.brightness.toFloat(), -100f, 100f, theme) {
                    onCommand(mapOf("brightness" to it.toInt().toString()))
                }

                AdjustmentSlider("Quality", status.jpegQuality.toFloat(), 10f, 100f, theme) {
                    onCommand(mapOf("quality" to it.toInt().toString()))
                }

                Spacer(Modifier.height(20.dp))

                // ─── STREAM SETTINGS ───
                Text(
                        "STREAM",
                        style = MaterialTheme.typography.overline,
                        color = theme.textSecondary
                )
                Spacer(Modifier.height(8.dp))

                // Resolution
                status.availableResolutions.chunked(2).forEach { row ->
                    Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { res ->
                            val selected = status.resolution == res
                            // Compact Button for Resolution
                            Button(
                                    onClick = { onCommand(mapOf("resolution" to res)) },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    backgroundColor =
                                                            if (selected) theme.primary
                                                            else theme.textPrimary.copy(0.05f),
                                                    contentColor =
                                                            if (selected) Color.White
                                                            else theme.textSecondary
                                            ),
                                    contentPadding = PaddingValues(0.dp),
                                    elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                            ) {
                                Text(
                                        res,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))

                // FPS
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    status.availableFps.forEach { fps ->
                        val selected = fps == status.currentFps
                        Button(
                                onClick = { onCommand(mapOf("fps" to fps.toString())) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                backgroundColor =
                                                        if (selected) theme.primary
                                                        else theme.textPrimary.copy(0.05f),
                                                contentColor =
                                                        if (selected) Color.White
                                                        else theme.textSecondary
                                        ),
                                contentPadding = PaddingValues(0.dp),
                                elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                        ) { Text("${fps} FPS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ─── AUDIO ───
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(
                                                theme.textPrimary.copy(0.05f),
                                                RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                                "Microphone",
                                style = MaterialTheme.typography.caption,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                        )
                        Text(
                                if (status.isAudioMuted) "Muted" else "Enabled",
                                style = MaterialTheme.typography.overline,
                                color = if (status.isAudioMuted) Color.Red else Color(0xFF4CAF50)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    CompactControlChip(
                            label = if (status.isAudioMuted) "Unmute" else "Mute",
                            icon =
                                    if (status.isAudioMuted) Icons.Filled.MicOff
                                    else Icons.Filled.Mic,
                            checked = !status.isAudioMuted,
                            theme = theme,
                            onClick = {
                                onCommand(mapOf("muteAudio" to (!status.isAudioMuted).toString()))
                            }
                    )
                }
            }

            Spacer(Modifier.height(60.dp)) // Bottom padding
        }
    }
}

@Composable
fun RowScope.IpField(
        value: String,
        onValueChange: (String) -> Unit,
        focusRequester: FocusRequester,
        modifier: Modifier = Modifier,
        onNext: () -> Unit = {},
        onPrev: () -> Unit = {},
        isFocus: Boolean = false
) {
    BasicTextField(
            value = value,
            onValueChange = {
                if (it.length <= 3 && it.all { c -> c.isDigit() }) {
                    onValueChange(it)
                    if (it.length == 3) onNext()
                }
            },
            textStyle =
                    TextStyle(
                            color = if (isFocus) Color.White else Color.LightGray,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                    ),
            modifier =
                    modifier.weight(1f)
                            .background(Color.Transparent)
                            .padding(bottom = 8.dp)
                            .focusRequester(focusRequester)
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                                event.key == Key.Backspace &&
                                                value.isEmpty()
                                ) {
                                    onPrev()
                                    true
                                } else {
                                    false
                                }
                            },
            singleLine = true,
            cursorBrush = SolidColor(NeonPurple),
            decorationBox = { innerTextField ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    innerTextField()
                    Spacer(Modifier.height(4.dp))
                    Box(
                            Modifier.fillMaxWidth()
                                    .height(2.dp)
                                    .background(
                                            if (value.isNotEmpty() || isFocus) NeonPurple
                                            else Color.Gray.copy(alpha = 0.3f)
                                    )
                    )
                }
            }
    )
}

@Composable
fun RowScope.PinDigitField(
        value: String,
        onValueChange: (String) -> Unit,
        focusRequester: FocusRequester,
        onNext: () -> Unit = {},
        onBack: () -> Unit = {},
        isFocus: Boolean = false
) {
    val activeColor = MaterialTheme.colors.primary
    val inactiveBorder = MaterialTheme.colors.onSurface.copy(alpha = 0.25f)
    val backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.06f)

    BasicTextField(
            value = value,
            onValueChange = {
                if (it.length <= 1 && it.all(Char::isDigit)) {
                    onValueChange(it)
                    if (it.length == 1) onNext()
                }
            },
            cursorBrush = SolidColor(activeColor),
            singleLine = true,
            textStyle =
                    TextStyle(
                            textAlign = TextAlign.Center,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFocus) activeColor else MaterialTheme.colors.onSurface
                    ),
            modifier =
                    Modifier.size(46.dp)
                            .focusRequester(focusRequester)
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                                event.key == Key.Backspace &&
                                                value.isEmpty()
                                ) {
                                    onBack()
                                    true
                                } else false
                            }
                            .background(color = backgroundColor, shape = RoundedCornerShape(10.dp))
                            .border(
                                    width = if (isFocus) 2.dp else 1.dp,
                                    color = if (isFocus) activeColor else inactiveBorder,
                                    shape = RoundedCornerShape(10.dp)
                            ),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    innerTextField()
                }
            }
    )
}

@Composable
fun Dot() {
    Text(
            ".",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun ControlSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.overline, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
// --- Data & Networking (Kept similar but cleaned up) ---

enum class CameraResolution(val width: Int, val height: Int, val label: String) {
    SD_480P(640, 480, "480p"),
    HD_720P(1280, 720, "720p"),
    FHD_1080P(1920, 1080, "1080p"),
    QHD_2K(2560, 1440, "2K"),
    DCI_2K(2048, 1080, "DCI 2K"),
    UHD_4K(3840, 2160, "4K"),
    DCI_4K(4096, 2160, "DCI 4K"),
    UHD_8K(7680, 4320, "8K")
}

data class CameraStatus(
        val isStreaming: Boolean = false,
        val clientName: String? = null,
        val deviceName: String? = null,
        val isClientConnected: Boolean = false,
        val lens: String = "BACK",
        val availableLenses: List<String> = listOf("BACK"),
        val resolution: String = "SD_480P",
        val availableResolutions: List<String> = listOf("SD_480P"),
        val zoom: Float = 1.0f,
        val maxZoom: Float = 1.0f,
        val minZoom: Float = 1.0f,
        val exposure: Int = 0,
        val minExposure: Int = -10,
        val maxExposure: Int = 10,
        val isBokehEnabled: Boolean = false,
        val isFlashEnabled: Boolean = false,
        val contrast: Int = 0,
        val brightness: Int = 0,
        val backMinZoom: Float = 1.0f,
        val backMaxZoom: Float = 1.0f,
        val isAudioMuted: Boolean = false,
        val streamingInterface: StreamingInterface = StreamingInterface.NONE,
        val currentFps: Int = 30,
        val availableFps: List<Int> = listOf(24, 30, 60),
        val jpegQuality: Int = 60,
        val streamMode: String = "CAMERA"
)

fun fetchCameraStatus(baseUrl: String, pin: String): CameraStatus? {
    return try {
        val query = if (pin.isNotEmpty()) "?pin=$pin" else ""
        val url = URL("$baseUrl/status$query")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        if (conn.responseCode == 200) {
            val json = conn.inputStream.bufferedReader().readText()
            parseStatusJson(json)
        } else {
            println("Status err: ${conn.responseCode}")
            null
        }
    } catch (e: Exception) {
        println("Status Fetch Failed: ${e.message}")
        null
    }
}

fun sendControlCommand(baseUrl: String, params: Map<String, String>) {
    try {
        val query = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val url = URL("$baseUrl/control?$query")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 3000
        conn.connect()
        conn.inputStream.close()
    } catch (e: Exception) {
        println("Cmd failed: $e")
    }
}

fun downloadSnapshot(baseUrl: String, params: Map<String, String>): File? {
    try {
        val query = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val url = URL("$baseUrl/snapshot?$query")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.connect()

        if (conn.responseCode == 200) {
            val bytes = conn.inputStream.use { it.readBytes() }
            val dir = File(System.getProperty("user.home"), "Pictures/MoonlightWebcam")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "Snapshot_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            return file
        }
        conn.disconnect()
    } catch (e: Exception) {
        println("Download failed: $e")
    }
    return null
}

fun parseStatusJson(json: String): CameraStatus {
    fun s(k: String) = "\"$k\":\\s*\"([^\"]+)\"".toRegex().find(json)?.groupValues?.get(1) ?: ""

    fun b(k: String) = "\"$k\":\\s*(true|false)".toRegex().find(json)?.groupValues?.get(1) == "true"

    fun f(k: String) =
            "\"$k\":\\s*([0-9.]+)".toRegex().find(json)?.groupValues?.get(1)?.toFloatOrNull() ?: 1f

    fun i(k: String, def: Int = 0) =
            "\"$k\":\\s*(-?[0-9]+)".toRegex().find(json)?.groupValues?.get(1)?.toIntOrNull() ?: def

    val lensesJson =
            "\"availableLenses\":\\s*\\[(.*?)\\]".toRegex().find(json)?.groupValues?.get(1) ?: ""
    val availableLenses =
            lensesJson.split(",").map { it.trim().replace("\"", "") }.filter { it.isNotEmpty() }

    val resJson =
            "\"availableResolutions\":\\s*\\[(.*?)\\]".toRegex().find(json)?.groupValues?.get(1)
                    ?: ""
    val availableResolutions =
            resJson.split(",").map { it.trim().replace("\"", "") }.filter { it.isNotEmpty() }
    val fpsJson = "\"availableFps\":\\s*\\[(.*?)\\]".toRegex().find(json)?.groupValues?.get(1) ?: ""
    val availableFps = fpsJson.split(",").mapNotNull { it.trim().toIntOrNull() }

    return CameraStatus(
            isStreaming = b("isStreaming"),
            streamMode = s("streamMode").ifEmpty { "CAMERA" }, // PARSE HERE
            clientName = s("clientName").ifEmpty { null },
            deviceName = s("serverDeviceName").ifEmpty { null },
            isClientConnected = b("isClientConnected"),
            lens = s("lens"),
            availableLenses = availableLenses.ifEmpty { listOf("BACK", "FRONT") },
            resolution = s("resolution"),
            availableResolutions = availableResolutions.ifEmpty { listOf("SD_480P", "HD_720P") },
            zoom = f("zoom"),
            maxZoom = f("maxZoom"),
            minZoom = f("minZoom"),
            exposure = i("exposure"),
            minExposure = i("minExposure", -10),
            maxExposure = i("maxExposure", 10),
            isBokehEnabled = b("isBokehEnabled"),
            isFlashEnabled = b("isFlashEnabled"),
            contrast = i("contrast"),
            brightness = i("brightness"),
            backMinZoom = f("backMinZoom"),
            backMaxZoom = f("backMaxZoom"),
            isAudioMuted = b("isAudioMuted"),
            streamingInterface =
                    when (s("streamingInterface")) {
                        "USB" -> StreamingInterface.USB
                        "WIFI" -> StreamingInterface.WIFI
                        else -> StreamingInterface.NONE
                    },
            currentFps = i("currentFps", 30),
            availableFps = if (availableFps.isNotEmpty()) availableFps else listOf(24, 30, 60),
            jpegQuality = i("jpegQuality", 60)
    )
}

suspend fun connectToStream(
        url: String,
        onDebug: (String) -> Unit,
        onFrame: (ImageBitmap) -> Unit
) {
    withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            onDebug("Connecting to $url...")
            conn = URI.create(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 10000
            conn.connect()

            if (conn.responseCode == 200) {
                onDebug("Connected")
                val boundary =
                        conn.getHeaderField("Content-Type")?.substringAfter("boundary=")
                                ?: "boundary"
                onDebug("Connected! Stream ID: $boundary")

                val dataInputStream = DataInputStream(conn.inputStream)
                while (true) {
                    var contentLength = 0
                    while (true) {
                        val lineBytes = ByteArrayOutputStream()
                        var b: Int
                        while (dataInputStream.read().also { b = it } != -1) {
                            if (b == '\n'.code) break
                            lineBytes.write(b)
                        }
                        val line = lineBytes.toString().trim()
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:")) {
                            contentLength = line.substringAfter(":").trim().toInt()
                        }
                    }

                    if (contentLength > 0) {
                        val imageBytes = ByteArray(contentLength)
                        dataInputStream.readFully(imageBytes)
                        val bitmap = Image.makeFromEncoded(imageBytes).toComposeImageBitmap()
                        withContext(Dispatchers.Main) { onFrame(bitmap) }
                    }
                }
            } else {
                val msg =
                        if (conn.responseCode == 401) "Incorrect PIN"
                        else "Error: ${conn.responseCode}"
                onDebug(msg)
                throw Exception(msg)
            }
        } catch (e: Exception) {
            if (e is EOFException || e.message?.contains("Premature EOF") == true) {
                onDebug("Stream Ended")
            } else if (e !is SocketTimeoutException) {
                onDebug("Stream Error: ${e.message}")
                e.printStackTrace()
            }
        } finally {
            conn?.disconnect()
            onDebug("Disconnected")
        }
    }
}

// --- AUDIO IMPLEMENTATION ---

class AudioPlayer {
    private var sourceDataLine: SourceDataLine? = null

    fun init() {
        try {
            val format = AudioFormat(44100f, 16, 1, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)

            if (!AudioSystem.isLineSupported(info)) {
                println("Audio: Line not supported")
                return
            }

            sourceDataLine = AudioSystem.getLine(info) as SourceDataLine
            // Reduce buffer latency (e.g. 4096 bytes ~= 23ms @ 44.1kHz 16-bit mono which is 88200
            // bytes/sec)
            // Default is often too large (500ms+).
            // Aggressive Latency Tweak: 1024 bytes (~11ms)
            sourceDataLine?.open(format, 1024 * 2)
            sourceDataLine?.start()
            println("Audio: Player initialized")
        } catch (e: Exception) {
            println("Audio: Init failed: ${e.message}")
        }
    }

    fun play(data: ByteArray, length: Int) {
        sourceDataLine?.write(data, 0, length)
    }

    fun close() {
        try {
            sourceDataLine?.drain()
            sourceDataLine?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
    }
}

suspend fun connectToAudio(videoUrl: String, pin: String, onAudioLevel: (Float) -> Unit) {
    var audioUrl = videoUrl.trim()
    if (audioUrl.endsWith("/")) audioUrl = audioUrl.dropLast(1)

    val baseAudioUrl =
            if (audioUrl.endsWith("/video")) {
                audioUrl.replace("/video", "/audio")
            } else if (audioUrl.contains("/video?")) {
                audioUrl.replace("/video?", "/audio?")
            } else if (!audioUrl.contains("/audio")) {
                "$audioUrl/audio"
            } else {
                audioUrl
            }

    val urlWithPin =
            if (baseAudioUrl.contains("pin=")) baseAudioUrl // Already has PIN
            else if (baseAudioUrl.contains("?")) "$baseAudioUrl&pin=$pin"
            else "$baseAudioUrl?pin=$pin"
    println("AUDIO DEBUG: Starting connection to $baseAudioUrl")

    try {
        val mixers = AudioSystem.getMixerInfo()
        if (mixers.isEmpty()) {
            println("AUDIO ERROR: No audio mixers found on this system!")
            return
        }
    } catch (e: Exception) {
        println("AUDIO ERROR: Could not access local audio system: ${e.message}")
    }

    var conn: HttpURLConnection? = null
    var player: AudioPlayer? = null

    try {
        val url = URL(urlWithPin)
        val connection = url.openConnection() as HttpURLConnection
        conn = connection
        connection.connectTimeout = 5000
        connection.readTimeout = 0 // Infinite timeout for streaming
        connection.useCaches = false

        if (connection.responseCode == 200) {
            val inputStream = connection.inputStream
            val header = ByteArray(44)
            var headerReadTotal = 0
            while (headerReadTotal < 44) {
                val r = inputStream.read(header, headerReadTotal, 44 - headerReadTotal)
                if (r == -1) break
                headerReadTotal += r
            }

            if (headerReadTotal < 44) {
                println("AUDIO ERROR: Could not read full WAV header")
                return
            }

            println("AUDIO DEBUG: Header read, starting playback...")
            player = AudioPlayer()
            player.init()

            val readBuffer = ByteArray(4096)
            // Allocate separate write buffer large enough for read + potential leftover
            val writeBuffer = ByteArray(4096 + 1)
            var bytesRead: Int
            var leftover: Byte? = null
            var loopCount = 0

            while (inputStream.read(readBuffer).also { bytesRead = it } != -1) {
                if (bytesRead == 0) continue

                var writeSize = 0

                // Prepend leftover if present
                if (leftover != null) {
                    writeBuffer[0] = leftover!!
                    writeSize++
                    leftover = null
                }

                // Copy new data
                System.arraycopy(readBuffer, 0, writeBuffer, writeSize, bytesRead)
                writeSize += bytesRead

                // Save leftover if odd
                if (writeSize % 2 != 0) {
                    leftover = writeBuffer[writeSize - 1]
                    writeSize--
                }

                if (writeSize > 0) {
                    player.play(writeBuffer, writeSize)

                    // Visualizer Logic - Throttle to every 5th frame (~200ms)
                    if (++loopCount % 5 == 0) {
                        var sum = 0.0
                        // Sub-sample logic for speed
                        for (i in 0 until writeSize step 4) {
                            if (i + 1 >= writeSize) break
                            val low = writeBuffer[i].toInt() and 0xFF
                            val high = writeBuffer[i + 1].toInt() shl 8
                            val sample = (high or low).toShort()
                            sum += sample * sample
                        }
                        // Adjust count for step 4
                        val rms = sqrt(sum / (writeSize / 4))
                        val level = (rms / 10000.0).coerceIn(0.0, 1.0).toFloat()
                        withContext(Dispatchers.Main) { onAudioLevel(level) }
                    }
                }
            }
        }
    } catch (e: Exception) {
        println("AUDIO EXCEPTION: ${e.message}")
    } finally {
        player?.close()
        conn?.disconnect()
    }
}

@Composable
fun CompactChoiceChip(label: String, selected: Boolean, theme: AppTheme, onClick: () -> Unit) {
    Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (selected) theme.primary else theme.textPrimary.copy(alpha = 0.08f),
            modifier = Modifier.height(32.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                    label,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) Color.White else theme.textPrimary
            )
        }
    }
}

@Composable
fun CompactControlChip(
        label: String,
        icon: ImageVector,
        checked: Boolean,
        theme: AppTheme,
        onClick: () -> Unit
) {
    Surface(
            shape = RoundedCornerShape(8.dp),
            color =
                    if (checked) theme.primary.copy(alpha = 0.15f)
                    else theme.textPrimary.copy(alpha = 0.05f),
            border =
                    BorderStroke(
                            1.dp,
                            if (checked) theme.primary.copy(alpha = 0.5f) else Color.Transparent
                    ),
            modifier = Modifier.height(36.dp).clickable(onClick = onClick)
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                    icon,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = if (checked) theme.primary else theme.textSecondary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                    label,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Medium,
                    color = if (checked) theme.primary else theme.textSecondary
            )
        }
    }
}

@Composable
fun AdjustmentSlider(
        label: String,
        value: Float,
        min: Float,
        max: Float,
        theme: AppTheme,
        onValueChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.caption, color = theme.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                    if (label == "Exposure") value.toInt().toString()
                    else String.format("%.1fx", value),
                    style = MaterialTheme.typography.caption,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold
            )
        }
        Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = min..max,
                colors =
                        SliderDefaults.colors(
                                thumbColor = theme.primary,
                                activeTrackColor = theme.primary,
                                inactiveTrackColor = theme.textPrimary.copy(alpha = 0.1f)
                        )
        )
    }
}
