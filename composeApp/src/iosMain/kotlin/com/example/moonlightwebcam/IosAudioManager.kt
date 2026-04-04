@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.moonlightwebcam

import kotlinx.cinterop.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import platform.AVFAudio.*
import platform.Foundation.*

class IosAudioManager : AudioManager {
    private val _audioFlow =
            MutableSharedFlow<ByteArray?>(
                    replay = 0,
                    extraBufferCapacity = 128,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
    override val audioFlow: SharedFlow<ByteArray?> = _audioFlow

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel

    private val audioEngine = AVAudioEngine()
    private var isRunning = false

    override fun startAudio() {
        if (isRunning) return

        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(
                    AVAudioSessionCategoryPlayAndRecord,
                    withOptions =
                            AVAudioSessionCategoryOptionDefaultToSpeaker or
                                    AVAudioSessionCategoryOptionAllowBluetooth,
                    error = null
            )
            session.setMode(AVAudioSessionModeVideoChat, error = null)
            session.setPreferredSampleRate(44100.0, error = null)
            session.setActive(true, error = null)

            val inputNode = audioEngine.inputNode
            // We request the tap on the input node's native format, but we tried to prefer 44.1k
            // If the hardware supports it, we get 44.1k. If not, we get hardware rate (e.g. 48k).
            // For robustness, we should ideally resample, but "best effort" with preferred rate
            // helps.
            val format = inputNode.inputFormatForBus(0u)

            // Expected format: PCM 16-bit 44100Hz 1ch?
            // AVFoundation usually gives Float32. We might need to convert or just stream what we
            // get.
            // For simplicity in this "MJPEG+WAV" hack, we ideally want PCM 16-bit.
            // Converting Float32 to Int16 is needed if we claim "PCM 16-bit" in the header.
            // Or we can configure the tap to requests a specific format?
            // installTapOnBus format should match inputNode's hardware format usually.

            // Let's try to request conversion settings if possible, or manually convert.
            // Simple approach: Capture Float32, convert to Int16.

            inputNode.installTapOnBus(0u, 4096u, format) { buffer, time ->
                if (buffer != null) {
                    if (_isMuted.value) {
                        // send silence? or just drop
                        return@installTapOnBus
                    }

                    val channelData = buffer.floatChannelData?.get(0) ?: return@installTapOnBus
                    val frameLength = buffer.frameLength.toInt()

                    // Simple manual conversion Float -> Int16
                    // We need a byte array. frameLength * 2 bytes.
                    val byteArray = ByteArray(frameLength * 2)
                    var sum = 0.0

                    // Pointer arithmetic in Kotlin/Native:
                    for (i in 0 until frameLength) {
                        val floatSample = channelData[i]
                        // RMS Accumulation
                        val normalized = floatSample.toDouble()
                        sum += normalized * normalized

                        // Clamp and convert
                        var sample = (floatSample * 32767.0f).toInt()
                        if (sample > 32767) sample = 32767
                        if (sample < -32768) sample = -32768

                        val shortVal = sample.toShort()
                        // Little Endian
                        byteArray[i * 2] = (shortVal.toInt() and 0xFF).toByte()
                        byteArray[i * 2 + 1] = ((shortVal.toInt() shr 8) and 0xFF).toByte()
                    }

                    // Update Audio Level
                    val rms = kotlin.math.sqrt(sum / frameLength).toFloat()
                    _audioLevel.value = rms

                    // Emitting from background thread to Flow?
                    // MutableStateFlow is thread-safe?
                    // Ideally we should use a shared flow or handle concurrency,
                    // but simple emit might work if lucky.
                    // NOTE: collecting from main thread might be okay.
                    // Since this callback is on an audio thread, be careful.
                    // Use tryEmit for non-suspending emission from callback
                    _audioFlow.tryEmit(byteArray)
                }
            }

            audioEngine.prepare()
            audioEngine.startAndReturnError(null)
            isRunning = true
        } catch (e: Exception) {
            println("Error starting audio: ${e}")
        }
    }

    override fun stopAudio() {
        if (!isRunning) return
        audioEngine.stop()
        audioEngine.inputNode.removeTapOnBus(0u)
        isRunning = false
    }

    override fun setMute(muted: Boolean) {
        _isMuted.value = muted
    }
}
