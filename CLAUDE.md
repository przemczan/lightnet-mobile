# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**This code is still in heavy development**. At this point anything can be changed, backwards compatibility is not a concern.
**Never** suggest in comments that something has been changed or how the code was working previously, always treat the current code as the only known version.
**For now** do NOT bump protocol version when changing protocols/APIs, as I said, backwards compatibility is not a concern at this point.

## Companion Repos

- **Firmware** — `../lightnet-firmware` (relative to this repo). Check `lib/Lightnet/Controller/API/http/` for route handler implementations to verify HTTP verb, endpoint path, and exact JSON field names before adding API calls to `LightnetHttpClient` / `DeviceHttpApi`.

## On-Device Debugging (adb)

- **UI element bounds**: `adb shell uiautomator dump //sdcard/ui.xml` then `adb pull //sdcard/ui.xml ./ui.xml` (the `//` prefix avoids Git Bash MSYS path-mangling of `/sdcard/...`). Gives exact `bounds`, `content-desc`, `checked`/`enabled` attributes — far more reliable than tapping based on screenshot coordinates.
- **Screenshot**: `adb exec-out screencap -p > screen.png`.
- **Relaunch app**: `adb shell monkey -p com.lightnet -c android.intent.category.LAUNCHER 1`.
- **HTTP/WS logs**: `DebugLog.debugMode` is in-memory and resets to `false` on every app restart — toggle the "Debug" switch on the Debug Log screen each session, then `adb logcat -c` (clear) and `adb logcat -d | grep -i "Lightnet/HTTP"`.
- **Ground truth**: `curl http://<device-ip>/api/state` (or other endpoints) hits the firmware directly, bypassing the app — useful for confirming whether a UI mismatch is a client bug or a firmware lag.

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
| `api/http/` | `DeviceHttpApi`, `LightnetHttpClient`, JSON models; `DemoHttpClient` for the demo device |
| `api/websocket/` | WebSocket transport: `Connector`, `SocketConnector`, `MessageApiService` |
| `api/websocket/protocol/` | Binary codec: `ByteReader`, `ByteWriter`, `Crc`, `MessageParser`, message types |
| `api/websocket/model/` | WebSocket domain models: `PanelInfo`, `EdgeInfo`, `PanelLayout`, `EdgeCoords`, `PanelState` |
| `animation/` | `NativeAnimCore`, `NativeSceneCore`, `PanelAnimationPlayer` — bindings to firmware C++ cores |
| `demo/` | `DemoConnector`, `DemoHttpClient`, `DemoTopologyGenerator` — in-app fake device (`DEMO_DEVICE_ID`) |
| `device/` | `LightnetDevice`, `LightnetDevicePanel`, `PanelsListService`, `PanelsStatesService`, `PanelsLayoutService`, `PanelMirrorService`, `OfflineSceneService`, `DeviceLivenessService`, `PanelPacketRenderer` |
| `geometry/` | `GeometryUtils.isInsidePolygon()` — ray-casting hit test used by the visualiser |
| `discovery/` | `ServiceDiscovery` interface, `SavedDevice`, `DeviceRepository` |
| `network/` | `resolveHostToIp()` — platform DNS resolution for mDNS hostnames (Android); iOS stub returns null |
| `settings/` | `AppPreferences`, `DevicePreferences`, `SceneRepository`, `DemoSettings` — multiplatform-settings backed prefs |
| `debug/` | `DebugLog` — in-app log buffer + debug mode flag |
| `ui/screens/` | `MyDevicesScreen`, `DeviceControllerScreen`, `DeviceSettingsScreen`, `GlobalSettingsScreen`, `ScenesSettingsScreen`, `PaletteEditorScreen`, `DebugScreen`; bottom sheets: `AddDeviceSheet`, `EditDeviceSheet`, `ColorPickerSheet`, `DeviceSwitcherSheet` |
| `ui/screens/scene/` | Scene editor: `TimelineSceneEditorScreen`, `LayerEditorScreen`, `StepEditorScreen` |
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
- `MessageApiService` — collects `connector.incoming`, parses into a hot `_messages` flow; exposes `edgesList`, `panelsStates`, `mirrorBatches`, and `pong` as derived flows
- `PanelsListService` — sends `GET_EDGES_LIST`, builds `PanelInfo` tree; uses `CoroutineStart.UNDISPATCHED` before sending to prevent race condition with the response
- `PanelsStatesService` — sends `GET_PANELS_STATES` after panels load; subscribes to live state pushes
- `PanelsLayoutService` — pure geometry; converts edge topology to 2-D polygon coordinates
- `PanelMirrorService` — live preview: feeds `MIRROR_BATCH` frames into a `PanelPacketRenderer` at ~30 fps when `livePreview` is on
- `OfflineSceneService` — offline scene playback via `NativeSceneCore`; feeds the same `PanelPacketRenderer` path as the mirror
- `DeviceLivenessService` — PING/PONG liveness; exposes `isOnline: StateFlow<Boolean?>`
- `LightnetDevicePanel` — per-panel handle; optimistic local state + syncs from the combined render-state flow; exposes `state: StateFlow<PanelState>`

