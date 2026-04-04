package com.example.moonlightwebcam

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*

class AndroidScreenManager(private val context: Context, private val onFrame: (ByteArray) -> Unit) {

    var mediaProjection: MediaProjection? = null
        private set

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var fps: Int = 30
    var currentQuality: Int = 60

    private var currentWidth: Int = 1280
    private var currentHeight: Int = 720

    /* ---------------------------------------------------- */
    /* Lifecycle                                            */
    /* ---------------------------------------------------- */

    fun start(resultCode: Int, data: Intent, quality: Int, fps: Int) {
        stop()
        this.fps = fps
        this.currentQuality = quality

        val mpManager =
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mediaProjection =
                try {
                    mpManager.getMediaProjection(resultCode, data)
                } catch (e: Exception) {
                    Log.e("ScreenManager", "Failed to get MediaProjection", e)
                    null
                }

        if (mediaProjection == null) {
            Log.e("ScreenManager", "MediaProjection is null")
            return
        }

        mediaProjection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stop()
                    }
                },
                null
        )

        val audioManager = DependencyHolder.audioManager.value
        if (audioManager is AndroidAudioManager) {
            audioManager.activeMediaProjection = mediaProjection
        }

        setupVirtualDisplay()
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null

        val audioManager = DependencyHolder.audioManager.value
        if (audioManager is AndroidAudioManager) {
            audioManager.activeMediaProjection = null
        }
    }

    fun pause() {
        captureJob?.cancel()
        captureJob = null
        // Do NOT release virtualDisplay or imageReader to keep the session alive
    }

    fun resume(quality: Int) {
        if (mediaProjection == null) {
            Log.e("ScreenManager", "Cannot resume, MediaProjection is null")
            return
        }

        currentQuality = quality

        // If we already have a display, just restart the loop
        if (virtualDisplay != null && imageReader != null) {
            startCaptureLoop()
        } else {
            setupVirtualDisplay()
        }
    }

    /* ---------------------------------------------------- */
    /* Display management                                   */
    /* ---------------------------------------------------- */

    private fun setupVirtualDisplay() {
        // Safety cleanup if somehow called
        virtualDisplay?.release()
        imageReader?.close()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)

        currentHeight = currentWidth * metrics.heightPixels / metrics.widthPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(currentWidth, currentHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay =
                mediaProjection?.createVirtualDisplay(
                        "MoonlightScreen",
                        currentWidth,
                        currentHeight,
                        density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader?.surface,
                        null,
                        null
                )

        startCaptureLoop()
    }

    fun resizeDisplay(newWidth: Int, newHeight: Int, quality: Int) {
        if (mediaProjection == null) return

        currentWidth = newWidth
        currentHeight = newHeight
        currentQuality = quality

        captureJob?.cancel()
        imageReader?.close()

        imageReader = ImageReader.newInstance(currentWidth, currentHeight, PixelFormat.RGBA_8888, 2)

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        val density = metrics.densityDpi

        virtualDisplay?.resize(currentWidth, currentHeight, density)
        virtualDisplay?.setSurface(imageReader?.surface)

        startCaptureLoop()
    }

    /* ---------------------------------------------------- */
    /* Capture loop                                         */
    /* ---------------------------------------------------- */

    private fun startCaptureLoop() {
        captureJob =
                scope.launch {
                    var frameCount = 0

                    while (isActive) {
                        val startTime = System.currentTimeMillis()

                        try {
                            val image = imageReader?.acquireLatestImage()
                            if (image != null) {
                                val plane = image.planes[0]
                                val buffer = plane.buffer
                                val pixelStride = plane.pixelStride
                                val rowStride = plane.rowStride
                                val rowPadding = rowStride - pixelStride * currentWidth

                                val bitmap =
                                        createBitmap(
                                                currentWidth + rowPadding / pixelStride,
                                                currentHeight
                                        )

                                bitmap.copyPixelsFromBuffer(buffer)
                                image.close()

                                val finalBitmap =
                                        if (rowPadding == 0) {
                                            bitmap
                                        } else {
                                            Bitmap.createBitmap(
                                                    bitmap,
                                                    0,
                                                    0,
                                                    currentWidth,
                                                    currentHeight
                                            )
                                        }

                                val out = ByteArrayOutputStream()
                                finalBitmap.compress(
                                        Bitmap.CompressFormat.JPEG,
                                        currentQuality,
                                        out
                                )

                                onFrame(out.toByteArray())

                                if (rowPadding != 0) finalBitmap.recycle()
                                bitmap.recycle()

                                frameCount++
                                if (frameCount % 60 == 0) {
                                    Log.d(
                                            "ScreenManager",
                                            "Sent $frameCount frames (Q=$currentQuality)"
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ScreenManager", "Screen capture error", e)
                        }

                        val processTime = System.currentTimeMillis() - startTime
                        val targetDelay = 1000L / fps
                        val delayMs = (targetDelay - processTime).coerceAtLeast(0)

                        if (delayMs > 0) delay(delayMs)
                    }
                }
    }
}
