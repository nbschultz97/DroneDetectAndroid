#!/usr/bin/env python3
"""Generate assets/drone_ouis.json from an IEEE OUI CSV.

The IEEE registry file `oui.csv` can be downloaded separately and stored
locally for offline use. This script extracts OUI prefixes for known drone
vendors and writes them to a JSON asset.
"""
import argparse
import csv
import json
import re
from pathlib import Path
from typing import List

# Keywords identifying drone manufacturers in the IEEE registry
DRONE_KEYWORDS = ["DJI", "PARROT", "SKYDIO"]

ROOT = Path(__file__).resolve().parent.parent
ASSET_PATH = ROOT / "assets" / "drone_ouis.json"


def extract_ouis(csv_path: Path) -> List[str]:
    ouis: set[str] = set()
    with csv_path.open(newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            name = row.get("Organization Name", "").upper()
            for key in DRONE_KEYWORDS:
                if key in name:
                    assignment = re.sub("[^0-9A-F]", "", row["Assignment"].upper())
                    ouis.add(assignment)
                    break
    return sorted(ouis)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "csv", nargs="?", default="oui.csv", help="Path to downloaded oui.csv"
    )
    parser.add_argument(
        "-o",
        "--output",
        default=str(ASSET_PATH),
        help="Destination JSON asset (default: assets/drone_ouis.json)",
    )
    args = parser.parse_args()
    csv_path = Path(args.csv)
    ouis = extract_ouis(csv_path)
    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(ouis, indent=2) + "\n")
    print(f"wrote {len(ouis)} OUIs -> {out_path}")


if __name__ == "__main__":
    main()
