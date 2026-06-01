# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Companion Repos

- **Firmware** — `../lightnet-firmware` (relative to this repo). Check `lib/Lightnet/Controller/API/http/` for route handler implementations to verify HTTP verb, endpoint path, and exact JSON field names before adding API calls to `LightnetHttpClient`.

## Commands

```bat
:: Build debug APK
.\gradlew.bat :composeApp:assembleDebug

:: Build + install on connected Android device
.\gradlew.bat :composeApp:installDebug

:: Run all tests (commonTest — protocol layer unit tests)
.\gradlew.bat :composeApp:allTests

:: Run a single test class
.\gradlew.bat :composeApp:testDebugUnitTest --tests "com.lightnet.protocol.MessageProtocolTest"

:: Clean build
.\gradlew.bat clean
```

## Architecture

All application code lives in `composeApp/src/`. The project uses a single Gradle module with three source sets:

- `commonMain` — all logic and UI; runs on Android and iOS
- `androidMain` — thin Android wiring only (`MainActivity`, `NsdServiceDiscovery`)
- `iosMain` — stub iOS implementations (`StubServiceDiscovery`, `MainViewController`)

### `commonMain` package layout

| Package | Responsibility |
|---|---|
| `api/http/` | HTTP client: `LightnetHttpClient`, JSON models |
| `api/websocket/` | WebSocket transport: `Connector`, `SocketConnector`, `MockConnector`, `MessageApiService`, `PanelsGenerator` |
| `api/websocket/protocol/` | Binary codec: `ByteReader`, `ByteWriter`, `Crc`, `MessageParser`, message types |
| `api/websocket/model/` | WebSocket domain models: `PanelInfo`, `EdgeInfo`, `PanelLayout`, `EdgeCoords`, `PanelState` |
| `device/` | Device domain layer: `LightnetDevice`, `LightnetDevicePanel`, panel services |
| `geometry/` | `GeometryUtils.isInsidePolygon()` — ray-casting hit test used by the visualiser |
| `discovery/` | `ServiceDiscovery` interface, `SavedDevice`, `DeviceRepository` |
| `ui/screens/` | Compose screens: `MyDevicesScreen`, `DeviceDiscoveryScreen`, `DeviceControllerScreen` |
| `ui/components/` | `LightnetDeviceVisualizer` — Compose Canvas renderer + gesture handling |

### Binary protocol

Custom binary format over WebSocket. Every packet:
```
[type:u8][version:u16LE][nonce:u32LE][headerCRC:u16LE][payloadCRC:u16LE][payloadSize:u16LE][payload…]
```
- CRC-16 (poly `0xA001`, init `0xFFFF`) over the 7-byte header, and separately over the payload
- All multi-byte integers are **little-endian** — enforced by `ByteReader`/`ByteWriter`
- `MessageParser.parse(ByteArray)` validates both CRCs and returns `Result.Success/Failure`
- Outgoing messages extend `Message` and implement `encodePayload(ByteWriter)`
- Inbound variable-length payloads decoded by top-level functions `decodeEdgesList` / `decodePanelsStates`

### Device domain layer

`LightnetDevice` owns a `CoroutineScope` and composes:
- `MessageApiService` — hot `MutableSharedFlow` fed by `connector.incoming`; exposes `edgesList` and `panelsStates` flows
- `PanelsListService` — sends `GET_EDGES_LIST`, builds `PanelInfo` tree; uses `CoroutineStart.UNDISPATCHED` before sending to prevent race condition with the response
- `PanelsStatesService` — sends `GET_PANELS_STATES` after panels load; subscribes to live state pushes
- `PanelsLayoutService` — pure geometry; converts edge topology to 2-D polygon coordinates
- `LightnetDevicePanel` — per-panel handle; optimistic local state + syncs from device push; exposes `state: StateFlow<PanelState>`

`LightnetDevice.snapshot: StateFlow<DeviceSnapshot?>` is null while loading, set after the first edge list arrives, cleared on disconnect so the UI returns to the loading indicator during reconnect.

`LightnetDevice.close()` calls `connector.close()` then cancels the device scope — always call this when the screen exits.

### Connector

`Connector` interface: `state: StateFlow<ConnectorState>`, `incoming: Flow<ByteArray>`, `connect()`, `disconnect()`, `send()`, `close()`.

