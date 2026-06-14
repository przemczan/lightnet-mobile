# Native animation core (Android NDK)

Builds `liblightnet_anim.so` from the firmware's portable `LightnetCore` (`lib/Lightnet/Core/Panel`
+ `lib/Lightnet/Core/Controller/Scene`) + its C ABIs (`lib/Lightnet/Core/CApi`), wrapped in JNI
(`jni_anim.cpp`, `jni_scene.cpp`). This is the shared C++ animation math the firmware runs —
consumed here instead of re-implemented in Kotlin.

## Prerequisites

- **Android NDK** installed (Android Studio → SDK Manager → SDK Tools → NDK, or
  `sdkmanager "ndk;<version>"`). AGP's `externalNativeBuild` needs it.

## Where the firmware code comes from

Gradle resolves `lightnetFirmwareDir` (see `composeApp/build.gradle.kts`) in this order:

1. **`-PlightnetFirmwareDir=<path>`** — for local dev, set it in a gitignored `local.properties` at
   the repo root (Gradle exposes those as project properties):
   ```properties
   lightnetFirmwareDir=D:/Projects/Lightnet/lightnet-firmware
   ```
   Points the build straight at your firmware working copy — edit firmware, rebuild the app, no push.
2. **`third_party/lightnet-firmware`** — git submodule, the reproducible default (CI / fresh clones).
3. **`../lightnet-firmware`** — sibling checkout fallback.

### Adding the submodule (after the firmware Core is pushed)

```bash
cd lightnet-mobile
git submodule add git@github.com:przemczan/lightnet-firmware.git third_party/lightnet-firmware
git commit -m "Vendor lightnet-firmware (animation core) as submodule"
# fresh clones: git clone --recurse-submodules …  (or: git submodule update --init)
```

Until then, use option 1 (local.properties) — no submodule or push required.

## Verify (build gate)

```bash
./gradlew :composeApp:assembleDebug
```

A successful build means the NDK + CMake + firmware Core all compile and link for Android. The
real check is the in-app live preview rendering correctly via `NativeAnimCore`/`NativeAnimBridge`
(`com.lightnet.animation`), which drives the same C++ core as the firmware.
