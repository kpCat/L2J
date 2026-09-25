"""Bounded alternate native-entry to room checks after first spanning seed fails."""

import argparse
import math
from pathlib import Path

import live002_content_bridge as bridge
import live002_content_graph as graph
import travel_backbone as d1


def prepare(module, family, source_filter=""):
    if family not in ("SSQ_CATACOMBS_NECROPOLIS", "TOWER_OF_INSOLENCE", "IVORY_TOWER", "IMPERIAL_TOMB"):
        raise RuntimeError("Unsupported retry family")
    registry = module / "docs/phantoms/live-world"
    evidence = module / "dist/game/data/phantoms/evidence"
    plan = [row for row in d1.read_tsv(registry / "FINAL_CONTENT_ACCESS_PLAN.tsv") if row["family"] == family and (not source_filter or row["source_family"] == source_filter)]
    if not plan:
        raise RuntimeError("No native source family for seed retry")
    source_by_key = {row["coverage_key"]: row["source_family"] for row in plan}
    anchors = {row["coverage_key"]: row for row in d1.read_tsv(evidence / "live002-content-anchor-geo.tsv") if row["family"] == family and row["coverage_key"] in source_by_key}
    initial = [row for row in d1.read_tsv(registry / "FINAL_CONTENT_GEO_CANDIDATES.tsv") if row["family"] == family]
    _, attempted = bridge.current(module, family)
    seeds = {}
    seed_source = {}
    for row in plan:
        xyz = tuple(int(row["entrance_" + axis]) for axis in "xyz")
        ident = "entry." + d1.key(family, row["source_family"], row["entrance_owner"], *xyz)
        seeds[ident] = (xyz, row["entrance_class"], row["entrance_owner"])
        seed_source[ident] = row["source_family"]
    toi, ivory = graph.native_extra_seeds(module)
    extras = toi if family == "TOWER_OF_INSOLENCE" else ivory if family == "IVORY_TOWER" else []
    seeds.update({ident: (xyz, semantic, owner) for ident, xyz, semantic, owner in extras})
    seed_source.update({ident: plan[0]["source_family"] for ident, _, _, _ in extras})
    options = []
    for ident, (source_xyz, semantic, owner) in sorted(seeds.items()):
        rank = sorted((math.dist(source_xyz, tuple(int(anchor["anchor_" + axis]) for axis in "xyz")), key)
                      for key, anchor in anchors.items() if source_by_key[key] == seed_source[ident])
        for distance, key in rank[:20 if family == "SSQ_CATACOMBS_NECROPOLIS" else 12]:
            to_id = "generated.farm." + key[:24] + ".anchor"
            if (ident, to_id) in attempted:
                continue
            target_xyz = tuple(int(anchors[key]["anchor_" + axis]) for axis in "xyz")
            options.append((distance, ident, to_id, source_xyz, target_xyz, semantic, owner))
    options.sort()
    if len(attempted) + len(options) > graph.LIMITS[family]:
        options = options[:graph.LIMITS[family] - len(attempted)]
    rows = []
    for distance, source_id, target_id, source_xyz, target_xyz, semantic, owner in options:
        rows.append({"connector_id": "content.seed.retry." + d1.key(family, source_id, target_id),
                     "family": family, "source_family": seed_source[source_id],
                     "from_id": source_id, "to_id": target_id,
                     **{"from_" + axis: str(value) for axis, value in zip("xyz", source_xyz)},
                     **{"to_" + axis: str(value) for axis, value in zip("xyz", target_xyz)},
                     "entrance_semantic": semantic, "entrance_owner": owner,
                     "selection": "NATIVE_ENTRY_RETRY", "straight_distance": str(math.ceil(distance))})
    metadata = registry / f"FINAL_CONTENT_{family}_SEED_RETRY_CANDIDATES.tsv"
    input_path = evidence / f"live002-content-{family.lower()}-seed-retry-input.tsv"
    d1.write_tsv(metadata, graph.EDGE_COLUMNS, rows)
    d1.write_tsv(input_path, graph.INPUT_COLUMNS,
                 [{"connector_id": row["connector_id"], "from_x": row["from_x"], "from_y": row["from_y"], "from_z": row["from_z"], "from_instance": "0", "to_x": row["to_x"], "to_y": row["to_y"], "to_z": row["to_z"], "to_instance": "0"} for row in rows])
    print("CONTENT_SEED_RETRY", family, "checks=" + str(len(rows)), "total=" + str(len(attempted) + len(rows)))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--family", required=True)
    parser.add_argument("--source-family", default="")
    args = parser.parse_args()
    prepare(Path(__file__).resolve().parents[2], args.family, args.source_family)
