package com.example.moonlightwebcam

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private var screenCaptureCallback: ((Int, Intent?) -> Unit)? = null
    private val screenCaptureLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                screenCaptureCallback?.invoke(result.resultCode, result.data)
                screenCaptureCallback = null
            }

    fun requestScreenCapture(callback: (Int, Intent?) -> Unit) {
        val mediaProjectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as
                        android.media.projection.MediaProjectionManager
        screenCaptureCallback = callback
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        enableEdgeToEdge(
                statusBarStyle =
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle =
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Add additional permissions if needed (e.g., AUDIO)
        permissions.add(Manifest.permission.RECORD_AUDIO)

        val permissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                        result ->
                    if (result.all { it.value }) {
                        setupContent()
                    } else {
                        setupContent() // Attempt anyway
                    }
                }

        if (permissions.all {
                    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
                }
        ) {
            setupContent()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onStart() {
        super.onStart()

        // Restart service if needed (Only if permissions are granted)
        val hasCameraPermission =
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission && DependencyHolder.cameraManager.value == null) {
            val serviceIntent = Intent(this, MoonlightService::class.java)
            Log.d("MainActivity", "Restarting MoonlightService from onStart...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        // 🔌 Register USB / power receiver
        registerReceiver(usbReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
    private fun setupContent() {
        PlatformUtils.setContext(this)

        // Start the Service to initialize Camera/Server with Service Lifecycle
        val serviceIntent = Intent(this, MoonlightService::class.java)
        Log.d("MainActivity", "Starting MoonlightService...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            val cameraManager by DependencyHolder.cameraManager.collectAsState()

            if (cameraManager != null) {
                // We have a manager!
                val server = DependencyHolder.streamingServer
                App(cameraManager!!, server!!)
            } else {
                // Loading / Waiting for Service
                Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                            "Starting Camera Service...",
                            color = Color.White,
                            modifier = Modifier.padding(top = 32.dp)
                    )
                }
            }
        }
    }
    private val usbReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    // Later: update StateFlow / notify PlatformUtils
                    Log.d("MainActivity", "USB/Battery state changed")
                }
            }

    override fun onStop() {
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered — ignore safely
        }
        super.onStop()
    }
    override fun onDestroy() {
        super.onDestroy()
        // Do NOT clear DependencyHolder here. The Service owns the CameraManager.
        // We want streaming to continue even if Activity is destroyed.
        if (instance == this) instance = null
    }

    fun setFullscreen(enabled: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        var instance: MainActivity? = null
    }
}