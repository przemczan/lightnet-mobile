---
icon: material/rocket-launch-outline
---

# Getting Started

This page is the developer-side reference — clone, build, install, run, and test. For a higher-level user walkthrough see the hub's **[Get Started → Use the app](../getting-started/using-the-app.md)**.

## Clone

```bash
git clone https://github.com/przemczan/lightnet-mobile.git
cd lightnet-mobile
```

The project uses the Gradle wrapper (`gradlew.bat` / `./gradlew`) — no separate Gradle install needed.

## Prerequisites

=== "Android"
    - Android SDK with **API 24+** platform installed
    - A connected device or an emulator running

=== "iOS"
    - **Xcode 14+** on macOS
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

    # Run all tests
    ./gradlew :composeApp:allTests

    # Clean build outputs
    ./gradlew clean
    ```

## Run

=== "Android"
    1. Connect an Android device (API 24+) or start an emulator
    2. `./gradlew :composeApp:installDebug`
    3. Launch the **Lightnet** app on the device

=== "iOS"
    1. Open `iosApp/iosApp.xcodeproj` in Xcode
    2. Run the `iosApp` scheme on a simulator or device
    3. The KMP shared module compiles automatically as part of the Xcode build phase

## Demo device — develop without hardware

!!! tip "No controller required"
    On the device discovery screen, tap **Demo Device** to connect to `DemoConnector` — a self-contained fake controller that responds with properly encoded, CRC-correct protocol packets. Every UI flow exercises the full protocol path, including encode/decode, so it's also the recommended harness for testing domain logic.

---

- [Development](development.md) — code structure and key conventions
- [Connectivity](connectivity.md) — protocol details and device discovery
