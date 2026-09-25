"""Decompose the selected Imperial native-entry A* chain into bounded hops."""

from pathlib import Path

import live002_content_graph as graph
import travel_backbone as d1


RAW = "live002-imperial-oversized-01.tsv"
START_ID = "generated.route.content.imperial.arrival"
FARM_ID = "generated.farm.0ac141f2d795f5695b18d40a"
START = (186699, -75915, -2826)
TARGET = (181429, -78686, -2728)
HOP_COLUMNS = ("connector_id", "from_id", "to_id", "from_raw_ordinal", "to_raw_ordinal", "from_x", "from_y", "from_z", "to_x", "to_y", "to_z", "raw_chain_sha256")


def prepare(module):
    registry = module / "docs/phantoms/live-world"
    evidence = module / "dist/game/data/phantoms/evidence"
    raw_path = evidence / RAW
    raw = d1.read_tsv(raw_path)
    if len(raw) != 535 or [int(row["ordinal"]) for row in raw] != list(range(535)):
        raise RuntimeError("Imperial oversized raw chain drift")
    anchor = next(row for row in d1.read_tsv(evidence / "live002-content-anchor-geo.tsv") if row["coverage_key"].startswith("0ac141f2d795f5695b18d40a"))
    if tuple(int(anchor["anchor_" + axis]) for axis in "xyz") != TARGET or anchor["status"] != "VALID":
        raise RuntimeError("Imperial target native farm drift")
    points = [(START_ID, -1, START)]
    for ordinal in (160, 320, 480):
        row = raw[ordinal]
        xyz = tuple(int(row["world_" + axis]) if axis != "z" else int(row["geo_z"]) for axis in "xyz")
        points.append(("generated.route.content.imperial.geo." + d1.key(ordinal, *xyz), ordinal, xyz))
    points.append((FARM_ID, -1, TARGET))
    rows = []
    for (source_id, source_ordinal, source), (target_id, target_ordinal, target) in zip(points, points[1:]):
        rows.append({"connector_id": "content.imperial.oversized." + d1.key(source_id, target_id),
                     "from_id": source_id, "to_id": target_id,
                     "from_raw_ordinal": str(source_ordinal), "to_raw_ordinal": str(target_ordinal),
                     **{"from_" + axis: str(value) for axis, value in zip("xyz", source)},
                     **{"to_" + axis: str(value) for axis, value in zip("xyz", target)},
                     "raw_chain_sha256": d1.sha_file(raw_path)})
    d1.write_tsv(registry / "FINAL_CONTENT_IMPERIAL_OVERSIZED_HOPS.tsv", HOP_COLUMNS, rows)
    d1.write_tsv(evidence / "live002-imperial-oversized-hops-input.tsv", graph.INPUT_COLUMNS,
                 [{"connector_id": row["connector_id"], "from_x": row["from_x"], "from_y": row["from_y"], "from_z": row["from_z"], "from_instance": "0", "to_x": row["to_x"], "to_y": row["to_y"], "to_z": row["to_z"], "to_instance": "0"} for row in rows])
    print("IMPERIAL_OVERSIZED_HOPS", len(rows), "raw_sha=" + d1.sha_file(raw_path))


if __name__ == "__main__":
    prepare(Path(__file__).resolve().parents[2])
