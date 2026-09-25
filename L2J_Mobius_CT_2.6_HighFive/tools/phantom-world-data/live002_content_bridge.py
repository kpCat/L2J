"""Select one bounded directed check per unreachable room at a component boundary."""

import argparse
from collections import defaultdict, deque
import math
from pathlib import Path

import live002_content_graph as graph
import travel_backbone as d1


def current(module, family):
    registry = module / "docs/phantoms/live-world"
    evidence = module / "dist/game/data/phantoms/evidence"
    pairs = [(registry / "FINAL_CONTENT_GEO_CANDIDATES.tsv", evidence / "live002-content-geo-proof.tsv")]
    for wave in range(1, 13):
        metadata = registry / f"FINAL_CONTENT_{family}_BRIDGE_{wave}_CANDIDATES.tsv"
        proof = evidence / f"live002-content-{family.lower()}-bridge-{wave}-proof.tsv"
        if metadata.exists() != proof.exists():
            raise RuntimeError("Incomplete content bridge wave")
        if metadata.exists():
            pairs.append((metadata, proof))
    seed_metadata = registry / f"FINAL_CONTENT_{family}_SEED_RETRY_CANDIDATES.tsv"
    seed_proof = evidence / f"live002-content-{family.lower()}-seed-retry-proof.tsv"
    if seed_metadata.exists() != seed_proof.exists():
        raise RuntimeError("Incomplete content seed retry")
    if seed_metadata.exists():
        pairs.append((seed_metadata, seed_proof))
    edges, attempted = [], set()
    for metadata, proof_path in pairs:
        proof = {row["connector_id"]: row for row in d1.read_tsv(proof_path)}
        for edge in d1.read_tsv(metadata):
            if edge["family"] != family:
                continue
            result = proof.get(edge["connector_id"])
            if result is None:
                raise RuntimeError("Missing content Geo proof")
            attempted.add((edge["from_id"], edge["to_id"]))
            if result["validation_status"] in ("VALID_DIRECT", "VALID_PATH") and result["collision_proof"] == "STATIC_XML_CLEAR" and result["door_intersections"] == "0" and result["fence_intersections"] == "0" and int(result["path_length"]) > 0:
                edges.append(edge)
    return edges, attempted


def prepare(module, family, wave):
    if family not in graph.LIMITS or wave not in range(1, 13):
        raise RuntimeError("Unsupported family/wave")
    registry = module / "docs/phantoms/live-world"
    evidence = module / "dist/game/data/phantoms/evidence"
    all_first = [row for row in d1.read_tsv(registry / "FINAL_CONTENT_GEO_CANDIDATES.tsv") if row["family"] == family]
    anchors = {row["to_id"]: tuple(int(row["to_" + axis]) for axis in "xyz") for row in all_first}
    source_families = {row["to_id"]: row["source_family"] for row in all_first}
    accepted, attempted = current(module, family)
    if len(attempted) >= graph.LIMITS[family]:
        raise RuntimeError("Content directed-check budget exhausted")
    adjacent = defaultdict(list)
    for edge in accepted:
        adjacent[edge["from_id"]].append(edge["to_id"])
    seeds = {row["from_id"] for row in all_first if row["selection"] == "NATIVE_ENTRY_TO_FIRST_ROOM"}
    seed_retry = registry / f"FINAL_CONTENT_{family}_SEED_RETRY_CANDIDATES.tsv"
    if seed_retry.exists():
        seeds.update(row["from_id"] for row in d1.read_tsv(seed_retry))
    if family == "IMPERIAL_TOMB":
        hops = registry / "FINAL_CONTENT_IMPERIAL_OVERSIZED_HOPS.tsv"
        hop_proof = evidence / "live002-imperial-oversized-hops-proof.tsv"
        if hops.exists() and hop_proof.exists():
            rows = d1.read_tsv(hops)
            proof = {row["connector_id"]: row for row in d1.read_tsv(hop_proof)}
            if len(rows) == 4 and all(proof[row["connector_id"]]["validation_status"] in ("VALID_DIRECT", "VALID_PATH") and proof[row["connector_id"]]["collision_proof"] == "STATIC_XML_CLEAR" for row in rows):
                seeds.add(rows[-1]["to_id"] + ".anchor")
    seen, queue = set(seeds), deque(sorted(seeds))
    while queue:
        source = queue.popleft()
        for target in adjacent[source]:
            if target not in seen:
                seen.add(target)
                queue.append(target)
    reached = {key: xyz for key, xyz in anchors.items() if key in seen}
    if not reached:
        print("CONTENT_BRIDGE_NO_REACHED_FARM", family)
        return
    options = []
    for target, target_xyz in anchors.items():
        if target in seen:
            continue
        choices = sorted((math.dist(source_xyz, target_xyz), source, source_xyz) for source, source_xyz in reached.items() if (source, target) not in attempted and source_families[source] == source_families[target])
        if choices:
            distance, source, source_xyz = choices[0]
            base = next(row for row in all_first if row["to_id"] == target)
            options.append((distance, source, target, source_xyz, target_xyz, base))
    selected = []
    cap = min(128, graph.LIMITS[family] - len(attempted))
    for distance, source, target, source_xyz, target_xyz, base in sorted(options)[:cap]:
        selected.append({"connector_id": "content.bridge." + d1.key(family, wave, source, target),
                         "family": family, "source_family": base["source_family"], "from_id": source, "to_id": target,
                         **{"from_" + axis: str(value) for axis, value in zip("xyz", source_xyz)},
                         **{"to_" + axis: str(value) for axis, value in zip("xyz", target_xyz)},
                         "entrance_semantic": "WALK_PASSAGE", "entrance_owner": base["entrance_owner"],
                         "selection": "COMPONENT_BOUNDARY_BRIDGE", "straight_distance": str(math.ceil(distance))})
    if not selected:
        print("CONTENT_BRIDGE_NO_MORE_CANDIDATES", family, "reachable=" + str(len(reached)))
        return
    metadata = registry / f"FINAL_CONTENT_{family}_BRIDGE_{wave}_CANDIDATES.tsv"
    input_path = evidence / f"live002-content-{family.lower()}-bridge-{wave}-input.tsv"
    d1.write_tsv(metadata, graph.EDGE_COLUMNS, selected)
    d1.write_tsv(input_path, graph.INPUT_COLUMNS,
                 [{"connector_id": edge["connector_id"], "from_x": edge["from_x"], "from_y": edge["from_y"], "from_z": edge["from_z"], "from_instance": "0", "to_x": edge["to_x"], "to_y": edge["to_y"], "to_z": edge["to_z"], "to_instance": "0"} for edge in selected])
    print("CONTENT_BRIDGE_PREPARED", family, "wave=" + str(wave), "reachable=" + str(len(reached)), "checks=" + str(len(selected)), "total_checks=" + str(len(attempted) + len(selected)))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--family", required=True)
    parser.add_argument("--wave", type=int, required=True)
    args = parser.parse_args()
    prepare(Path(__file__).resolve().parents[2], args.family, args.wave)
