@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.moonlightwebcam

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.darwin.NSObject

class IosDiscoveryManager : DiscoveryManager {
    private var netService: NSNetService? = null
    private val delegate =
            object : NSObject(), NSNetServiceDelegateProtocol {
                override fun netServiceDidPublish(sender: NSNetService) {
                    println("IosDiscoveryManager: Service published successfully")
                }

                // Note: handling didNotPublish might require specific signature match for mapping
                // validation, simplify for now or add if needed.
            }

    override fun publishService(port: Int) {
        if (netService != null) return

        netService =
                NSNetService(
                                domain = "local.",
                                type = "_moonlight-webcam._tcp",
                                name = "MoonlightWebcam",
                                port = port
                        )
                        .apply {
                            delegate = this@IosDiscoveryManager.delegate

                            // Create TXT Record
                            val pinStr = DependencyHolder.pin.value
                            val pinData = pinStr.encodeToByteArray()
                            val txtPinData: NSData? = memScoped {
                                NSData.dataWithBytes(
                                        pinData.refTo(0).getPointer(this),
                                        pinData.size.toULong()
                                )
                            }

                            val deviceName = platform.UIKit.UIDevice.currentDevice.name
                            val deviceNameData = deviceName.encodeToByteArray()
                            val txtDeviceNameData: NSData? = memScoped {
                                NSData.dataWithBytes(
                                        deviceNameData.refTo(0).getPointer(this),
                                        deviceNameData.size.toULong()
                                )
                            }

                            val txtDict = mutableMapOf<Any?, Any?>()
                            if (txtPinData != null) {
                                txtDict["pin"] = txtPinData
                            }
                            if (txtDeviceNameData != null) {
                                txtDict["name"] = txtDeviceNameData
                            }

                            val finalTxtData = NSNetService.dataFromTXTRecordDictionary(txtDict)
                            if (finalTxtData != null) {
                                this.setTXTRecordData(finalTxtData)
                            }

                            publish()
                        }
    }

    override fun stopService() {
        netService?.stop()
        netService = null
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    private fun ByteArray.toNSData(): platform.Foundation.NSData {
        val pinned = this.pin()
        return platform.Foundation.NSData.dataWithBytes(pinned.addressOf(0), this.size.toULong())
                .also { pinned.unpin() }
    }
}
