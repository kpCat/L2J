"""Read-only LIVE-002 gap classification over accepted C/D1/D2 artifacts."""

import argparse
import json
import math
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

import normal_gk_catalog as d2
import travel_backbone as d1


COLUMNS = (
    "coverage_key", "level_min", "level_max", "source_path", "source_family",
    "current_anchor_status", "published", "component_id", "walk_reachable",
    "normal_gk_reachable", "primary_blocker", "secondary_blocker",
    "nearest_reachable_component_distance", "candidate_factual_bridge_kind", "evidence_refs",
)
BRIDGE_COLUMNS = ("connector_id", "component_id", "search_radius", "candidate_rank", "direction", "from_id", "to_id", "from_x", "from_y", "from_z", "to_x", "to_y", "to_z", "from_instance", "to_instance", "validation_status", "path_length", "path_segments", "source_refs")
CLOSED_COLUMNS = ("source_family", "coverage_key", "entrance_status", "entrance_kind", "outside_anchor", "inside_anchor", "door_ids", "door_default_statuses", "floor_key", "transition_source", "geodata_status", "planner_status", "blocker", "instance_id", "room_territory", "zone_refs", "evidence_refs")
FINAL_COLUMNS = ("start_family", "level_band", "published_ordinary_farms", "topology_only_reachable", "normal_gk_reachable", "closed_area_factual_reachable", "final_reachable", "blocker_count", "witness_coverage_key", "witness_path")


def source_family(path):
    parts = Path(path.replace("\\", "/")).parts
    return parts[2] if len(parts) > 3 and parts[:2] == ("data", "spawns") else Path(path).stem


def classify_rows(coverage, candidates, proofs, anchors, components, walk, normal, connectors):
    ordinary = [row for row in coverage if row.get("classification") == "ORDINARY_WORLD" and row.get("status") == "READY_STATIC"]
    keys = [row["coverage_key"] for row in ordinary]
    if len(keys) != len(set(keys)):
        raise ValueError("duplicate ordinary coverage key")
    reachable_points = [(anchor["x"], anchor["y"], components.get(anchor_id, ""))
                        for anchor_id, anchor in anchors.items() if anchor_id in normal and anchor["role"] in d1.ROLES]
    rows = []
    for fact in ordinary:
        key = fact["coverage_key"]
        candidate = candidates.get(key)
        if candidate is None:
            raise ValueError("missing topology candidate: " + key)
        proof = proofs.get(key, {})
        status = proof.get("status", "EXISTING_CORE")
        anchor_id = candidate["anchor_id"]
        published = anchor_id in anchors and anchors[anchor_id]["role"] == "FARMING"
        closed = status == "CLOSED_SOURCE_NO_DOOR_PATH"
        if published and anchor_id in normal:
            blocker, secondary = "REACHABLE", ""
        elif closed:
            blocker, secondary = "CLOSED_SOURCE_ENTRANCE_REQUIRED", "DOOR_ROOM_MODEL_REQUIRED"
        elif not published:
            blocker = ("PLANNER_TARGET_DISTANCE" if status == "PLANNER_TARGET_DISTANCE" else
                       "ANCHOR_GEOMETRY_BLOCKED" if status in ("OUTSIDE_GEOMETRY", "AMBIGUOUS_GEOMETRY", "UNSUPPORTED_POINT_AREA", "INVALID_SOURCE_GEOMETRY") else
                       "OTHER_EXPLICIT")
            secondary = status
        else:
            blocker, secondary = "OPEN_COMPONENT_DISCONNECTED", "NO_PROVEN_COMPONENT_BRIDGE"
        point = anchors.get(anchor_id)
        x = point["x"] if point else int(fact.get("sample_x") or 0)
        y = point["y"] if point else int(fact.get("sample_y") or 0)
        nearest = min((math.ceil(math.hypot(x - rx, y - ry)) for rx, ry, component in reachable_points
                       if not published or component != components.get(anchor_id, "")), default=None)
        rows.append({
            "coverage_key": key, "level_min": fact.get("npc_level_min", ""), "level_max": fact.get("npc_level_max", ""),
            "source_path": fact.get("source_path", ""), "source_family": source_family(fact.get("source_path", "")),
            "current_anchor_status": status, "published": str(published).lower(),
            "component_id": components.get(anchor_id, "") if published else "",
            "walk_reachable": str(anchor_id in walk).lower(), "normal_gk_reachable": str(anchor_id in normal).lower(),
            "primary_blocker": blocker, "secondary_blocker": secondary,
            "nearest_reachable_component_distance": "" if nearest is None else str(nearest),
            "candidate_factual_bridge_kind": "ADAPTIVE_GEODATA_PROBE_REQUIRED" if published and blocker != "REACHABLE" else "NONE_PROVEN",
            "evidence_refs": "|".join(("WORLD_COVERAGE.tsv:" + key, "TOPOLOGY_CANDIDATES.tsv:" + key,
                                      "ANCHOR_GEODATA_VALIDATION.tsv:" + key if proof else "high-five-core.xml:" + anchor_id,
                                      fact.get("source_path", ""))),
        })
    return sorted(rows, key=lambda row: row["coverage_key"])


