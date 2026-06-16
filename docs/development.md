---
icon: material/code-braces
---

# Development

## Package Layout

All application code lives in `composeApp/src/commonMain/`. The project follows a feature-package structure:

| Package | Responsibility |
|---|---|
| `api/http/` | HTTP client: `LightnetHttpClient`, `DeviceHttpApi` interface, JSON models |
| `api/websocket/` | WebSocket transport: `Connector`, `SocketConnector`, `MessageApiService` |
| `api/websocket/protocol/` | Binary codec: `ByteReader`, `ByteWriter`, `Crc`, `MessageParser`, message types |
| `api/websocket/model/` | WebSocket domain models: `PanelInfo`, `EdgeInfo`, `PanelLayout`, `EdgeCoords`, `PanelState` |
| `device/` | Device domain layer: `LightnetDevice`, `LightnetDevicePanel`, panel services, `OfflineSceneService` |
| `demo/` | Demo device: `DemoConnector`, `DemoHttpClient`, `DemoTopologyGenerator`, `DemoDataInitializer`, `DemoDevice` |
| `geometry/` | `GeometryUtils.isInsidePolygon()` — ray-casting hit test used by the visualiser |
| `discovery/` | `ServiceDiscovery` interface, `SavedDevice`, `DeviceRepository` |
| `animation/` | `NativeAnimCore`, `PanelAnimationPlayer` — client-side preview of panel animations |
| `network/` | `DnsResolver` — resolves mDNS hostnames to IPs |
| `settings/` | `AppPreferences`, `DevicePreferences`, `DemoSettings`, `SceneRepository` — multiplatform-settings backed prefs |
| `debug/` | `DebugLog` — in-app log buffer + debug mode flag |
| `ui/screens/` | Compose screens: `MyDevicesScreen`, `DeviceControllerScreen`, `DeviceSettingsScreen`, `GlobalSettingsScreen`, `DebugScreen`, `PaletteEditorScreen`, `ScenesSettingsScreen`; bottom sheets: `AddDeviceSheet`, `EditDeviceSheet`, `ColorPickerSheet`, `DeviceSwitcherSheet` |
| `ui/screens/scene/` | Scene editor: `SceneEditorScreen`, `LayerEditorScreen`, `StepEditorScreen` |
| `ui/components/` | `LightnetDeviceVisualizer` — Compose Canvas renderer + gesture handling |
| `ui/theme/` | App theming (colours, typography) |

## Device Domain Layer

`LightnetDevice` owns a `CoroutineScope` and composes four services:

- **`MessageApiService`** — hot `MutableSharedFlow` fed by `connector.incoming`; exposes `edgesList` and `panelsStates` flows
- **`PanelsListService`** — sends `GET_EDGES_LIST`, builds `PanelInfo` tree; uses `CoroutineStart.UNDISPATCHED` before sending to prevent a race condition with the response
- **`PanelsStatesService`** — sends `GET_PANELS_STATES` after panels load; subscribes to live state pushes
- **`PanelsLayoutService`** — pure geometry; converts edge topology to 2-D polygon coordinates

`LightnetDevice.snapshot: StateFlow<DeviceSnapshot?>` is `null` while loading, set after the first edge list arrives, and cleared on disconnect so the UI returns to the loading indicator during reconnect.

!!! warning "Always call `LightnetDevice.close()`"
    Call `close()` when the screen exits — it calls `connector.close()` then cancels the device scope. Failing to do so leaks the coroutine scope and the WebSocket connection.

## Connector

The `Connector` interface exposes:

```kotlin
val state: StateFlow<ConnectorState>
val incoming: Flow<ByteArray>
fun connect()
fun disconnect()
fun send(data: ByteArray)
fun close()
```

**`SocketConnector`** — Ktor WebSocket with exponential back-off reconnect (1 s → doubles → 30 s cap). `CancellationException` breaks the loop on explicit `disconnect()` / `close()`.