`LightnetDevice.snapshot: StateFlow<DeviceSnapshot?>` is null while `panelsListService.panels` is loading (`null`), set once the edge list arrives, cleared on disconnect so the UI returns to the loading indicator during reconnect. `livePreview: StateFlow<Boolean>` toggles mirrored animation preview (off by default).

`LightnetDevice.close()` calls `connector.close()`, `offlineSceneService.close()`, then cancels the device scope. **Do not call this from UI screens** — `App.kt` owns a persistent device pool (one `LightnetDevice` per saved device keyed by `id`, plus `DEMO_DEVICE_ID` when the demo is enabled) that keeps connections alive across navigation. `close()` is called automatically when a device is removed from the pool or the app is disposed.

### Connector

`Connector` interface: `state: StateFlow<ConnectorState>`, `incoming: Flow<ByteArray>`, `connect()`, `disconnect()`, `send()`, `close()`.

`SocketConnector` — Ktor WebSocket with exponential back-off reconnect loop (1 s → doubles → 30 s cap). `CancellationException` breaks the loop on explicit `disconnect()` / `close()`.

`DemoConnector` (`demo/`) — self-contained fake device enabled in **Settings → Enable demo device**; auto-responds to `GET_EDGES_LIST`, `GET_PANELS_STATES`, `TOGGLE`, `SET_COLOR`, `PING` with properly encoded protocol packets. Recommended test harness for domain logic without hardware.

### Navigation

State-based in `App.kt` — no navigation library, no sealed screen class. Three boolean/nullable vars drive routing: `showGlobalSettings`, `showDevice`, `activeDevice`. The default view is `MyDevicesScreen`; opening a device sets `activeDevice` and `showDevice = true`.

Device add/edit is handled by `AddDeviceSheet` / `EditDeviceSheet` bottom sheets overlaid on whichever screen is active.

`App.kt` owns a persistent device pool (one `LightnetDevice` per saved device, keyed by `id`, plus `DEMO_DEVICE_ID` when the demo is enabled) that keeps connections alive across navigation.

