"""Publish independently proven closed-family components and exact blockers."""

import csv
from collections import Counter, defaultdict, deque
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET

import live002_content_graph as graph
import live002_final_content_access as access
import normal_gk_catalog as gk
import travel_backbone as d1


SHARDS = {
    access.FAMILIES[0]: "high-five-closed-ssq-01.xml",
    access.FAMILIES[1]: "high-five-closed-devils-isle.xml",
    access.FAMILIES[2]: "high-five-closed-toi.xml",
    access.FAMILIES[3]: "high-five-closed-ivory.xml",
    access.FAMILIES[4]: "high-five-closed-imperial.xml",
}
PROOF_COLUMNS = ("row_kind", "family", "coverage_key", "source_family", "status", "blocker", "blocker_edge_id", "geo_reason", "entrance_class", "entrance_owner", "native_condition", "farm_anchor_id", "internal_component_id", "source_path", "floor_key", "proof_ref")
FARM_COLUMNS = ("family", "coverage_key", "source_family", "source_path", "source_group", "room_territory", "floor_key", "anchor_id", "anchor_x", "anchor_y", "anchor_z", "local_x", "local_y", "local_z", "validation_status", "collision_proof", "candidate_attempts", "entrance_class", "access_status", "internal_component_id")
CONDITION_COLUMNS = ("family", "source_owner", "native_identity", "teleporter_npc_id", "teleport_type", "destination_index", "source_xyz", "destination_xyz", "fee_id", "fee_count", "condition_type", "exact_required_state", "affected_group_count", "affected_coverage_keys", "background_eligible", "reason", "evidence_refs")


def positive(proof):
    return (proof["validation_status"] in ("VALID_DIRECT", "VALID_PATH") and proof["collision_proof"] == "STATIC_XML_CLEAR"
            and proof["door_intersections"] == "0" and proof["fence_intersections"] == "0"
            and int(proof["required_buffer"]) <= 500 and proof["max_pathfind_buffer"] == "500"
            and int(proof["path_length"]) > 0 and int(proof["path_segments"]) > 0)


def source_path(owner):
    return "data/teleporters/" + owner if owner.startswith("dungeon/") else owner


def read_graph(module):
    registry = module / "docs/phantoms/live-world"
    evidence = module / "dist/game/data/phantoms/evidence"
    pairs = [(registry / "FINAL_CONTENT_GEO_CANDIDATES.tsv", evidence / "live002-content-geo-proof.tsv")]
    for label in access.FAMILIES:
        for wave in range(1, 13):
            metadata = registry / f"FINAL_CONTENT_{label}_BRIDGE_{wave}_CANDIDATES.tsv"
            proof = evidence / f"live002-content-{label.lower()}-bridge-{wave}-proof.tsv"
            if metadata.exists() != proof.exists():
                raise RuntimeError("Incomplete content bridge evidence")
            if metadata.exists():
                pairs.append((metadata, proof))
        metadata = registry / f"FINAL_CONTENT_{label}_SEED_RETRY_CANDIDATES.tsv"
        proof = evidence / f"live002-content-{label.lower()}-seed-retry-proof.tsv"
        if metadata.exists() != proof.exists():
            raise RuntimeError("Incomplete content seed evidence")
        if metadata.exists():
            pairs.append((metadata, proof))
    all_edges, proofs, accepted, used = [], {}, [], Counter()
    for metadata, proof_path in pairs:
        proof_rows = d1.read_tsv(proof_path)
        proof_by_id = {row["connector_id"]: row for row in proof_rows}
        rows = d1.read_tsv(metadata)
        if len(proof_by_id) != len(proof_rows) or {row["connector_id"] for row in rows} != set(proof_by_id):
            raise RuntimeError("Content candidate/proof key drift: " + metadata.name)
        for edge in rows:
            ident = edge["connector_id"]
            if ident in proofs:
                raise RuntimeError("Duplicate content directed proof")
            proof = proof_by_id[ident]
            all_edges.append(dict(edge, proof_ref="data/phantoms/evidence/" + proof_path.name))
            proofs[ident] = proof
            used[edge["family"]] += 1
            if positive(proof):
                accepted.append(dict(edge, proof_ref="data/phantoms/evidence/" + proof_path.name, path_length=proof["path_length"]))
    for label, limit in graph.LIMITS.items():
        if used[label] > limit:
            raise RuntimeError("Content family Geo check budget exceeded: " + label)
    return all_edges, proofs, accepted, used


