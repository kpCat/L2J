"""Deterministic, source-derived LIVE-002-D1 travel evidence builder."""

import argparse
import csv
import hashlib
import json
import math
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict, deque
from pathlib import Path


ACCEPTED_C = {
    "ANCHOR_GEODATA_VALIDATION.tsv": "42a3987c12a74cc9cad369e919680ef6798488262eee4dcfbddc3ef391b79b04",
    "ROUTE_GEODATA_VALIDATION.tsv": "1c1d4b19669d704c162e971fcc6a24bdd3cb1325bc3783d9cfadc552a326a22b",
    "RUINS_GEODATA_VALIDATION.tsv": "785d75c6a5909180bb88c3069786ef0b663bbbbbb1d1f2cb6da10f5214c5fb4b",
    "high-five-generated-01.xml": "9242aaf4f4d711cbf99311f2be4f049110c83acb1e242bd7ca22dbcd03e051c4",
    "VALIDATED_TOPOLOGY_MANIFEST.json": "82e76d497c496cdd69c13df49c98dcfff25e4b26bff813ee8acc97b5724775ce",
}
ACCEPTED_AB = {
    "WORLD_COVERAGE.tsv": "84af90619ff959af64119ad079cf0f2b85692dec2aa985d2314185b059c76c8e",
    "WORLD_DATA_MANIFEST.json": "94ac62cbe005e6df43529506fbb7af6511525a8d3bad355e66413583788a6bf4",
    "WORLD_SPATIAL_ASSOCIATIONS.tsv": "8ce940eeba2285ced494b5880be5f624eb8eba91a4ed24b5c7d5688e15600935",
    "TOPOLOGY_CANDIDATES.tsv": "aec029b27e0f9e8f04a86afc16b3c1a5e7e6ddbaedc7a37f25b0b2f08b7c873f",
    "ROUTE_CANDIDATES.tsv": "b757c0690c4890f5d3464cc921897087841261766f560b838f90a80f5a829bfa",
    "TOPOLOGY_CANDIDATE_MANIFEST.json": "d5ebd388bf59a9ee24c59d8bfe801c72c0f2783a2c92c49f4b0a1d5948039a6a",
}
BANDS = ((1, 5), (6, 10), (11, 19), (20, 39), (40, 51), (52, 60), (61, 75), (76, 80), (81, 85))
ROLES = {"ROUTE", "RESPAWN", "CITY_CENTER", "GATEKEEPER", "FARMING"}
CELL = 8192
RADIUS = 8192
FANOUT = 8
FACT_COLUMNS = ("fact_key", "teleporter_npc_id", "teleporter_spawn_key", "teleporter_spawn_x", "teleporter_spawn_y", "teleporter_spawn_z", "teleporter_instance_id", "list_name", "teleport_type", "destination_index", "destination_name", "destination_x", "destination_y", "destination_z", "fee_id", "fee_count", "castle_ids", "source_path", "spawn_source_path", "availability_class", "condition_summary")
CONNECTOR_COLUMNS = ("connector_id", "connector_kind", "from_type", "from_id", "to_type", "to_id", "instance_id", "from_x", "from_y", "from_z", "to_x", "to_y", "to_z", "map_region_from", "map_region_to", "straight_distance", "validation_status", "path_length", "path_segments", "source_refs", "reason")
TRANSITION_COLUMNS = ("transition_id", "from_gk_fact_key", "to_destination_fact_key", "teleporter_npc_id", "teleport_type", "destination_name", "fee_id", "fee_count", "condition_summary", "source_path", "transition_status", "reason")
REACH_COLUMNS = ("start_family", "start_anchor_ids", "level_band", "published_farming_groups", "walk_only_reachable", "normal_gk_reachable", "remaining_unreachable", "distinct_gk_transitions", "disconnected_components", "witness_farm_coverage_key", "witness_path")


def sha_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha_source(path):
    """Keep source fingerprints stable across Git's LF/CRLF checkout policy."""
    normalized = Path(path).read_text(encoding="utf-8").replace("\r\n", "\n")
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def key(*parts):
    return hashlib.sha256("|".join(map(str, parts)).encode("utf-8")).hexdigest()[:24]