The WebSocket `HttpClient` (WebSockets plugin, OkHttp engine on Android) is created once in `MainActivity` and injected into `LightnetApp`. A per-connection `LightnetHttpClient` (own Ktor client with JSON negotiation) is constructed in `App.kt` from the resolved WebSocket host once connected, then wired into the device via `device.attachHttpClient(...)` — the demo device uses `DemoHttpClient` instead.

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
| `device/PanelPacketRenderer.kt` | Shared per-panel render core: applies mirror records to `PanelAnimationPlayer`s; used by both `PanelMirrorService` and `OfflineSceneService` |
| `device/PanelMirrorService.kt` | Subscribes to `mirrorBatches`, feeds a `PanelPacketRenderer`, ticks at ~30 fps, publishes `_states` while live preview is on |
| `animation/PanelAnimationPlayer.kt` | Thin wrapper over [`NativeAnimCore`](#shared-animation-core-native) — owns only mobile-specific clock-domain translation (controller millis ↔ mobile monotonic clock via a single `clockOffsetMs`) and exposes `currentColor` |
| `animation/NativeAnimCore.kt` (+ `.android.kt` / `.ios.kt`) | `expect`/`actual` binding to the shared C++ panel animation core (see below) |
| `animation/NativeSceneCore.kt` (+ `.android.kt` / `.ios.kt`) | `expect`/`actual` binding to the shared C++ scene engine for offline playback (see below) |
| `api/websocket/protocol/message/MirrorBatchMessage.kt` | `decodeMirrorBatch()` — parses the raw MIRROR_BATCH payload into a `MirrorBatch` |
| `api/websocket/protocol/IicPacketType.kt` | I²C packet type constants; `IIC_META_SIZE = 5` (size of `Protocol::PacketMeta`) |

### How it works

`MessageApiService` emits `mirrorBatches` from decoded `MIRROR_BATCH` frames. `PanelPacketRenderer.applyRecord()` dispatches each record by type (address 0 = general call to all panels):
- `SET_COLOR` → `player.setColorDirect()` (runner animations — unicast per panel)
- `ANIMATION_PREPARE` → `player.prepare()` — raw packet bytes passed straight through
- `ANIMATION_START` → `player.start()` (starts queued animation; usually general call addr=0)
- `ANIMATION_CONTROL` → `player.control()` (STOP/PAUSE/RESUME/CLEAR_QUEUE)
- `ANIMATION_UPDATE_PARAMS` → `player.updateParams()`
- `TURN_ON_OFF`, `SET_PALETTE`, `SET_BASE_COLORS`, `SET_BACKGROUND` — applied to matching panels (palette/base-colors pass raw bytes through)

All renderer/player mutation is confined to a single-threaded dispatcher (`Dispatchers.Default.limitedParallelism(1)` in `PanelMirrorService`).

### Shared animation core (native)

Panel-local animation math and scene orchestration are **not** re-implemented in Kotlin. The mobile
app links two portable C++ cores from `lightnet-firmware`:

1. **Panel core** (`lib/Lightnet/Core/Panel`, C ABI `panel_core_c.h`) — per-panel FADE/BREATHE/PULSE/…,
   layer compositing. Surfaced as `NativeAnimCore` → `PanelAnimationPlayer`.
2. **Scene core** (`lib/Lightnet/Core/Controller/Scene`, C ABI `controller_core_c.h`) — whole-scene
   parser/player/scheduler with no hardware. Surfaced as `NativeSceneCore` → `OfflineSceneService`
   (emits MIRROR_BATCH payloads that feed the same `PanelPacketRenderer` path as live mirroring).

- **Android**: NDK `externalNativeBuild` (`composeApp/src/androidMain/cpp/`) builds `lightnet_anim.so`
  from `jni_anim.cpp` + `jni_scene.cpp`, linking both `panel_core` and `controller_core` static libs.
  JNI bridges: `NativeAnimBridge` → `NativeAnimCore.android.kt`, `NativeSceneBridge` →
  `NativeSceneCore.android.kt`.
- **iOS**: Kotlin/Native cinterop (`src/nativeInterop/cinterop/animcore.def`) binds both C ABIs;
  `actual` classes in `NativeAnimCore.ios.kt` / `NativeSceneCore.ios.kt`. Build steps (Mac-only,
  must finish linking `libpanel_core.a` + `libcontroller_core.a`): `composeApp/src/iosMain/README.md`.
- **Firmware checkout location**: resolved by `composeApp/build.gradle.kts` (`lightnetFirmwareDir`):
  `-PlightnetFirmwareDir` (typically in gitignored `local.properties`) → `third_party/lightnet-firmware`
  (submodule, not yet added) → `../lightnet-firmware` (sibling checkout — current setup).

`expect class NativeAnimCore` (`commonMain`) is the panel surface: `prepare`/`setPalette`/
`setBaseColors` take raw wire bytes (`PacketMeta` header included — same layout as `IicPacketType`
records); `start`/`control`/`updateParams`/`tick` take scalar fields + a `uint16` `now`;
`setBackground`/`setColorDirect`/`currentColor`/`takeDirty`/`isAnimating` for direct colour control
and output. Packet decoding, palette sampling, layer compositing, and easing math all live in the C++
core — Kotlin never re-implements them. Any firmware animation-math change is picked up
automatically once the linked core is rebuilt; no parallel Kotlin port to keep in sync.


## Key Conventions

- **`ByteWriter` / `ByteReader`** are in `com.lightnet.api.websocket.protocol`; use them for any manual binary serialisation — do not use `java.nio.ByteBuffer` (not available in `commonMain`)
- **Coroutine scope ownership**: `LightnetDevice` owns the scope; child services receive it as a constructor parameter. Never create a persistent `CoroutineScope` inside a `@Composable` function — use `rememberCoroutineScope()` or `DisposableEffect`
- **`DemoConnector` as a test harness**: prefer testing domain logic against `DemoConnector` rather than mocking individual services
- **Protocol responses** (`EdgesListResponse`, `PanelsStatesResponse`, `PongResponse`) extend `Message` just like commands — `DemoConnector` uses them to send properly-formed, CRC-correct packets back through the pipeline
- **`PanelsListService.load()`** cancels any in-flight load and resets `_panels` to `null` (loading) before starting — always safe to call on reconnect
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

## Taking app screenshots

Look for guide in `docs/assets/screenshots/CLAUDE.md`.

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
