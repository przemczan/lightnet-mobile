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
| `network/` | `DnsResolver` — resolves mDNS hostnames to IPs |
| `settings/` | `AppPreferences`, `DevicePreferences` — multiplatform-settings backed prefs |
| `debug/` | `DebugLog` — in-app log buffer + debug mode flag |
| `ui/screens/` | `MyDevicesScreen`, `DeviceControllerScreen`, `DeviceSettingsScreen`, `GlobalSettingsScreen`, `LibraryScreen`, `PaletteEditorScreen`, `DebugScreen`; bottom sheets: `AddDeviceSheet`, `EditDeviceSheet`, `ColorPickerSheet`, `DeviceSwitcherSheet` |
| `ui/components/` | `LightnetDeviceVisualizer` + visualizer helpers (`VisualizerConfig`, `VisualizerAnimations`, `VisualizerGeometry`, `VisualizerShadows`); shared widgets: `StatusDot`, `DeviceListItem`, `HueRingColorPicker`, `ReconnectingBanner`, etc. |

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

`LightnetDevice.close()` calls `connector.close()` then cancels the device scope. **Do not call this from UI screens** — `App.kt` owns a persistent device pool (one `LightnetDevice` per saved device, keyed by `id`) that keeps connections alive across navigation. `close()` is called automatically when a device is removed from the pool or the app is disposed.

### Connector

`Connector` interface: `state: StateFlow<ConnectorState>`, `incoming: Flow<ByteArray>`, `connect()`, `disconnect()`, `send()`, `close()`.

`SocketConnector` — Ktor WebSocket with exponential back-off reconnect loop (1 s → doubles → 30 s cap). `CancellationException` breaks the loop on explicit `disconnect()` / `close()`.

`MockConnector` — self-contained fake device; auto-responds to `GET_EDGES_LIST`, `GET_PANELS_STATES`, `TOGGLE`, `SET_COLOR` with properly encoded protocol packets.

### Navigation

State-based in `App.kt` — no navigation library, no sealed screen class. Three boolean/nullable vars drive routing: `showGlobalSettings`, `showDevice`, `activeDevice`. The default view is `MyDevicesScreen`; opening a device sets `activeDevice` and `showDevice = true`.

Device add/edit is handled by `AddDeviceSheet` / `EditDeviceSheet` bottom sheets overlaid on whichever screen is active.

The `HttpClient` (WebSockets plugin, OkHttp engine on Android) is created once in `MainActivity` and injected into `LightnetApp`. A per-connection `LightnetHttpClient` is constructed in `App.kt` from the resolved WebSocket host once connected, then wired into the device via `device.attachHttpClient(...)`.

### Visualiser

`LightnetDeviceVisualizer` (Compose `Canvas`), split across several files in `ui/components/`:

- **`LightnetDeviceVisualizer.kt`** — layout, gesture handling, draw passes. `BoxWithConstraints` computes bounding-box scale/offset; polygons are sorted `(x1,y1)` vertices per edge index. Draw order per panel: drop shadow → background fill → active colour → border → inner shadow → selection overlay.
- **`VisualizerConfig.kt`** — `PanelVisualConfig` data class: all visual knobs (padding, corner radius, border, shadow, inner shadow, animation style/speed). `PanelAnimationStyle`: `FromDirections`, `Rain`, `PopUp`, `Random`.
- **`VisualizerAnimations.kt`** — `EntrancePlan` holds per-panel `animatables` (translation, `1f→0f`) and `scaleAnimatables` (scale, `0f→1f` for PopUp). `rememberEntrancePlan` keys on the panel list identity so animation re-runs on every reconnect.
- **`VisualizerShadows.kt`** — drop shadow (Layered / Feathered / NativeBlur) and inner shadow helpers.
- **`VisualizerGeometry.kt`** — `shrinkPolygon`, `buildPanelPath` (rounded-corner path).

Gestures (`awaitEachGesture`): tap (< 4 px) → paint/toggle/select; drag → paint stroke (per-stroke visited set prevents double-toggle); long press (500 ms, no movement) → enter selection mode. Hit testing via `GeometryUtils.isInsidePolygon()` in layout coordinates (before scale/offset).

## Packet mirroring (live preview)

The controller streams every outbound I²C packet as `MIRROR_BATCH` WebSocket frames. The mobile decodes and replays them so the visualiser shows live panel colors without polling.

### Mobile side

