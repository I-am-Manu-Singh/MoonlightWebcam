package com.example.moonlightwebcam

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MoonlightService : androidx.lifecycle.LifecycleService() {

    companion object {
        const val ACTION_STOP_STREAM = "STOP_STREAM"
        const val CHANNEL_ID = "MoonlightServiceChannel"
        const val NOTIFICATION_ID = 1
    }

    private val serviceScope =
            kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
            )

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MoonlightService", "onCreate called")
        // Initialize CameraManager and Server here, tied to Service lifecycle
        try {
            if (DependencyHolder.cameraManager.value == null) {
                android.util.Log.d("MoonlightService", "Initializing CameraManager and Server")
                val cameraManager = CameraManager(applicationContext, this)
                val streamingServer = AndroidStreamingServer(cameraManager.frameFlow)

                DependencyHolder.setCameraManager(cameraManager)
                DependencyHolder.streamingServer = streamingServer

                // Audio Manager
                val audioManager = AndroidAudioManager()
                DependencyHolder.setAudioManager(audioManager)

                // Discovery Manager
                val discoveryManager = AndroidDiscoveryManager(applicationContext)
                DependencyHolder.setDiscoveryManager(discoveryManager)
                discoveryManager.publishService(8080)

                streamingServer.start()
                android.util.Log.d("MoonlightService", "Initialization complete")
            }

            // Observe State for Notification Updates
            serviceScope.launch {
                DependencyHolder.cameraManager.collect { manager ->
                    manager?.cameraState?.collect { state ->
                        updateNotification(state.isStreaming, state.isClientConnected)
                    }
                }
            }

            // AUTO-STOP LOGIC: Monitor connectivity and stop if lost
            serviceScope.launch {
                while (isActive) {
                    val manager = DependencyHolder.cameraManager.value
                    val isStreaming = manager?.cameraState?.value?.isStreaming == true

                    if (isStreaming) {
                        val isUsb = PlatformUtils.isUsbConnected()
                        val isWifi = PlatformUtils.isNetworkAvailable()

                        if (!isUsb && !isWifi) {
                            Log.w(
                                    "MoonlightService",
                                    "Connectivity lost! Stopping stream auto-safely."
                            )
                            stopStreaming()
                        }
                    }
                    delay(3000)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MoonlightService", "Error initializing camera service", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP_STREAM) {
            stopStreaming()
            // User requested stop via notification -> Kill the service entirely
            stopSelf()
            return START_NOT_STICKY
        }

        // Initial Notification
        startForegroundService()
        return START_STICKY
    }

    private fun updateNotification(isStreaming: Boolean, isClientConnected: Boolean) {
        val notification = buildNotification(isStreaming, isClientConnected)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)

        // Update Foreground Service Type
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type =
                        if (Build.VERSION.SDK_INT >= 30) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        } else {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                        }

                // If using Screen Lens, add MediaProjection type ONLY if we have the token
                val manager = DependencyHolder.cameraManager.value
                val state = manager?.cameraState?.value
                val isProjecting = state?.isProjecting == true

                if (state?.currentLens == CameraLens.SCREEN && Build.VERSION.SDK_INT >= 30) {
                    if (isProjecting) {
                        android.util.Log.d(
                                "MoonlightService",
                                "Adding MEDIA_PROJECTION type (Token Active)"
                        )
                        type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    } else {
                        android.util.Log.d(
                                "MoonlightService",
                                "Skipping MEDIA_PROJECTION type (No Token yet)"
                        )
                    }
                }

                android.util.Log.d("MoonlightService", "startForeground with type: $type")
                try {
                    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
                } catch (se: SecurityException) {
                    android.util.Log.w(
                            "MoonlightService",
                            "SecurityException starting FGS with type $type. Retrying without MEDIA_PROJECTION.",
                            se
                    )
                    // Fallback: Remove MEDIA_PROJECTION and try again
                    if (Build.VERSION.SDK_INT >= 30) {
                        val fallbackType =
                                type and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION.inv()
                        ServiceCompat.startForeground(
                                this,
                                NOTIFICATION_ID,
                                notification,
                                fallbackType
                        )
                    }
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("MoonlightService", "Failed to update foreground type", e)
        }
    }

    private fun startForegroundService() {
        createNotificationChannel()
        // Initial state is "Idle" usually, but let's sync with current state
        val state = DependencyHolder.cameraManager.value?.cameraState?.value
        val isStreaming = state?.isStreaming == true
        val isClientConnected = state?.isClientConnected == true
        updateNotification(isStreaming, isClientConnected)
    }

    // --- Network Utils Helper inside Service (or could be external object) ---
    object NetworkUtils {
        fun getIpAddress(): String? {
            try {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }

    private fun buildNotification(
            isStreaming: Boolean,
            isClientConnected: Boolean
    ): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

        val stopIntent =
                Intent(this, MoonlightService::class.java).apply { action = ACTION_STOP_STREAM }
        val stopPendingIntent =
                PendingIntent.getService(
                        this,
                        0,
                        stopIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

        val ip = NetworkUtils.getIpAddress() ?: "192.168.x.x"

        val iconRes =
                try {
                    R.drawable.ic_moonlight_notification
                } catch (e: Exception) {
                    android.R.drawable.ic_menu_camera
                }

        val builder =
                NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(iconRes)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .setForegroundServiceBehavior(
                                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                        )

        // Fetch latest state for countdown
        val state = DependencyHolder.cameraManager.value?.cameraState?.value
        val autoClose = state?.autoCloseRemaining

        if (isStreaming) {
            val isPaused = state?.isPaused == true

            if (isPaused) {
                builder.setContentTitle("Stream Paused")
                        .setContentText("Service Idle (Connection Active)")
                        .addAction(
                                R.drawable
                                        .ic_moonlight_notification, // Use app icon or play icon if
                                // available
                                "Resume",
                                pendingIntent
                        )
            } else if (isClientConnected) {
                builder.setContentTitle("Stream Active")
                val clientName = state?.clientName ?: "Unknown"
                val lens = if (state?.currentLens == CameraLens.SCREEN) "Screen" else "Camera"
                builder.setContentText("Connected to $clientName ($lens)")
            } else {
                if (autoClose != null && autoClose > 0) {
                    builder.setContentTitle("Closing in ${autoClose}s")
                            .setContentText("Waiting: http://$ip:8080/video")
                } else {
                    builder.setContentTitle("Camera Ready")
                            .setContentText("Waiting: http://$ip:8080/video")
                }
            }

            builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop Service",
                    stopPendingIntent
            )
        } else {
            builder.setContentTitle("MoonlightWebcam Service")
                    .setContentText("Service Idle (Ready to Stream)")
        }

        return builder.build()
    }

    private fun stopStreaming() {
        DependencyHolder.cameraManager.value?.stopCamera()
        DependencyHolder.streamingServer?.stop()
        DependencyHolder.discoveryManager.value?.stopService()
        // We do NOT nullify DependencyHolder anymore, to keep UI alive.
        // DependencyHolder.setCameraManager(null)
        // DependencyHolder.streamingServer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                    NotificationChannel(
                            CHANNEL_ID,
                            "Moonlight Streaming Service",
                            NotificationManager.IMPORTANCE_LOW
                    )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        stopStreaming()
        serviceScope.cancel()

        // CRITICAL FIX: When Service is destroyed, we MUST clear the dependencies.
        // Otherwise, restarting the Service will see old objects and skip initialization,
        // using a dead Context/Lifecycle.
        DependencyHolder.setCameraManager(null)
        DependencyHolder.streamingServer = null
        DependencyHolder.setDiscoveryManager(null)

        super.onDestroy()
    }
}
