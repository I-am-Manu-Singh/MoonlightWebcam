package com.example.moonlightwebcam

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DependencyHolder {
    private val _cameraManager = MutableStateFlow<CameraManager?>(null)
    val cameraManager: StateFlow<CameraManager?> = _cameraManager

    var streamingServer: StreamingServer? = null

    fun setCameraManager(manager: CameraManager?) {
        _cameraManager.value = manager
    }

    private val _audioManager = MutableStateFlow<AudioManager?>(null)
    val audioManager: StateFlow<AudioManager?> = _audioManager

    fun setAudioManager(manager: AudioManager?) {
        _audioManager.value = manager
    }

    private val _pin = MutableStateFlow(generatePin())
    val pin: StateFlow<String> = _pin

    private fun generatePin(): String {
        return (1000..9999).random().toString()
    }

    private val _discoveryManager = MutableStateFlow<DiscoveryManager?>(null)
    val discoveryManager: StateFlow<DiscoveryManager?> = _discoveryManager

    fun setDiscoveryManager(manager: DiscoveryManager?) {
        _discoveryManager.value = manager
    }
}
