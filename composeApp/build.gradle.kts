import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Firmware checkout holding the portable animation core (lib/Lightnet/Core/Panel) + its C ABI
// (lib/Lightnet/Core/CApi). Resolution order: -PlightnetFirmwareDir → third_party submodule → sibling.
val lightnetFirmwareDir: String = run {
    val explicit = project.findProperty("lightnetFirmwareDir") as String?
    val candidates = listOfNotNull(
        explicit,
        "$rootDir/third_party/lightnet-firmware",
        "$rootDir/../lightnet-firmware",
    )
    candidates.firstOrNull { file("$it/lib/Lightnet/Core/CApi/CMakeLists.txt").exists() } ?: candidates.first()
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
        // cinterop bindings for the animation core C ABI (animcore.*). The C++ object code is
        // linked from the `panel_core` and `controller_core` static libs built per-arch with
        // CMake — see composeApp/src/iosMain/README.md (Mac-only; finalize linking there).
        // Guarded to macOS so configuring it never affects the Android build on Windows.
        if (org.jetbrains.kotlin.konan.target.HostManager.hostIsMac) {
            iosTarget.compilations.getByName("main").cinterops.create("animcore") {
                defFile(project.file("src/nativeInterop/cinterop/animcore.def"))
                includeDirs(
                    "$lightnetFirmwareDir/lib/Lightnet/Core/CApi",
                    "$lightnetFirmwareDir/lib/Lightnet/Core/Panel",
                    "$lightnetFirmwareDir/lib/Lightnet/Core/Common",
                )
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.multiplatform.settings)
            implementation(compose.materialIconsExtended)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.lightnet"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.lightnet"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        externalNativeBuild {
            cmake {
                arguments("-DLIGHTNET_FIRMWARE_DIR=$lightnetFirmwareDir")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")  // real devices + emulator
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