def imperial_hops(module):
    registry = module / "docs/phantoms/live-world"
    evidence = module / "dist/game/data/phantoms/evidence"
    rows = d1.read_tsv(registry / "FINAL_CONTENT_IMPERIAL_OVERSIZED_HOPS.tsv")
    proof_path = evidence / "live002-imperial-oversized-hops-proof.tsv"
    proof = {row["connector_id"]: row for row in d1.read_tsv(proof_path)}
    raw = evidence / "live002-imperial-oversized-01.tsv"
    if len(rows) != 4 or any(row["raw_chain_sha256"] != d1.sha_file(raw) or not positive(proof[row["connector_id"]]) for row in rows):
        raise RuntimeError("Imperial proof-only raw path not production revalidated")
    for index, row in enumerate(rows):
        if index and (row["from_id"], *(row["from_" + axis] for axis in "xyz")) != (rows[index - 1]["to_id"], *(rows[index - 1]["to_" + axis] for axis in "xyz")):
            raise RuntimeError("Imperial oversized hop chain drift")
    return [dict(row, family="IMPERIAL_TOMB", source_family="ImperialTomb", from_id=row["from_id"] + ".anchor", to_id=row["to_id"] + ".anchor", entrance_semantic="NOBLESSE_CONDITIONAL", entrance_owner="data/teleporters/town/31275.xml", selection="RAW_ASTAR_DECOMPOSITION", proof_ref="data/phantoms/evidence/" + proof_path.name, path_length=proof[row["connector_id"]]["path_length"]) for row in rows]


def reachable(plan, accepted, doors):
    by_family = defaultdict(list)
    for edge in accepted:
        if edge["from_id"] not in doors and edge["to_id"] not in doors:
            by_family[edge["family"]].append(edge)
    states = {}
    for label in access.FAMILIES:
        edges = by_family[label]
        adjacent = defaultdict(list)
        for edge in edges:
            adjacent[edge["from_id"]].append(edge)
        seeds = {edge["from_id"]: edge["entrance_semantic"] for edge in edges if edge["from_id"].startswith("entry.") or (label == "IMPERIAL_TOMB" and edge["from_id"] == "generated.route.content.imperial.arrival.anchor")}
        queue = deque(sorted(seeds))
        previous = {seed: None for seed in seeds}
        origins = {seed: seed for seed in seeds}
        while queue:
            current = queue.popleft()
            for edge in sorted(adjacent[current], key=lambda item: item["connector_id"]):
                target = edge["to_id"]
                if target not in previous:
                    previous[target] = edge
                    origins[target] = origins[current]
                    queue.append(target)
        states[label] = (previous, origins, edges)
    return states


def route_node(root, ident, xyz, source):
    x, y, z = xyz
    node = ET.SubElement(root, "node", {"id": ident, "kind": "ROUTE_AREA", "instanceId": "0", "form": "CUBOID", "minX": str(x - 1), "maxX": str(x + 1), "minY": str(y - 1), "maxY": str(y + 1), "minZ": str(z - 1), "maxZ": str(z + 1), "tags": "route"})
    ET.SubElement(node, "source", {"path": source})
    anchor = ET.SubElement(root, "anchor", {"id": ident + ".anchor", "role": "ROUTE", "nodeId": ident, "x": str(x), "y": str(y), "z": str(z), "instanceId": "0", "tolerance": "0", "tags": "route"})
    ET.SubElement(anchor, "source", {"path": source})


def farm_node(root, row, geo):
    key = row["coverage_key"]
    ident = "generated.farm." + key[:24]
    _, minimum, maximum, points = access_territory(row["room_territory"])
    node = ET.SubElement(root, "node", {"id": ident, "kind": "FARMING_AREA", "instanceId": "0", "form": "POLYGON", "minZ": str(minimum), "maxZ": str(maximum), "tags": "content-farming"})
    for x, y in points:
        ET.SubElement(node, "vertex", {"x": str(x), "y": str(y)})
    ET.SubElement(node, "source", {"path": row["source_path"]})
    anchor = ET.SubElement(root, "anchor", {"id": ident + ".anchor", "role": "FARMING", "nodeId": ident, "x": geo["anchor_x"], "y": geo["anchor_y"], "z": geo["anchor_z"], "instanceId": "0", "tolerance": "0", "tags": "content-farming"})
    ET.SubElement(anchor, "source", {"path": row["source_path"]})