| File | Role |
|---|---|
| `device/PanelMirrorService.kt` | Decodes `MIRROR_BATCH` frames; routes each record to the right per-panel `PanelAnimationPlayer`; driver loop ticks all players at ~30 fps and emits `_states` |
| `animation/PanelAnimationPlayer.kt` | Faithful Kotlin port of the firmware `AnimationPlayer` (ATmega panel). Drives panel-local animations (FADE/BREATHE/PULSE/…) locally via integer math identical to the firmware |
| `animation/PanelAnimationPlayer.kt` — `decodeAnimationPrepare()` | Deserialises the 16-byte `PacketAnimationPrepare` body into `AnimationState` |
| `api/websocket/protocol/message/MirrorBatchMessage.kt` | `decodeMirrorBatch()` — parses the raw MIRROR_BATCH payload into a `MirrorBatch` |
| `api/websocket/protocol/IicPacketType.kt` | I²C packet type constants; `IIC_META_SIZE = 5` (size of `Protocol::PacketMeta`) |

### How it works

`MessageApiService` emits `mirrorBatches` from decoded `MIRROR_BATCH` frames. `PanelMirrorService.applyRecord()` dispatches each record by type:
- `SET_COLOR` → `player.setColorDirect()` (runner animations — unicast per panel)
- `ANIMATION_PREPARE` → `player.prepare()` (queues animation on target panel)
- `ANIMATION_START` → `player.start()` (starts queued animation; usually general call addr=0)
- `ANIMATION_CONTROL` → `player.control()` (STOP/PAUSE/RESUME/CLEAR_QUEUE)
- `TURN_ON_OFF`, `SET_PALETTE`, `SET_BASE_COLORS` — applied to matching panels

All player/state mutation is confined to a single-threaded dispatcher (`work`).

### Address routing — critical gotcha

```kotlin
private fun targets(address: Int): List<Int> =
    if (address == GENERAL_CALL) panelIds else listOf(address)
```

`panelIds` is populated from `panelsListService.panels` (sourced from the GET_PANELS_STATES response). **If `panelIds` is empty when a general-call packet arrives (addr=0 — START, STOP, CLEAR_QUEUE), `forEachTarget` is a no-op and the packet is silently ignored for all panels.**

Runner animations use unicast `SET_COLOR` (specific panel address) so they never hit this path and always work. Panel-local animations use general-call `START` (addr=0) and therefore depend on `panelIds` being populated. If panel-local animations appear not to animate in live preview, check that `panelIds.size > 0` when the `ANIMATION_START` record is processed in `applyRecord`.

---

## Key Conventions

- **`ByteWriter` / `ByteReader`** are in `com.lightnet.api.websocket.protocol`; use them for any manual binary serialisation — do not use `java.nio.ByteBuffer` (not available in `commonMain`)
- **Coroutine scope ownership**: `LightnetDevice` owns the scope; child services receive it as a constructor parameter. Never create a persistent `CoroutineScope` inside a `@Composable` function — use `rememberCoroutineScope()` or `DisposableEffect`
- **`MockConnector` as a test harness**: prefer testing domain logic against `MockConnector` rather than mocking individual services
- **Protocol responses** (`EdgesListResponse`, `PanelsStatesResponse`) extend `Message` just like commands — `MockConnector` uses them to send properly-formed, CRC-correct packets back through the pipeline
- **`PanelsListService.load()`** cancels any in-flight load and resets `_panels` to empty before starting — always safe to call on reconnect
- **Icons**: use only `Icons.Default.*` and `Icons.AutoMirrored.Filled.*` from the core Material icon set; `compose.materialIconsExtended` (CMP plugin DSL accessor) is included in `commonMain.dependencies`

## Design & UI Conventions

Aim for **standard Material 3** — prefer stock M3 components with default styling over custom looks. Follow the guidance on [m3.material.io](https://m3.material.io).

- **Theme**: the app follows the system light/dark setting via `LightnetTheme` (`ui/theme/Theme.kt`), using the default `lightColorScheme()` / `darkColorScheme()`. No custom palette. Drive component colors from `MaterialTheme.colorScheme` roles — **do not hardcode `Color(0x…)`**. The few exceptions are intentional and must work in both themes: semantic status colors (`StatusDot`, success green), the visualiser's black panel background, and the device paint-color default.
- **Don't override stock component defaults** (sizes, typography, shapes) without a reason — e.g. let `TopAppBar` use its default title typography and height rather than forcing `titleMedium`.
- **Action buttons** (OK / Save / Delete) are **wrap-content, centered** — place each in a `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center)` with `padding(top = 8.dp)`. this Row pattern is the convention.

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
