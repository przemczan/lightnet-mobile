---
icon: material/rocket-launch-outline
---

# Getting Started

## Repository

```bash
git clone https://github.com/przemczan/lightnet-mobile.git
cd lightnet-mobile
```

---

## Prerequisites

=== "Android"
    - Android SDK with API 24+ platform installed
    - A connected device or emulator

=== "iOS"
    - Xcode 14+ on macOS
    - A simulator or physical device

The project uses the Gradle wrapper (`gradlew.bat` / `./gradlew`) — no separate Gradle installation needed.

## Build Commands

=== "Windows"

    ```bat
    :: Build debug APK
    .\gradlew.bat :composeApp:assembleDebug

    :: Build and install on a connected Android device
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

    # Build and install on a connected Android device
    ./gradlew :composeApp:installDebug

    # Run all tests
    ./gradlew :composeApp:allTests

    # Clean build outputs
    ./gradlew clean
    ```

## Running the App

=== "Android"
    1. Connect an Android device (API 24+) or start an emulator
    2. Run `.\gradlew.bat :composeApp:installDebug`
    3. Launch the **Lightnet** app on the device

=== "iOS"
    1. Open `iosApp/iosApp.xcodeproj` in Xcode
    2. Run the `iosApp` scheme on a simulator or device
    3. The KMP shared module is compiled automatically by the Xcode build phase

## Demo Device

!!! tip "No hardware required"
    On the device discovery screen, tap **Demo Device** to connect to `MockConnector` — a self-contained fake controller that responds with properly encoded, CRC-correct protocol packets. All UI flows work without physical hardware.

---

- [Development](development.md) — Code structure and key conventions
- [Connectivity](connectivity.md) — Protocol details and device discovery
