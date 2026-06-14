# iOS native animation core (build on macOS)

The iOS `NativeAnimCore` actual binds to the firmware C ABI (`panel_core_c.h`) via Kotlin/Native
**cinterop** (configured in `composeApp/build.gradle.kts` → `cinterops.create("animcore")`, def at
`src/nativeInterop/cinterop/animcore.def`). cinterop generates the `animcore.*` bindings; the C++
object code (the player + shim) must be linked from the `panel_core` **static library** (and
`controller_core` for `NativeSceneCore`).

> iOS targets only build on macOS + Xcode. On Windows the iOS compilations are skipped, so the
> Android build is unaffected — but the steps below must be completed on a Mac before the iOS app
> links/runs.

## 1. Build the static lib per architecture (CMake, iOS toolchain)

`lib/Lightnet/Core/CApi/CMakeLists.txt` already produces `libpanel_core.a` and
`libcontroller_core.a`. Build it for each slice (disable the host smoke exes):

```bash
FW=../lightnet-firmware   # or third_party/lightnet-firmware (submodule)

# Device (arm64)
cmake -S "$FW/lib/Lightnet/Core/CApi" -B build/ios-arm64 -G Xcode \
  -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_OSX_SYSROOT=iphoneos -DANIM_CORE_BUILD_TESTS=OFF
cmake --build build/ios-arm64 --config Release

# Simulator (arm64)
cmake -S "$FW/lib/Lightnet/Core/CApi" -B build/ios-sim-arm64 -G Xcode \
  -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_OSX_SYSROOT=iphonesimulator -DANIM_CORE_BUILD_TESTS=OFF
cmake --build build/ios-sim-arm64 --config Release
```

## 2. Link it into the framework

Point each iOS target's framework at its slice in `composeApp/build.gradle.kts` — add inside the
`iosTarget.binaries.framework { … }` block (per target, choosing the matching lib path):

```kotlin
linkerOpts("-L${'$'}{<dir-for-this-target>}", "-lpanel_core", "-lcontroller_core", "-lc++")
```

(Or, equivalently, add `staticLibraries = libpanel_core.a libcontroller_core.a` +
`libraryPaths = <dir>` to `animcore.def` once the per-arch path is known.) `-lc++` pulls in the C++
runtime the player needs.

## 3. Verify

Build the framework / run the app from Xcode (or `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`).
The preview should render identically to the Android build. Keep the Kotlin path until this is
confirmed on a device (see Phase 3.3).
