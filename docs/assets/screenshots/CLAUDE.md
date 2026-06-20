# Screenshot Guide

How to (re)generate the showcase screenshots in this folder. Files are named
`NN-description.png` — when refreshing, overwrite the existing file with the same
name so doc references don't need updating.

## Prerequisites

- A physical/emulated Android device connected via `adb` with the app installed
  (debug build is fine: `.\gradlew.bat :composeApp:installDebug`).
- At least one saved device reachable on the network ("ESP32 Dev" in these shots,
  30 panels), with two global scenes (`TestScene1`, `TestScene2`) and one
  device-level scene (`TestScene1`) already created.
- Screen must be unlocked and on (no PIN) — `adb shell input keyevent KEYCODE_WAKEUP`
  wakes it, but a locked screen will show the lockscreen instead of the app.

## General technique

```bash
# Relaunch app fresh
adb shell am force-stop com.lightnet
adb shell monkey -p com.lightnet -c android.intent.category.LAUNCHER 1
sleep 2

# Take a screenshot (bash / Git Bash)
adb exec-out screencap -p > docs/assets/screenshots/NN-name.png

# Take a screenshot (PowerShell — do NOT use `>`; it writes UTF-16 and corrupts PNGs)
adb shell screencap -p //sdcard/lightnet_ss.png
adb pull //sdcard/lightnet_ss.png docs/assets/screenshots/NN-name.png
```

To find tap coordinates reliably, dump the UI hierarchy instead of guessing from
screenshot pixels (screenshots are 1080x2424 — adb tap coordinates are in this
same space, NOT the downscaled image shown to you):

```bash
adb shell uiautomator dump //sdcard/ui.xml   # the // prefix avoids Git Bash path mangling
adb pull //sdcard/ui.xml docs/assets/screenshots/ui.xml
grep -o 'content-desc="[^"]*"[^>]*bounds="[^"]*"' docs/assets/screenshots/ui.xml
```

Useful `content-desc` values on the device controller screen: `Switch device`,
`Device settings`, `Pick color`, `Adjust brightness, palette and speed`, `Scenes`,
`Turn off`, `Live preview`, `Stop scene` / `Play "<name>"`.

Delete `ui.xml` and any temp `check*.png` files when done — keep this folder to
just the numbered showcase shots + this guide.

## Sequence

1. **01-main-screen.png** — Fresh launch, `MyDevicesScreen` (device list).
   ```bash
   adb shell am force-stop com.lightnet
   adb shell monkey -p com.lightnet -c android.intent.category.LAUNCHER 1
   sleep 2
   adb exec-out screencap -p > docs/assets/screenshots/01-main-screen.png
   ```

2. **02-add-device.png** — Tap the "+" FAB (bottom-right, ~998,2120), wait ~1-2s
   for mDNS discovery to populate the "Discovered" list, screenshot the
   `AddDeviceSheet`.
   ```bash
   adb shell input tap 998 2120
   sleep 2
   adb exec-out screencap -p > docs/assets/screenshots/02-add-device.png
   ```

3. **03-device-controller.png** — Back out of the sheet (`KEYCODE_BACK`), tap the
   first device row (~270,740) to open `DeviceControllerScreen`.
   ```bash
   adb shell input keyevent KEYCODE_BACK
   sleep 1
   adb shell input tap 270 740
   sleep 2
   adb exec-out screencap -p > docs/assets/screenshots/03-device-controller.png
   ```

4. **04-scene-playing.png** — Tap the "Scenes" toolbar icon (~806,2189) to open
   the scenes bottom sheet, tap "Play" on the first scene (use uiautomator dump
   to find the exact `Play "<name>"` button bounds — they shift depending on
   which scenes are already running), wait ~2-3s, screenshot with the sheet
   still open showing the active scene + animated panels.
   ```bash
   adb shell input tap 806 2189
   sleep 1
   # dump ui.xml, find Play "<FirstSceneName>" bounds, tap its center
   adb shell input tap <x> <y>
   sleep 3
   adb exec-out screencap -p > docs/assets/screenshots/04-scene-playing.png
   ```

5. **05-scene-active.png** — `KEYCODE_BACK` to close the scenes sheet (scene keeps
   running), wait ~1s, screenshot `DeviceControllerScreen` with animated panels
   and no overlay.
   ```bash
   adb shell input keyevent KEYCODE_BACK
   sleep 1
   adb exec-out screencap -p > docs/assets/screenshots/05-scene-active.png
   ```

6. **06-device-settings.png** — Tap the gear icon top-right (~1006,237) to open
   the device's `Settings` menu (Device / Palettes / Scenes / Appearance /
   Debug console).
   ```bash
   adb shell input tap 1006 237
   sleep 1
   adb exec-out screencap -p > docs/assets/screenshots/06-device-settings.png
   ```

7. **07-scene-editor.png** — Tap "Scenes" row (~540,717) to open `ScenesSettingsScreen`
   (Global/Device tabs), switch to the Device tab if needed (~810,383), then tap
   the first scene row (not its play button, e.g. ~300,541) to open
   `SceneEditorScreen` for that scene.
   ```bash
   adb shell input tap 540 714
   sleep 1
   adb shell input tap 810 383
   sleep 1
   adb shell input tap 300 541
   sleep 1
   adb exec-out screencap -p > docs/assets/screenshots/07-scene-editor.png
   ```

## Notes / gotchas

- The app may resume with a scene already actively running from a previous
  session — that's fine, it just means step 4's "first scene" might already
  show a "Stop" icon instead of "Play". Pick whichever scene shows "Play" to
  demonstrate starting one, or just screenshot the already-running state if all
  scenes are active.
- Coordinates above were measured on a 1080x2424 display. If using a different
  device/emulator resolution, re-derive coordinates via `uiautomator dump`
  rather than scaling these numbers.
- `adb exec-out screencap -p > file.png` can return a black frame if the screen
  was just woken from lock — add `adb shell input keyevent KEYCODE_WAKEUP` and a
  short `sleep` before capturing if needed, and verify the screen isn't on the
  lockscreen first.
