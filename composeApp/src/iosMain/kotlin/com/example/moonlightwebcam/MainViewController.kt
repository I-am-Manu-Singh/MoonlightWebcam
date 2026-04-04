package com.example.moonlightwebcam

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    val cameraManager = remember { CameraManager() }
    val audioManager = remember { IosAudioManager() }
    val streamingServer = remember { IosStreamingServer(cameraManager.frameFlow) }
    val discoveryManager = remember { IosDiscoveryManager() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        DependencyHolder.setCameraManager(cameraManager)
        DependencyHolder.setAudioManager(audioManager)
        DependencyHolder.setDiscoveryManager(discoveryManager)
        DependencyHolder.streamingServer = streamingServer
        streamingServer.start()
        discoveryManager.publishService(8080)
    }

    App(cameraManager, streamingServer)
}
