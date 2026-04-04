package com.example.moonlightwebcam

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

class IosStreamingServer(private val frameFlow: StateFlow<ByteArray?>) : StreamingServer {
    private var server: ApplicationEngine? = null

    private var serverJob: Job? = null

    override fun start() {
        if (serverJob?.isActive == true) return

        serverJob =
                CoroutineScope(Dispatchers.IO).launch {
                    println("IosStreamingServer: Starting on 8080")
                    try {
                        server =
                                embeddedServer(
                                                CIO,
                                                port = 8080,
                                                configure = { reuseAddress = true }
                                        ) {
                                            intercept(ApplicationCallPipeline.Plugins) {
                                                val pin = DependencyHolder.pin.value
                                                val requestPin =
                                                        call.request.queryParameters["pin"]
                                                                ?: call.request.headers[
                                                                        "X-Moonlight-PIN"]
                                                if (pin != requestPin) {
                                                    call.respond(
                                                            HttpStatusCode.Unauthorized,
                                                            "Invalid PIN"
                                                    )
                                                    finish()
                                                }
                                            }
                                            routing {
                                                get("/video") {
                                                    println(
                                                            "IosStreamingServer: Client connected to /video"
                                                    )
                                                    val flow = frameFlow
                                                    call.respond(
                                                            object :
                                                                    io.ktor.http.content.OutgoingContent.WriteChannelContent() {
                                                                override val contentType =
                                                                        ContentType.parse(
                                                                                "multipart/x-mixed-replace; boundary=--boundary"
                                                                        )

                                                                override suspend fun writeTo(
                                                                        channel: ByteWriteChannel
                                                                ) {
                                                                    try {
                                                                        val manager =
                                                                                DependencyHolder
                                                                                        .cameraManager
                                                                                        .value!!

                                                                        val host =
                                                                                call.request
                                                                                        .local
                                                                                        .remoteHost
                                                                        val isLocal =
                                                                                host ==
                                                                                        "127.0.0.1" ||
                                                                                        host ==
                                                                                                "localhost" ||
                                                                                        host ==
                                                                                                "::1"

                                                                        manager.setStreamingInterface(
                                                                                if (isLocal)
                                                                                        StreamingInterface
                                                                                                .USB
                                                                                else
                                                                                        StreamingInterface
                                                                                                .WIFI
                                                                        )

                                                                        val clientName =
                                                                                call.request
                                                                                        .queryParameters[
                                                                                        "clientName"]
                                                                                        ?: "Unknown Client"
                                                                        manager.setClientConnected(
                                                                                true,
                                                                                clientName
                                                                        )

                                                                        if (manager.cameraState
                                                                                        .value
                                                                                        .currentLens !=
                                                                                        CameraLens
                                                                                                .SCREEN
                                                                        ) {
                                                                            manager.startCamera()
                                                                        }

                                                                        flow.collectLatest { frame
                                                                            ->
                                                                            if (frame != null) {
                                                                                // ...
                                                                                channel.writeFully(
                                                                                        "--boundary\r\n".encodeToByteArray()
                                                                                )
                                                                                channel.writeFully(
                                                                                        "Content-Type: image/jpeg\r\n".encodeToByteArray()
                                                                                )
                                                                                channel.writeFully(
                                                                                        "Content-Length: ${frame.size}\r\n\r\n".encodeToByteArray()
                                                                                )
                                                                                channel.writeFully(
                                                                                        frame
                                                                                )
                                                                                channel.writeFully(
                                                                                        "\r\n".encodeToByteArray()
                                                                                )
                                                                                channel.flush()
                                                                            }
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        // Client disconnected
                                                                    } finally {
                                                                        val manager =
                                                                                DependencyHolder
                                                                                        .cameraManager
                                                                                        .value
                                                                        manager?.let {
                                                                            it.setClientConnected(
                                                                                    false
                                                                            )
                                                                            it.setStreamingInterface(
                                                                                    StreamingInterface
                                                                                            .NONE
                                                                            )
                                                                            it.stopCamera()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                    )
                                                }

                                                get("/audio") {
                                                    call.respondBytesWriter(
                                                            ContentType.parse("audio/wav")
                                                    ) {
                                                        try {
                                                            val sampleRate = 44100
                                                            val channels = 1
                                                            val bitsPerSample = 16
                                                            val byteRate =
                                                                    sampleRate *
                                                                            channels *
                                                                            bitsPerSample / 8
                                                            val blockAlign =
                                                                    channels * bitsPerSample / 8

                                                            writeStringUtf8("RIFF")
                                                            writeIntLittleEndian(0)
                                                            writeStringUtf8("WAVE")

                                                            writeStringUtf8("fmt ")
                                                            writeIntLittleEndian(16)
                                                            writeShortLittleEndian(1)
                                                            writeShortLittleEndian(
                                                                    channels.toShort()
                                                            )
                                                            writeIntLittleEndian(sampleRate)
                                                            writeIntLittleEndian(byteRate)
                                                            writeShortLittleEndian(
                                                                    blockAlign.toShort()
                                                            )
                                                            writeShortLittleEndian(
                                                                    bitsPerSample.toShort()
                                                            )

                                                            writeStringUtf8("data")
                                                            writeIntLittleEndian(0)

                                                            flush()

                                                            val audioManager =
                                                                    DependencyHolder.audioManager
                                                                            .value
                                                            audioManager?.startAudio()

                                                            try {
                                                                audioManager?.audioFlow?.collect {
                                                                        bytes ->
                                                                    if (bytes != null) {
                                                                        writePacket {
                                                                            writeFully(bytes)
                                                                        }
                                                                        flush()
                                                                    }
                                                                }
                                                            } finally {
                                                                // cleanup
                                                            }
                                                        } catch (e: Exception) {
                                                            println("Audio stream error: ${e}")
                                                        }
                                                    }
                                                }

                                                // STATUS API
                                                get("/status") {
                                                    val manager =
                                                            DependencyHolder.cameraManager.value
                                                    val state = manager?.cameraState?.value
                                                    val audioManager =
                                                            DependencyHolder.audioManager.value
                                                    val isAudioMuted =
                                                            audioManager?.isMuted?.value ?: false

                                                    if (state == null) {
                                                        call.respondText(
                                                                "{}",
                                                                ContentType.Application.Json
                                                        )
                                                        return@get
                                                    }

                                                    // Manual JSON for simplicity
                                                    val json =
                                                            """
                                                            {
                                                                "clientName": "${state.clientName ?: "Unknown Client"}",
                                                                "serverDeviceName": "${state.serverDeviceName ?: "iOS Device"}",
                                                                "isStreaming": ${state.isStreaming},
                                                                "isAudioMuted": $isAudioMuted,
                                                                "lens": "${state.currentLens.name}",
                                                                "resolution": "${state.currentResolution.name}",
                                                                "zoom": ${state.zoomRatio},
                                                                "maxZoom": ${state.maxZoom},
                                                                "minZoom": ${state.minZoom},
                                                                "exposure": ${state.exposureIndex},
                                                                "minExposure": ${state.minExposure},
                                                                "maxExposure": ${state.maxExposure},
                                                                "isBokehEnabled": ${state.isBokehEnabled},
                                                                "isFlashEnabled": ${state.isFlashEnabled},
                                                                "contrast": ${state.contrast},
                                                                "brightness": ${state.brightness},
                                                                "backMinZoom": ${state.backMinZoom},
                                                                "backMaxZoom": ${state.backMaxZoom},
                                                                "availableLenses": [${state.availableLenses.joinToString(",") { "\"$it\"" }}],
                                                                "availableResolutions": [${state.availableResolutions.joinToString(",") { "\"${it.name}\"" }}]
                                                            }
                                                            """.trimIndent()
                                                    call.respondText(
                                                            json,
                                                            ContentType.Application.Json
                                                    )
                                                }

                                                get("/snapshot") {
                                                    val manager =
                                                            DependencyHolder.cameraManager.value
                                                    if (manager == null) {
                                                        call.respond(
                                                                HttpStatusCode.ServiceUnavailable
                                                        )
                                                        return@get
                                                    }
                                                    // Explicit non-null assertion
                                                    val bytes = manager!!.captureSnapshot()
                                                    if (bytes != null) {
                                                        call.respondBytes(
                                                                bytes,
                                                                ContentType.Image.JPEG
                                                        )
                                                    } else {
                                                        call.respond(
                                                                HttpStatusCode.InternalServerError,
                                                                "Capture failed"
                                                        )
                                                    }
                                                }

                                                // CONTROL API
                                                post("/control") {
                                                    val manager =
                                                            DependencyHolder.cameraManager.value
                                                    if (manager == null) {
                                                        call.respond(
                                                                HttpStatusCode.ServiceUnavailable
                                                        )
                                                        return@post
                                                    }

                                                    // Explicit cast to ensure resolution
                                                    val m = manager!!

                                                    val params = call.request.queryParameters

                                                    params["muteAudio"]?.toBooleanStrictOrNull()
                                                            ?.let {
                                                                DependencyHolder.audioManager.value
                                                                        ?.setMute(it)
                                                            }

                                                    params["zoom"]?.toFloatOrNull()?.let {
                                                        m.setZoom(it)
                                                    }

                                                    params["exposure"]?.toIntOrNull()?.let {
                                                        m.setExposure(it)
                                                    }
                                                    params["contrast"]?.toIntOrNull()?.let {
                                                        m.setContrast(it)
                                                    }
                                                    params["brightness"]?.toIntOrNull()?.let {
                                                        m.setBrightness(it)
                                                    }

                                                    params["bokeh"]?.toBooleanStrictOrNull()?.let {
                                                        m.setBokeh(it)
                                                    }

                                                    params["lens"]?.let { lensName ->
                                                        try {
                                                            val lens = CameraLens.valueOf(lensName)
                                                            m.switchCamera(lens)
                                                        } catch (e: Exception) {
                                                            println(
                                                                    "IosStreamServer: Invalid lens: $lensName"
                                                            )
                                                        }
                                                    }

                                                    params["resolution"]?.let { resName ->
                                                        try {
                                                            val res =
                                                                    CameraResolution.valueOf(
                                                                            resName
                                                                    )
                                                            m.setResolution(res)
                                                        } catch (e: Exception) {
                                                            println(
                                                                    "IosStreamServer: Invalid resolution: $resName"
                                                            )
                                                        }
                                                    }

                                                    params["fps"]?.toIntOrNull()?.let {
                                                        m.setFps(it)
                                                    }
                                                    params["quality"]?.toIntOrNull()?.let {
                                                        m.setJpegQuality(it)
                                                    }
                                                    params["flash"]?.toBooleanStrictOrNull()?.let {
                                                        m.setFlash(it)
                                                    }

                                                    val fx = params["focusX"]?.toFloatOrNull()
                                                    val fy = params["focusY"]?.toFloatOrNull()
                                                    if (fx != null && fy != null) {
                                                        m.focusAt(fx, fy)
                                                    }

                                                    // Input Injection Stub (Not supported on iOS
                                                    // via Accessibility Service in the same way)
                                                    val inputType = params["inputType"]
                                                    if (inputType != null) {
                                                        println(
                                                                "IosStreamingServer: Input injection requested ($inputType) - not supported on iOS"
                                                        )
                                                    }

                                                    call.respond(HttpStatusCode.OK)
                                                }

                                                get("/") {
                                                    val html =
                                                            """
                                                        <!DOCTYPE html>
                                                        <html>
                                                        <head>
                                                            <title>MoonlightWebcam iOS</title>
                                                            <meta name="viewport" content="width=device-width, initial-scale=1">
                                                            <style>
                                                                body { margin: 0; background: #000; color: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; font-family: sans-serif; }
                                                                img { max-width: 100%; max-height: 80vh; object-fit: contain; }
                                                                .controls { margin-top: 20px; text-align: center; }
                                                            </style>
                                                        </head>
                                                        <body>
                                                            <img src="/video" alt="Webcam Stream" />
                                                            <audio id="audioPlayer" controls autoplay>
                                                                <source src="/audio" type="audio/wav">
                                                            </audio>
                                                        </body>
                                                        </html>
                                                    """.trimIndent()
                                                    call.respondText(html, ContentType.Text.Html)
                                                }
                                            }
                                        }
                                        .start(wait = true)
                    } catch (e: Exception) {
                        println("IosStreamingServer: Failed to start server: $e")
                        e.printStackTrace()
                    }
                }

        // Countdown Loop
        CoroutineScope(Dispatchers.Default).launch {
            println("IosStreamingServer: Starting auto-close countdown (120s)...")
            for (i in 120 downTo 1) {
                val manager = DependencyHolder.cameraManager.value
                if (manager?.cameraState?.value?.isClientConnected == true) {
                    println("IosStreamingServer: Client connected! Stopping countdown.")
                    manager.setAutoCloseRemaining(null)
                    break
                }
                manager?.setAutoCloseRemaining(i)
                delay(1000)
            }
            // Clear countdown
            val manager = DependencyHolder.cameraManager.value
            manager?.setAutoCloseRemaining(null)

            if (manager?.cameraState?.value?.isClientConnected != true) {
                println("IosStreamingServer: No client connected within 120s. Shutting down.")
                manager?.stopCamera()
                stop()
            }
        }
    }

    override fun stop() {
        serverJob?.cancel()
        // Run blocking stop if needed, or fire and forget
        // CIO stop is blocking, so we should be careful calling it on main thread if that's where
        // stop() is called.
        // But normally stop() is called on disposal.
        try {
            server?.stop(100, 1000)
        } catch (e: Exception) {
            // Ignore
        }
        server = null
        serverJob = null
    }
}

private suspend fun io.ktor.utils.io.ByteWriteChannel.writeIntLittleEndian(value: Int) {
    writeByte((value and 0xFF).toByte())
    writeByte(((value shr 8) and 0xFF).toByte())
    writeByte(((value shr 16) and 0xFF).toByte())
    writeByte(((value shr 24) and 0xFF).toByte())
}

private suspend fun io.ktor.utils.io.ByteWriteChannel.writeShortLittleEndian(value: Short) {
    writeByte((value.toInt() and 0xFF).toByte())
    writeByte(((value.toInt() shr 8) and 0xFF).toByte())
}
