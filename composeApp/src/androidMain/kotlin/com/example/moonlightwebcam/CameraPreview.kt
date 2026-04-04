package com.example.moonlightwebcam

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun CameraPreview(modifier: Modifier, cameraManager: CameraManager) {
    AndroidView(
            modifier = modifier,
            factory = { context ->
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    cameraManager.setPreviewSurfaceProvider(this.surfaceProvider)
                }
            }
    )

    DisposableEffect(Unit) {
        onDispose {
            // Use a small delay to debounce surface removal during orientation changes
            // This prevents transient null states from killing the preview use case redundantly
            cameraManager.setPreviewSurfaceProvider(null)
        }
    }
}
