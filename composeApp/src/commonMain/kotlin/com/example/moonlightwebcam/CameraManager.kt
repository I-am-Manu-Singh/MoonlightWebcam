package com.example.moonlightwebcam

import kotlinx.coroutines.flow.StateFlow

expect class CameraManager {
    // A Flow of JPEG bytes or similar frame data
    val frameFlow: StateFlow<ByteArray?>
    val cameraState: StateFlow<CameraState>

    fun startCamera()
    fun stopCamera()
    fun togglePause()

    fun switchCamera(lens: CameraLens)
    fun setResolution(resolution: CameraResolution)
    fun setZoom(ratio: Float)
    fun setExposure(index: Int)
    fun setFps(fps: Int)
    fun setJpegQuality(quality: Int)
    fun focusAt(x: Float, y: Float) // x, y are 0.0..1.0 relative coords
    fun setBokeh(enabled: Boolean)
    fun takePicture()
    fun setFlash(enabled: Boolean)
    fun setStreamMode(mode: StreamMode)
    fun setContrast(value: Int)
    fun setBrightness(value: Int)
    fun setClientConnected(connected: Boolean, name: String? = null)
    fun setStreamingInterface(iface: StreamingInterface)
    fun setAutoCloseRemaining(seconds: Int?)
    suspend fun captureSnapshot(): ByteArray?
}
