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
