# DroneDetectAndroid

This repo hosts Android-side utilities for drone rotor detection.
Binary assets (TensorFlow Lite model and icons) are stored in base64 to
keep the repo lean for air-gapped deployments.

## Bootstrap

After cloning, restore the binary assets:

```bash
python3 scripts/bootstrap_assets.py
```

This will recreate the `rotor_v1.tflite` model and the required PNG
drawables under `app/src/main/...`.

## Pipeline

1. **Asset bootstrap** – Decode the model and icons from `assets/*.b64` using the script above. Generated files land under `app/src/main/...`.
2. **Runtime wiring** – `MainActivity` creates a `DroneSignalDetector`, which launches a coroutine to fetch flight data and schedule Wi‑Fi scans.
3. **Scan handling** – `WifiScanReceiver` receives scan results. Future work will feed the TFLite model for rotor classification.

## Testing

Verify the asset bootstrap step restores the binaries:

```bash
python3 scripts/bootstrap_assets.py
```

Each asset should report a `decoded` message. Remove the generated `app/src/main/assets` and `app/src/main/res` directories after testing to keep the repo text‑only.