`SocketConnector` — Ktor WebSocket with exponential back-off reconnect loop (1 s → doubles → 30 s cap). `CancellationException` breaks the loop on explicit `disconnect()` / `close()`.

`MockConnector` — self-contained fake device; auto-responds to `GET_EDGES_LIST`, `GET_PANELS_STATES`, `TOGGLE`, `SET_COLOR` with properly encoded protocol packets. Used by **Demo Device** in `DeviceDiscoveryScreen`.

### Navigation

Simple state-based back-stack in `App.kt` (`mutableStateListOf<AppScreen>`). No navigation library. `AppScreen` sealed class has three variants: `MyDevices`, `DeviceDiscovery`, `DeviceController(host, port)`. Device list state is lifted to `LightnetApp` and refreshed after every add/delete.

`DeviceControllerScreen` uses `host == "mock"` to choose `MockConnector` vs `SocketConnector`. The `HttpClient` (with WebSockets plugin, OkHttp engine on Android) is created once in `MainActivity` and injected down through `LightnetApp`.

### Visualiser

`LightnetDeviceVisualizer` (Compose `Canvas`):
- `BoxWithConstraints` computes bounding-box scale and offset so all panels fit the viewport
- Polygons are the `(x1, y1)` vertex of each `EdgeCoords` entry, sorted by edge index
- Two draw layers per panel: black fill (background) + coloured overlay (`alpha = brightness/255`) when `on == true`
- Single `awaitEachGesture` block: tap (< 4 px movement) → toggle; drag → paint stroke (per-stroke visited set prevents double-toggle)
- Hit testing via `GeometryUtils.isInsidePolygon()` in layout coordinates (before scale/offset)

## Key Conventions

- **`ByteWriter` / `ByteReader`** are in `com.lightnet.api.websocket.protocol`; use them for any manual binary serialisation — do not use `java.nio.ByteBuffer` (not available in `commonMain`)
- **Coroutine scope ownership**: `LightnetDevice` owns the scope; child services receive it as a constructor parameter. Never create a persistent `CoroutineScope` inside a `@Composable` function — use `rememberCoroutineScope()` or `DisposableEffect`
- **`MockConnector` as a test harness**: prefer testing domain logic against `MockConnector` rather than mocking individual services
- **Protocol responses** (`EdgesListResponse`, `PanelsStatesResponse`) extend `Message` just like commands — `MockConnector` uses them to send properly-formed, CRC-correct packets back through the pipeline
- **`PanelsListService.load()`** cancels any in-flight load and resets `_panels` to empty before starting — always safe to call on reconnect
- **Icons**: use only `Icons.Default.*` and `Icons.AutoMirrored.Filled.*` from the core Material icon set; `compose.materialIconsExtended` (CMP plugin DSL accessor) is included in `commonMain.dependencies`

## Updating the App Icon

Drop an `icon.zip` (generated from the Lightnet logo canvas) into the project root, then run:

```bash
unzip -o icon.zip -d /tmp/lightnet-icon-extract/

RES=composeApp/src/androidMain/res
SRC=/tmp/lightnet-icon-extract/res

cp "$SRC/mipmap-anydpi-v26/ic_launcher.xml"       "$RES/mipmap-anydpi-v26/ic_launcher.xml"
cp "$SRC/mipmap-anydpi-v26/ic_launcher_round.xml"  "$RES/mipmap-anydpi-v26/ic_launcher_round.xml"
cp "$SRC/drawable/ic_launcher_background.xml"       "$RES/drawable/ic_launcher_background.xml"
cp "$SRC/drawable/ic_launcher_foreground.xml"       "$RES/drawable/ic_launcher_foreground.xml"
cp "$SRC/drawable/ic_launcher_monochrome.xml"       "$RES/drawable/ic_launcher_monochrome.xml"
```

The zip supplies vector-based adaptive icons (API 26+). The existing raster `.webp` files in `mipmap-hdpi/mdpi/xhdpi/xxhdpi/xxxhdpi` are kept as fallbacks for API 24–25 (minSdk).

## Versions

| Component | Version |
|---|---|
| Kotlin | 2.3.21 |
| Compose Multiplatform | 1.10.3 |
| AGP | 8.11.2 |
| Ktor | 3.1.3 |
| kotlinx-coroutines | 1.10.1 |
| multiplatform-settings | 1.2.0 |
| Android minSdk | 24 |
| Android compileSdk | 36 |
