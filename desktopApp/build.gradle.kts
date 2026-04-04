import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.compose.material.icons.extended)
                implementation(libs.jmdns)
            }
        }
    }
}
compose.desktop {
    application {
        mainClass = "com.example.moonlightwebcam.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb
            )

            packageName = "MoonlightWebcam"
            packageVersion = "1.0.0"

            // ── macOS ─────────────────────────────────────
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/moonlight_logo.icns"))
                bundleID = "com.moonlight.webcam"
                packageName = "MoonlightWebcam"   // THIS controls the name
                appCategory = "public.app-category.video"
            }
            // ── Windows ───────────────────────────────────
            windows {
                iconFile.set(
                    project.file("src/desktopMain/resources/moonlight_logo.ico")
                )

                menuGroup = "MoonlightWebcam"
                upgradeUuid = "12345678-1234-5678-1234-567812345678"
            }

            // ── Linux ─────────────────────────────────────
            linux {
                iconFile.set(
                    project.file("src/desktopMain/resources/moonlight_logo.png")
                )

                packageName = "moonlightwebcam"
                debMaintainer = "you@example.com"
                shortcut = true
            }
        }
    }
}