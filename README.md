# Lightnet Mobile

Android app (Kotlin Multiplatform / Compose Multiplatform) for visualising and controlling **Lightnet** light panel arrays. Panels are connected over Wi-Fi; the app communicates with the firmware via a custom binary protocol over WebSocket.

---

## Features

- **Panel visualiser** — renders the physical layout of all connected panels as a 2-D polygon canvas
- **Tap to toggle** — tap any panel to turn it on or off
- **Paint mode** — drag across the canvas to toggle every panel the finger enters in one stroke
- **Device discovery** — scans the local network for Lightnet devices via mDNS (Android NSD)
- **Saved devices** — persist favourite devices across app restarts
- **Demo mode** — a built-in simulated device so the full UI can be exercised without hardware
- **Auto-reconnect** — WebSocket connection retries with exponential back-off (1 s → 30 s cap) on unexpected drops

---

## How it works

### Communication

The app connects to each panel controller via WebSocket (`ws://<host>:<port>/ws`). Every message is a binary packet:

```
[type:u8][protocolVersion:u16LE][nonce:u32LE][headerCRC:u16LE][payloadCRC:u16LE][payloadSize:u16LE][payload…]
```

CRC-16 (polynomial `0xA001`, init `0xFFFF`) is computed separately over the 7-byte header and over the payload. The app validates both CRCs on every inbound message before processing it.

### Initialisation sequence

1. Connect → firmware responds; `SocketConnector` state transitions `IDLE → CONNECTING → CONNECTED`
2. Send `GET_EDGES_LIST` → firmware replies with the panel topology (which edge of which panel connects to which edge of which neighbour)
3. Build a panel tree from the edge list; compute 2-D polygon coordinates for every panel using `PanelsLayoutService`
4. Send `GET_PANELS_STATES` → firmware replies with current on/off, colour, and brightness for every panel
5. Render the visualiser; subsequent `PANELS_STATES` pushes keep the UI in sync

### Architecture

All business logic lives in `commonMain` (shared between Android and future iOS):

```
commonMain/
├── protocol/     Binary codec — ByteReader/ByteWriter, CRC, message types, MessageParser
├── device/       Domain layer — LightnetDevice, services, SocketConnector, MockConnector
├── model/        Domain models — PanelInfo, PanelLayout, EdgeCoords, PanelState
├── geometry/     GeometryUtils — ray-casting point-in-polygon (hit testing)
├── discovery/    ServiceDiscovery interface + DeviceRepository (multiplatform-settings)
└── ui/           Compose screens + LightnetDeviceVisualizer (Canvas)
```

Platform-specific code is minimal:
- `androidMain` — `NsdServiceDiscovery` (Android `NsdManager`), `MainActivity` (entry point)
- `iosMain` — `StubServiceDiscovery` placeholder, `MainViewController` (entry point)

### Key libraries

| Library | Purpose |
|---|---|
| Kotlin Multiplatform + CMP 1.10.x | Shared code and UI |
| Ktor 3.x (OkHttp engine on Android) | WebSocket client |
| kotlinx-coroutines / Flow | Reactive state — replaces RxJS from the original React Native app |
| multiplatform-settings | Persistent device list (SharedPreferences on Android, NSUserDefaults on iOS) |
| Android NsdManager | mDNS service discovery (no external library needed) |

---

## Build & install

### Prerequisites

- Android Studio or JDK 11+
- Android SDK (`sdk.dir` set in `local.properties`)
- A connected Android device with **USB Debugging** enabled, or an emulator

### Commands

```bat
:: Build debug APK
.\gradlew.bat :composeApp:assembleDebug

:: Build + install directly on connected device (recommended)
.\gradlew.bat :composeApp:installDebug

:: Run unit tests (protocol layer)
.\gradlew.bat :composeApp:allTests
```

The APK is written to `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

> **Note:** mDNS device discovery does not work reliably on Android emulators (multicast is blocked). Test discovery on a real device on the same Wi-Fi network as the panels.

---

## Project layout

```
lightnet-mobile/
├── composeApp/
│   └── src/
│       ├── commonMain/    ← all logic and UI (Kotlin + Compose Multiplatform)
│       ├── androidMain/   ← MainActivity, NsdServiceDiscovery
│       └── iosMain/       ← MainViewController, StubServiceDiscovery
├── iosApp/                ← Xcode project (iOS support in progress)
└── gradle/
    └── libs.versions.toml ← version catalog
```

---


## iOS

The project is structured for iOS from day one — all UI and business logic is in `commonMain`. The iOS entry point (`iosMain/MainViewController.kt`) is wired and compiles; service discovery uses a stub until the Bonjour implementation is added. Building for iOS requires macOS + Xcode.

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
