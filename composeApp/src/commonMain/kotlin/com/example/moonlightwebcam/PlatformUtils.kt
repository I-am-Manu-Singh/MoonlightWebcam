package com.example.moonlightwebcam

expect object PlatformUtils {
    fun shareText(text: String)
    fun openUrl(url: String)
    fun requestNotificationPermission()
    fun setFullscreen(enabled: Boolean)
    fun getIpAddress(): String?
    fun saveBoolean(key: String, value: Boolean)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun saveString(key: String, value: String)
    fun getString(key: String, defaultValue: String): String
    fun getNetworkName(): String?
    fun isUsbConnected(): Boolean
    fun isAndroid(): Boolean
    fun isIos(): Boolean
    fun isNetworkAvailable(): Boolean
    fun setStatusBarColor(isDarkIcons: Boolean)
    fun isAdbEnabled(): Boolean
    fun openAccessibilitySettings()
    fun openDeveloperSettings()
    fun stopService()
}
