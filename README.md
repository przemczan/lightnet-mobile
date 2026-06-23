# Lightnet Mobile

Android and iOS app (Kotlin Multiplatform / Compose Multiplatform) for visualising and controlling **Lightnet** light panel arrays. The app talks to a controller over HTTP and a binary WebSocket — it never speaks to panels directly.

---

## Features

- **Panel visualiser** — renders the physical layout of all connected panels as a 2-D polygon canvas
- **Tap to toggle** — tap any panel to turn it on or off
- **Paint mode** — drag across the canvas to toggle every panel the finger enters in one stroke
- **Live preview** — opt-in `MIRROR_BATCH` stream replays outbound I²C packets for real-time colours
- **Scenes & palettes** — browse, edit, and play scenes; manage palettes on the controller
- **Device discovery** — scans the local network for Lightnet controllers via mDNS (Android NSD)
- **Demo device** — built-in simulated controller (`DemoConnector`); enable in **Settings** for development without hardware
- **Auto-reconnect** — WebSocket connection retries with exponential back-off (1 s → 30 s cap) on unexpected drops

---

## How it works

### Communication

The app connects to each controller via WebSocket (`ws://<host>:<port>/ws`). Every message is a binary packet:

```
[type:u8][protocolVersion:u16LE][nonce:u32LE][headerCRC:u16LE][payloadCRC:u16LE][payloadSize:u16LE][payload…]
```

CRC-16/IBM (reflected, poly `0xA001`, init `0xFFFF`) is computed separately over the 7-byte header and over the payload. The app validates both CRCs on every inbound message before processing it. REST calls go through `LightnetHttpClient` on port 80.

### Initialisation sequence

1. Connect → `SocketConnector` state transitions `IDLE → CONNECTING → CONNECTED`
2. Send `GET_EDGES_LIST` → firmware replies with the panel topology
3. Build a panel tree; compute 2-D polygon coordinates via `PanelsLayoutService`
4. Send `GET_PANELS_STATES` → firmware replies with current on/off and colour for every panel
5. Render the visualiser; optional `SET_MIRROR(1)` enables live `MIRROR_BATCH` preview

### Architecture

All business logic lives in `commonMain` (shared between Android and iOS):

```
commonMain/
├── api/http/       LightnetHttpClient — REST API client
├── api/websocket/  SocketConnector, MessageApiService, binary protocol codec
├── device/         LightnetDevice, panel services, OfflineSceneService
├── demo/           DemoConnector, DemoHttpClient — virtual device
├── animation/      NativeSceneCore, PanelAnimationPlayer — client-side preview
├── discovery/      ServiceDiscovery interface + DeviceRepository
└── ui/             Compose screens + LightnetDeviceVisualizer (Canvas)
```

Platform-specific code is minimal:
- `androidMain` — `NsdServiceDiscovery` (Android `NsdManager`), `MainActivity`, NDK `liblightnet_anim.so`
- `iosMain` — `StubServiceDiscovery` placeholder, `MainViewController`

### Key libraries

| Library | Purpose |
|---|---|
| Kotlin Multiplatform + CMP 1.10.x | Shared code and UI |
| Ktor 3.x | HTTP + WebSocket client |
| kotlinx-coroutines / Flow | Reactive state |
| multiplatform-settings | Persistent device list and demo state |
| Android NsdManager | mDNS service discovery |

---

## Build & install

### Prerequisites

- Android Studio or JDK 11+ (Android); Xcode 14+ on macOS (iOS)
- Android SDK with **API 24+** (`sdk.dir` in `local.properties`)
- Firmware checkout for the native scene core — sibling `../lightnet-firmware` by default (see `composeApp/build.gradle.kts`)

### Commands

```bat
:: Build debug APK
.\gradlew.bat :composeApp:assembleDebug

:: Build + install directly on connected device (recommended)
.\gradlew.bat :composeApp:installDebug

:: Run unit tests
.\gradlew.bat :composeApp:allTests
```

The APK is written to `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

> **Note:** mDNS device discovery does not work reliably on Android emulators (multicast is blocked). Test discovery on a real device on the same Wi-Fi network as the controller.

---

## Documentation

| Document | Contents |
|---|---|
| [docs/getting-started.md](docs/getting-started.md) | Clone, build, run, demo device |
| [docs/development.md](docs/development.md) | Package layout, domain layer, conventions |
| [docs/connectivity.md](docs/connectivity.md) | WebSocket protocol, discovery, live preview |

Full docs hub: [lightnet-website](https://github.com/przemczan/lightnet) (MkDocs site built from firmware + mobile `docs/`).

---

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
