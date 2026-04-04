package com.example.moonlightwebcam

interface DiscoveryManager {
    fun publishService(port: Int)
    fun stopService()
}