**`DemoConnector`** — in-memory fake device used by the demo device. Responds to `GET_EDGES_LIST`, `GET_PANELS_STATES`, `TOGGLE`, `SET_COLOR`, `PING` with properly CRC-correct protocol packets. Persists topology and panel state in `Settings` across app restarts. Calling `resetLayout(newCount)` clears the stored topology so the next `connect()` generates a fresh random layout.

## Demo Device

The demo device (`com.lightnet.demo`) is a fully functional virtual device that runs without any physical hardware. It is enabled via **Settings → Enable demo device** and always appears at the top of the device list with a "Demo" badge.

| Component | Role |
|---|---|
| `DemoConnector` | Implements `Connector`; handles WebSocket protocol in-memory. Persists topology + panel state in `Settings`. |
| `DemoHttpClient` | Implements `DeviceHttpApi`; stores palettes and scenes in `Settings`; routes scene playback to `OfflineSceneService`. |
| `DemoTopologyGenerator` | Generates a random spanning-tree panel topology (3 edges per panel). |
| `DemoDataInitializer` | Default palette set seeded on first use. |
| `DemoDevice.kt` | `createDemoDevice()` factory + `DEMO_DEVICE_ID` constant. |
| `DemoSettings` | Persists `demoEnabled` and `demoPanelCount` (1–50). |

**Panel regeneration**: changing the panel count clears the stored topology via `DemoConnector.resetLayout()` and triggers a reconnect — the next `connect()` call generates a fresh random layout. In `DeviceControllerScreen` the **shuffle button** (only shown for the demo) also triggers an immediate regeneration.

**Scene playback**: routes through `LightnetDevice.offlineSceneService` (`OfflineSceneService` + shared C++ scene engine) — no separate demo-specific player. Palettes must be registered (via `device.loadPalettes()`) before a scene is played; this happens automatically when `DeviceControllerScreen` opens.

## Navigation

State-based in `App.kt` — no navigation library, no sealed screen class. Three boolean/nullable vars drive routing: `showGlobalSettings`, `showDevice`, `activeDevice`. The default view is `MyDevicesScreen`; opening a device sets `activeDevice` and `showDevice = true`.

Device add/edit is handled by `AddDeviceSheet` / `EditDeviceSheet` bottom sheets overlaid on whichever screen is active.

## Visualiser

`LightnetDeviceVisualizer` is a Compose `Canvas` component:

- `BoxWithConstraints` computes bounding-box scale and offset so all panels fit the viewport
- Polygons are the `(x1, y1)` vertex of each `EdgeCoords` entry, sorted by edge index
- Two draw layers per panel: black fill (background) + coloured overlay (`alpha = brightness/255`) when `on == true`
- Single `awaitEachGesture` block: tap (< 4 px movement) → toggle; drag → paint stroke (per-stroke visited set prevents double-toggle)
- Hit testing via `GeometryUtils.isInsidePolygon()` in layout coordinates (before scale/offset)

## Key Conventions

- **`ByteWriter` / `ByteReader`** are in `com.lightnet.api.websocket.protocol` — use them for any manual binary serialisation. Do not use `java.nio.ByteBuffer` (not available in `commonMain`).
- **Coroutine scope ownership**: `LightnetDevice` owns the scope; child services receive it as a constructor parameter. Never create a persistent `CoroutineScope` inside a `@Composable` — use `rememberCoroutineScope()` or `DisposableEffect`.
- **Protocol responses** (`EdgesListResponse`, `PanelsStatesResponse`) extend `Message` just like commands — `DemoConnector` uses them to send properly-formed, CRC-correct packets back through the pipeline.
- **`PanelsListService.load()`** cancels any in-flight load and resets `_panels` to empty before starting — always safe to call on reconnect.
- **Icons**: use only `Icons.Default.*` and `Icons.AutoMirrored.Filled.*` from the core Material icon set.
