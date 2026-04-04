package com.example.moonlightwebcam

enum class CameraLens {
        FRONT,
        BACK,
        ULTRA_WIDE,
        TELEPHOTO,
        SCREEN
}

enum class CameraResolution(val width: Int, val height: Int, val label: String) {
        SD_480P(640, 480, "480p"),
        HD_720P(1280, 720, "720p"),
        FHD_1080P(1920, 1080, "1080p"),
        QHD_2K(2560, 1440, "2K"),
        DCI_2K(2048, 1080, "DCI 2K"),
        UHD_4K(3840, 2160, "4K"),
        DCI_4K(4096, 2160, "DCI 4K"),
        UHD_8K(7680, 4320, "8K")
}

enum class StreamingInterface {
        NONE,
        WIFI,
        USB
}

enum class StreamMode {
        CAMERA,
        STREAM
}

data class CameraState(
        val isStreaming: Boolean = false,
        val isPaused: Boolean = false,
        val currentLens: CameraLens = CameraLens.BACK,
        val availableLenses: List<String> = listOf("BACK", "FRONT"),
        val currentResolution: CameraResolution = CameraResolution.HD_720P,
        val availableResolutions: List<CameraResolution> =
                listOf(
                        CameraResolution.SD_480P,
                        CameraResolution.HD_720P,
                        CameraResolution.FHD_1080P
                ),
        val zoomRatio: Float = 1f,
        val maxZoom: Float = 1f,
        val minZoom: Float = 1f,
        val backMaxZoom: Float = 1f,
        val backMinZoom: Float = 1f,
        val exposureIndex: Int = 0,
        val minExposure: Int = 0,
        val maxExposure: Int = 0,
        val isBokehEnabled: Boolean = false,
        val isFlashEnabled: Boolean = false,
        val isClientConnected: Boolean = false,
        val clientName: String? = null,
        val serverDeviceName: String? = null,
        val streamingInterface: StreamingInterface = StreamingInterface.NONE,
        val autoCloseRemaining: Int? = null,
        val linkSpeedMbps: Int? = null,
        val isProjecting: Boolean = false,
        val currentFps: Int = 30,
        val availableFps: List<Int> = listOf(24, 30, 60),
        val jpegQuality: Int = 60,
        val streamMode: StreamMode = StreamMode.STREAM,
        val contrast: Int = 0,
        val brightness: Int = 0
)
