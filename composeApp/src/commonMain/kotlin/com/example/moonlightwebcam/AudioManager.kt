package com.example.moonlightwebcam

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface AudioManager {
    val audioFlow: SharedFlow<ByteArray?>
    val isMuted: StateFlow<Boolean>
    val audioLevel: StateFlow<Float>

    fun startAudio()
    fun stopAudio()
    fun setMute(muted: Boolean)
}
