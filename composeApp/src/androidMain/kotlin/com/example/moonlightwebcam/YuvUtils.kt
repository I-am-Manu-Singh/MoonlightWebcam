package com.example.moonlightwebcam

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

// Helper to convert ImageProxy to NV21 ByteArray for YuvImage
fun ImageProxy.toNv21(): ByteArray {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    // U and V are swapped
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    // Helper logic to handle strides would go here for robust implementation.
    // For this prototype we assume standard packing which works on many implementations.
    // If we see green artifacts, we fix the strides.
    return nv21
}

fun ImageProxy.toYuvImage(): YuvImage? {
    if (format != ImageFormat.YUV_420_888) return null
    val nv21 = yuv420888ToNv21(this)
    return YuvImage(nv21, ImageFormat.NV21, width, height, null)
}

// More robust conversion 
private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val width = image.width
    val height = image.height
    val ySize = width * height
    val uvSize = width * height / 2
    val nv21 = ByteArray(ySize + uvSize)

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    var rowStride = yPlane.rowStride
    var pixelStride = yPlane.pixelStride

    // Copy Y
    var pos = 0
    if (rowStride == width) {
        yBuffer.get(nv21, 0, ySize)
        pos = ySize
    } else {
        // Line by line
        for (row in 0 until height) {
            yBuffer.position(row * rowStride)
            yBuffer.get(nv21, pos, width)
            pos += width
        }
    }

    // Interleave U and V
    rowStride = uPlane.rowStride
    pixelStride = uPlane.pixelStride
    
    val uWidth = width / 2
    val uHeight = height / 2

    // For NV21 we want V then U interleaved (VU VU VU...) or standard NV21 is YYYYY... VUVUVU
    // NV21 is YUV 4:2:0 semi-planar with V as the first interleaved component.
    // So: V0 U0 V1 U1...
    
    val vBufferBytes = ByteArray(vBuffer.remaining())
    val uBufferBytes = ByteArray(uBuffer.remaining())
    vBuffer.get(vBufferBytes)
    uBuffer.get(uBufferBytes)

    for (row in 0 until uHeight) {
        for (col in 0 until uWidth) {
            val vIndex = row * rowStride + col * pixelStride
            val uIndex = row * rowStride + col * pixelStride
            // This is simplified; assumes U/V planes have same geometry
            
            // Check bounds just in case
            if (vIndex < vBufferBytes.size && uIndex < uBufferBytes.size) {
                nv21[pos++] = vBufferBytes[vIndex]
                nv21[pos++] = uBufferBytes[uIndex]
            }
        }
    }

    return nv21
}
