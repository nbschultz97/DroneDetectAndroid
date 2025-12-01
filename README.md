# DroneDetectAndroid

Android-side utilities for rotor detection and Wi‑Fi reconnaissance. Assets ship as base64 to keep the repo lean for air-gapped deployments and are restored automatically during Gradle builds.

## Bootstrap

After cloning, restore the binary assets (TensorFlow Lite model and PNG drawables):

```bash
python3 scripts/bootstrap_assets.py
```

Gradle also depends on this script via the `preBuild` hook, so IDE and CI builds will automatically decode assets into `app/src/main/...` if Python is available.

## Pipeline

1. **Asset bootstrap** – Decode the model and icons from `assets/*.b64` using the script above (or let Gradle call it).
2. **Runtime wiring** – `MainActivity` requests Wi‑Fi/location permissions, wires a simple UI, and calls `DroneSignalDetector.startScan()`.
3. **Scan handling** – `DroneSignalDetector` schedules repeated `WifiManager.startScan()` calls, ingests stub flight data, and surfaces scan results plus the last telemetry snapshot to the UI via callbacks.

## Building

This repo ships a minimal Gradle wrapper script for offline/air-gapped work. Supply a compatible `gradle/wrapper/gradle-wrapper.jar` (from Gradle 8.2.1) in your environment, then run:

```bash
./gradlew assembleDebug
```

If you prefer a hosted toolchain, open the project in Android Studio (Giraffe+), accept SDK prompts, and build the `app` module. Remember to run the asset bootstrap script if Python is missing from your build host.

## Testing

To validate the asset bootstrap step:

```bash
python3 scripts/bootstrap_assets.py
```

Each asset should report a `decoded` message. Remove the generated `app/src/main/assets` and `app/src/main/res/drawable` contents after testing to keep the repo text‑only.
