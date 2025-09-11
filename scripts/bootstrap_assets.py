#!/usr/bin/env python3
"""Decode base64-encoded assets into their binary forms.

Designed for air-gapped setups: keeps repository text-only and restores
binary assets locally. Handles the rotor model, PNG icons, and JSON
lookup tables.
"""
import base64
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSET_SRC = ROOT / 'assets'
MODEL_DST = ROOT / 'app' / 'src' / 'main' / 'assets'
DRAWABLE_DST = ROOT / 'app' / 'src' / 'main' / 'res' / 'drawable'


def decode_file(src: Path, dst: Path) -> None:
    data = base64.b64decode(src.read_text())
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_bytes(data)
    print(f"decoded {src} -> {dst}")


def main() -> None:
    # Model
    model_b64 = ASSET_SRC / 'rotor_v1.tflite.b64'
    if model_b64.exists():
        decode_file(model_b64, MODEL_DST / 'rotor_v1.tflite')
    # PNG assets
    for b64_file in ASSET_SRC.glob('*.png.b64'):
        out_name = b64_file.name[:-4]  # strip .b64
        decode_file(b64_file, DRAWABLE_DST / out_name)
    # JSON assets (copied as-is)
    for json_file in ASSET_SRC.glob('*.json'):
        dst = MODEL_DST / json_file.name
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(json_file.read_text())
        print(f"copied {json_file} -> {dst}")


if __name__ == '__main__':
    main()
