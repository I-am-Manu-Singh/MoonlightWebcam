package com.example.moonlightwebcam

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import java.lang.ref.WeakReference

@SuppressLint("StaticFieldLeak")
actual object PlatformUtils {
    private var contextRef: WeakReference<Context>? = null
    var activity: Activity? = null

    actual fun setStatusBarColor(isDarkIcons: Boolean) {
        MainActivity.instance?.runOnUiThread {
            MainActivity.instance?.let { activity ->
                val window = activity.window
                val insetsController =
                        androidx.core.view.WindowCompat.getInsetsController(
                                window,
                                window.decorView
                        )
                insetsController.isAppearanceLightStatusBars = isDarkIcons
            }
        }
    }

    fun setContext(context: Context) {
        contextRef = WeakReference(context)
    }

    actual fun shareText(text: String) {
        val context = contextRef?.get() ?: return
        val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
        val chooser = Intent.createChooser(intent, "Share via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    actual fun openUrl(url: String) {
        val context = contextRef?.get() ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun requestNotificationPermission() {
        // Handled by MainActivity
    }

    actual fun setFullscreen(enabled: Boolean) {
        MainActivity.instance?.runOnUiThread { MainActivity.instance?.setFullscreen(enabled) }
    }

    actual fun getIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
            val interfaceList = interfaces.toList()

            // 1. Try to find a Wi-Fi interface specifically (common on Android: wlan0)
            for (iface in interfaceList) {
                if (iface.name.contains("wlan", ignoreCase = true) &&
                                iface.isUp &&
                                !iface.isLoopback
                ) {
                    val address =
                            iface.inetAddresses
                                    .asSequence()
                                    .filterIsInstance<java.net.Inet4Address>()
                                    .firstOrNull { !it.isLoopbackAddress }
                    if (address != null) return address.hostAddress
                }
            }

            // 2. Fallback to any active non-loopback IPv4 address, but skip p2p, tun, bridge etc.
            val skipPatterns = listOf("p2p", "tun", "bridge", "dummy", "ap", "vbox")
            for (iface in interfaceList) {
                if (iface.isUp &&
                                !iface.isLoopback &&
                                skipPatterns.none { iface.name.contains(it, ignoreCase = true) }
                ) {
                    val address =
                            iface.inetAddresses
                                    .asSequence()
                                    .filterIsInstance<java.net.Inet4Address>()
                                    .firstOrNull { !it.isLoopbackAddress }
                    if (address != null) return address.hostAddress
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    actual fun saveBoolean(key: String, value: Boolean) {
        val context = contextRef?.get() ?: return
        val prefs = context.getSharedPreferences("MoonlightParams", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val context = contextRef?.get() ?: return defaultValue
        val prefs = context.getSharedPreferences("MoonlightParams", Context.MODE_PRIVATE)
        return prefs.getBoolean(key, defaultValue)
    }

    actual fun saveString(key: String, value: String) {
        val context = contextRef?.get() ?: return
        val prefs = context.getSharedPreferences("MoonlightParams", Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
    }

    actual fun getString(key: String, defaultValue: String): String {
        val context = contextRef?.get() ?: return defaultValue
        val prefs = context.getSharedPreferences("MoonlightParams", Context.MODE_PRIVATE)
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    actual fun getNetworkName(): String? {
        val context = contextRef?.get() ?: return null
        val cm =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as?
                        android.net.ConnectivityManager
                        ?: return null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(net) ?: return null
            return when {
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ->
                        "Mobile Data"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            }
        } else {
            @Suppress("DEPRECATION") val info = cm.activeNetworkInfo
            return if (info?.isConnected == true) {
                when (info.type) {
                    android.net.ConnectivityManager.TYPE_WIFI -> "Wi-Fi"
                    android.net.ConnectivityManager.TYPE_MOBILE -> "Mobile Data"
                    else -> "Connected"
                }
            } else null
        }
    }

    @SuppressLint("MissingPermission")
    actual fun isUsbConnected(): Boolean {
        val context = contextRef?.get() ?: return false

        val intent =
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                        ?: return false

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)

        return plugged and BatteryManager.BATTERY_PLUGGED_USB != 0
    }
    actual fun isAdbEnabled(): Boolean {
        val context = contextRef?.get() ?: return false
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    actual fun isNetworkAvailable(): Boolean {
        val context = contextRef?.get() ?: return false

        val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        // Relaxed check: We valid streaming on local networks (no internet).
        // Just check if we have a valid transport.
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    actual fun isAndroid() = true
    actual fun isIos() = false

    actual fun openAccessibilitySettings() {
        val context = contextRef?.get() ?: return
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    actual fun openDeveloperSettings() {
        val context = contextRef?.get() ?: return
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings if dev settings intent fails (e.g. hidden)
            val settingsIntent = Intent(Settings.ACTION_SETTINGS)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
        }
    }

    actual fun stopService() {
        val context = contextRef?.get() ?: return
        val stopIntent =
                Intent(context, MoonlightService::class.java).apply {
                    action = MoonlightService.ACTION_STOP_STREAM
                }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(stopIntent)
        } else {
            context.startService(stopIntent)
        }
    }
}
