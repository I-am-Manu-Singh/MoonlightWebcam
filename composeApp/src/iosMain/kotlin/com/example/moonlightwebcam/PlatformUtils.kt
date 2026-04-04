@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)


package com.example.moonlightwebcam

import kotlinx.cinterop.*
import platform.Foundation.NSURL
import platform.SystemConfiguration.*
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState
import platform.posix.*

actual object PlatformUtils {
    actual fun shareText(text: String) {
        // Simple implementation attempting to find root VC
        val window =
                UIApplication.sharedApplication.windows.firstOrNull() as? platform.UIKit.UIWindow
        val rootVC = window?.rootViewController

        if (rootVC != null) {
            val activityVC = UIActivityViewController(listOf(text), null)
            rootVC.presentViewController(activityVC, true, null)
        }
    }

    actual fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl != null) {
            UIApplication.sharedApplication.openURL(nsUrl)
        }
    }

    actual fun requestNotificationPermission() {
        val center = platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter()
        center.requestAuthorizationWithOptions(
                options =
                        platform.UserNotifications.UNAuthorizationOptionAlert or
                                platform.UserNotifications.UNAuthorizationOptionSound or
                                platform.UserNotifications.UNAuthorizationOptionBadge,
                completionHandler = { granted, error ->
                    // Handle result if needed
                }
        )
    }

    actual fun setFullscreen(enabled: Boolean) {
        // No-op for now on iOS
    }

    actual fun setStatusBarColor(isDarkIcons: Boolean) {
        // No-op for now on iOS
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getIpAddress(): String? {
        var address: String? = null
        memScoped {
            val ifap = alloc<CPointerVar<platform.darwin.ifaddrs>>()
            if (platform.darwin.getifaddrs(ifap.ptr) == 0) {
                var ptr = ifap.value
                while (ptr != null) {
                    val ifa = ptr.pointed
                    val name = ifa.ifa_name?.toKString()
                    val family = ifa.ifa_addr?.pointed?.sa_family?.toInt()

                    if (family == platform.posix.AF_INET) {
                        if (name == "en0") { // usually WiFi
                            // Manually format IPv4 to avoid unresolved inet_ntop/inet_ntoa
                            val sockaddr = ifa.ifa_addr?.reinterpret<platform.posix.sockaddr_in>()
                            val rawPtr = sockaddr!!.pointed.sin_addr.ptr.reinterpret<UByteVar>()
                            address = "${rawPtr[0]}.${rawPtr[1]}.${rawPtr[2]}.${rawPtr[3]}"
                            break
                        }
                    }
                    ptr = ifa.ifa_next
                }
                platform.darwin.freeifaddrs(ifap.value)
            }
        }
        return address
    }

    actual fun saveBoolean(key: String, value: Boolean) {
        platform.Foundation.NSUserDefaults.standardUserDefaults.setBool(value, key)
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val defaults = platform.Foundation.NSUserDefaults.standardUserDefaults
        if (defaults.objectForKey(key) == null) return defaultValue
        return defaults.boolForKey(key)
    }

    actual fun saveString(key: String, value: String) {
        platform.Foundation.NSUserDefaults.standardUserDefaults.setObject(value, key)
    }

    actual fun getString(key: String, defaultValue: String): String {
        return platform.Foundation.NSUserDefaults.standardUserDefaults.stringForKey(key)
                ?: defaultValue
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    actual fun getNetworkName(): String? {
        var netName: String? = null
        memScoped {
            val ifap = alloc<CPointerVar<platform.darwin.ifaddrs>>()
            if (platform.darwin.getifaddrs(ifap.ptr) == 0) {
                var ptr = ifap.value
                while (ptr != null) {
                    val ifa = ptr.pointed
                    val name = ifa.ifa_name?.toKString()
                    val family = ifa.ifa_addr?.pointed?.sa_family?.toInt()

                    if (family == platform.posix.AF_INET) {
                        if (name == "en0") {
                            netName = "Wi-Fi"
                            break // Prioritize Wi-Fi
                        } else if (name?.startsWith("pdp_ip") == true) {
                            netName = "Mobile Data"
                        } else if (netName == null && name != "lo0") {
                            netName = "Connected"
                        }
                    }
                    ptr = ifa.ifa_next
                }
                platform.darwin.freeifaddrs(ifap.value)
            }
        }
        return netName
    }

    actual fun isUsbConnected(): Boolean {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        return device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
                device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull
    }

    actual fun isNetworkAvailable(): Boolean {
        memScoped {
            val zeroAddr =
                    alloc<sockaddr_in>().apply {
                        sin_len = sizeOf<sockaddr_in>().toUByte()
                        sin_family = AF_INET.toUByte()
                    }

            val reachability =
                    SCNetworkReachabilityCreateWithAddress(
                            null,
                            zeroAddr.ptr.reinterpret<sockaddr>()
                    )
                            ?: return false

            val flags = alloc<SCNetworkReachabilityFlagsVar>()
            if (!SCNetworkReachabilityGetFlags(reachability, flags.ptr)) return false

            val f = flags.value
            return (f and kSCNetworkReachabilityFlagsReachable) != 0u &&
                    (f and kSCNetworkReachabilityFlagsConnectionRequired) == 0u
        }
    }
    actual fun isAndroid() = false
    actual fun isIos() = true

    actual fun openAccessibilitySettings() {
        // No-op on iOS
    }

    actual fun openDeveloperSettings() {
        // No-op on iOS
    }

    actual fun stopService() {
        // No-op on iOS
    }

    actual fun isAdbEnabled(): Boolean = false
}
