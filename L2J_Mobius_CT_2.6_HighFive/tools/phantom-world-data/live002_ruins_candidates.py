"""Select bounded factual native NPC points along the Ruins destination to farm corridor."""

import argparse
import csv
import math
from pathlib import Path
import xml.etree.ElementTree as ET


START = (-19120, 136816)
FARM = (-33539, 137701)
SOURCES = ("19_21", "18_22")
COLUMNS = ("point_id", "source_path", "source_group", "npc_id", "x", "y", "native_z", "projection", "corridor_distance", "nearest_endpoint_distance")


def candidates(module):
    dx, dy = FARM[0] - START[0], FARM[1] - START[1]
    squared = dx * dx + dy * dy
    seen = set()
    result = []
    for group in SOURCES:
        source = f"data/spawns/Others/{group}.xml"
        for npc in ET.parse(module / "dist/game" / source).getroot().iter("npc"):
            x, y, z = (int(npc.get(axis)) for axis in ("x", "y", "z"))
            nearest = min(math.dist((x, y), START), math.dist((x, y), FARM))
            if nearest > 16000:
                continue
            identity = (source, npc.get("id"), x, y, z)
            if identity in seen:
                continue
            seen.add(identity)
            projection = ((x - START[0]) * dx + (y - START[1]) * dy) / squared
            clipped = max(0, min(1, projection))
            perpendicular = math.dist((x, y), (START[0] + clipped * dx, START[1] + clipped * dy))
            result.append((int(clipped * 8) if clipped < 1 else 7, perpendicular, nearest, int(npc.get("id")), x, y, z, source, group, projection))
    result.sort(key=lambda item: (item[0], item[1], item[2], item[3], item[4], item[5], item[6], item[7]))
    selected = []
    for segment in range(8):
        selected.extend([row for row in result if row[0] == segment][:3])
    if len(selected) > 24:
        raise AssertionError("Route waypoint quota exceeded")
    return selected


def write(module, output):
    rows = candidates(module)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=COLUMNS, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        for index, row in enumerate(rows, 1):
            _, perpendicular, nearest, npc_id, x, y, z, source, group, projection = row
            writer.writerow(dict(point_id=f"route-{index:02}", source_path=source, source_group=group, npc_id=npc_id, x=x, y=y, native_z=z, projection=f"{projection:.6f}", corridor_distance=f"{perpendicular:.2f}", nearest_endpoint_distance=f"{nearest:.2f}"))
    return len(rows)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print("FACTUAL_ROUTE_POINTS", write(Path(__file__).resolve().parents[2], args.output))
