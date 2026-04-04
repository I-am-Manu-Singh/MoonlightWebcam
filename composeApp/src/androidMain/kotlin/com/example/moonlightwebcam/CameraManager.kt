package com.example.moonlightwebcam

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

actual class CameraManager(
        private val context: Context,
        private val lifecycleOwner: LifecycleOwner
) {
    private val _frameFlow = MutableStateFlow<ByteArray?>(null)
    actual val frameFlow: StateFlow<ByteArray?> = _frameFlow

    private val _cameraState =
            MutableStateFlow(
                    CameraState(
                            serverDeviceName =
                                    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                    )
            )
    actual val cameraState: StateFlow<CameraState> = _cameraState

    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: androidx.camera.extensions.ExtensionsManager? = null
    private var camera: androidx.camera.core.Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: androidx.camera.core.ImageCapture? = null
    private var preview: androidx.camera.core.Preview? = null
    private var surfaceProvider: androidx.camera.core.Preview.SurfaceProvider? = null
    private val screenManager = AndroidScreenManager(context) { _frameFlow.value = it }

    val isProjecting: Boolean
        get() = screenManager.mediaProjection != null

    private var lastScreenResultCode: Int = 0
    private var lastScreenData: Intent? = null

    private val audioManager
        get() = DependencyHolder.audioManager.value as? AndroidAudioManager

    fun setPreviewSurfaceProvider(provider: androidx.camera.core.Preview.SurfaceProvider?) {
        surfaceProvider = provider
        if (provider == null) {
            // Unbind only the preview use case to keep the mjpeg stream alive
            ContextCompat.getMainExecutor(context).execute {
                try {
                    // Fix: Do NOT unbind the use case, just clear the surface provider.
                    Log.d(
                            "CameraManager",
                            "Clearing preview surface provider (keeping use case bound)"
                    )
                    preview?.setSurfaceProvider(null)
                } catch (e: Exception) {
                    Log.e("CameraManager", "Failed to clear preview", e)
                }
            }
        } else {
            // OPTIMIZATION: If we are already running and just getting a new surface (rotation),
            // just update the provider directly to avoid re-binding the whole camera.
            ContextCompat.getMainExecutor(context).execute {
                if (camera != null && preview != null && cameraProvider?.isBound(preview!!) == true
                ) {
                    Log.d("CameraManager", "Hot-swapping preview surface provider")
                    preview?.setSurfaceProvider(provider)
                } else {
                    Log.d("CameraManager", "Full bind required for surface provider")
                    bindCameraUseCases()
                }
            }
        }
    }

    // Default Camera Selector
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private fun initializeProvider() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
                {
                    cameraProvider = cameraProviderFuture.get()
                    // Init Extensions
                    val extensionsFuture =
                            androidx.camera.extensions.ExtensionsManager.getInstanceAsync(
                                    context,
                                    cameraProvider!!
                            )
                    extensionsFuture.addListener(
                            {
                                extensionsManager = extensionsFuture.get()
                                onProviderInitialized()
                            },
                            ContextCompat.getMainExecutor(context)
                    )
                },
                ContextCompat.getMainExecutor(context)
        )
    }

    private fun onProviderInitialized() {
        discoverCapabilities()
        if (_cameraState.value.isStreaming) {
            bindCameraUseCases()
        }
    }

    private fun discoverCapabilities() {
        val provider = cameraProvider ?: return
        try {
            // Find Back Camera Info to determine capabilities (Ultrawide/Tele)
            val backCameraInfo =
                    provider.availableCameraInfos.firstOrNull {
                        it.lensFacing == CameraSelector.LENS_FACING_BACK
                    }

            if (backCameraInfo != null) {
                // We use the ZoomState to guess physical lenses
                val zoomState = backCameraInfo.zoomState.value
                val minZoom = zoomState?.minZoomRatio ?: 1f
                val maxZoom = zoomState?.maxZoomRatio ?: 1f

                val availableLenses = mutableListOf("BACK", "FRONT")
                if (minZoom < 1.0f) availableLenses.add("ULTRA_WIDE")
                if (maxZoom >= 2.0f) availableLenses.add("TELEPHOTO")

                _cameraState.value =
                        _cameraState.value.copy(
                                backMinZoom = minZoom,
                                backMaxZoom = maxZoom,
                                // We don't overwrite current min/max zoom here blindly
                                // as we might be on Front camera by default?
                                // But initializing back params is key.
                                availableLenses = availableLenses.distinct()
                        )
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Discovery failed", e)
        }
    }

    init {
        initializeProvider()
    }

    actual fun startCamera() {
        ContextCompat.getMainExecutor(context).execute {
            if (cameraProvider == null) {
                initializeProvider()
            }

            try {
                val serviceIntent = Intent(context, MoonlightService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e("CameraManager", "Failed to start service", e)
            }

            val current = _cameraState.value.currentLens
            // Fix: If we are already in SCREEN mode, don't reset to BACK/FRONT.
            val targetLens =
                    if (current == CameraLens.SCREEN) CameraLens.SCREEN
                    else if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraLens.BACK
                    else CameraLens.FRONT

            _cameraState.value =
                    _cameraState.value.copy(isStreaming = true, currentLens = targetLens)

            if (targetLens == CameraLens.SCREEN) {
                Log.d("CameraManager", "Restoring Screen Capture session...")
                MainActivity.instance?.requestScreenCapture { resultCode, data ->
                    if (data != null) {
                        lastScreenResultCode = resultCode
                        lastScreenData = data
                        val res = _cameraState.value.currentResolution
                        screenManager.resizeDisplay(
                                res.width,
                                res.height,
                                _cameraState.value.jpegQuality
                        )
                        screenManager.start(
                                resultCode,
                                data,
                                _cameraState.value.jpegQuality,
                                _cameraState.value.currentFps
                        )
                        _cameraState.update { it.copy(isProjecting = true) }
                        audioManager?.activeMediaProjection = screenManager.mediaProjection
                        audioManager?.restartAudio()
                    } else {
                        Log.e("CameraManager", "Screen capture permission denied or failed")
                        stopCamera()
                    }
                }
            } else {
                // Ensure binding happens if we just got the provider or already had it
                if (cameraProvider != null) {
                    bindCameraUseCases()
                }
            }
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        // Critical Fix: Do NOT bind camera use cases if we are in Screen Cast mode.
        // ScreenManager handles the "produciton" of frames in this mode.
        if (_cameraState.value.currentLens == CameraLens.SCREEN) {
            Log.d("CameraManager", "Skipping camera binding (Mode: SCREEN)")
            return
        }

        // Image Analysis (MJPEG stream source)
        if (imageAnalysis == null) {
            val resolution = _cameraState.value.currentResolution
            val size = android.util.Size(resolution.width, resolution.height)

            val resolutionSelector =
                    androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                            .setAspectRatioStrategy(
                                    androidx.camera.core.resolutionselector.AspectRatioStrategy
                                            .RATIO_16_9_FALLBACK_AUTO_STRATEGY
                            )
                            .setResolutionStrategy(
                                    androidx.camera.core.resolutionselector.ResolutionStrategy(
                                            size,
                                            androidx.camera.core.resolutionselector
                                                    .ResolutionStrategy
                                                    .FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                    )
                            )
                            .build()

            val imageAnalysisBuilder =
                    ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setResolutionSelector(resolutionSelector)

            // Enable Continuous Auto Focus and 24 FPS
            androidx.camera.camera2.interop.Camera2Interop.Extender(imageAnalysisBuilder)
                    .setCaptureRequestOption(
                            android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE,
                            android.hardware.camera2.CaptureRequest
                                    .CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    .setCaptureRequestOption(
                            android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            android.util.Range(
                                    _cameraState.value.currentFps,
                                    _cameraState.value.currentFps
                            )
                    )

            imageAnalysis = imageAnalysisBuilder.build()
            imageAnalysis?.setAnalyzer(executor) { image -> processImage(image) }
        }

        // Preview Use Case
        // If we have a provider but no preview, OR if preview exists but we want to refresh?
        // Actually, Preview is better recreated or updated if surfaceProvider changes.
        if (surfaceProvider != null) {
            if (preview == null) {
                preview = androidx.camera.core.Preview.Builder().build()
            }
            preview?.setSurfaceProvider(surfaceProvider)
        } else {
            preview = null
        }

        // Image Capture Use Case
        if (imageCapture == null) {
            imageCapture =
                    androidx.camera.core.ImageCapture.Builder()
                            .setCaptureMode(
                                    androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                            )
                            .build()
        }

        var cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Apply Bokeh if requested and available
        if (_cameraState.value.isBokehEnabled && extensionsManager != null) {
            if (extensionsManager!!.isExtensionAvailable(
                            cameraSelector,
                            androidx.camera.extensions.ExtensionMode.BOKEH
                    )
            ) {
                cameraSelector =
                        extensionsManager!!.getExtensionEnabledCameraSelector(
                                cameraSelector,
                                androidx.camera.extensions.ExtensionMode.BOKEH
                        )
            }
        }

        try {
            // Do NOT unbindAll() here. It kills the ImageAnalysis stream.
            // cameraProvider.unbindAll()

            // Check if lifecycle is destroyed to avoid crash
            if (lifecycleOwner.lifecycle.currentState ==
                            androidx.lifecycle.Lifecycle.State.DESTROYED
            ) {
                Log.w("CameraManager", "Lifecycle is destroyed, skipping bind")
                return
            }

            val useCasesToBind = mutableListOf<androidx.camera.core.UseCase>()
            imageAnalysis?.let { if (!cameraProvider.isBound(it)) useCasesToBind.add(it) }
            preview?.let { if (!cameraProvider.isBound(it)) useCasesToBind.add(it) }
            imageCapture?.let { if (!cameraProvider.isBound(it)) useCasesToBind.add(it) }

            if (useCasesToBind.isNotEmpty()) {
                Log.d(
                        "CameraManager",
                        "Binding use cases: ${useCasesToBind.joinToString { it.javaClass.simpleName }}"
                )

                try {
                    camera =
                            cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    *useCasesToBind.toTypedArray()
                            )
                    // Restore State (Zoom & Exposure)
                    camera?.cameraControl?.setExposureCompensationIndex(storedExposureIndex)
                    camera?.cameraControl?.setZoomRatio(storedZoom)
                } catch (e: Exception) {
                    Log.e("CameraManager", "Binding failed! trying fallback resolution...", e)
                    // FALLBACK: If specific resolution failed (e.g. 4K on Front cam), try 1080p
                    try {
                        // Reset to safe 1080p
                        val fallbackRes = CameraResolution.FHD_1080P
                        Log.d("CameraManager", "Fallback to $fallbackRes")

                        // Re-create ImageAnalysis with fallback
                        imageAnalysis =
                                androidx.camera.core.ImageAnalysis.Builder()
                                        .setBackpressureStrategy(
                                                androidx.camera.core.ImageAnalysis
                                                        .STRATEGY_KEEP_ONLY_LATEST
                                        )
                                        .setResolutionSelector(
                                                androidx.camera.core.resolutionselector
                                                        .ResolutionSelector.Builder()
                                                        .setResolutionStrategy(
                                                                androidx.camera.core
                                                                        .resolutionselector
                                                                        .ResolutionStrategy(
                                                                                android.util.Size(
                                                                                        fallbackRes
                                                                                                .width,
                                                                                        fallbackRes
                                                                                                .height
                                                                                ),
                                                                                androidx.camera.core
                                                                                        .resolutionselector
                                                                                        .ResolutionStrategy
                                                                                        .FALLBACK_RULE_CLOSEST_LOWER
                                                                        )
                                                        )
                                                        .build()
                                        )
                                        .setOutputImageFormat(
                                                androidx.camera.core.ImageAnalysis
                                                        .OUTPUT_IMAGE_FORMAT_RGBA_8888
                                        )
                                        .build()

                        imageAnalysis?.setAnalyzer(executor) { proxy -> processImage(proxy) }

                        // Re-populate use cases
                        val fallbackUseCases = mutableListOf<androidx.camera.core.UseCase>()
                        imageAnalysis?.let { fallbackUseCases.add(it) }
                        preview?.let { fallbackUseCases.add(it) }

                        camera =
                                cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        *fallbackUseCases.toTypedArray()
                                )
                        // Update state to reflect fallback
                        _cameraState.value =
                                _cameraState.value.copy(currentResolution = fallbackRes)
                    } catch (retryEx: Exception) {
                        Log.e("CameraManager", "Critical: Fallback binding also failed", retryEx)
                    }
                }

                // Restore State (Zoom & Exposure)
                camera?.cameraControl?.setExposureCompensationIndex(storedExposureIndex)
                camera?.cameraControl?.setZoomRatio(storedZoom)

                updateCameraStateInfo()
            }
        } catch (exc: Exception) {
            Log.e("CameraManager", "Use case binding failed", exc)
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun updateCameraStateInfo() {
        camera?.cameraInfo?.let { info ->
            val zoomState = info.zoomState.value
            val exposureState = info.exposureState

            val currentMinZoom = zoomState?.minZoomRatio ?: 1f
            val currentMaxZoom = zoomState?.maxZoomRatio ?: 1f

            // Update persistent capabilities if we are on the Standard Back Camera
            val isStandardBack = _cameraState.value.currentLens == CameraLens.BACK
            val newBackMin = if (isStandardBack) currentMinZoom else _cameraState.value.backMinZoom
            val newBackMax = if (isStandardBack) currentMaxZoom else _cameraState.value.backMaxZoom

            // Calculate supported resolutions
            val supportedResolutions =
                    try {
                        val camera2Info =
                                androidx.camera.camera2.interop.Camera2CameraInfo.from(info)
                        val map =
                                camera2Info.getCameraCharacteristic(
                                        android.hardware.camera2.CameraCharacteristics
                                                .SCALER_STREAM_CONFIGURATION_MAP
                                )
                        val outputSizes =
                                map?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)

                        if (outputSizes != null) {
                            Log.d(
                                    "CameraManager",
                                    "Raw Supported Sizes: ${outputSizes.joinToString { "${it.width}x${it.height}" }}"
                            )
                            CameraResolution.values().filter { res ->
                                outputSizes.any { size ->
                                    (size.width == res.width && size.height == res.height) ||
                                            (size.width == res.height && size.height == res.width)
                                }
                            }
                        } else {
                            Log.w("CameraManager", "Output sizes was null, allowing all")
                            CameraResolution.values().toList()
                        }
                    } catch (e: Exception) {
                        Log.e("CameraManager", "Failed to get supported resolutions", e)
                        // Fallback: Default to common + 4K if we can't query
                        listOf(
                                CameraResolution.SD_480P,
                                CameraResolution.HD_720P,
                                CameraResolution.FHD_1080P,
                                CameraResolution.UHD_4K
                        )
                    }

            // Validated current resolution
            val savedRes = getSavedResolutionForLens(_cameraState.value.currentLens)
            val targetRes =
                    if (savedRes != null && supportedResolutions.contains(savedRes)) {
                        savedRes
                    } else {
                        _cameraState.value.currentResolution
                    }

            val validatedResolution =
                    if (supportedResolutions.contains(targetRes)) {
                        targetRes
                    } else {
                        // If current is invalid and no valid saved, pick best or default
                        supportedResolutions.maxByOrNull { it.width * it.height }
                                ?: CameraResolution.HD_720P
                    }

            // Determine Available Lenses (Always based on Back Camera capabilities)
            val availableLenses = mutableListOf("BACK", "FRONT", "SCREEN")

            // Use derived Back keys (which are updated when on Back, and persisted when on Front)
            if (newBackMin < 1.0f) availableLenses.add("ULTRA_WIDE")
            if (newBackMax >= 2.0f) availableLenses.add("TELEPHOTO")
            // Sort custom: Front, Ultra, Back, Tele
            // Or just Keep simple order

            _cameraState.value =
                    _cameraState.value.copy(
                            zoomRatio = zoomState?.zoomRatio ?: storedZoom,
                            maxZoom = currentMaxZoom,
                            minZoom = currentMinZoom,
                            minExposure = exposureState.exposureCompensationRange.lower,
                            maxExposure = exposureState.exposureCompensationRange.upper,
                            exposureIndex = exposureState.exposureCompensationIndex,
                            backMinZoom = newBackMin,
                            backMaxZoom = newBackMax,
                            availableResolutions = supportedResolutions,
                            currentResolution = validatedResolution,
                            // Convert enum names to strings for availableLenses
                            availableLenses = availableLenses.distinct()
                    )

            // Check network speed
            updateNetworkSpeed()

            // Validated Zoom from state vs stored

            // Validated Zoom from state vs stored
            // If the restored zoom hasn't taken effect yet, zoomState.zoomRatio might be 1.0
            // (default).
            // But we want the UI to reflect our stored (and requested) zoom.
            // So we override the zoomRatio from zoomState with storedZoom IF they differ
            // significantly
            // during this stabilization phase. However, zoomState is the source of truth for the
            // SLIDER.
            // If we use storedZoom here, the slider stays stable.
            if (_cameraState.value.currentLens == CameraLens.BACK ||
                            _cameraState.value.currentLens == CameraLens.FRONT
            ) {
                // Only overwrite if reasonable? No, just trust storedZoom for UI stability on
                // rebind
                if (java.lang.Math.abs((zoomState?.zoomRatio ?: 1f) - storedZoom) > 0.1f) {
                    _cameraState.value = _cameraState.value.copy(zoomRatio = storedZoom)
                }
            }

            // Watch Zoom State changes
            info.zoomState.observe(lifecycleOwner) { z ->
                _cameraState.value =
                        _cameraState.value.copy(zoomRatio = z.zoomRatio, minZoom = z.minZoomRatio)
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun processImage(image: ImageProxy) {
        // Log.d("CameraManager", "processImage called")
        // toBitmap() automatically applies the rotation defined in image.imageInfo.rotationDegrees
        try {
            val originalBitmap = image.toBitmap()

            // Check if we need image adjustments (Brightness/Contrast)
            val brightness = _cameraState.value.brightness
            val contrast = _cameraState.value.contrast

            val processedBitmap =
                    if (brightness != 0 || contrast != 0) {
                        val cm = android.graphics.ColorMatrix()

                        // Contrast Scale: Map -100..100 to 0.0..2.0 (1.0 is default)
                        val scale = (contrast + 100f) / 100f

                        // Brightness Offset: Map -100..100 to -255..255
                        val offset = brightness * 2.55f

                        cm.set(
                                floatArrayOf(
                                        scale,
                                        0f,
                                        0f,
                                        0f,
                                        offset,
                                        0f,
                                        scale,
                                        0f,
                                        0f,
                                        offset,
                                        0f,
                                        0f,
                                        scale,
                                        0f,
                                        offset,
                                        0f,
                                        0f,
                                        0f,
                                        1f,
                                        0f
                                )
                        )

                        val paint = android.graphics.Paint()
                        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)

                        val mutableBitmap =
                                createBitmap(originalBitmap.width, originalBitmap.height)
                        val canvas = android.graphics.Canvas(mutableBitmap)
                        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
                        mutableBitmap
                    } else {
                        originalBitmap
                    }

            val out = ByteArrayOutputStream()
            // Dynamic Quality from state
            val quality = _cameraState.value.jpegQuality
            processedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            val jpegBytes = out.toByteArray()

            if (System.currentTimeMillis() % 1000 < 50) {
                Log.d(
                        "CameraManager",
                        "Frame generated: ${image.width}x${image.height}, ${jpegBytes.size} bytes (Sampled)"
                )
            }
            _frameFlow.value = jpegBytes
        } catch (e: Exception) {
            Log.e("CameraManager", "Error processing image", e)
        } finally {
            image.close()
        }
    }

    actual fun stopCamera() {
        _cameraState.value = _cameraState.value.copy(isStreaming = false)
        ContextCompat.getMainExecutor(context).execute {
            try {
                cameraProvider?.unbindAll()
                // IMPORTANT: Stop screen capture if running
                screenManager.stop()
                _cameraState.update { it.copy(isProjecting = false) }
                audioManager?.stopAudio() // Stop audio when camera stops
            } catch (e: Exception) {
                Log.e("CameraManager", "Failed to unbind camera", e)
            }
        }

        // We do NOT stop the service here anymore.
        // We keep the service alive (Idle) so the UI stays on the Home Screen.
        // The user can stop the service effectively via the Notification 'Stop' action if needed.
    }

    actual fun togglePause() {
        val wasPaused = _cameraState.value.isPaused
        val isNowPaused = !wasPaused
        _cameraState.value = _cameraState.value.copy(isPaused = isNowPaused)

        if (isNowPaused) {
            // PAUSE
            Log.d("CameraManager", "Pausing Stream (keeping connection alive)...")
            ContextCompat.getMainExecutor(context).execute {
                try {
                    if (_cameraState.value.currentLens == CameraLens.SCREEN) {
                        screenManager.pause()
                    } else {
                        cameraProvider?.unbindAll()
                        imageAnalysis = null
                        preview = null
                        camera = null
                    }
                    audioManager?.stopAudio()
                } catch (e: Exception) {
                    Log.e("CameraManager", "Error pausing", e)
                }
            }
        } else {
            // RESUME
            Log.d("CameraManager", "Resuming Stream...")
            ContextCompat.getMainExecutor(context).execute {
                if (_cameraState.value.currentLens == CameraLens.SCREEN) {
                    if (screenManager.mediaProjection != null) {
                        screenManager.resume(_cameraState.value.jpegQuality)
                        audioManager?.restartAudio()
                    } else if (lastScreenData != null) {
                        val res = _cameraState.value.currentResolution
                        screenManager.resizeDisplay(
                                res.width,
                                res.height,
                                _cameraState.value.jpegQuality
                        )
                        screenManager.start(
                                lastScreenResultCode,
                                lastScreenData!!,
                                _cameraState.value.jpegQuality,
                                _cameraState.value.currentFps
                        )
                        audioManager?.activeMediaProjection = screenManager.mediaProjection
                        audioManager?.restartAudio()
                    } else {
                        Log.e(
                                "CameraManager",
                                "Cannot resume screen: No intent cached. Requesting again..."
                        )
                        MainActivity.instance?.requestScreenCapture { resultCode, data ->
                            if (data != null) {
                                lastScreenResultCode = resultCode
                                lastScreenData = data
                                val res = _cameraState.value.currentResolution
                                screenManager.resizeDisplay(
                                        res.width,
                                        res.height,
                                        _cameraState.value.jpegQuality
                                )
                                screenManager.start(
                                        resultCode,
                                        data,
                                        _cameraState.value.jpegQuality,
                                        _cameraState.value.currentFps
                                )
                                audioManager?.activeMediaProjection = screenManager.mediaProjection
                                audioManager?.restartAudio()
                            }
                        }
                    }
                } else {
                    bindCameraUseCases()
                    audioManager?.restartAudio()
                }
            }
        }
    }

    actual fun switchCamera(lens: CameraLens) {
        ContextCompat.getMainExecutor(context).execute {
            val currentLens = _cameraState.value.currentLens
            val isLensChanged = currentLens != lens

            // If switching TO Screen
            if (lens == CameraLens.SCREEN) {
                Log.d("CameraManager", "Switching to SCREEN")
                try {
                    cameraProvider?.unbindAll()
                    imageAnalysis = null
                    preview = null
                    camera = null
                } catch (e: Exception) {
                    Log.e("CameraManager", "Failed to unbind for screen", e)
                }

                screenManager.stop() // Stop previous if any
                _cameraState.value = _cameraState.value.copy(currentLens = lens)

                // Request Permission
                MainActivity.instance?.requestScreenCapture { resultCode, data ->
                    if (data != null) {
                        lastScreenResultCode = resultCode
                        lastScreenData = data
                        val res = _cameraState.value.currentResolution
                        screenManager.resizeDisplay(
                                res.width,
                                res.height,
                                _cameraState.value.jpegQuality
                        )
                        screenManager.start(
                                resultCode,
                                data,
                                _cameraState.value.jpegQuality,
                                _cameraState.value.currentFps
                        )
                        _cameraState.update { it.copy(isProjecting = true) }

                        audioManager?.activeMediaProjection = screenManager.mediaProjection
                        audioManager?.restartAudio()
                    } else {
                        Log.e("CameraManager", "Screen capture permission denied")
                    }
                }
                return@execute
            }

            // If switching FROM Screen (to Camera)
            if (currentLens == CameraLens.SCREEN) {
                screenManager.stop()
                audioManager?.activeMediaProjection = null
                audioManager?.restartAudio()
            }

            val newLensFacing =
                    when (lens) {
                        CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
                        else -> CameraSelector.LENS_FACING_BACK
                    }

            lensFacing = newLensFacing
            _cameraState.value = _cameraState.value.copy(currentLens = lens)

            // Check if we need to rebind.
            // If the camera var is null, we definitely need to rebind.
            val needsRebind = isLensChanged || camera == null

            if (needsRebind) {
                Log.d("CameraManager", "Rebinding camera for $lens. Unbinding all...")
                try {
                    cameraProvider?.unbindAll()
                } catch (e: Exception) {
                    Log.e("CameraManager", "Failed to unbindAll", e)
                }

                // Force recreation of use cases
                imageAnalysis = null
                preview = null
                camera = null // Clear camera ref

                Log.d("CameraManager", "Unbound all. Setting up new use cases...")

                // Update storedZoom to defaults for the new lens BEFORE rebind
                val newDefaultZoom =
                        when (lens) {
                            CameraLens.ULTRA_WIDE -> 0.5f
                            CameraLens.TELEPHOTO -> 2.0f
                            else -> 1.0f
                        }
                val safeZoom =
                        when (lens) {
                            CameraLens.ULTRA_WIDE -> _cameraState.value.backMinZoom
                            CameraLens.TELEPHOTO ->
                                    3.0f.coerceAtMost(_cameraState.value.backMaxZoom)
                            else -> 1.0f
                        }
                storedZoom = safeZoom
                bindCameraUseCases()
            } else {
                Log.d("CameraManager", "Applying zoom for $lens")
                try {
                    applyLensZoom(lens)
                } catch (e: Exception) {
                    Log.e(
                            "CameraManager",
                            "Failed to apply zoom (camera likely inactive). Rebinding...",
                            e
                    )
                    bindCameraUseCases()
                }
            }
        }
    }

    private fun applyLensZoom(lens: CameraLens) {
        val zoomState = camera?.cameraInfo?.zoomState?.value
        if (zoomState == null) {
            Log.e("CameraManager", "ZoomState is MSG_NULL")
            return
        }

        Log.d(
                "CameraManager",
                "Zoom State: min=${zoomState.minZoomRatio}, max=${zoomState.maxZoomRatio}"
        )

        when (lens) {
            CameraLens.ULTRA_WIDE -> {
                val z = zoomState.minZoomRatio
                Log.d("CameraManager", "Setting Ultrawide Zoom: $z")
                setZoom(z)
            }
            CameraLens.BACK -> {
                val z = 1.0f.coerceAtLeast(zoomState.minZoomRatio)
                Log.d("CameraManager", "Setting Back Zoom: $z")
                setZoom(z)
            }
            CameraLens.TELEPHOTO -> {
                val z = 3.0f.coerceAtMost(zoomState.maxZoomRatio)
                Log.d("CameraManager", "Setting Telephoto Zoom: $z")
                setZoom(z)
            }
            else -> {}
        }
    }

    actual fun setBokeh(enabled: Boolean) {
        if (_cameraState.value.isBokehEnabled != enabled) {
            _cameraState.value = _cameraState.value.copy(isBokehEnabled = enabled)
            bindCameraUseCases()
        }
    }

    actual fun setResolution(resolution: CameraResolution) {
        if (_cameraState.value.currentResolution != resolution) {
            saveResolutionForLens(_cameraState.value.currentLens, resolution) // Save!
            _cameraState.value = _cameraState.value.copy(currentResolution = resolution)

            if (_cameraState.value.currentLens == CameraLens.SCREEN) {
                screenManager.resizeDisplay(
                        resolution.width,
                        resolution.height,
                        _cameraState.value.jpegQuality
                )
            } else {
                cameraProvider?.let { provider ->
                    imageAnalysis?.let { if (provider.isBound(it)) provider.unbind(it) }
                }
                imageAnalysis = null
                bindCameraUseCases()
            }
        }
    }

    private var storedZoom: Float = 1f
    private var storedExposureIndex: Int = 0

    actual fun setZoom(ratio: Float) {
        storedZoom = ratio
        val future = camera?.cameraControl?.setZoomRatio(ratio)
        future?.addListener(
                {
                    try {
                        future.get()
                        Log.d("CameraManager", "Zoom to $ratio APPLIED SUCCESSFULLY")
                    } catch (e: Exception) {
                        Log.e("CameraManager", "Zoom to $ratio FAILED", e)
                    }
                },
                ContextCompat.getMainExecutor(context)
        )
    }

    actual fun setExposure(index: Int) {
        storedExposureIndex = index
        camera?.cameraControl?.setExposureCompensationIndex(index)
        _cameraState.value = _cameraState.value.copy(exposureIndex = index)
    }

    actual fun setFps(fps: Int) {
        if (_cameraState.value.currentFps != fps) {
            _cameraState.value = _cameraState.value.copy(currentFps = fps)

            if (_cameraState.value.currentLens == CameraLens.SCREEN) {
                screenManager.fps = fps
            } else {
                ContextCompat.getMainExecutor(context).execute {
                    cameraProvider?.let { provider ->
                        imageAnalysis?.let { if (provider.isBound(it)) provider.unbind(it) }
                    }
                    imageAnalysis = null
                    bindCameraUseCases()
                }
            }
        }
    }

    actual fun setJpegQuality(quality: Int) {
        val q = quality.coerceIn(10, 100)
        _cameraState.value = _cameraState.value.copy(jpegQuality = q)
        screenManager.currentQuality = q
    }

    actual fun focusAt(x: Float, y: Float) {
        val factory = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(x, y)
        val action =
                androidx.camera.core.FocusMeteringAction.Builder(point)
                        .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
        camera?.cameraControl?.cancelFocusAndMetering()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    actual fun setFlash(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
        _cameraState.value = _cameraState.value.copy(isFlashEnabled = enabled)
    }

    actual fun setStreamMode(mode: StreamMode) {
        _cameraState.value = _cameraState.value.copy(streamMode = mode)
    }

    actual fun setContrast(value: Int) {
        _cameraState.value = _cameraState.value.copy(contrast = value)
        // Todo: Implement contrast using ToneMapCurve or post-processing
    }

    actual fun setBrightness(value: Int) {
        _cameraState.value = _cameraState.value.copy(brightness = value)
        // Todo: Implement brightness if distinct from Exposure
    }

    actual fun setClientConnected(connected: Boolean, name: String?) {
        _cameraState.update {
            it.copy(isClientConnected = connected, clientName = if (connected) name else null)
        }
    }

    actual fun setStreamingInterface(iface: StreamingInterface) {
        _cameraState.value = _cameraState.value.copy(streamingInterface = iface)
    }

    actual fun setAutoCloseRemaining(seconds: Int?) {
        _cameraState.value = _cameraState.value.copy(autoCloseRemaining = seconds)
    }

    actual fun takePicture() {
        val imageCapture = imageCapture ?: return

        val name =
                java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", java.util.Locale.US)
                        .format(System.currentTimeMillis())
        val contentValues =
                android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                        put(
                                android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                                "Pictures/Moonlight"
                        )
                    }
                }

        val outputOptions =
                androidx.camera.core.ImageCapture.OutputFileOptions.Builder(
                                context.contentResolver,
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                contentValues
                        )
                        .build()

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: androidx.camera.core.ImageCaptureException) {
                        Log.e("CameraManager", "Photo capture failed: ${exc.message}", exc)
                    }

                    override fun onImageSaved(
                            output: androidx.camera.core.ImageCapture.OutputFileResults
                    ) {
                        val msg = "Photo capture succeeded: ${output.savedUri}"
                        Log.d("CameraManager", msg)
                        android.widget.Toast.makeText(
                                        context,
                                        "Photo Saved!",
                                        android.widget.Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                }
        )
    }

    actual suspend fun captureSnapshot(): ByteArray? =
            kotlin.coroutines.suspendCoroutine { cont ->
                val imageCapture = imageCapture
                if (imageCapture == null) {
                    cont.resumeWith(Result.success(null))
                    return@suspendCoroutine
                }

                imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : androidx.camera.core.ImageCapture.OnImageCapturedCallback() {
                            override fun onError(exc: androidx.camera.core.ImageCaptureException) {
                                Log.e(
                                        "CameraManager",
                                        "Snapshot capture failed: ${exc.message}",
                                        exc
                                )
                                cont.resumeWith(Result.success(null))
                            }

                            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                try {
                                    val buffer = image.planes[0].buffer
                                    val bytes = ByteArray(buffer.remaining())
                                    buffer.get(bytes)
                                    cont.resumeWith(Result.success(bytes))
                                } catch (e: Exception) {
                                    Log.e("CameraManager", "Error processing snapshot", e)
                                    cont.resumeWith(Result.success(null))
                                } finally {
                                    image.close()
                                }
                            }
                        }
                )
            }

    // --- Smart Features Implementation ---

    private fun getPrefs() = context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE)

    private fun saveResolutionForLens(lens: CameraLens, resolution: CameraResolution) {
        getPrefs().edit().putString("res_${lens.name}", resolution.name).apply()
    }

    private fun getSavedResolutionForLens(lens: CameraLens): CameraResolution? {
        val name = getPrefs().getString("res_${lens.name}", null) ?: return null
        return try {
            CameraResolution.valueOf(name)
        } catch (e: Exception) {
            null
        }
    }

    private fun updateNetworkSpeed() {
        try {
            val wifiManager =
                    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as?
                            android.net.wifi.WifiManager
            val info = wifiManager?.connectionInfo
            if (info != null) {
                val speed = info.linkSpeed // Mbps
                if (speed != -1) {
                    _cameraState.value = _cameraState.value.copy(linkSpeedMbps = speed)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
