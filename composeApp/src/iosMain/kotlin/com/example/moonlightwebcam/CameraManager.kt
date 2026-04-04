@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.moonlightwebcam

import kotlinx.cinterop.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.AVFoundation.*
import platform.CoreGraphics.*
import platform.CoreImage.*
import platform.CoreMedia.*
import platform.CoreVideo.*
import platform.Foundation.*
import platform.ImageIO.*
import platform.Photos.*
import platform.QuartzCore.* // For kCGImageDestinationLossyCompressionQuality
import platform.ReplayKit.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create

@OptIn(ExperimentalForeignApi::class)
actual class CameraManager {
    private val _frameFlow = MutableStateFlow<ByteArray?>(null)
    actual val frameFlow: StateFlow<ByteArray?> = _frameFlow

    private val _cameraState =
            MutableStateFlow(
                    CameraState(serverDeviceName = platform.UIKit.UIDevice.currentDevice.name)
            )
    actual val cameraState: StateFlow<CameraState> = _cameraState

    private var session: AVCaptureSession? = null
    private var device: AVCaptureDevice? = null
    private var input: AVCaptureDeviceInput? = null
    private var videoOutput: AVCaptureVideoDataOutput? = null
    private var photoOutput: AVCapturePhotoOutput? = null
    private val screenRecorder = RPScreenRecorder.sharedRecorder()

    private val photoDelegate =
            object : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
                override fun captureOutput(
                        output: AVCapturePhotoOutput,
                        didFinishProcessingPhoto: AVCapturePhoto,
                        error: NSError?
                ) {
                    if (error != null) {
                        println("CameraManager: Photo capture error: ${error.localizedDescription}")
                        return
                    }
                    val data = didFinishProcessingPhoto.fileDataRepresentation() ?: return

                    // Save to Photos Library
                    PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                                PHAssetCreationRequest.creationRequestForAsset()
                                        .addResourceWithType(PHAssetResourceTypePhoto, data, null)
                            }) { success, err ->
                        if (success) {
                            println("CameraManager: Photo saved successfully")
                            // Ideally notify UI via event/toast, but for now log is fine
                        } else {
                            println(
                                    "CameraManager: Error saving photo: ${err?.localizedDescription}"
                            )
                        }
                    }
                }
            }
    private val queue = dispatch_queue_create("cameraQueue", null)

    // Store user preferences to persist across rebinds/switches
    private var storedZoom: Float = 1.0f
    private var storedExposureIndex: Int = 0

    // CoreImage Context for JPEG conversion (expensive to recreate, so keep one)
    private val ciContext = CIContext()

    // Delegate to receive frames
    private val collector =
            object : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
                override fun captureOutput(
                        output: AVCaptureOutput,
                        didOutputSampleBuffer: CMSampleBufferRef?,
                        fromConnection: AVCaptureConnection
                ) {
                    // println("CameraManager: captureOutput received buffer")
                    didOutputSampleBuffer?.let { buffer ->
                        // Convert CMSampleBuffer to JPEG Data
                        val jpegData = convertToJpeg(buffer)
                        jpegData?.let { data ->
                            val bytes = data.toByteArray()
                            CoroutineScope(Dispatchers.Default).launch { _frameFlow.emit(bytes) }
                        }
                    }
                }
            }

    actual fun startCamera() {
        if (session != null) return
        println("CameraManager: startCamera called")

        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        println("CameraManager: Auth status: $status")
        updateAvailableDevices() // Update list on start
        if (status == AVAuthorizationStatusAuthorized) {
            setupAndStartSession()
        } else if (status == AVAuthorizationStatusNotDetermined) {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                if (granted) {
                    // Start session on main thread or background as needed
                    // AVCaptureSession startRunning is synchronous and can block, so careful.
                    // But callback is arbitrary thread.
                    setupAndStartSession()
                }
            }
        }
    }

    private fun setupAndStartSession() {
        println("CameraManager: setupAndStartSession")

        // Check for Simulator
        val deviceName = platform.UIKit.UIDevice.currentDevice.model
        val environment = platform.Foundation.NSProcessInfo.processInfo.environment
        val isSimulator =
                deviceName.contains("Simulator") || environment["SIMULATOR_DEVICE_NAME"] != null

        println("CameraManager: Device Model: $deviceName")
        println("CameraManager: Is Simulator: $isSimulator")

        if (isSimulator) {
            println("CameraManager: Running on Simulator - Starting Mock Camera")
            startMockCamera()
            return
        }

        val newSession = AVCaptureSession()
        newSession.sessionPreset = AVCaptureSessionPreset640x480

        // Default to Back Camera
        val camera = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        device = camera

        configureSessionInput(newSession, camera)

        val output = AVCaptureVideoDataOutput()
        output.setSampleBufferDelegate(collector, queue)
        // kCVPixelFormatType_32BGRA is good for CIImage
        output.videoSettings = mapOf(kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA)

        if (newSession.canAddOutput(output)) {
            newSession.addOutput(output)
            videoOutput = output

            // Fix Rotation: Set orientation to Portrait
            val connection = output.connectionWithMediaType(AVMediaTypeVideo)
            if (connection?.isVideoOrientationSupported() == true) {
                connection.videoOrientation = AVCaptureVideoOrientationPortrait
            }
            // Fix Mirroring for Front Camera if needed
            if (connection?.isVideoMirroringSupported() == true &&
                            camera.position == AVCaptureDevicePositionFront
            ) {
                connection.videoMirrored = true
            }
        }

        val photoOut = AVCapturePhotoOutput()
        if (newSession.canAddOutput(photoOut)) {
            newSession.addOutput(photoOut)
            photoOutput = photoOut
        }

        newSession.startRunning()
        session = newSession

        _cameraState.value =
                _cameraState.value.copy(
                        isStreaming = true,
                        currentLens = CameraLens.BACK,
                        maxZoom =
                                camera.activeFormat
                                        .videoMaxZoomFactor
                                        .toFloat()
                                        .coerceAtMost(5f) // Limit zoom
                )

        // Restore Zoom and Exposure
        setZoom(storedZoom)
        setExposure(storedExposureIndex)
    }

    private fun startMockCamera() {
        _cameraState.value = _cameraState.value.copy(isStreaming = true)
        CoroutineScope(Dispatchers.Default).launch {
            while (_cameraState.value.isStreaming) {
                val mockFrame = createMockFrame()
                if (mockFrame != null) {
                    _frameFlow.emit(mockFrame)
                }
                kotlinx.coroutines.delay(33) // ~30 FPS
            }
        }
    }

    private fun createMockFrame(): ByteArray? {
        // Create a simple colored image
        val width = 640.0
        val height = 480.0
        val rect = platform.CoreGraphics.CGRectMake(0.0, 0.0, width, height)

        platform.UIKit.UIGraphicsBeginImageContext(platform.CoreGraphics.CGSizeMake(width, height))
        val context = platform.UIKit.UIGraphicsGetCurrentContext()

        // Fill with changing color
        val time = platform.Foundation.NSDate.date().timeIntervalSince1970
        val red = (kotlin.math.sin(time) + 1) / 2.0
        val green = (kotlin.math.cos(time) + 1) / 2.0
        val blue = 0.5

        platform.UIKit.UIColor.colorWithRed(red, green, blue, 1.0).setFill()
        platform.UIKit.UIRectFill(rect)

        // Add Text
        val text = "Simulator Mock Camera"
        val attrs =
                mapOf<Any?, Any?>(
                        platform.UIKit.NSFontAttributeName to
                                platform.UIKit.UIFont.boldSystemFontOfSize(40.0),
                        platform.UIKit.NSForegroundColorAttributeName to
                                platform.UIKit.UIColor.whiteColor
                )
        val nsText = NSString.create(string = text)
        nsText.drawInRect(platform.CoreGraphics.CGRectMake(50.0, 200.0, 500.0, 100.0), attrs)

        val image = platform.UIKit.UIGraphicsGetImageFromCurrentImageContext()
        platform.UIKit.UIGraphicsEndImageContext()

        return if (image != null) {
            val data = platform.UIKit.UIImageJPEGRepresentation(image, 0.6)
            data?.toByteArray()
        } else {
            null
        }
    }

    private fun configureSessionInput(session: AVCaptureSession, camera: AVCaptureDevice) {
        val currentInput = input
        if (currentInput != null) {
            session.removeInput(currentInput)
        }

        val newInput = AVCaptureDeviceInput.deviceInputWithDevice(camera, null) ?: return
        if (session.canAddInput(newInput)) {
            session.addInput(newInput)
            input = newInput
        }
    }

    actual fun stopCamera() {
        session?.stopRunning()
        session = null
        input = null
        device = null
        _cameraState.value = _cameraState.value.copy(isStreaming = false)
    }

    actual fun togglePause() {
        val wasPaused = _cameraState.value.isPaused
        val isNowPaused = !wasPaused
        _cameraState.value = _cameraState.value.copy(isPaused = isNowPaused)

        if (isNowPaused) {
            val currentLens = _cameraState.value.currentLens
            if (currentLens == CameraLens.SCREEN) {
                if (screenRecorder.recording) {
                    screenRecorder.stopCaptureWithHandler(null)
                }
            } else {
                session?.stopRunning()
            }
        } else {
            val currentLens = _cameraState.value.currentLens
            if (currentLens == CameraLens.SCREEN) {
                startScreenCapture()
            } else {
                // Re-verify session validity if needed, but startRunning checks it
                session?.startRunning()
            }
        }
    }

    actual fun switchCamera(lens: CameraLens) {
        // Stop Screen Recorder if running
        if (screenRecorder.recording) {
            screenRecorder.stopCaptureWithHandler { error ->
                if (error != null)
                        println("CameraManager: Screen stop error: ${error?.localizedDescription}")
            }
        }

        val session = session

        if (lens == CameraLens.SCREEN) {
            session?.stopRunning()
            startScreenCapture()
            _cameraState.value = _cameraState.value.copy(currentLens = lens, isStreaming = true)
            return
        }

        // Ensure session is running if switching back to camera
        if (session != null && !session.running) {
            session.startRunning()
        }

        if (session != null) {
            // Real switching logic
            session.beginConfiguration()

            val position =
                    when (lens) {
                        CameraLens.FRONT -> AVCaptureDevicePositionFront
                        CameraLens.BACK -> AVCaptureDevicePositionBack // Wide
                        CameraLens.ULTRA_WIDE -> AVCaptureDevicePositionBack // Ultra Wide
                        CameraLens.TELEPHOTO -> AVCaptureDevicePositionBack // Telephoto
                        else -> AVCaptureDevicePositionBack
                    }

            // Find device specific to lens type
            val deviceType =
                    when (lens) {
                        CameraLens.ULTRA_WIDE -> AVCaptureDeviceTypeBuiltInUltraWideCamera
                        CameraLens.TELEPHOTO -> AVCaptureDeviceTypeBuiltInTelephotoCamera
                        else -> AVCaptureDeviceTypeBuiltInWideAngleCamera // Default
                    }

            // Find device
            val newDevice = getCamera(position, deviceType)
            if (newDevice != null) {
                configureSessionInput(session, newDevice)
                device = newDevice

                // Update Mirroring based on new lens
                val connection = videoOutput?.connectionWithMediaType(AVMediaTypeVideo)
                if (connection?.isVideoMirroringSupported() == true) {
                    connection.videoMirrored = (position == AVCaptureDevicePositionFront)
                }

                // Update State
                _cameraState.value =
                        _cameraState.value.copy(
                                currentLens = lens,
                                maxZoom =
                                        newDevice
                                                .activeFormat
                                                .videoMaxZoomFactor
                                                .toFloat()
                                                .coerceAtMost(5f),
                                zoomRatio = 1f
                        )

                // Reset stored zoom for new lens, but keep exposure?
                // Android impl resets zoom to 1.0 (or min for ultra) but keeps exposure.
                storedZoom = 1.0f // Reset zoom on lens switch
                // storedExposureIndex remains

                // Apply defaults
                setZoom(storedZoom)
                setExposure(storedExposureIndex)
            } else if (_cameraState.value.isStreaming) {
                // Mock Switch
                println("CameraManager: Simulating Switch Camera to $lens")
                _cameraState.value = _cameraState.value.copy(currentLens = lens, zoomRatio = 1f)
            }
        }

        session?.commitConfiguration()
    }

    private fun startScreenCapture() {
        if (!screenRecorder.available) {
            println("CameraManager: Screen recording not available")
            return
        }
        screenRecorder.startCaptureWithHandler({ buffer, type, error ->
            if (error != null) {
                println("CameraManager: Screen capture error: ${error.localizedDescription}")
                return@startCaptureWithHandler
            }
            if (type == RPSampleBufferTypeVideo && buffer != null) {
                val jpegData = convertToJpeg(buffer)
                jpegData?.let { data ->
                    val bytes = data.toByteArray()
                    CoroutineScope(Dispatchers.Default).launch { _frameFlow.emit(bytes) }
                }
            }
        }) { error ->
            if (error != null)
                    println("CameraManager: Screen start error: ${error.localizedDescription}")
            else println("CameraManager: Screen capture started")
        }
    }

    private fun getCamera(
            position: AVCaptureDevicePosition,
            type: AVCaptureDeviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera
    ): AVCaptureDevice? {
        val devices =
                AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
                                listOf(type),
                                AVMediaTypeVideo,
                                position
                        )
                        .devices
        return devices?.firstOrNull() as? AVCaptureDevice
    }

    actual fun setResolution(resolution: CameraResolution) {
        val session = session ?: return
        session.beginConfiguration()

        val preset =
                when (resolution) {
                    CameraResolution.SD_480P -> AVCaptureSessionPreset640x480
                    CameraResolution.HD_720P -> AVCaptureSessionPreset1280x720
                    CameraResolution.FHD_1080P -> AVCaptureSessionPreset1920x1080
                    CameraResolution.QHD_2K ->
                            AVCaptureSessionPreset3840x2160 // Closest to 2K/4K if supported
                    CameraResolution.DCI_2K ->
                            AVCaptureSessionPreset1920x1080 // Fallback to 1080p or upsample
                    CameraResolution.UHD_4K -> AVCaptureSessionPreset3840x2160
                    CameraResolution.DCI_4K -> AVCaptureSessionPreset3840x2160
                    CameraResolution.UHD_8K -> AVCaptureSessionPreset3840x2160 // Fallback to 4K
                }

        if (session.canSetSessionPreset(preset)) {
            session.sessionPreset = preset
            _cameraState.value = _cameraState.value.copy(currentResolution = resolution)
        }

        session.commitConfiguration()
    }

    actual fun setZoom(ratio: Float) {
        val device = device
        if (device != null) {
            try {
                if (device.lockForConfiguration(null)) {
                    device.videoZoomFactor =
                            ratio.toDouble()
                                    .coerceAtLeast(1.0)
                                    .coerceAtMost(device.activeFormat.videoMaxZoomFactor)
                    device.unlockForConfiguration()
                    _cameraState.value = _cameraState.value.copy(zoomRatio = ratio)
                    storedZoom = ratio
                }
            } catch (e: Exception) {
                println("CameraManager: setZoom error: $e")
            }
        } else if (_cameraState.value.isStreaming) {
            println("CameraManager: Simulating Set Zoom to $ratio")
            _cameraState.value = _cameraState.value.copy(zoomRatio = ratio)
        }
    }

    actual fun setExposure(index: Int) {
        val device = device
        if (device != null) {
            try {
                if (device.lockForConfiguration(null)) {
                    val bias = index.toFloat() * 0.5f
                    device.setExposureTargetBias(bias) { time -> }
                    device.unlockForConfiguration()
                    _cameraState.value = _cameraState.value.copy(exposureIndex = index)
                    storedExposureIndex = index
                }
            } catch (e: Exception) {}
        } else if (_cameraState.value.isStreaming) {
            println("CameraManager: Simulating Set Exposure to $index")
            _cameraState.value = _cameraState.value.copy(exposureIndex = index)
        }
    }

    actual fun setFps(fps: Int) {
        val device = device ?: return
        try {
            if (device.lockForConfiguration(null)) {
                val duration = CMTimeMake(1, fps)
                device.activeVideoMinFrameDuration = duration
                device.activeVideoMaxFrameDuration = duration
                device.unlockForConfiguration()
                _cameraState.value = _cameraState.value.copy(currentFps = fps)
            }
        } catch (e: Exception) {
            println("CameraManager: setFps error $e")
        }
    }

    actual fun setJpegQuality(quality: Int) {
        val q = quality.coerceIn(10, 100)
        _cameraState.value = _cameraState.value.copy(jpegQuality = q)
    }

    actual fun focusAt(x: Float, y: Float) {
        val device = device ?: return // Todo: Simulate focus point?
        try {
            if (device.lockForConfiguration(null)) {
                val point = CGPointMake(x.toDouble(), y.toDouble())
                if (device.isFocusPointOfInterestSupported()) {
                    device.focusPointOfInterest = point
                    device.focusMode = AVCaptureFocusModeAutoFocus
                }
                if (device.isExposurePointOfInterestSupported()) {
                    device.exposurePointOfInterest = point
                    device.exposureMode = AVCaptureExposureModeAutoExpose
                }
                device.unlockForConfiguration()
            }
        } catch (e: Exception) {}
    }

    actual fun takePicture() {
        val output = photoOutput ?: return
        val settings = AVCapturePhotoSettings.photoSettings()
        // settings.flashMode = ... (if needed)

        output.capturePhotoWithSettings(settings, photoDelegate)
    }

    actual suspend fun captureSnapshot(): ByteArray? =
            kotlin.coroutines.suspendCoroutine { cont ->
                val output = photoOutput
                if (output == null) {
                    cont.resumeWith(Result.success(null))
                    return@suspendCoroutine
                }

                val settings = AVCapturePhotoSettings.photoSettings()

                val delegate =
                        object : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
                            override fun captureOutput(
                                    output: AVCapturePhotoOutput,
                                    didFinishProcessingPhoto: AVCapturePhoto,
                                    error: NSError?
                            ) {
                                if (error != null) {
                                    println(
                                            "CameraManager: Snapshot error: ${error.localizedDescription}"
                                    )
                                    cont.resumeWith(Result.success(null))
                                    return
                                }
                                val data = didFinishProcessingPhoto.fileDataRepresentation()
                                val bytes = data?.toByteArray()
                                cont.resumeWith(Result.success(bytes))
                            }
                        }

                // We must artificially retain the delegate if using a one-off?
                // Actually AVCapturePhotoOutput retains it during the capture.
                output.capturePhotoWithSettings(settings, delegate)
            }

    actual fun setFlash(enabled: Boolean) {
        val device = device ?: return
        try {
            if (device.hasTorch && device.isTorchModeSupported(AVCaptureTorchModeOn)) {
                if (device.lockForConfiguration(null)) {
                    device.torchMode = if (enabled) AVCaptureTorchModeOn else AVCaptureTorchModeOff
                    device.unlockForConfiguration()
                    _cameraState.value = _cameraState.value.copy(isFlashEnabled = enabled)
                }
            }
        } catch (e: Exception) {
            println("CameraManager: setFlash error $e")
        }
    }

    actual fun setStreamMode(mode: StreamMode) {
        _cameraState.value = _cameraState.value.copy(streamMode = mode)
    }

    actual fun setContrast(value: Int) {
        _cameraState.value = _cameraState.value.copy(contrast = value)
    }

    actual fun setBrightness(value: Int) {
        _cameraState.value = _cameraState.value.copy(brightness = value)
    }

    actual fun setBokeh(enabled: Boolean) {
        // Basic state update
        _cameraState.value = _cameraState.value.copy(isBokehEnabled = enabled)
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
        // Stub for iOS
        _cameraState.value = _cameraState.value.copy(autoCloseRemaining = seconds)
    }

    // Helper to find all available devices names
    private fun updateAvailableDevices() {
        val discoverySession =
                AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
                        listOf(
                                AVCaptureDeviceTypeBuiltInWideAngleCamera,
                                AVCaptureDeviceTypeBuiltInTelephotoCamera,
                                AVCaptureDeviceTypeBuiltInUltraWideCamera
                        ),
                        AVMediaTypeVideo,
                        AVCaptureDevicePositionUnspecified
                )

        val devices = discoverySession.devices as? List<AVCaptureDevice> ?: emptyList()
        val lensNames =
                devices
                        .mapNotNull { dev ->
                            when (dev.position) {
                                AVCaptureDevicePositionBack -> {
                                    if (dev.deviceType == AVCaptureDeviceTypeBuiltInUltraWideCamera)
                                            "ULTRA_WIDE"
                                    else if (dev.deviceType ==
                                                    AVCaptureDeviceTypeBuiltInTelephotoCamera
                                    )
                                            "TELEPHOTO"
                                    else "BACK"
                                }
                                AVCaptureDevicePositionFront -> "FRONT"
                                else -> null
                            }
                        }
                        .distinct()
                        .toMutableList()
                        .apply { add("SCREEN") }

        _cameraState.value =
                _cameraState.value.copy(
                        availableLenses = lensNames,
                        availableResolutions =
                                CameraResolution.values()
                                        .toList(), // iOS generally supports all mapped presets
                        backMinZoom = if (lensNames.contains("ULTRA_WIDE")) 0.5f else 1.0f,
                        backMaxZoom = if (lensNames.contains("TELEPHOTO")) 2.0f else 1.0f
                )
    }

    private fun convertToJpeg(buffer: CMSampleBufferRef): NSData? {
        val imageBuffer = CMSampleBufferGetImageBuffer(buffer) ?: return null

        // CIImage from CVPixelBuffer
        var ciImage = CIImage.imageWithCVPixelBuffer(imageBuffer)

        // Apply Adjustments (Brightness / Contrast) using CoreImage
        val brightness = _cameraState.value.brightness
        val contrast = _cameraState.value.contrast

        if (brightness != 0 || contrast != 0) {
            val filter = CIFilter.filterWithName("CIColorControls")
            filter?.setValue(ciImage, forKey = kCIInputImageKey)

            // Map -100..100 to useful ranges
            // Brightness in CI: -1.0 to 1.0 (default 0). But standard photo manip usually small.
            // Let's try -0.5 to 0.5 for safer range? Or just direct linear map -1.0..1.0
            val bVal = brightness / 200.0 // Map +/-100 to +/-0.5
            filter?.setValue(bVal, forKey = kCIInputBrightnessKey)

            // Contrast in CI: 0.0 to 4.0 (default 1.0)
            // Map -100..100 -> 0.5 .. 1.5? Or 0.0 .. 2.0
            val cVal = 1.0 + (contrast / 100.0) // Map +/-100 to 0.0..2.0
            filter?.setValue(cVal, forKey = kCIInputContrastKey)

            val out = filter?.outputImage
            if (out != null) {
                ciImage = out
            }
        }

        // Render to JPEG
        // Quality 0.6 = 60%
        val options: Map<Any?, *> = mapOf(kCGImageDestinationLossyCompressionQuality to 0.6)

        /*
           Kotlin Native Interop for ColorSpace usually requires creating a CGColorSpace.
           For simplicity, we can let CIContext handle defaults or try to get null.
           jpegRepresentationOfImage added in iOS 10.
        */

        // Note: CIContext.jpegRepresentationOfImage returns NSData?
        // We'll use the default colorspace (null)
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val data = ciContext.JPEGRepresentationOfImage(ciImage, colorSpace, options)
        CGColorSpaceRelease(colorSpace)

        return data
    }

    // Helper to convert NSData to ByteArray
    private fun NSData.toByteArray(): ByteArray {
        val size = this.length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) {
            memScoped {
                val ptr = this@toByteArray.bytes
                val region = ptr?.readBytes(size)
                // If readBytes works directly, great.
                // Otherwise we need to loop copy.
                // Kotlin Native's readBytes(count) on CPointer<ByteVar> is the standard way.
                if (region != null) {
                    return region
                }
            }
        }
        return ByteArray(0)
    }
}