def read_tsv(path):
    with open(path, encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def write_tsv(path, columns, rows):
    with open(path, "w", encoding="utf-8", newline="\n") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns, delimiter="\t", lineterminator="\n", extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({column: row.get(column, "") for column in columns})


def verify_c(module):
    registry = module / "docs/phantoms/live-world"
    for name, expected in ACCEPTED_AB.items():
        path = registry / name
        if sha_file(path) != expected:
            raise RuntimeError(f"BLOCKED_INPUT_DRIFT: {path}")
    for name, expected in ACCEPTED_C.items():
        path = module / "dist/game/data/phantoms/topology" / name if name.endswith(".xml") else registry / name
        if sha_file(path) != expected:
            raise RuntimeError(f"BLOCKED_INPUT_DRIFT: {path}")


def source_path(data_root, path):
    return "data/" + path.relative_to(data_root).as_posix()


def extract_spawns(data_root):
    by_npc = defaultdict(list)
    for path in sorted((data_root / "spawns").rglob("*.xml")):
        root = ET.parse(path).getroot()
        if root.get("enabled", "true").lower() != "true":
            continue
        relative = source_path(data_root, path)
        for group_index, group in enumerate(root.findall("spawn")):
            for npc_index, npc in enumerate(group.findall("npc")):
                npc_id = npc.get("id", "")
                if not npc_id:
                    continue
                spawn_key = "spawn." + key(relative, group_index, npc_index, npc_id)
                row = {"key": spawn_key, "x": npc.get("x", ""), "y": npc.get("y", ""), "z": npc.get("z", ""),
                       "instance": "0", "source": relative, "group": group.get("zone", group.get("name", ""))}
                by_npc[npc_id].append(row)
    return by_npc


def extract_facts(data_root):
    spawns = extract_spawns(data_root)
    facts = []
    for path in sorted((data_root / "teleporters").rglob("*.xml")):
        relative = source_path(data_root, path)
        root = ET.parse(path).getroot()
        for npc in root.findall("npc"):
            npc_ids = [npc.get("id", "")] + [alias.get("id", "") for alias in npc.findall("npcs/npc")]
            for teleport in npc.findall("teleport"):
                kind = teleport.get("type", "")
                list_name = teleport.get("name", kind)
                for index, destination in enumerate(teleport.findall("location")):
                    for npc_id in npc_ids:
                        associated = spawns.get(npc_id) or [{"key": "", "x": "", "y": "", "z": "", "instance": "", "source": ""}]
                        for spawn in associated:
                            available = ("BLOCKED_SOURCE" if not spawn["key"] or not all(spawn[a] for a in ("x", "y", "z"))
                                         else "NORMAL" if kind == "NORMAL" else "NOBLESSE" if kind.startswith("NOBLES_")
                                         else "SPECIAL_OR_UNMODELED")
                            contract = "TeleportHolder.doTeleport:alive,castle-siege;TeleportHolder.shouldPayFee:fee"
                            if kind == "NORMAL":
                                contract += ",normal-source-siege,karma,combat-flag,MAX_FREE_TELEPORT_LEVEL,subclass;TeleportHolder.calculateFee:discount"
                            elif kind.startswith("NOBLES_"):
                                contract += ",noblesse"
                            fact = {
                                "teleporter_npc_id": npc_id, "teleporter_spawn_key": spawn["key"],
                                "teleporter_spawn_x": spawn["x"], "teleporter_spawn_y": spawn["y"], "teleporter_spawn_z": spawn["z"],
                                "teleporter_instance_id": spawn["instance"], "list_name": list_name, "teleport_type": kind,
                                "destination_index": str(index), "destination_name": destination.get("name", ""),
                                "destination_x": destination.get("x", ""), "destination_y": destination.get("y", ""), "destination_z": destination.get("z", ""),
                                "fee_id": destination.get("feeId", "57"), "fee_count": destination.get("feeCount", "0"),
                                "castle_ids": destination.get("castleId", ""), "source_path": relative, "spawn_source_path": spawn["source"],
                                "availability_class": available, "condition_summary": contract,
                            }
                            fact["fact_key"] = "fact." + key(relative, npc_id, list_name, kind, index, spawn["key"])
                            facts.append(fact)
    list_sources = defaultdict(set)
    for fact in facts:
        list_sources[(fact["teleporter_npc_id"], fact["list_name"])].add(fact["source_path"])
    for fact in facts:
        if len(list_sources[(fact["teleporter_npc_id"], fact["list_name"])]) > 1:
            fact["availability_class"] = "BLOCKED_SOURCE"
            fact["condition_summary"] += ";TeleporterData:duplicate-list-source-order"
    facts.sort(key=lambda row: row["fact_key"])
    if len({row["fact_key"] for row in facts}) != len(facts):
        raise RuntimeError("Duplicate native fact keys")
    return facts


def transition_status(fact):
    if fact["availability_class"] == "BLOCKED_SOURCE":
        return "BLOCKED_SOURCE"
    if fact["teleport_type"] == "NORMAL":
        return "FACTUAL_NORMAL"
    if fact["teleport_type"] in ("NOBLES_TOKEN", "NOBLES_ADENA"):
        return "FACTUAL_CONDITIONAL"
    return "UNSUPPORTED_SEMANTICS"


def endpoints(facts):
    result = {}
    for fact in facts:
        if fact["availability_class"] == "BLOCKED_SOURCE":
            continue
        spawn_id = fact["teleporter_spawn_key"]
        result[spawn_id] = {"id": spawn_id, "type": "GK", "x": int(fact["teleporter_spawn_x"]), "y": int(fact["teleporter_spawn_y"]), "z": int(fact["teleporter_spawn_z"]), "instance": int(fact["teleporter_instance_id"]), "source": fact["spawn_source_path"]}
        dest_id = "dest." + key(fact["destination_x"], fact["destination_y"], fact["destination_z"], fact["teleporter_instance_id"])
        fact["destination_key"] = dest_id
        result[dest_id] = {"id": dest_id, "type": "DEST", "x": int(fact["destination_x"]), "y": int(fact["destination_y"]), "z": int(fact["destination_z"]), "instance": int(fact["teleporter_instance_id"]), "source": fact["source_path"]}
    return result


def topology(module):
    anchors = {}
    edges = []
    directory = module / "dist/game/data/phantoms/topology"
    for name in ("high-five-core.xml", "high-five-siege.xml", "high-five-generated-01.xml"):
        root = ET.parse(directory / name).getroot()
        for anchor in root.findall("anchor"):
            anchors[anchor.get("id")] = {"id": anchor.get("id"), "type": "ANCHOR", "role": anchor.get("role"),
                "x": int(anchor.get("x")), "y": int(anchor.get("y")), "z": int(anchor.get("z")),
                "instance": int(anchor.get("instanceId")), "source": "|".join(s.get("path", "") for s in anchor.findall("source")),
                "map_region": anchor.get("mapRegionLocId", ""), "tags": anchor.get("tags", "")}
        for edge in root.findall("edge"):
            if edge.get("mode") == "BACKGROUND" and edge.get("backgroundEligible") == "true":
                a, b = edge.get("fromAnchorId"), edge.get("toAnchorId")
                if a in anchors and b in anchors:
                    edges.append((a, b, edge.get("id")))
                    if edge.get("bidirectional") == "true":
                        edges.append((b, a, edge.get("id") + ":reverse"))
    return anchors, edges


def is_proven(proof):
    return proof["validation_status"] in ("VALID_DIRECT", "VALID_PATH")


def shortest_paths(start, edges, include_gk):
    adjacent = defaultdict(list)
    for source, target, label in edges:
        if include_gk or not label.startswith("gk:"):
            adjacent[source].append((target, label))
    found = {start: ([start], [])}
    queue = deque([start])
    while queue:
        source = queue.popleft()
        for target, label in sorted(adjacent[source]):
            if target not in found:
                vertices, labels = found[source]
                found[target] = (vertices + [target], labels + [label])
                queue.append(target)
    return found


def map_regions(data_root):
    mapping = defaultdict(set)
    for path in sorted((data_root / "mapregion").rglob("*.xml")):
        for region in ET.parse(path).getroot().findall("region"):
            for cell in region.findall("map"):
                index = (int(cell.get("X")), int(cell.get("Y")))
                mapping[index].add(region.get("locId"))
    return mapping


def region_for(point, mapping):
    return "|".join(sorted(mapping.get(((point["x"] >> 15) + 20, (point["y"] >> 15) + 18), ())))


def build_index(anchors):
    buckets = defaultdict(list)
    for anchor in anchors:
        if anchor["role"] in ROLES:
            buckets[(anchor["instance"], anchor["x"] // CELL, anchor["y"] // CELL)].append(anchor)
    return buckets


def indexed_candidates(endpoint, buckets):
    cx, cy = endpoint["x"] // CELL, endpoint["y"] // CELL
    near = []
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for anchor in buckets.get((endpoint["instance"], cx + dx, cy + dy), ()):
                distance = math.hypot(endpoint["x"] - anchor["x"], endpoint["y"] - anchor["y"])
                if distance <= RADIUS:
                    near.append((distance, anchor["id"], anchor))
    return [row[2] for row in sorted(near)[:FANOUT]]


def candidate_rows(module, facts):
    anchors, _ = topology(module)
    points = endpoints(facts)
    buckets = build_index(anchors.values())
    regions = map_regions(module / "dist/game/data")
    rows = []
    for endpoint in sorted(points.values(), key=lambda p: p["id"]):
        for anchor in indexed_candidates(endpoint, buckets):
            pairs = ((anchor, endpoint, "ANCHOR_TO_GK" if endpoint["type"] == "GK" else "ANCHOR_TO_DEST"),
                     (endpoint, anchor, "GK_TO_ANCHOR" if endpoint["type"] == "GK" else "DEST_TO_ANCHOR"))
            for source, target, kind in pairs:
                row = {"connector_id": "connector." + key(kind, source["id"], target["id"]), "connector_kind": kind,
                    "from_type": source["type"], "from_id": source["id"], "to_type": target["type"], "to_id": target["id"],
                    "instance_id": str(source["instance"]), "from_x": str(source["x"]), "from_y": str(source["y"]), "from_z": str(source["z"]),
                    "to_x": str(target["x"]), "to_y": str(target["y"]), "to_z": str(target["z"]),
                    "map_region_from": region_for(source, regions), "map_region_to": region_for(target, regions),
                    "straight_distance": str(math.ceil(math.dist((source["x"], source["y"], source["z"]), (target["x"], target["y"], target["z"])))),
                    "source_refs": source["source"] + "|" + target["source"], "validation_status": "UNPROVEN", "reason": "PENDING_GEOENGINE"}
                rows.append(row)
    rows.sort(key=lambda row: row["connector_id"])
    if len({row["connector_id"] for row in rows}) != len(rows):
        raise RuntimeError("Duplicate connector candidate")
    return rows


def source_hashes(root, subdirectory):
    return [{"path": source_path(root, path), "sha256": sha_file(path)} for path in sorted((root / subdirectory).rglob("*.xml"))]


def prepare(module, work):
    verify_c(module)
    data = module / "dist/game/data"
    facts = extract_facts(data)
    rows = candidate_rows(module, facts)
    work.mkdir(parents=True, exist_ok=True)
    write_tsv(work / "facts.tsv", FACT_COLUMNS, facts)
    write_tsv(work / "candidates.tsv", CONNECTOR_COLUMNS, rows)
    columns = ("connector_id", "from_x", "from_y", "from_z", "from_instance", "to_x", "to_y", "to_z", "to_instance")
    write_tsv(work / "geo-input.tsv", columns, [dict(row, from_instance=row["instance_id"], to_instance=row["instance_id"]) for row in rows])
    print(f"TRAVEL_PREPARE facts={len(facts)} connector_candidates={len(rows)} endpoints={len(endpoints(facts))}")


def transitions(facts):
    rows = []
    for fact in facts:
        status = transition_status(fact)
        row = {"transition_id": "transition." + key(fact["fact_key"]), "from_gk_fact_key": fact["teleporter_spawn_key"],
               "to_destination_fact_key": fact.get("destination_key", ""), "teleporter_npc_id": fact["teleporter_npc_id"],
               "teleport_type": fact["teleport_type"], "destination_name": fact["destination_name"],
               "fee_id": fact["fee_id"], "fee_count": fact["fee_count"], "condition_summary": fact["condition_summary"],
               "source_path": fact["source_path"], "transition_status": status,
               "reason": "NATIVE_FACT" if status in ("FACTUAL_NORMAL", "FACTUAL_CONDITIONAL") else fact["availability_class"]}
        rows.append(row)
    return sorted(rows, key=lambda row: row["transition_id"])


def normal_transition_edges(transition_rows):
    return [(row["from_gk_fact_key"], row["to_destination_fact_key"], "gk:" + row["transition_id"])
            for row in transition_rows if row["transition_status"] == "FACTUAL_NORMAL" and row["from_gk_fact_key"] and row["to_destination_fact_key"]]


def published_farms(module, anchors):
    candidates = read_tsv(module / "docs/phantoms/live-world/TOPOLOGY_CANDIDATES.tsv")
    coverage = {row["coverage_key"]: row for row in read_tsv(module / "docs/phantoms/live-world/WORLD_COVERAGE.tsv")}
    farm_rows = []
    for row in candidates:
        anchor = row["anchor_id"]
        if anchor in anchors and anchors[anchor]["role"] == "FARMING" and row["coverage_key"] in coverage:
            fact = coverage[row["coverage_key"]]
            if fact["classification"] == "ORDINARY_WORLD" and fact["status"] == "READY_STATIC":
                farm_rows.append({"coverage_key": row["coverage_key"], "anchor": anchor,
                                  "minimum": int(fact["npc_level_min"]), "maximum": int(fact["npc_level_max"])})
    return sorted(farm_rows, key=lambda row: row["coverage_key"])


def roots(anchors):
    by_family = defaultdict(list)
    for anchor in anchors.values():
        if anchor["id"].startswith("population.ingress.") and "population-ingress" in anchor["tags"]:
            family = anchor["id"].removeprefix("population.ingress.").rsplit(".", 1)[0]
            by_family[family].append(anchor["id"])
    return {family: sorted(ids) for family, ids in sorted(by_family.items())}


def shortest_multi(starts, edges, normal):
    adjacent = defaultdict(list)
    for source, target, label in edges:
        if normal or not label.startswith("gk:"):
            adjacent[source].append((target, label))
    found = {start: ([start], []) for start in sorted(starts)}
    queue = deque(sorted(starts))
    while queue:
        source = queue.popleft()
        for target, label in sorted(adjacent[source]):
            if target not in found:
                vertices, labels = found[source]
                found[target] = (vertices + [target], labels + [label])
                queue.append(target)
    return found


def components(vertices, edges):
    adjacency = defaultdict(set)
    for source, target, _ in edges:
        adjacency[source].add(target)
        adjacency[target].add(source)
    component = {}
    for root in sorted(vertices):
        if root in component:
            continue
        component_id = "component." + key(root)
        pending = [root]
        component[root] = component_id
        while pending:
            for neighbor in sorted(adjacency[pending.pop()]):
                if neighbor not in component:
                    component[neighbor] = component_id
                    pending.append(neighbor)
    return component


def graph_outputs(module, facts, connector_rows, transition_rows):
    anchors, background = topology(module)
    point_by_id = endpoints(facts)
    movement = list(background)
    for row in connector_rows:
        if is_proven(row):
            movement.append((row["from_id"], row["to_id"], "connector:" + row["connector_id"]))
    normal_edges = normal_transition_edges(transition_rows)
    all_edges = movement + normal_edges
    family_roots = roots(anchors)
    if set(family_roots) != {"human-fighter", "human-mystic", "elf", "dark-elf", "orc", "dwarf", "kamael"}:
        raise RuntimeError(f"Unexpected factual ingress families: {sorted(family_roots)}")
    farms = published_farms(module, anchors)
    component = components(set(anchors) | set(point_by_id), all_edges)
    transition_by_id = {row["transition_id"]: row for row in transition_rows}
    rows = []
    for family, start_ids in list(family_roots.items()) + [("GLOBAL", sorted({r for family in family_roots.values() for r in family}))]:
        walk = shortest_multi(start_ids, movement, False)
        normal = shortest_multi(start_ids, all_edges, True)
        for minimum, maximum in BANDS:
            band_farms = [farm for farm in farms if farm["minimum"] <= maximum and farm["maximum"] >= minimum]
            walk_farms = [farm for farm in band_farms if farm["anchor"] in walk]
            normal_farms = [farm for farm in band_farms if farm["anchor"] in normal]
            unreachable = [farm for farm in band_farms if farm["anchor"] not in normal]
            used = set()
            for farm in normal_farms:
                used.update(label[3:] for label in normal[farm["anchor"]][1] if label.startswith("gk:"))
            witness = next((farm for farm in normal_farms if farm["anchor"] not in walk), None)
            if witness is None and normal_farms:
                witness = normal_farms[0]
            path = ""
            if witness:
                vertices, labels = normal[witness["anchor"]]
                segments = [vertices[0]]
                for label, vertex in zip(labels, vertices[1:]):
                    if label.startswith("gk:"):
                        transition = transition_by_id[label[3:]]
                        label += f"[npc={transition['teleporter_npc_id']},feeId={transition['fee_id']},feeCount={transition['fee_count']},type={transition['teleport_type']}]"
                    segments.append(label + "->" + vertex)
                path = "|".join(segments)
            rows.append({"start_family": family, "start_anchor_ids": "|".join(start_ids), "level_band": f"{minimum}-{maximum}",
                "published_farming_groups": len(band_farms), "walk_only_reachable": len(walk_farms),
                "normal_gk_reachable": len(normal_farms), "remaining_unreachable": len(unreachable),
                "distinct_gk_transitions": len(used), "disconnected_components": len({component[farm["anchor"]] for farm in unreachable}),
                "witness_farm_coverage_key": witness["coverage_key"] if witness else "", "witness_path": path if path else "UNREACHABLE"})
    ruins = [fact for fact in facts if fact["destination_name"] == "Ruins of Despair" and fact["teleport_type"] == "NORMAL"]
    ruins_proof = []
    for fact in ruins:
        dest_id = fact["destination_key"]
        for family, start_ids in family_roots.items():
            reachable = shortest_multi(start_ids, all_edges, True)
            coverage_keys = sorted(farm["coverage_key"] for farm in farms if farm["anchor"] in reachable and dest_id in reachable and dest_id in reachable[farm["anchor"]][0])
            ruins_proof.append({"family": family, "fact_key": fact["fact_key"], "destination_key": dest_id,
                                "gk_access": fact["teleporter_spawn_key"] in reachable,
                                "destination_connector": any(row["from_id"] == dest_id and is_proven(row) for row in connector_rows),
                                "coverage_keys": coverage_keys})
    root_rows = []
    for family, ids in family_roots.items():
        for anchor_id in ids:
            anchor = anchors[anchor_id]
            root_rows.append({"family": family, "anchor_id": anchor_id, "source_refs": anchor["source"],
                "x": anchor["x"], "y": anchor["y"], "z": anchor["z"], "component_id": component[anchor_id]})
    return rows, root_rows, ruins_proof, len(farms), len(background)


def aggregate_hash(entries):
    return hashlib.sha256("".join(entry["path"] + "\t" + entry["sha256"] + "\n" for entry in entries).encode("utf-8")).hexdigest()


def finalize(module, work, output):
    verify_c(module)
    facts = read_tsv(work / "facts.tsv")
    candidates = read_tsv(work / "candidates.tsv")
    proofs = read_tsv(work / "geo-proof.tsv")
    proof_by_id = {row["connector_id"]: row for row in proofs}
    if len(proof_by_id) != len(proofs) or set(proof_by_id) != {row["connector_id"] for row in candidates}:
        raise RuntimeError("Missing/duplicate GeoEngine connector proofs")
    for row in candidates:
        proof = proof_by_id[row["connector_id"]]
        row.update(proof)
        row["reason"] = "GEOENGINE_PROVEN" if is_proven(row) else proof["validation_status"]
    endpoints(facts)
    transition_rows = transitions(facts)
    reach_rows, root_rows, ruins, farm_count, background_count = graph_outputs(module, facts, candidates, transition_rows)
    output.mkdir(parents=True, exist_ok=True)
    write_tsv(output / "GATEKEEPER_FACTS.tsv", FACT_COLUMNS, facts)
    write_tsv(output / "TRAVEL_CONNECTORS.tsv", CONNECTOR_COLUMNS, candidates)
    write_tsv(output / "TRAVEL_TRANSITIONS.tsv", TRANSITION_COLUMNS, transition_rows)
    write_tsv(output / "TRAVEL_REACHABILITY.tsv", REACH_COLUMNS, reach_rows)
    data = module / "dist/game/data"
    native_sources = {name: source_hashes(data, name) for name in ("teleporters", "spawns", "mapregion")}
    geodata = [{"path": source_path(data, path), "sha256": sha_file(path)} for path in sorted((data / "geodata").glob("*.l2j"))]
    topology_hashes = {name: sha_file(data / "phantoms/topology" / name) for name in ("high-five-core.xml", "high-five-siege.xml", "high-five-generated-01.xml")}
    status_count = Counter(row["validation_status"] for row in candidates)
    transition_count = Counter(row["transition_status"] for row in transition_rows)
    fee_count = Counter("ZERO" if int(row["fee_count"]) == 0 else "ADENA" if row["fee_id"] == "57" else "OTHER_ITEM" for row in transition_rows)
    manifest = {
        "schema": "LIVE-002-D1/1", "accepted_ab_sha256": ACCEPTED_AB, "accepted_c_sha256": ACCEPTED_C,
        "input_aggregate_sha256": {**{name: aggregate_hash(entries) for name, entries in native_sources.items()}, "geodata": aggregate_hash(geodata)},
        "topology_sha256": topology_hashes,
        "source_hash_policy": "UTF-8, LF-normalized before SHA-256",
        "generator_sha256": sha_source(module / "tools/phantom-world-data/travel_backbone.py"),
        "test_sha256": sha_source(module / "tools/phantom-world-data/test_travel_backbone.py"),
        "geoprobe_sha256": sha_source(module / "test/java/org/l2jmobius/tests/phantoms/PhantomTravelGeoProbe.java"),
        "script_sha256": {name: sha_source(module / "tools/phantom-world-data" / name) for name in
                          ("Generate-TravelBackbone.ps1", "Test-TravelBackbone.ps1", "Validate-TravelBackbone.ps1")},
        "bounds": {"cell_units": CELL, "radius_units": RADIUS, "nearest_anchor_fanout": FANOUT},
        "counts": {"native_relationships": len({(row["source_path"], row["teleporter_npc_id"], row["list_name"], row["destination_index"]) for row in facts}),
                   "facts": len(facts), "transitions": len(transition_rows), "connectors": len(candidates),
                   "connector_status": dict(sorted(status_count.items())), "transition_status": dict(sorted(transition_count.items())),
                   "availability_class": dict(sorted(Counter(row["availability_class"] for row in facts).items())),
                   "fee_classes": dict(sorted(fee_count.items())), "published_farming_groups": farm_count,
                   "background_directed_edges": background_count},
        "roots": root_rows, "reachability": reach_rows, "ruins_of_despair": ruins,
        "output_sha256": {name: sha_file(output / name) for name in ("GATEKEEPER_FACTS.tsv", "TRAVEL_CONNECTORS.tsv", "TRAVEL_TRANSITIONS.tsv", "TRAVEL_REACHABILITY.tsv")},
    }
    (output / "TRAVEL_BACKBONE_MANIFEST.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"TRAVEL_FINALIZE facts={len(facts)} connectors={len(candidates)} proven={sum(is_proven(row) for row in candidates)} farms={farm_count}")


def validate(module, output, proof_file):
    verify_c(module)
    facts = read_tsv(output / "GATEKEEPER_FACTS.tsv")
    expected = extract_facts(module / "dist/game/data")
    if facts != expected:
        raise RuntimeError("Native teleporter/spawn fact accounting mismatch")
    transitions_found = read_tsv(output / "TRAVEL_TRANSITIONS.tsv")
    endpoints(facts)
    if transitions_found != transitions(facts):
        raise RuntimeError("Transition accounting mismatch")
    connectors = read_tsv(output / "TRAVEL_CONNECTORS.tsv")
    expected_candidates = candidate_rows(module, facts)
    if [row["connector_id"] for row in connectors] != [row["connector_id"] for row in expected_candidates]:
        raise RuntimeError("Connector candidate accounting mismatch")
    proofs = read_tsv(proof_file)
    proof_by_id = {row["connector_id"]: row for row in proofs}
    if len(proofs) != len(proof_by_id) or set(proof_by_id) != {row["connector_id"] for row in connectors}:
        raise RuntimeError("GeoEngine proof accounting mismatch")
    for row, expected_row in zip(connectors, expected_candidates):
        for column in CONNECTOR_COLUMNS:
            if column not in ("validation_status", "path_length", "path_segments", "reason") and row[column] != str(expected_row.get(column, "")):
                raise RuntimeError(f"Connector source fact mismatch: {row['connector_id']}:{column}")
        if row["validation_status"] not in ("VALID_DIRECT", "VALID_PATH") and not row["reason"]:
            raise RuntimeError("Unproven connector without reason")
        proof = proof_by_id[row["connector_id"]]
        for column in ("validation_status", "path_length", "path_segments"):
            if row[column] != proof[column]:
                raise RuntimeError(f"GeoEngine proof mismatch: {row['connector_id']}:{column}")
        expected_reason = "GEOENGINE_PROVEN" if is_proven(row) else proof["validation_status"]
        if row["reason"] != expected_reason:
            raise RuntimeError(f"GeoEngine proof mismatch: {row['connector_id']}:reason")
    reach, roots_found, ruins, farm_count, background_count = graph_outputs(module, facts, connectors, transitions_found)
    if read_tsv(output / "TRAVEL_REACHABILITY.tsv") != [{key: str(row.get(key, "")) for key in REACH_COLUMNS} for row in reach]:
        raise RuntimeError("Reachability mismatch")
    manifest = json.loads((output / "TRAVEL_BACKBONE_MANIFEST.json").read_text(encoding="utf-8"))
    data = module / "dist/game/data"
    source_aggregates = {name: aggregate_hash(source_hashes(data, name)) for name in ("teleporters", "spawns", "mapregion")}
    geodata = [{"path": source_path(data, path), "sha256": sha_file(path)} for path in sorted((data / "geodata").glob("*.l2j"))]
    source_aggregates["geodata"] = aggregate_hash(geodata)
    if manifest["input_aggregate_sha256"] != source_aggregates:
        raise RuntimeError("Native source/geodata aggregate drift")
    if manifest["topology_sha256"] != {name: sha_file(data / "phantoms/topology" / name) for name in ("high-five-core.xml", "high-five-siege.xml", "high-five-generated-01.xml")}:
        raise RuntimeError("Topology input drift")
    if manifest["generator_sha256"] != sha_source(module / "tools/phantom-world-data/travel_backbone.py"):
        raise RuntimeError("Generator source drift")
    if manifest["test_sha256"] != sha_source(module / "tools/phantom-world-data/test_travel_backbone.py") or manifest["geoprobe_sha256"] != sha_source(module / "test/java/org/l2jmobius/tests/phantoms/PhantomTravelGeoProbe.java"):
        raise RuntimeError("Test/probe source drift")
    if manifest["script_sha256"] != {name: sha_source(module / "tools/phantom-world-data" / name) for name in ("Generate-TravelBackbone.ps1", "Test-TravelBackbone.ps1", "Validate-TravelBackbone.ps1")}:
        raise RuntimeError("Script source drift")
    for name, expected_hash in manifest["output_sha256"].items():
        if sha_file(output / name) != expected_hash:
            raise RuntimeError(f"Output hash mismatch: {name}")
    if len(facts) != manifest["counts"]["facts"] or len(connectors) != manifest["counts"]["connectors"] or len(ruins) != len(manifest["ruins_of_despair"]):
        raise RuntimeError("Manifest counts mismatch")
    if len({row["transition_id"] for row in transitions_found}) != len(transitions_found) or len({row["connector_id"] for row in connectors}) != len(connectors):
        raise RuntimeError("Duplicate transition/connector IDs")
    print(f"TRAVEL_VALIDATION GREEN facts={len(facts)} connectors={len(connectors)} reachability_rows={len(reach)}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("prepare", "finalize", "validate"))
    parser.add_argument("--module", type=Path, required=True)
    parser.add_argument("--work", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--proof", type=Path)
    args = parser.parse_args()
    if args.action == "prepare":
        prepare(args.module, args.work)
    elif args.action == "finalize":
        finalize(args.module, args.work, args.output)
    else:
        if args.proof is None:
            raise RuntimeError("GeoEngine proof required for validation")
        validate(args.module, args.output, args.proof)


if __name__ == "__main__":
    main()
