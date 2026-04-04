package com.example.moonlightwebcam

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.darwin.*
import platform.posix.*

// We would use getifaddrs here usually.
// For now, hardcode or stub.
// Implementing getifaddrs via cinterop to standard C libs is needed.

actual object NetworkUtils {
    @OptIn(ExperimentalForeignApi::class)
    actual fun getIpAddress(): String? {
        var ip: String? = null
        memScoped {
            val ifap = alloc<CPointerVar<ifaddrs>>()
            if (getifaddrs(ifap.ptr) == 0) {
                var current = ifap.value
                while (current != null) {
                    val ifa = current.pointed
                    val name = ifa.ifa_name?.toKString()
                    val addr = ifa.ifa_addr?.pointed

                    // Check for IPv4 (AF_INET) and not loopback
                    if (addr?.sa_family == AF_INET.toUByte()) {
                        // En0 is usually WiFi, pdp_ip0 is cellular. We want a non-loopback address.
                        if (name == "en0" || name?.startsWith("pdp_ip") == true) {
                            val sin = ifa.ifa_addr!!.reinterpret<sockaddr_in>()
                            // INET_ADDRSTRLEN is 16
                            // Use fully qualified ByteVar
                            val ipStr = allocArray<kotlinx.cinterop.ByteVar>(16)
                            inet_ntop(AF_INET, sin.pointed.sin_addr.ptr, ipStr, 16u)
                            ip = ipStr.toKString()
                            // If we found en0, that's preferred, return immediately
                            if (name == "en0") return ip
                        }
                    }
                    current = ifa.ifa_next
                }
                freeifaddrs(ifap.value)
            }
        }
        return ip
    }
}
