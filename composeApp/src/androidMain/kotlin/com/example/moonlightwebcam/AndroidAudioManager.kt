package com.example.moonlightwebcam

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidAudioManager : AudioManager {
    private val _audioFlow =
            MutableSharedFlow<ByteArray?>(
                    replay = 0,
                    extraBufferCapacity =
                            1, // Reduced to 1 to minimize latency (drop oldest if network slow)
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
    override val audioFlow: SharedFlow<ByteArray?> = _audioFlow

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val bufferSize =
            AudioRecord.getMinBufferSize(
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            )

    var activeMediaProjection: android.media.projection.MediaProjection? = null
    var useMicOnly: Boolean = false

    @SuppressLint("MissingPermission") // Checked by caller/Manifest
    override fun startAudio() {
        if (recordingJob?.isActive == true) return

        try {
            // Prioritize Internal Audio (MediaProjection) unless Mic is forced or not available
            if (!useMicOnly &&
                            activeMediaProjection != null &&
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
            ) {
                // Internal Audio
                val config =
                        android.media.AudioPlaybackCaptureConfiguration.Builder(
                                        activeMediaProjection!!
                                )
                                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                                .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                                .build()

                audioRecord =
                        AudioRecord.Builder()
                                .setAudioFormat(
                                        AudioFormat.Builder()
                                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                                .setSampleRate(44100)
                                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                                .build()
                                )
                                .setAudioPlaybackCaptureConfig(config)
                                .setBufferSizeInBytes(bufferSize)
                                .build()
                android.util.Log.d("AndroidAudioManager", "Starting Internal Audio Capture")
            } else {
                // Mic
                audioRecord =
                        AudioRecord(
                                MediaRecorder.AudioSource.MIC,
                                44100,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize
                        )
                android.util.Log.d(
                        "AndroidAudioManager",
                        "Starting Mic Capture (useMicOnly=$useMicOnly, projection=${activeMediaProjection != null})"
                )
            }

            audioRecord?.startRecording()
            android.util.Log.d(
                    "AndroidAudioManager",
                    "Audio recording started (SessionId: ${audioRecord?.audioSessionId})"
            )
            recordingJob =
                    scope.launch {
                        val buffer = ByteArray(bufferSize)
                        var logCounter = 0
                        while (isActive) { // Check cancellation
                            if (_isMuted.value) {
                                _audioFlow.emit(ByteArray(bufferSize))
                                _audioLevel.value = 0f
                                kotlinx.coroutines.delay(10)
                                continue
                            }

                            try {
                                var read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                                if (read < 0) { // Error codes are negative
                                    android.util.Log.e(
                                            "AndroidAudioManager",
                                            "Audio Read Error: $read"
                                    )
                                    break
                                }

                                // Ensure even number of bytes (16-bit mono = 2 bytes/frame)
                                if (read % 2 != 0) {
                                    read--
                                }

                                if (read > 0) {
                                    if (logCounter++ % 100 == 0) {
                                        android.util.Log.v(
                                                "AndroidAudioManager",
                                                "Audio Read: $read bytes (RMS: ${_audioLevel.value})"
                                        )
                                    }

                                    // Calculate RMS
                                    var sum = 0.0
                                    for (i in 0 until read step 2) {
                                        val sample =
                                                ((buffer[i + 1].toInt() shl 8) or
                                                                (buffer[i].toInt() and 0xFF))
                                                        .toShort()
                                        val normalized = sample / 32768.0
                                        sum += normalized * normalized
                                    }
                                    val rms = kotlin.math.sqrt(sum / (read / 2)).toFloat()
                                    _audioLevel.value = rms

                                    // Emit aligned data
                                    _audioFlow.emit(buffer.copyOfRange(0, read))
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(
                                        "AndroidAudioManager",
                                        "Exception in read loop: ${e.message}"
                                )
                                break
                            }
                        }

                        // Clean up ref on exit if needed, but stopAudio does it.
                    }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopAudio() {
        try {
            recordingJob?.cancel()
            // Don't release immediately if the thread is reading?
            // Releasing can cause the native crash if active.
            // But we need to stop.
            // audioRecord?.stop() interrupts the recording immediately.
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun setMute(muted: Boolean) {
        _isMuted.value = muted
    }

    fun restartAudio() {
        stopAudio()
        startAudio()
    }
}
