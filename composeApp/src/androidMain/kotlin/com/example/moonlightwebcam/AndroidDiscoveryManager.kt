package com.example.moonlightwebcam

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class AndroidDiscoveryManager(context: Context) : DiscoveryManager {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private val serviceName = "MoonlightWebcam - ${android.os.Build.MODEL ?: "Android"}"
    private val serviceType = "_moonlight-webcam._tcp"

    override fun publishService(port: Int) {
        if (registrationListener != null) return // Already registered

        val serviceInfo =
                NsdServiceInfo().apply {
                    this.serviceName = this@AndroidDiscoveryManager.serviceName
                    this.serviceType = this@AndroidDiscoveryManager.serviceType
                    this.port = port
                    // Publish PIN for auto-discovery
                    val pin = DependencyHolder.pin.value
                    this.setAttribute("pin", pin)
                    // Publish Device Name
                    this.setAttribute("name", android.os.Build.MODEL ?: "Unknown Android")
                }

        registrationListener =
                object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                        Log.d(
                                "DiscoveryManager",
                                "Service registered: ${NsdServiceInfo.serviceName}"
                        )
                    }

                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e("DiscoveryManager", "Registration failed: $errorCode")
                        registrationListener = null
                    }

                    override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                        Log.d("DiscoveryManager", "Service unregistered")
                        registrationListener = null
                    }

                    override fun onUnregistrationFailed(
                            serviceInfo: NsdServiceInfo,
                            errorCode: Int
                    ) {
                        Log.e("DiscoveryManager", "Unregistration failed: $errorCode")
                    }
                }

        try {
            nsdManager.registerService(
                    serviceInfo,
                    NsdManager.PROTOCOL_DNS_SD,
                    registrationListener
            )
        } catch (e: Exception) {
            Log.e("DiscoveryManager", "Error registering service", e)
        }
    }

    override fun stopService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e("DiscoveryManager", "Error unregistering service", e)
            }
        }
    }
}