def access_territory(value):
    _, minimum, maximum, vertices = value.split(":", 3)
    return value, int(minimum), int(maximum), [tuple(map(int, pair.split(","))) for pair in vertices.split("|")]


def edge_xml(root, edge, eligible, source):
    from_id = edge["from_id"].removesuffix(".anchor")
    to_id = edge["to_id"].removesuffix(".anchor")
    item = ET.SubElement(root, "edge", {"id": edge["connector_id"], "fromNodeId": from_id, "toNodeId": to_id,
                       "mode": "BACKGROUND", "bidirectional": "false", "baseCost": "1",
                       "baseTravelMillis": str(int(edge["path_length"]) * 10),
                       "backgroundEligible": "true" if eligible else "false", "channels": "COMBAT,TARGETABILITY",
                       "fromAnchorId": edge["from_id"] if edge["from_id"].endswith(".anchor") else edge["from_id"] + ".anchor",
                       "toAnchorId": edge["to_id"] if edge["to_id"].endswith(".anchor") else edge["to_id"] + ".anchor"})
    ET.SubElement(item, "source", {"path": source})
    ET.SubElement(item, "source", {"path": edge["proof_ref"]})


def components(rows, accepted, doors):
    keys = {"generated.farm." + row["coverage_key"][:24] + ".anchor": row["coverage_key"] for row in rows}
    parent = {key: key for key in keys}
    def root(key):
        while parent[key] != key:
            key = parent[key]
        return key
    for edge in accepted:
        source, target = edge["from_id"], edge["to_id"]
        if source in parent and target in parent and source not in doors and target not in doors:
            first, second = root(source), root(target)
            if first != second:
                parent[max(first, second)] = min(first, second)
    return {key: "component." + d1.key(root(key)) for key in keys}


def first_blocker(target, first_edges, proofs, reached, doors):
    seen = set()
    current = target
    while current not in seen:
        seen.add(current)
        if current in doors:
            return "DOOR_STATE_BLOCKED", "native_closed_door", "default_status=close"
        edge = first_edges.get(current)
        if edge is None:
            return "GEODATA_INTERNAL_BLOCKED", "native_entry_room_missing", "NO_NATIVE_SPANNING_EDGE"
        proof = proofs[edge["connector_id"]]
        if not positive(proof):
            reason = proof["validation_status"]
            blocker = "DOOR_STATE_BLOCKED" if reason == "STATIC_DOOR_INTERSECTION_UNRESOLVED" else "GEODATA_INTERNAL_BLOCKED"
            return blocker, edge["connector_id"], reason
        current = edge["from_id"]
        if current in reached:
            return "GEODATA_INTERNAL_BLOCKED", edge["connector_id"], "PUBLISHED_COMPONENT_NOT_CONNECTED"
    return "GEODATA_INTERNAL_BLOCKED", "native_spanning_cycle", "NO_PROVEN_ENTRY_PATH"


