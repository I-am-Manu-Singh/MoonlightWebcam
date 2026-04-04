package com.example.moonlightwebcam

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
actual fun CameraPreview(modifier: Modifier, cameraManager: CameraManager) {
    val frame by cameraManager.frameFlow.collectAsState()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (frame != null) {
            val imageBitmap =
                    remember(frame) {
                        try {
                            org.jetbrains.skia.Image.makeFromEncoded(frame!!).toComposeImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }
            if (imageBitmap != null) {
                androidx.compose.foundation.Image(
                        bitmap = imageBitmap,
                        contentDescription = "Camera Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
        } else {
            // Loading or Paused state
            androidx.compose.material.CircularProgressIndicator(color = Color.White)
        }
    }
}
