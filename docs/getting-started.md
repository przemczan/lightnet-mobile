---
icon: material/rocket-launch-outline
---

# Getting Started

This page is the developer-side reference — clone, build, install, run, and test. For end-user documentation see the [Lightnet docs hub](https://github.com/przemczan/lightnet).

## Clone

```bash
git clone https://github.com/przemczan/lightnet-mobile.git
cd lightnet-mobile
```

The project uses the Gradle wrapper (`gradlew.bat` / `./gradlew`) — no separate Gradle install needed.

## Prerequisites

=== "Android"
    - Android SDK with **API 24+** platform installed
    - **Android NDK** (Android Studio → SDK Manager → SDK Tools → NDK)
    - **[lightnet-firmware](https://github.com/przemczan/lightnet-firmware)** checkout — sibling at `../lightnet-firmware`, or set `lightnetFirmwareDir` in a gitignored `local.properties` at the repo root (see `composeApp/src/androidMain/cpp/README.md`)
    - A connected device or an emulator

=== "iOS"
    - **Xcode 14+** on macOS
    - **lightnet-firmware** checkout + per-arch static lib build (see `composeApp/src/iosMain/README.md`)
    - A simulator or physical device

## Build commands

=== "Windows"

    ```bat
    :: Build debug APK
    .\gradlew.bat :composeApp:assembleDebug

    :: Build and install on the connected Android device
    .\gradlew.bat :composeApp:installDebug

    :: Run all tests (commonTest — protocol layer unit tests)
    .\gradlew.bat :composeApp:allTests

    :: Run a single test class
    .\gradlew.bat :composeApp:testDebugUnitTest --tests "com.lightnet.protocol.MessageProtocolTest"

    :: Clean build outputs
    .\gradlew.bat clean
    ```

=== "macOS / Linux"

    ```bash
    # Build debug APK
    ./gradlew :composeApp:assembleDebug

    # Build and install on the connected Android device
    ./gradlew :composeApp:installDebug

    # Run all tests (commonTest — protocol layer unit tests)
    ./gradlew :composeApp:allTests

    # Run a single test class
    ./gradlew :composeApp:testDebugUnitTest --tests "com.lightnet.protocol.MessageProtocolTest"

    # Clean build outputs
    ./gradlew clean
    ```

The debug APK is written to `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

## Run

=== "Android"
    1. Connect an Android device (API 24+) or start an emulator
    2. `./gradlew :composeApp:installDebug` (or `.\gradlew.bat` on Windows)
    3. Launch the **Lightnet** app on the device

    !!! note "mDNS on emulators"
        Device discovery via mDNS does not work reliably on Android emulators (multicast is blocked). Use a real device on the same Wi-Fi as the controller, or enable the demo device below.

=== "iOS"
    1. Open `iosApp/iosApp.xcodeproj` in Xcode
    2. Run the `iosApp` scheme on a simulator or device
    3. The KMP shared module compiles automatically as part of the Xcode build phase

## Demo device — develop without hardware

!!! tip "No controller required"
    Open **Settings** (gear icon on the device list), turn on **Enable demo device**, then tap the **Demo** entry at the top of your device list. Adjust the virtual panel count in Settings while the demo is enabled. It connects to `DemoConnector` — a self-contained fake controller that responds with properly encoded, CRC-correct protocol packets. Every UI flow exercises the full protocol path, including encode/decode, so it's also the recommended harness for testing domain logic.

---

- [Development](development.md) — code structure and key conventions
- [Connectivity](connectivity.md) — protocol details and device discovery
