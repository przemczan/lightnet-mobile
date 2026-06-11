---
icon: material/code-braces
---

# Development

## Package Layout

All application code lives in `composeApp/src/commonMain/`. The project follows a feature-package structure:

| Package | Responsibility |
|---|---|
| `api/http/` | HTTP client: `LightnetHttpClient`, JSON models |
| `api/websocket/` | WebSocket transport: `Connector`, `SocketConnector`, `MockConnector`, `MessageApiService`, `PanelsGenerator` |
| `api/websocket/protocol/` | Binary codec: `ByteReader`, `ByteWriter`, `Crc`, `MessageParser`, message types |
| `api/websocket/model/` | WebSocket domain models: `PanelInfo`, `EdgeInfo`, `PanelLayout`, `EdgeCoords`, `PanelState` |
| `device/` | Device domain layer: `LightnetDevice`, `LightnetDevicePanel`, panel services |
| `geometry/` | `GeometryUtils.isInsidePolygon()` — ray-casting hit test used by the visualiser |
| `discovery/` | `ServiceDiscovery` interface, `SavedDevice`, `DeviceRepository` |
| `animation/` | `NativeAnimCore`, `PanelAnimationPlayer` — client-side preview of panel animations |
| `network/` | `DnsResolver` — resolves mDNS hostnames to IPs |
| `settings/` | `AppPreferences`, `DevicePreferences`, `SceneRepository` — multiplatform-settings backed prefs |
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

**`MockConnector`** — self-contained fake device; auto-responds to `GET_EDGES_LIST`, `GET_PANELS_STATES`, `TOGGLE`, `SET_COLOR` with properly CRC-correct protocol packets. Used by **Demo Device** in `DeviceDiscoveryScreen`.

!!! tip "Prefer `MockConnector` for testing"
    Test domain logic against `MockConnector` rather than mocking individual services — it exercises the full protocol path including CRC validation.

`DeviceControllerScreen` uses `host == "mock"` to choose `MockConnector` vs `SocketConnector`.

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
- **Protocol responses** (`EdgesListResponse`, `PanelsStatesResponse`) extend `Message` just like commands — `MockConnector` uses them to send properly-formed, CRC-correct packets back through the pipeline.
- **`PanelsListService.load()`** cancels any in-flight load and resets `_panels` to empty before starting — always safe to call on reconnect.
- **Icons**: use only `Icons.Default.*` and `Icons.AutoMirrored.Filled.*` from the core Material icon set.