def adaptive_candidates(anchors, components, reachable, target_components, max_checks=32):
    if not 1 <= max_checks <= 32:
        raise ValueError("component search budget must be within 1..32")
    cell = d1.CELL
    buckets = defaultdict(list)
    for anchor_id in sorted(reachable):
        anchor = anchors[anchor_id]
        if anchor["role"] in d1.ROLES and anchor["instance"] == 0:
            buckets[(anchor["x"] // cell, anchor["y"] // cell)].append(anchor)
    by_component = defaultdict(list)
    for anchor_id, anchor in anchors.items():
        component = components.get(anchor_id)
        if component in target_components and anchor["role"] in d1.ROLES and anchor["instance"] == 0:
            by_component[component].append(anchor)
    rows = []
    for component in sorted(target_components):
        for radius in (8192, 16384, 32768):
            pairs = {}
            cell_radius = math.ceil(radius / cell)
            for target in sorted(by_component[component], key=lambda row: row["id"]):
                cx, cy = target["x"] // cell, target["y"] // cell
                for dx in range(-cell_radius, cell_radius + 1):
                    for dy in range(-cell_radius, cell_radius + 1):
                        for source in buckets.get((cx + dx, cy + dy), ()):
                            if components.get(source["id"]) == component:
                                continue
                            distance = math.hypot(source["x"] - target["x"], source["y"] - target["y"])
                            if distance <= radius:
                                pairs[(source["id"], target["id"])] = (distance, source, target)
            if not pairs:
                continue
            ranked = sorted(pairs.values(), key=lambda item: (item[0], item[1]["id"], item[2]["id"]))
            for rank, (_, source, target) in enumerate(ranked[:max_checks // 2], 1):
                for direction, start, end in (("F", source, target), ("R", target, source)):
                    rows.append({
                        "connector_id": "bridge." + d1.key(component, source["id"], target["id"], direction),
                        "component_id": component, "search_radius": str(radius), "candidate_rank": str(rank),
                        "direction": direction, "from_id": start["id"], "to_id": end["id"],
                        "from_x": str(start["x"]), "from_y": str(start["y"]), "from_z": str(start["z"]),
                        "to_x": str(end["x"]), "to_y": str(end["y"]), "to_z": str(end["z"]),
                        "from_instance": "0", "to_instance": "0", "validation_status": "UNPROVEN",
                        "source_refs": start["source"] + "|" + end["source"],
                    })
            break
    return sorted(rows, key=lambda row: row["connector_id"])


def proven_bridges(candidates, proofs):
    proof_by_id = {row["connector_id"]: row for row in proofs}
    if len(proof_by_id) != len(proofs) or set(proof_by_id) != {row["connector_id"] for row in candidates}:
        raise ValueError("GeoEngine bridge proof accounting mismatch")
    result = []
    for candidate in candidates:
        proof = proof_by_id[candidate["connector_id"]]
        if proof["validation_status"] in ("VALID_DIRECT", "VALID_PATH"):
            if int(proof["path_length"]) <= 0 or int(proof["path_segments"]) <= 0:
                raise ValueError("Invalid accepted bridge proof")
            result.append(dict(candidate, **proof))
    return result


def bridge_xml(proven, anchor_nodes):
    root = ET.Element("topology", {"schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"})
    ids = set()
    for row in sorted(proven, key=lambda item: item["connector_id"]):
        edge_id = row["connector_id"]
        if edge_id in ids or row["from_id"] not in anchor_nodes or row["to_id"] not in anchor_nodes:
            raise ValueError("Duplicate bridge or missing factual endpoint")
        ids.add(edge_id)
        if row["validation_status"] not in ("VALID_DIRECT", "VALID_PATH") or int(row["path_length"]) <= 0 or int(row["path_segments"]) <= 0:
            raise ValueError("Unproven bridge")
        if row["from_instance"] != "0" or row["to_instance"] != "0":
            raise ValueError("Non-ordinary bridge")
        edge = ET.SubElement(root, "edge", {
            "id": edge_id, "fromNodeId": anchor_nodes[row["from_id"]], "toNodeId": anchor_nodes[row["to_id"]],
            "mode": "BACKGROUND", "bidirectional": "false", "baseCost": "1",
            "baseTravelMillis": str(max(1000, int(row["path_length"]) * 10)),
            "backgroundEligible": "true", "channels": "COMBAT,TARGETABILITY",
            "fromAnchorId": row["from_id"], "toAnchorId": row["to_id"],
        })
        for path in dict.fromkeys(row["source_refs"].split("|")):
            if path:
                ET.SubElement(edge, "source", {"path": path})
    ET.indent(root, space="\t")
    return ET.tostring(root, encoding="utf-8", xml_declaration=True) + b"\n"


def active_topology(module):
    anchors, edges = d1.topology(module)
    bridge_file = module / "dist/game/data/phantoms/topology/high-five-generated-02.xml"
    if bridge_file.exists():
        for edge in ET.parse(bridge_file).getroot().findall("edge"):
            if edge.get("mode") == "BACKGROUND" and edge.get("backgroundEligible") == "true":
                if edge.get("bidirectional") != "false":
                    raise ValueError("Adaptive bridge cannot imply reverse movement")
                source, target = edge.get("fromAnchorId"), edge.get("toAnchorId")
                if source not in anchors or target not in anchors:
                    raise ValueError("Bridge references missing active anchor")
                edges.append((source, target, edge.get("id")))
    return anchors, edges


def closed_access_row(candidate, doors, coverage=None):
    geometry = candidate["source_geometry"]
    pieces = geometry.split(":", 3)
    if len(pieces) != 4 or pieces[0] != "territory":
        raise ValueError("Closed source lacks native territory geometry")
    minimum_z, maximum_z = int(pieces[1]), int(pieces[2])
    points = [tuple(map(int, pair.split(","))) for pair in pieces[3].split("|")]
    min_x, max_x = min(x for x, _ in points), max(x for x, _ in points)
    min_y, max_y = min(y for _, y in points), max(y for _, y in points)
    nearby = sorted((door for door in doors if min_x <= door["x"] <= max_x and min_y <= door["y"] <= max_y and minimum_z <= door["z"] <= maximum_z), key=lambda door: door["id"])
    source = candidate["source_refs"]
    family = Path(source).stem
    return {
        "source_family": family, "coverage_key": candidate["coverage_key"],
        "entrance_status": "UNPROVEN", "entrance_kind": "", "outside_anchor": "", "inside_anchor": "",
        "door_ids": "|".join(door["id"] for door in nearby),
        "door_default_statuses": "|".join(door["status"] for door in nearby),
        "floor_key": f"z:{minimum_z}:{maximum_z}", "transition_source": "",
        "geodata_status": "NOT_PROBED_CLOSED", "planner_status": "BLOCKED",
        "blocker": "NATIVE_ENTRANCE_PATH_UNPROVEN", "instance_id": candidate.get("instance_id", ""),
        "room_territory": geometry, "zone_refs": (coverage or {}).get("zone_refs", ""),
        "evidence_refs": "|".join((source, "TOPOLOGY_CANDIDATES.tsv:" + candidate["coverage_key"],
                                      *("data/Doors.xml#" + door["id"] for door in nearby))),
    }


def generate_closed_access(module, output):
    registry = module / "docs/phantoms/live-world"
    classified = d1.read_tsv(registry / "WORLD_GAP_CLASSIFICATION.tsv")
    closed_keys = {row["coverage_key"] for row in classified if row["current_anchor_status"] == "CLOSED_SOURCE_NO_DOOR_PATH"}
    if len(closed_keys) != 676:
        raise RuntimeError("Closed corpus drift")
    candidates = {row["coverage_key"]: row for row in d1.read_tsv(registry / "TOPOLOGY_CANDIDATES.tsv")}
    coverage = {row["coverage_key"]: row for row in d1.read_tsv(registry / "WORLD_COVERAGE.tsv")}
    doors = []
    for door in ET.parse(module / "dist/game/data/Doors.xml").getroot().findall("door"):
        position = door.get("pos", "").split(",")
        if len(position) != 3:
            continue
        doors.append({"id": door.get("id"), "x": int(position[0]), "y": int(position[1]),
                      "z": int(door.get("nodeZ", position[2])), "status": door.get("default_status", "")})
    rows = [closed_access_row(candidates[key], doors, coverage[key]) for key in sorted(closed_keys)]
    if len(rows) != 676 or any(row["instance_id"] != "0" for row in rows):
        raise RuntimeError("Closed ordinary instance accounting mismatch")
    d1.write_tsv(output, CLOSED_COLUMNS, rows)
    print(f"CLOSED_AREA_ACCESS rows=676 native_doors={len(doors)} with_nearby_floor_door={sum(bool(row['door_ids']) for row in rows)} sha256={d1.sha_file(output)}")
    print("families " + " ".join(f"{name}:{count}" for name, count in sorted(Counter(row["source_family"] for row in rows).items())))


def reachability_rows(family_roots, farms, background, catalog_edges, closed_reachable, ordinary=None):
    rows = []
    all_edges = background + catalog_edges
    closed = set(closed_reachable)
    root_groups = list(sorted(family_roots.items())) + [("GLOBAL", sorted({anchor for ids in family_roots.values() for anchor in ids}))]
    for family, starts in root_groups:
        walk = d1.shortest_multi(starts, background, False)
        normal = d1.shortest_multi(starts, all_edges, True)
        for lower, upper in d1.BANDS:
            band_farms = [farm for farm in farms if farm["minimum"] <= upper and farm["maximum"] >= lower]
            walk_farms = [farm for farm in band_farms if farm["anchor"] in walk]
            normal_farms = [farm for farm in band_farms if farm["anchor"] in normal]
            closed_farms = [farm for farm in normal_farms if farm["coverage_key"] in closed]
            witness = normal_farms[0] if normal_farms else None
            total_ordinary = sum(int(row["npc_level_min"]) <= upper and int(row["npc_level_max"]) >= lower for row in ordinary) if ordinary else len(band_farms)
            rows.append({
                "start_family": family, "level_band": f"{lower}-{upper}",
                "published_ordinary_farms": len(band_farms), "topology_only_reachable": len(walk_farms),
                "normal_gk_reachable": len(normal_farms), "closed_area_factual_reachable": len(closed_farms),
                "final_reachable": len(normal_farms), "blocker_count": total_ordinary - len(normal_farms),
                "witness_coverage_key": witness["coverage_key"] if witness else "",
                "witness_path": "|".join([normal[witness["anchor"]][0][0]] + [label + "->" + node for label, node in zip(normal[witness["anchor"]][1], normal[witness["anchor"]][0][1:])]) if witness else "UNREACHABLE",
            })
    return rows


def generate_final(module, output):
    registry = module / "docs/phantoms/live-world"
    anchors, background = active_topology(module)
    farms = d1.published_farms(module, anchors)
    root_groups = d1.roots(anchors)
    catalog = ET.parse(module / "dist/game/data/phantoms/travel/high-five-normal-gk.xml").getroot()
    catalog_edges = [(leg.get("fromAnchorId"), leg.get("toAnchorId"), "normal-gk:" + leg.get("id")) for leg in catalog.findall("leg")]
    ordinary = [row for row in d1.read_tsv(registry / "WORLD_COVERAGE.tsv") if row["classification"] == "ORDINARY_WORLD" and row["status"] == "READY_STATIC"]
    closed = d1.read_tsv(registry / "CLOSED_AREA_ACCESS.tsv")
    closed_reachable = [row["coverage_key"] for row in closed if row["geodata_status"] in ("VALID_DIRECT", "VALID_PATH") and row["entrance_status"] == "PROVEN"]
    rows = reachability_rows(root_groups, farms, background, catalog_edges, closed_reachable, ordinary)
    if len(rows) != 72:
        raise RuntimeError("Final family/band accounting mismatch")
    d1.write_tsv(output, FINAL_COLUMNS, rows)
    facts = d1.read_tsv(registry / "GATEKEEPER_FACTS.tsv")
    endpoints = d1.endpoints(facts)
    connectors = d1.read_tsv(registry / "TRAVEL_CONNECTORS.tsv")
    graph = background + [(row["from_id"], row["to_id"], "connector:" + row["connector_id"]) for row in connectors if d1.is_proven(row)] + catalog_edges
    reached = d1.shortest_multi(sorted({root for ids in root_groups.values() for root in ids}), graph, True)
    ruins = []
    catalog_transitions = {leg.get("transitionId") for leg in catalog.findall("leg")}
    for fact in facts:
        if fact["destination_name"] != "Ruins of Despair" or fact["teleport_type"] != "NORMAL" or fact["availability_class"] != "NORMAL":
            continue
        destination = fact["destination_key"]
        local = d1.shortest_multi([destination], graph, True)
        local_farms = sorted(farm["coverage_key"] for farm in farms if farm["anchor"] in local)
        source = fact["teleporter_spawn_key"]
        transition = "transition." + d1.key(fact["fact_key"])
        proof = {
            "fact_key": fact["fact_key"], "native_source": fact["source_path"], "source_gk": source,
            "destination": destination, "ingress_to_gk": source in reached,
            "admitted_normal_transition": transition in catalog_transitions,
            "destination_connector": any(row["from_id"] == destination and d1.is_proven(row) for row in connectors),
            "local_farm_coverage_keys": local_farms,
        }
        proof["status"] = "VERIFIED_REACHABLE" if proof["ingress_to_gk"] and proof["admitted_normal_transition"] and proof["destination_connector"] and local_farms else "BLOCKED"
        ruins.append(proof)
    manifest = {
        "schema": "LIVE-002-FINAL-GEO/1", "baseline": "700811df18562a9313d122373d21865fbb10d592",
        "accepted_c_sha256": d1.ACCEPTED_C, "accepted_d1_sha256": d2.ACCEPTED_D1,
        "input_sha256": {name: d1.sha_file(registry / name) for name in ("WORLD_COVERAGE.tsv", "TOPOLOGY_CANDIDATES.tsv", "ANCHOR_GEODATA_VALIDATION.tsv", "ROUTE_GEODATA_VALIDATION.tsv", "GATEKEEPER_FACTS.tsv", "TRAVEL_CONNECTORS.tsv", "TRAVEL_TRANSITIONS.tsv")},
        "adaptive_geo_proof_sha256": {name: d1.sha_file(module / ".phantom-local/logs/LIVE-002-FINAL-GEO" / name / "adaptive-geo-proof.tsv") for name in ("current", "wave2")},
        "native_geodata_aggregate_sha256": json.loads((registry / "TRAVEL_BACKBONE_MANIFEST.json").read_text(encoding="utf-8"))["input_aggregate_sha256"]["geodata"],
        "native_doors_sha256": d1.sha_file(module / "dist/game/data/Doors.xml"),
        "d2_catalog_sha256": d1.sha_file(module / "dist/game/data/phantoms/travel/high-five-normal-gk.xml"),
        "generator_sha256": d1.sha_source(module / "tools/phantom-world-data/final_geo.py"),
        "output_sha256": {name: d1.sha_file(registry / name) for name in ("WORLD_GAP_CLASSIFICATION.tsv", "WORLD_COMPONENT_BRIDGES.tsv", "CLOSED_AREA_ACCESS.tsv", "LIVE002_FINAL_REACHABILITY.tsv")},
        "bridge_shard_sha256": d1.sha_file(module / "dist/game/data/phantoms/topology/high-five-generated-02.xml"),
        "counts": {"ordinary": len(ordinary), "closed": len(closed), "bridge_proofs": sum(d1.is_proven(row) for row in d1.read_tsv(registry / "WORLD_COMPONENT_BRIDGES.tsv")), "reachable_band_count": sum(int(row["final_reachable"]) > 0 for row in rows if row["start_family"] == "GLOBAL")},
        "ruins_of_despair": ruins,
    }
    manifest_file = registry / "LIVE002_FINAL_GEO_MANIFEST.json"
    manifest_file.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("FINAL_REACHABILITY " + " ".join(f"{row['level_band']}:{row['final_reachable']}" for row in rows if row["start_family"] == "GLOBAL"))
    print(f"RUINS {ruins} manifest_sha256={d1.sha_file(manifest_file)}")


def publish_bridges(module, work, output):
    rows = d1.read_tsv(module / "docs/phantoms/live-world/WORLD_COMPONENT_BRIDGES.tsv")
    proven = [row for row in rows if d1.is_proven(row)]
    nodes = {}
    directory = module / "dist/game/data/phantoms/topology"
    for name in ("high-five-core.xml", "high-five-siege.xml", "high-five-generated-01.xml"):
        for anchor in ET.parse(directory / name).getroot().findall("anchor"):
            nodes[anchor.get("id")] = anchor.get("nodeId")
    data = bridge_xml(proven, nodes)
    if len(data) >= 4 * 1024 * 1024:
        raise ValueError("Generated bridge shard exceeds loader bound")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(data)
    print(f"BRIDGES_PUBLISHED directions={len(proven)} sha256={d1.sha_file(output)}")


def prepare_adaptive(module, work):
    registry = module / "docs/phantoms/live-world"
    classified = d1.read_tsv(registry / "WORLD_GAP_CLASSIFICATION.tsv")
    if len(classified) != 2652:
        raise RuntimeError("Phase-0 classification required before adaptive search")
    anchors, background = active_topology(module)
    facts = d1.read_tsv(registry / "GATEKEEPER_FACTS.tsv")
    endpoints = d1.endpoints(facts)
    connectors = d1.read_tsv(registry / "TRAVEL_CONNECTORS.tsv")
    transitions = d1.read_tsv(registry / "TRAVEL_TRANSITIONS.tsv")
    edges = background + [(row["from_id"], row["to_id"], row["connector_id"]) for row in connectors if d1.is_proven(row)] + d1.normal_transition_edges(transitions)
    base_anchors, base_background = d1.topology(module)
    base_edges = base_background + [(row["from_id"], row["to_id"], row["connector_id"]) for row in connectors if d1.is_proven(row)] + d1.normal_transition_edges(transitions)
    components = d1.components(set(base_anchors) | set(endpoints), base_edges)
    roots = sorted({root for family in d1.roots(anchors).values() for root in family})
    reachable = set(d1.shortest_multi(roots, edges, True)) & set(anchors)
    already_checked = {row["component_id"] for row in d1.read_tsv(registry / "WORLD_COMPONENT_BRIDGES.tsv")} if (registry / "WORLD_COMPONENT_BRIDGES.tsv").exists() else set()
    target_components = {row["component_id"] for row in classified if row["primary_blocker"] == "OPEN_COMPONENT_DISCONNECTED" and row["component_id"] and row["component_id"] not in already_checked and row["component_id"] not in {components[anchor] for anchor in reachable}}
    rows = adaptive_candidates(anchors, components, reachable, target_components)
    work.mkdir(parents=True, exist_ok=True)
    d1.write_tsv(work / "adaptive-candidates.tsv", BRIDGE_COLUMNS, rows)
    geo_columns = ("connector_id", "from_x", "from_y", "from_z", "from_instance", "to_x", "to_y", "to_z", "to_instance")
    d1.write_tsv(work / "adaptive-geo-input.tsv", geo_columns, rows)
    print(f"ADAPTIVE_PREPARE target_components={len(target_components)} with_candidates={len({row['component_id'] for row in rows})} checks={len(rows)} max_per_component=32")


def finalize_adaptive(module, work, output):
    candidates = d1.read_tsv(work / "adaptive-candidates.tsv")
    proofs = d1.read_tsv(work / "adaptive-geo-proof.tsv")
    accepted = proven_bridges(candidates, proofs)
    by_id = {row["connector_id"]: row for row in proofs}
    rows = [dict(row, **by_id[row["connector_id"]]) for row in candidates]
    if output.exists():
        rows += d1.read_tsv(output)
        if len({row["connector_id"] for row in rows}) != len(rows):
            raise ValueError("Duplicate bridge proof across adaptive waves")
    rows.sort(key=lambda row: row["connector_id"])
    d1.write_tsv(output, BRIDGE_COLUMNS, rows)
    counts = Counter(row["validation_status"] for row in rows)
    accepted_components = {row["component_id"] for row in accepted}
    expanded = {row["component_id"] for row in accepted if int(row["search_radius"]) > 8192 or int(row["candidate_rank"]) > 8}
    print(f"ADAPTIVE_PROOF checks={len(rows)} proven={len(accepted)} proven_components={len(accepted_components)} D1_bound_components={len(expanded)} statuses={dict(sorted(counts.items()))} sha256={d1.sha_file(output)}")


def diagnose(module, output):
    d1.verify_c(module)
    registry = module / "docs/phantoms/live-world"
    for name, expected in d2.ACCEPTED_D1.items():
        if d1.sha_file(registry / name) != expected:
            raise RuntimeError("BLOCKED_INPUT_DRIFT: " + name)
    coverage = d1.read_tsv(registry / "WORLD_COVERAGE.tsv")
    candidates = {row["coverage_key"]: row for row in d1.read_tsv(registry / "TOPOLOGY_CANDIDATES.tsv")}
    proofs = {row["coverage_key"]: row for row in d1.read_tsv(registry / "ANCHOR_GEODATA_VALIDATION.tsv")}
    facts = d1.read_tsv(registry / "GATEKEEPER_FACTS.tsv")
    connectors = d1.read_tsv(registry / "TRAVEL_CONNECTORS.tsv")
    transitions = d1.read_tsv(registry / "TRAVEL_TRANSITIONS.tsv")
    anchors, background = d1.topology(module)
    endpoints = d1.endpoints(facts)
    movement = background + [(row["from_id"], row["to_id"], "connector:" + row["connector_id"])
                             for row in connectors if d1.is_proven(row)]
    all_edges = movement + d1.normal_transition_edges(transitions)
    roots = sorted({root for family in d1.roots(anchors).values() for root in family})
    walk = set(d1.shortest_multi(roots, movement, False))
    normal = set(d1.shortest_multi(roots, all_edges, True))
    components = d1.components(set(anchors) | set(endpoints), all_edges)
    rows = classify_rows(coverage, candidates, proofs, anchors, components, walk, normal, connectors)
    if len(rows) != 2652 or len({row["coverage_key"] for row in rows}) != 2652:
        raise RuntimeError("ordinary READY_STATIC accounting mismatch")
    if sum(row["current_anchor_status"] == "CLOSED_SOURCE_NO_DOOR_PATH" for row in rows) != 676:
        raise RuntimeError("closed source accounting mismatch")
    output.parent.mkdir(parents=True, exist_ok=True)
    d1.write_tsv(output, COLUMNS, rows)
    print("WORLD_GAP_CLASSIFICATION rows=2652 closed=676 sha256=" + d1.sha_file(output))
    for field in ("primary_blocker", "source_family"):
        counts = Counter(row[field] for row in rows)
        print(field + " " + " ".join(f"{key}:{count}" for key, count in sorted(counts.items())))
    counts = Counter(row["component_id"] for row in rows if row["component_id"])
    print("components count=" + str(len(counts)) + " largest=" + " ".join(f"{key}:{count}" for key, count in counts.most_common(8)))
    for lower, upper in d1.BANDS:
        band = [row for row in rows if int(row["level_min"]) <= upper and int(row["level_max"]) >= lower]
        counts = Counter(row["primary_blocker"] for row in band)
        print(f"band {lower}-{upper} groups={len(band)} " + " ".join(f"{key}:{count}" for key, count in sorted(counts.items())))
    return rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("diagnose", "prepare-adaptive", "finalize-adaptive", "publish-bridges", "closed-access", "final"), nargs="?", default="diagnose")
    parser.add_argument("--module", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path)
    parser.add_argument("--work", type=Path)
    args = parser.parse_args()
    if args.action == "diagnose":
        diagnose(args.module, args.output or args.module / "docs/phantoms/live-world/WORLD_GAP_CLASSIFICATION.tsv")
    elif args.action == "prepare-adaptive":
        prepare_adaptive(args.module, args.work or args.module / ".phantom-local/logs/LIVE-002-FINAL-GEO/current")
    elif args.action == "finalize-adaptive":
        finalize_adaptive(args.module, args.work or args.module / ".phantom-local/logs/LIVE-002-FINAL-GEO/current",
                          args.output or args.module / "docs/phantoms/live-world/WORLD_COMPONENT_BRIDGES.tsv")
    elif args.action == "publish-bridges":
        publish_bridges(args.module, args.work or args.module / ".phantom-local/logs/LIVE-002-FINAL-GEO/current",
                        args.output or args.module / "dist/game/data/phantoms/topology/high-five-generated-02.xml")
    elif args.action == "closed-access":
        generate_closed_access(args.module, args.output or args.module / "docs/phantoms/live-world/CLOSED_AREA_ACCESS.tsv")
    else:
        generate_final(args.module, args.output or args.module / "docs/phantoms/live-world/LIVE002_FINAL_REACHABILITY.tsv")
