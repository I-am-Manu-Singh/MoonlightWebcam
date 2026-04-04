package com.example.moonlightwebcam

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.engine.stop
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AndroidStreamingServer(private val frameFlow: StateFlow<ByteArray?>) : StreamingServer {
    private var server: ApplicationEngine? = null
    private var countdownJob: kotlinx.coroutines.Job? = null

    override fun start() {
        if (server != null) return

        try {
            // Attempt to bind
            bindServer()
        } catch (e: Exception) {
            // If bind fails (likely address in use), wait and retry once
            android.util.Log.e("StreamServer", "Bind failed, retrying in 500ms: ${e.message}")
            try {
                Thread.sleep(500)
                bindServer()
            } catch (retryEx: Exception) {
                android.util.Log.e("StreamServer", "Retry failed: ${retryEx.message}", retryEx)
                server = null
            }
        }
    }

    override fun stop() {
        try {
            server?.stop(100, 100)
            server = null
            countdownJob?.cancel()
            countdownJob = null
            DependencyHolder.cameraManager.value?.stopCamera()
        } catch (e: Exception) {
            android.util.Log.e("StreamServer", "Error stopping server", e)
        }
    }

    private fun bindServer() {
        val newServer =
                embeddedServer(CIO, port = 8080) {
                    intercept(ApplicationCallPipeline.Plugins) {
                        val pin = DependencyHolder.pin.value
                        val requestPin =
                                call.request.queryParameters["pin"]
                                        ?: call.request.headers["X-Moonlight-PIN"]

                        // Pass if PIN matches OR if it's the root path (optional, maybe secure root
                        // too?)
                        // Let's secure everything except maybe a landing page if we had one.
                        // For now, secure EVERYTHING.
                        if (pin != requestPin) {
                            android.util.Log.w(
                                    "StreamServer",
                                    "PIN Mismatch! Expected: $pin, Received: $requestPin"
                            )
                            call.respond(HttpStatusCode.Unauthorized, "Invalid PIN")
                            finish()
                        }
                    }

                    routing {
                        get("/video") {
                            android.util.Log.d(
                                    "StreamServer",
                                    "New client connected: ${call.request.local.remoteHost}"
                            )

                            call.respondBytesWriter(
                                    ContentType.parse(
                                            "multipart/x-mixed-replace; boundary=moonlight"
                                    )
                            ) {
                                val manager = DependencyHolder.cameraManager.value

                                try {
                                    if (manager == null) {
                                        android.util.Log.e("StreamServer", "CameraManager is NULL!")
                                        return@respondBytesWriter
                                    }

                                    val host = call.request.local.remoteHost
                                    val isLocal =
                                            host == "127.0.0.1" ||
                                                    host == "localhost" ||
                                                    host == "::1"

                                    // 1️⃣ Mark connection state
                                    manager.setStreamingInterface(
                                            if (isLocal) StreamingInterface.USB
                                            else StreamingInterface.WIFI
                                    )
                                    val clientName =
                                            call.request.queryParameters["clientName"]
                                                    ?: "Unknown Client"
                                    manager.setClientConnected(true, clientName)

                                    android.util.Log.d(
                                            "StreamServer",
                                            "Client connected ($clientName) via ${if (isLocal) "USB" else "WIFI"} ($host)"
                                    )

                                    if (manager.cameraState.value.currentLens != CameraLens.SCREEN
                                    ) {
                                        manager.startCamera()
                                    } else {
                                        android.util.Log.d(
                                                "StreamServer",
                                                "Skipping startCamera (In Screen Mode)"
                                        )
                                    }

                                    // 3️⃣ Stream frames
                                    frameFlow.collect { frame ->
                                        if (frame != null) {
                                            writeStringUtf8("--moonlight\r\n")
                                            writeStringUtf8("Content-Type: image/jpeg\r\n")
                                            writeStringUtf8("Content-Length: ${frame.size}\r\n\r\n")
                                            writePacket { writeFully(frame) }
                                            writeStringUtf8("\r\n")
                                            flush()
                                        }
                                    }
                                } catch (ioe: java.io.IOException) {
                                    // Broken pipe is expected when client disconnects
                                    android.util.Log.i(
                                            "StreamServer",
                                            "Client disconnected (IO): ${ioe.message}"
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e(
                                            "StreamServer",
                                            "Stream loop error: ${e.message}",
                                            e
                                    )
                                } finally {
                                    // 4️⃣ HARD CLEANUP (already correct, just keep it)
                                    manager?.setClientConnected(false)
                                    manager?.setStreamingInterface(StreamingInterface.NONE)
                                    manager?.stopCamera()

                                    android.util.Log.d(
                                            "StreamServer",
                                            "Client disconnected, camera stopped"
                                    )
                                }
                            }
                        }

                        get("/audio") {
                            call.respondBytesWriter(ContentType.parse("audio/wav")) {
                                val audioManager = DependencyHolder.audioManager.value
                                val manager = DependencyHolder.cameraManager.value

                                try {
                                    android.util.Log.d(
                                            "StreamServer",
                                            "New audio client connecting..."
                                    )
                                    // Mark client connected to prevent auto-shutdown
                                    manager?.setClientConnected(true)

                                    // WAV Header
                                    val sampleRate = 44100
                                    val channels = 1
                                    val bitsPerSample = 16
                                    val byteRate = sampleRate * channels * bitsPerSample / 8
                                    val blockAlign = channels * bitsPerSample / 8

                                    writeStringUtf8("RIFF")
                                    writeIntLittleEndian(Int.MAX_VALUE - 8)
                                    writeStringUtf8("WAVE")
                                    writeStringUtf8("fmt ")
                                    writeIntLittleEndian(16)
                                    writeShortLittleEndian(1)
                                    writeShortLittleEndian(channels.toShort())
                                    writeIntLittleEndian(sampleRate)
                                    writeIntLittleEndian(byteRate)
                                    writeShortLittleEndian(blockAlign.toShort())
                                    writeShortLittleEndian(bitsPerSample.toShort())
                                    writeStringUtf8("data")
                                    writeIntLittleEndian(Int.MAX_VALUE)

                                    flush()

                                    audioManager?.startAudio()
                                    android.util.Log.d(
                                            "StreamServer",
                                            "Audio stream loop starting..."
                                    )

                                    audioManager?.audioFlow?.collect { bytes ->
                                        if (bytes != null) {
                                            writePacket { writeFully(bytes) }
                                            flush()
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e(
                                            "StreamServer",
                                            "Audio error: ${e.message}",
                                            e
                                    )
                                } finally {
                                    android.util.Log.d("StreamServer", "Audio client disconnected.")
                                    audioManager?.stopAudio()
                                }
                            }
                        }

                        // STATUS API
                        get("/status") {
                            val manager = DependencyHolder.cameraManager.value
                            val state = manager?.cameraState?.value
                            val audioManager = DependencyHolder.audioManager.value
                            val isAudioMuted = audioManager?.isMuted?.value ?: false

                            if (state == null) {
                                call.respondText("{}", ContentType.Application.Json)
                                return@get
                            }
                            // Manual JSON for simplicity
                            val json =
                                    """
                             {
                                 "clientName": "${state.clientName ?: "Unknown Client"}",
                                 "serverDeviceName": "${state.serverDeviceName ?: "Android Device"}",
                                 "isStreaming": ${state.isStreaming},
                                 "streamMode": "${state.streamMode}",
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
                                 "availableResolutions": [${
                                state.availableResolutions.joinToString(
                                    ","
                                ) { "\"${it.name}\"" }
                            }],
                                 "currentFps": ${state.currentFps},
                                 "availableFps": [${state.availableFps.joinToString(",")}],
                                 "jpegQuality": ${state.jpegQuality}
                             }
                             """.trimIndent()
                            call.respondText(json, ContentType.Application.Json)
                        }

                        get("/snapshot") {
                            val manager = DependencyHolder.cameraManager.value
                            if (manager == null) {
                                call.respond(HttpStatusCode.ServiceUnavailable)
                                return@get
                            }
                            val bytes = manager.captureSnapshot()
                            if (bytes != null) {
                                call.respondBytes(bytes, ContentType.Image.JPEG)
                            } else {
                                call.respond(HttpStatusCode.InternalServerError, "Capture failed")
                            }
                        }

                        // CONTROL API
                        post("/control") {
                            val manager = DependencyHolder.cameraManager.value
                            if (manager == null) {
                                call.respond(HttpStatusCode.ServiceUnavailable)
                                return@post
                            }

                            val params = call.request.queryParameters

                            params["muteAudio"]?.toBooleanStrictOrNull()?.let {
                                DependencyHolder.audioManager.value?.setMute(it)
                            }

                            params["zoom"]?.toFloatOrNull()?.let { manager.setZoom(it) }
                            params["fps"]?.toIntOrNull()?.let { manager.setFps(it) }
                            params["quality"]?.toIntOrNull()?.let { manager.setJpegQuality(it) }

                            params["exposure"]?.toIntOrNull()?.let { manager.setExposure(it) }
                            params["contrast"]?.toIntOrNull()?.let { manager.setContrast(it) }
                            params["brightness"]?.toIntOrNull()?.let { manager.setBrightness(it) }

                            params["bokeh"]?.toBooleanStrictOrNull()?.let { manager.setBokeh(it) }

                            params["flash"]?.toBooleanStrictOrNull()?.let { manager.setFlash(it) }

                            if (params["action"] == "takePicture") {
                                manager.takePicture()
                            }

                            params["lens"]?.let { lensName ->
                                try {
                                    val lens = CameraLens.valueOf(lensName)
                                    manager.switchCamera(lens)
                                } catch (e: Exception) {
                                    android.util.Log.e("StreamServer", "Invalid lens: $lensName")
                                }
                            }

                            params["resolution"]?.let { resName ->
                                try {
                                    val res = CameraResolution.valueOf(resName)
                                    manager.setResolution(res)
                                } catch (e: Exception) {
                                    android.util.Log.e(
                                            "StreamServer",
                                            "Invalid resolution: $resName"
                                    )
                                }
                            }

                            // 1. Focus Logic (Camera Mode)
                            val fx = params["focusX"]?.toFloatOrNull()
                            val fy = params["focusY"]?.toFloatOrNull()
                            if (fx != null && fy != null) {
                                // Only apply focus if we have coordinates
                                // (Input Injection allows simulated taps too, but usually separate)
                            }

                            // 2. Input Injection (Screen Cast Mode)
                            val inputType = params["inputType"]
                            if (inputType == "mouse") {
                                val action = params["action"]
                                val x = params["x"]?.toFloatOrNull()
                                val y = params["y"]?.toFloatOrNull()

                                android.util.Log.d(
                                        "StreamServer",
                                        "Input Injection Request: Mouse Action=$action X=$x Y=$y"
                                )

                                if (x != null && y != null && action == "tap") {
                                    com.example.moonlightwebcam.InputInjector.perfomTap(x, y)
                                } else {
                                    android.util.Log.w("StreamServer", "Invalid Mouse Params")
                                }
                            } else if (inputType == "text") {
                                val text = params["text"]
                                android.util.Log.d(
                                        "StreamServer",
                                        "Input Injection Request: Text='$text'"
                                )
                                if (text != null) {
                                    com.example.moonlightwebcam.InputInjector.injectText(text)
                                }
                            } else if (inputType == "global") {
                                // Future: HOME, BACK, RECENT
                            }

                            // 3. Independent Focus triggering if valid
                            if (fx != null && fy != null) {
                                // We don't have a direct 'focusAt' on Manager yet that takes 0..1,
                                // usually Manager handles it via tap or auto.
                                // But checking if we need to call something here.
                                // The original code didn't actually CALL focus!
                                // It just checked if (fx != null) and then had the Input block
                                // inside.
                                // Wait, looking at original code:
                                // It checked fx, fy.
                                // Then checked inputType.
                                // It did NOT call manager.focus(...)!
                                // So Camera Focus from Desktop wasn't working either?
                                // Ah, StreamView sends "focusX/Y".
                                // But Server didn't use them?
                                // Let's check if manager has a method.
                                manager.focusAt(fx, fy)
                            }

                            call.respond(HttpStatusCode.OK)
                        }

                        get("/") {
                            // Serve a simple HTML player
                            val html =
                                    """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <title>MoonlightWebcam</title>
                                <meta name="viewport" content="width=device-width, initial-scale=1">
                                <style>
                                    body { margin: 0; background: #000; color: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; font-family: sans-serif; }
                                    img { max-width: 100%; max-height: 80vh; object-fit: contain; }
                                    .controls { margin-top: 20px; text-align: center; }
                                    button { padding: 10px 20px; font-size: 16px; cursor: pointer; background: #333; color: white; border: 1px solid #555; border-radius: 4px; }
                                </style>
                            </head>
                            <body>
                                <img src="/video" alt="Webcam Stream" />
                                <audio id="audioPlayer" controls autoplay>
                                    <source src="/audio" type="audio/wav">
                                </audio>
                                <div class="controls">
                                    <p>Tap 'Play' on the audio player if it doesn't start automatically.</p>
                                </div>
                                <script>
                                    const audio = document.getElementById('audioPlayer');
                                    // Attempt auto-play on load
                                    window.onload = () => {
                                        audio.play().catch(e => console.log("Autoplay blocked:", e));
                                    };
                                </script>
                            </body>
                            </html>
                        """.trimIndent()
                            call.respondText(html, ContentType.Text.Html)
                        }
                    }
                }

        server = newServer
        newServer.start(wait = false)

        android.util.Log.d("StreamServer", "Server started on port 8080")
        // Cancel any existing countdown job to prevent overlaps
        countdownJob?.cancel()
        countdownJob =
                CoroutineScope(Dispatchers.Default).launch {
                    // Countdown Loop
                    android.util.Log.d("StreamServer", "Starting auto-close countdown (120s)...")
                    for (i in 120 downTo 1) {
                        if (DependencyHolder.cameraManager
                                        .value
                                        ?.cameraState
                                        ?.value
                                        ?.isClientConnected == true
                        ) {
                            android.util.Log.d(
                                    "StreamServer",
                                    "Client connected! Stopping countdown."
                            )
                            DependencyHolder.cameraManager.value?.setAutoCloseRemaining(null)
                            break
                        }
                        DependencyHolder.cameraManager.value?.setAutoCloseRemaining(i)
                        delay(1000)
                    }
                    // Clear countdown
                    DependencyHolder.cameraManager.value?.setAutoCloseRemaining(null)

                    val manager = DependencyHolder.cameraManager.value
                    if (manager?.cameraState?.value?.isClientConnected != true) {
                        android.util.Log.w(
                                "StreamServer",
                                "No client connected within 120s. Shutting down server to save power."
                        )

                        manager?.stopCamera()
                        stop()
                    }
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
}