def build_shards(module, plan, geo, accepted, states):
    topology = module / "dist/game/data/phantoms/topology"
    by_family = defaultdict(list)
    for row in plan:
        by_family[row["family"]].append(row)
    hashes = {}
    for label in access.FAMILIES:
        previous, _, edges = states[label]
        selected = {row["coverage_key"]: row for row in by_family[label] if "generated.farm." + row["coverage_key"][:24] + ".anchor" in previous}
        relevant = [edge for edge in edges if edge["from_id"] in previous and edge["to_id"] in previous]
        root = ET.Element("topology", {"schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"})
        route_points = {}
        for edge in relevant:
            for prefix in ("from", "to"):
                ident = edge[prefix + "_id"]
                if ident.startswith("generated.farm."):
                    continue
                ident = ident.removesuffix(".anchor")
                xyz = tuple(int(edge[prefix + "_" + axis]) for axis in "xyz")
                source = ("data/phantoms/evidence/live002-imperial-oversized-01.tsv" if ".geo." in ident
                          else source_path(edge["entrance_owner"]))
                prior = route_points.get(ident)
                if prior is not None and prior[0] != xyz:
                    raise RuntimeError("Conflicting native route point: " + ident)
                route_points[ident] = (xyz, source)
        for ident, (xyz, source) in sorted(route_points.items()):
            route_node(root, ident, xyz, source)
        for key, row in sorted(selected.items()):
            farm_node(root, row, geo[key])
        for edge in sorted(relevant, key=lambda item: item["connector_id"]):
            target = edge["to_id"]
            target_row = next((row for row in by_family[label] if target == "generated.farm." + row["coverage_key"][:24] + ".anchor"), None)
            source = target_row["source_path"] if target_row is not None else source_path(edge["entrance_owner"])
            edge_xml(root, edge, label == access.FAMILIES[1], source)
        ET.indent(root, space="\t")
        path = topology / SHARDS[label]
        path.write_bytes(b'<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(root, encoding="utf-8") + b"\n")
        if path.stat().st_size >= 4 * 1024 * 1024:
            raise RuntimeError("Closed content shard exceeds 4 MiB: " + path.name)
        hashes[label] = d1.sha_file(path)
    return hashes


def add_devils_normal(module, states, hashes):
    registry = module / "docs/phantoms/live-world"
    previous, _, edges = states[access.FAMILIES[1]]
    seed = next((edge for edge in edges if edge["from_id"].startswith("entry.") and edge["to_id"] in previous), None)
    if seed is None:
        raise RuntimeError("Devil's Isle has no proven native entry to publish")
    facts = d1.read_tsv(registry / "GATEKEEPER_FACTS.tsv")
    fact = next(row for row in facts if row["teleporter_npc_id"] == "30080" and row["teleport_type"] == "NORMAL" and row["destination_name"] == "Devil's Isle" and tuple(int(row["destination_" + axis]) for axis in "xyz") == tuple(int(seed["from_" + axis]) for axis in "xyz"))
    dest = "dest." + d1.key(fact["destination_x"], fact["destination_y"], fact["destination_z"], "0")
    anchor_id = seed["from_id"] + ".anchor"
    identity = "connector." + d1.key("DEST_TO_ANCHOR", dest, anchor_id)
    row = {"connector_id": identity, "connector_kind": "DEST_TO_ANCHOR", "from_type": "DEST", "from_id": dest, "to_type": "ANCHOR", "to_id": anchor_id, "instance_id": "0", **{"from_" + axis: fact["destination_" + axis] for axis in "xyz"}, **{"to_" + axis: fact["destination_" + axis] for axis in "xyz"}, "map_region_from": "", "map_region_to": "", "straight_distance": "0", "validation_status": "VALID_IDENTITY", "path_length": "0", "path_segments": "0", "source_refs": fact["source_path"], "reason": "CANONICAL_IDENTITY"}
    supplement_path = registry / "TARGETED_TRAVEL_CONNECTORS.tsv"
    original = d1.read_tsv(supplement_path)
    prior = [item for item in original if item["connector_id"] == identity]
    if len(original) not in (12, 13) or (prior and prior != [row]) or len({item["connector_id"] for item in original}) != len(original):
        raise RuntimeError("Closed NORMAL supplement drift")
    if not prior:
        d1.write_tsv(supplement_path, d1.CONNECTOR_COLUMNS, sorted(original + [row], key=lambda item: item["connector_id"]))
    manifest_path = registry / "TARGETED_TRAVEL_CONNECTORS_MANIFEST.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["output_sha256"]["TARGETED_TRAVEL_CONNECTORS.tsv"] = d1.sha_file(supplement_path)
    manifest["counts"]["targeted_connectors"] = 13
    manifest["counts"]["closed_normal_entry_connectors"] = 1
    manifest["closed_devils_sha256"] = hashes[access.FAMILIES[1]]
    manifest["closed_family_shards_sha256"] = hashes
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8", newline="\n")
    java_path = module / "java/org/l2jmobius/gameserver/phantoms/background/PhantomNormalGatekeeperTravel.java"
    java = java_path.read_text(encoding="utf-8")
    phase0_pin = "c436039da63f72a93fb6d8eafe7f1b76d2e7fe033302da76be5588d31fa330bd"
    final_pin = d1.sha_file(supplement_path)
    if java.count(phase0_pin) + java.count(final_pin) != 1:
        raise RuntimeError("Closed NORMAL production pin drift")
    if phase0_pin in java:
        java_path.write_text(java.replace(phase0_pin, final_pin), encoding="utf-8", newline="")
    legs = gk.build(module, module / "dist/game/data/phantoms/travel/high-five-normal-gk.xml")
    if not any(leg["teleporterNpcId"] == "30080" and leg["toAnchorId"] == anchor_id for leg in legs):
        raise RuntimeError("Devil's Isle native NORMAL leg not executable")
    return final_pin, len(legs)


def conditional_catalog(module, plan, closed):
    registry = module / "docs/phantoms/live-world"
    data = module / "dist/game"
    by_family = defaultdict(list)
    for row in plan:
        by_family[row["family"]].append(row)
    entries = []
    owners = []
    owners.extend((access.FAMILIES[0], f"data/teleporters/dungeon/{npc}.xml") for npc in range(31095, 31126) if (data / f"data/teleporters/dungeon/{npc}.xml").exists())
    owners.extend((access.FAMILIES[2], "data/teleporters/town/30848.xml") for _ in range(1))
    owners.extend((access.FAMILIES[3], "data/teleporters/others/IvoryTower/" + name + ".xml") for name in ("30162", "30716", "30719", "30722", "30727"))
    owners.append((access.FAMILIES[4], "data/teleporters/town/31275.xml"))
    for family, owner in owners:
        root = ET.parse(data / owner).getroot()
        for npc in root.findall("npc"):
            npc_id = npc.get("id")
            for teleport in npc.findall("teleport"):
                kind = teleport.get("type")
                if family in (access.FAMILIES[2], access.FAMILIES[4]) and kind not in ("NOBLES_TOKEN", "NOBLES_ADENA"):
                    continue
                if family in (access.FAMILIES[0], access.FAMILIES[3]) and kind != "OTHER":
                    continue
                for index, location in enumerate(teleport.findall("location"), 1):
                    xyz = tuple(int(location.get(axis)) for axis in "xyz")
                    if family == access.FAMILIES[2] and location.get("name") not in {"Tower of Insolence, 3rd Floor", "Tower of Insolence, 5th Floor", "Tower of Insolence, 7th Floor", "Tower of Insolence, 10th Floor", "Tower of Insolence, 13th Floor"}:
                        continue
                    if family == access.FAMILIES[4] and location.get("name") != "Imperial Tomb":
                        continue
                    if family == access.FAMILIES[0]:
                        affected = [r for r in by_family[family] if source_path(r["entrance_owner"]) == owner]
                        condition = "OTHER_HOLDER_SEMANTIC_UNMODELED; OracleTeleport rift branch separately requires level>=20, active quests<=40, item7079, level-scaled Adena"
                        reason = "NATIVE_CONDITION_UNMODELED"
                    elif family == access.FAMILIES[3]:
                        affected = by_family[family]
                        condition = "OTHER_HOLDER_SEMANTIC_UNMODELED; scoped Ivory Tower internal holder"
                        reason = "CONTENT_TELEPORT_SEMANTIC_BLOCKED"
                    else:
                        affected = by_family[family]
                        condition = "NOBLESSE_TOKEN_13722" if kind == "NOBLES_TOKEN" else "NOBLESSE_ADENA_1000"
                        reason = "CONDITIONAL_NATIVE_ACCESS"
                    if not affected and family != access.FAMILIES[0]:
                        continue
                    entries.append({"family": family, "source_owner": owner, "native_identity": "conditional." + d1.key(owner, npc_id, kind, str(index), *map(str, xyz)),
                                    "teleporter_npc_id": npc_id, "teleport_type": kind, "destination_index": str(index), "source_xyz": "NATIVE_NPC_HOLDER", "destination_xyz": ",".join(map(str, xyz)),
                                    "fee_id": location.get("feeId", ""), "fee_count": location.get("feeCount", ""), "condition_type": kind,
                                    "exact_required_state": condition, "affected_group_count": str(len(affected)),
                                    "affected_coverage_keys": "|".join(sorted(r["coverage_key"] for r in affected)), "background_eligible": "0", "reason": reason,
                                    "evidence_refs": owner + ("|data/scripts/ai/others/OracleTeleport/OracleTeleport.java" if family == access.FAMILIES[0] else "")})
    for row in closed:
        if row["door_default_statuses"].lower().find("close") < 0:
            continue
        family = access.family(row["source_family"])
        if family != access.FAMILIES[2] or not row["door_ids"]:
            raise RuntimeError("Unexpected closed native door scope")
        entries.append({"family": family, "source_owner": "data/Doors.xml", "native_identity": "closed-door." + row["coverage_key"][:24],
                        "teleporter_npc_id": "", "teleport_type": "DOOR", "destination_index": "", "source_xyz": "NATIVE_DOOR", "destination_xyz": "NATIVE_ROOM",
                        "fee_id": "", "fee_count": "", "condition_type": "DOOR_STATE", "exact_required_state": "door_ids=" + row["door_ids"] + ";default_status=close;native_opener_not_proven",
                        "affected_group_count": "1", "affected_coverage_keys": row["coverage_key"], "background_eligible": "0", "reason": "DOOR_STATE_BLOCKED",
                        "evidence_refs": "data/Doors.xml|data/spawns/Aden/TowerOfInsolence.xml"})
    d1.write_tsv(registry / "CONDITIONAL_CONTENT_ACCESS.tsv", CONDITION_COLUMNS, sorted(entries, key=lambda item: item["native_identity"]))
    return entries


def publish(module):
    registry = module / "docs/phantoms/live-world"
    evidence = module / "dist/game/data/phantoms/evidence"
    plan = d1.read_tsv(registry / "FINAL_CONTENT_ACCESS_PLAN.tsv")
    closed = d1.read_tsv(registry / "CLOSED_AREA_ACCESS.tsv")
    if len(plan) != 676 or len(closed) != 676 or {r["coverage_key"] for r in plan} != {r["coverage_key"] for r in closed}:
        raise RuntimeError("Historical closed corpus drift")
    geo_rows = d1.read_tsv(evidence / "live002-content-anchor-geo.tsv")
    geo = {row["coverage_key"]: row for row in geo_rows if row["status"] == "VALID" and row["collision_proof"] == "STATIC_XML_CLEAR"}
    if len(geo) != 676 or len(geo_rows) != 676:
        raise RuntimeError("Content farm anchor evidence incomplete")
    all_edges, proofs, accepted, checks = read_graph(module)
    oversized = imperial_hops(module)
    accepted += oversized
    doors = {"generated.farm." + row["coverage_key"][:24] + ".anchor" for row in closed if "close" in row["door_default_statuses"].lower()}
    states = reachable(plan, accepted, doors)
    shards = build_shards(module, plan, geo, accepted, states)
    targeted_sha, normal_count = add_devils_normal(module, states, shards)
    conditions = conditional_catalog(module, plan, closed)
    component = components(plan, accepted, doors)
    first_edges = {edge["to_id"]: edge for edge in all_edges if edge["selection"] == "NATIVE_ROOM_SPANNING"}
    proof_rows, farm_rows = [], []
    by_family = Counter()
    status_counts = Counter()
    for row in plan:
        label, key = row["family"], row["coverage_key"]
        ident = "generated.farm." + key[:24] + ".anchor"
        reached = states[label][0]
        if ident in doors:
            status, blocker, blocker_edge, reason = "DOOR_STATE_BLOCKED", "DOOR_STATE_BLOCKED", "native_closed_door", "default_status=close"
        elif label == access.FAMILIES[0]:
            status, blocker, blocker_edge, reason = "NATIVE_CONDITION_UNMODELED", "NATIVE_CONDITION_UNMODELED", "ZIGGURAT_OTHER", "PROVEN_INTERNAL_GRAPH" if ident in reached else "OTHER_HOLDER_UNMODELED"
        elif ident in reached:
            status = "REACHABLE_ORDINARY" if label == access.FAMILIES[1] else "REACHABLE_CONDITIONAL"
            blocker, blocker_edge, reason = "", "", "PROVEN_NATIVE_ENTRY_AND_INTERNAL_GRAPH"
        elif label == access.FAMILIES[3]:
            status, blocker, blocker_edge, reason = "CONTENT_TELEPORT_SEMANTIC_BLOCKED", "CONTENT_TELEPORT_SEMANTIC_BLOCKED", "IVORY_OTHER", "NO_PROVEN_NATIVE_ENTRY_TO_ROOM"
        else:
            blocker, blocker_edge, reason = first_blocker(ident, first_edges, proofs, reached, doors)
            status = blocker
        entrance_class = row["entrance_class"]
        native_condition = row["native_condition"]
        if ident in reached:
            origin = states[label][1][ident]
            entry = next((edge for edge in states[label][2] if edge["from_id"] == origin), None)
            if entry is None:
                raise RuntimeError("Reachable content farm lacks native seed evidence")
            entrance_class = entry["entrance_semantic"]
            if entrance_class == "NOBLESSE_CONDITIONAL":
                native_condition = "NOBLESSE_TOKEN_13722_OR_NOBLES_ADENA_1000" if label == access.FAMILIES[4] else "NOBLESSE_TOKEN_13722_OR_NOBLES_ADENA_1000;EXACT_FLOOR_DESTINATION"
        record = {"row_kind": "GROUP", "family": label, "coverage_key": key, "source_family": row["source_family"], "status": status,
                  "blocker": blocker, "blocker_edge_id": blocker_edge, "geo_reason": reason, "entrance_class": entrance_class,
                  "entrance_owner": source_path(row["entrance_owner"]), "native_condition": native_condition, "farm_anchor_id": ident,
                  "internal_component_id": component[ident], "source_path": row["source_path"], "floor_key": row["floor_key"],
                  "proof_ref": "data/phantoms/evidence/live002-content-anchor-geo.tsv"}
        proof_rows.append(record)
        farm_rows.append({"family": label, "coverage_key": key, "source_family": row["source_family"], "source_path": row["source_path"],
                          "source_group": row["source_group"], "room_territory": row["room_territory"], "floor_key": row["floor_key"],
                          "anchor_id": ident, "anchor_x": geo[key]["anchor_x"], "anchor_y": geo[key]["anchor_y"], "anchor_z": geo[key]["anchor_z"],
                          "local_x": geo[key]["local_x"], "local_y": geo[key]["local_y"], "local_z": geo[key]["local_z"],
                          "validation_status": "VALID", "collision_proof": "STATIC_XML_CLEAR", "candidate_attempts": geo[key]["attempts"],
                          "entrance_class": entrance_class, "access_status": status, "internal_component_id": component[ident]})
        by_family[label] += 1
        status_counts[(label, status)] += 1
    for label in access.FAMILIES:
        group = [row for row in proof_rows if row["family"] == label]
        green = sum(row["status"].startswith("REACHABLE_") for row in group)
        family_status = "GREEN_CONDITIONAL" if green == len(group) and label != access.FAMILIES[1] else "GREEN_ORDINARY" if green == len(group) else "GREEN_PARTIAL" if green else "BLOCKED"
        proof_rows.append({"row_kind": "FAMILY", "family": label, "coverage_key": "", "source_family": "", "status": family_status,
                           "blocker": "" if green == len(group) else ("NATIVE_CONDITION_UNMODELED" if label == access.FAMILIES[0] else "CONTENT_TELEPORT_SEMANTIC_BLOCKED" if label == access.FAMILIES[3] else "GEODATA_INTERNAL_BLOCKED"),
                           "blocker_edge_id": "", "geo_reason": f"accounted={len(group)};reachable={green};geo_checks={checks[label]}",
                           "entrance_class": group[0]["entrance_class"], "entrance_owner": group[0]["entrance_owner"], "native_condition": group[0]["native_condition"],
                           "farm_anchor_id": "", "internal_component_id": "", "source_path": "", "floor_key": "", "proof_ref": "data/phantoms/evidence/live002-content-geo-proof.tsv"})
    d1.write_tsv(registry / "FINAL_CONTENT_FARM_ANCHORS.tsv", FARM_COLUMNS, farm_rows)
    d1.write_tsv(registry / "FINAL_CONTENT_ACCESS_PROOF.tsv", PROOF_COLUMNS, proof_rows)
    print("CONTENT_PUBLISHED groups=676", dict(status_counts), "shards=" + str(shards), "normal=" + str(normal_count), "targeted_sha=" + targeted_sha, "conditional=" + str(len(conditions)))


if __name__ == "__main__":
    publish(Path(__file__).resolve().parents[2])
