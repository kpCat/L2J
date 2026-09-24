"""DB-free, bounded inventory of current ordinary high-band routes."""

import argparse
from collections import defaultdict, deque
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET

import normal_gk_catalog as gk
import travel_backbone as d1


BANDS = ((52, 60), (61, 75), (76, 80), (81, 85))
SHARDS = ("high-five-core.xml", "high-five-siege.xml", "high-five-generated-01.xml",
          "high-five-generated-02.xml", "high-five-generated-03.xml")
PLAN_COLUMNS = ("band", "rank", "coverage_key", "source_path", "source_zone", "npc_level_min",
                "npc_level_max", "target_anchor_id", "anchor_validation_status", "active_anchor",
                "current_reachable", "teleporter_npc_id", "transition_id", "native_destination_name",
                "destination_x", "destination_y", "destination_z", "destination_connector_id",
                "source_anchor_id", "source_current_reachable", "missing_hub", "missing_local_route",
                "closed_or_multifloor", "estimated_new_geo_checks", "selection_status", "selection_reason")
EXCLUDED = ("catacomb", "necropolis", "towerofinsolence", "toii", "krateiscube", "monasteryofsilence",
            "forgeofthegods", "giantscave", "stakatonest", "mithrilmines")
HUB_NPCS = {"30177", "30848", "31275", "31320"}


def active_graph(module):
    anchors = {}
    edges = []
    folder = module / "dist/game/data/phantoms/topology"
    for name in SHARDS:
        root = ET.parse(folder / name).getroot()
        if root.get("schemaVersion") != "1" or root.get("datasetVersion") != "4":
            raise RuntimeError("Topology version drift: " + name)
        for anchor in root.findall("anchor"):
            aid = anchor.get("id")
            if aid in anchors:
                raise RuntimeError("Duplicate anchor: " + aid)
            anchors[aid] = anchor
        for edge in root.findall("edge"):
            if edge.get("mode") == "BACKGROUND" and edge.get("backgroundEligible") == "true":
                item = (edge.get("fromAnchorId"), edge.get("toAnchorId"))
                edges.append(item)
                if edge.get("bidirectional") == "true":
                    edges.append(item[::-1])
    catalog = ET.parse(module / "dist/game/data/phantoms/travel/high-five-normal-gk.xml").getroot()
    registry = module / "docs/phantoms/live-world"
    base_ids = {row["connector_id"] for row in d1.read_tsv(registry / "TRAVEL_CONNECTORS.tsv")}
    base_ids.update(("connector.73bea2de1609f40ad6b3230e", "connector.92ca2c480990a8b06fa07d82"))
    legs = [leg.attrib for leg in catalog.findall("leg") if leg.get("sourceConnectorId") in base_ids and leg.get("destinationConnectorId") in base_ids]
    if len(legs) != 12:
        raise RuntimeError("BLOCKED_BASELINE_REACHABILITY_DRIFT: GK legs != 12")
    edges.extend((leg["fromAnchorId"], leg["toAnchorId"]) for leg in legs)
    if any(a not in anchors or b not in anchors for a, b in edges):
        raise RuntimeError("Active edge endpoint missing")
    return anchors, edges, legs


def reachable(starts, edges):
    adjacent = defaultdict(list)
    for source, target in edges:
        adjacent[source].append(target)
    visited = set(starts)
    queue = deque(sorted(starts))
    while queue:
        for target in sorted(adjacent[queue.popleft()]):
            if target not in visited:
                visited.add(target)
                queue.append(target)
    return visited


def inventory(module):
    registry = module / "docs/phantoms/live-world"
    anchors, edges, legs = active_graph(module)
    starts = {aid for aid in anchors if aid.startswith("population.ingress.dwarf.")}
    if len(starts) != 6:
        raise RuntimeError("BLOCKED_BASELINE_REACHABILITY_DRIFT: ingress set")
    seen = reachable(starts, edges)
    coverage = d1.read_tsv(registry / "WORLD_COVERAGE.tsv")
    candidates = {row["coverage_key"]: row for row in d1.read_tsv(registry / "TOPOLOGY_CANDIDATES.tsv")}
    geo = {row["coverage_key"]: row for row in d1.read_tsv(registry / "ANCHOR_GEODATA_VALIDATION.tsv")}
    counts = {}
    for low, high in ((40, 51), *BANDS):
        keys = {"generated.farm." + row["coverage_key"][:24] + ".anchor" for row in coverage
                if row["classification"] == "ORDINARY_WORLD" and row["status"] == "READY_STATIC"
                and row["instance_id"] == "0" and int(row["npc_level_max"]) >= low
                and int(row["npc_level_min"]) <= high}
        counts[f"{low}-{high}"] = len(keys & seen)
    if counts != {"40-51": 1, "52-60": 0, "61-75": 0, "76-80": 0, "81-85": 0}:
        raise RuntimeError("BLOCKED_BASELINE_REACHABILITY_DRIFT: " + str(counts))

    facts = {"transition." + d1.key(row["fact_key"]): row for row in d1.read_tsv(registry / "GATEKEEPER_FACTS.tsv")}
    transitions = d1.read_tsv(registry / "TRAVEL_TRANSITIONS.tsv")
    base_ids = {row["connector_id"] for row in d1.read_tsv(registry / "TRAVEL_CONNECTORS.tsv")}
    base_ids.update(("connector.73bea2de1609f40ad6b3230e", "connector.92ca2c480990a8b06fa07d82"))
    connectors = [row for name in ("TRAVEL_CONNECTORS.tsv", "TARGETED_TRAVEL_CONNECTORS.tsv")
                  for row in d1.read_tsv(registry / name) if row["connector_id"] in base_ids]
    source_by_gk = defaultdict(list)
    dest_by_id = defaultdict(list)
    for row in connectors:
        if row["connector_kind"] == "ANCHOR_TO_GK" and row["validation_status"] in ("VALID_DIRECT", "VALID_PATH"):
            source_by_gk[row["to_id"]].append(row)
        if gk.destination_eligible(row, {aid: tuple(anchor.get(axis) for axis in ("x", "y", "z", "instanceId")) for aid, anchor in anchors.items()}):
            dest_by_id[row["from_id"]].append(row)
    by_source = defaultdict(list)
    for row in coverage:
        if row["classification"] == "ORDINARY_WORLD" and row["status"] == "READY_STATIC" and row["instance_id"] == "0":
            by_source[row["source_path"]].append(row)
    options = defaultdict(list)
    for transition in transitions:
        if transition["transition_status"] != "FACTUAL_NORMAL" or transition["teleport_type"] != "NORMAL":
            continue
        fact = facts.get(transition["transition_id"])
        if fact is None or fact["availability_class"] != "NORMAL":
            continue
        sources = source_by_gk[transition["from_gk_fact_key"]]
        destinations = dest_by_id[transition["to_destination_fact_key"]]
        if not destinations:
            continue
        for dest in destinations:
            anchor = anchors.get(dest["to_id"])
            if anchor is None:
                continue
            spawn_paths = {source.get("path") for source in anchor.findall("source")}
            for path in spawn_paths:
                for row in by_source.get(path, ()):
                    target = "generated.farm." + row["coverage_key"][:24] + ".anchor"
                    if target not in anchors:
                        continue
                    direct_or_connected = target in reachable((dest["to_id"],), edges)
                    best_source = min(sources, key=lambda item: (item["from_id"] not in seen, item["connector_id"])) if sources else None
                    source_reachable = best_source is not None and best_source["from_id"] in seen
                    hub = fact["teleporter_npc_id"] in HUB_NPCS and not source_reachable
                    options[row["coverage_key"]].append((not direct_or_connected, not source_reachable, not hub,
                                                           transition["transition_id"], dest["connector_id"],
                                                           fact, transition, dest, best_source))
    rows = []
    for low, high in BANDS:
        band = f"{low}-{high}"
        for row in coverage:
            if row["classification"] != "ORDINARY_WORLD" or row["status"] != "READY_STATIC" or row["instance_id"] != "0" or int(row["npc_level_max"]) < low or int(row["npc_level_min"]) > high:
                continue
            key = row["coverage_key"]
            target = "generated.farm." + key[:24] + ".anchor"
            closed = any(word in row["source_path"].lower().replace("_", "") for word in EXCLUDED)
            best = min(options[key]) if options[key] else None
            fact, transition, dest, source = best[5:] if best else (None, None, None, None)
            local = int(best[0]) if best else 1
            hub = int(best[2] is False and best[1] is True) if best else 0
            estimate = (2 * hub + local) if best else 99
            rows.append({"band": band, "coverage_key": key, "source_path": row["source_path"],
                         "source_zone": row["source_group"], "npc_level_min": row["npc_level_min"],
                         "npc_level_max": row["npc_level_max"], "target_anchor_id": target,
                         "anchor_validation_status": geo.get(key, {}).get("status", candidates.get(key, {}).get("anchor_validation", "")),
                         "active_anchor": str(target in anchors).lower(), "current_reachable": str(target in seen).lower(),
                         "teleporter_npc_id": fact["teleporter_npc_id"] if fact else "",
                         "transition_id": transition["transition_id"] if transition else "",
                         "native_destination_name": fact["destination_name"] if fact else "",
                         "destination_x": fact["destination_x"] if fact else "",
                         "destination_y": fact["destination_y"] if fact else "",
                         "destination_z": fact["destination_z"] if fact else "",
                         "destination_connector_id": dest["connector_id"] if dest else "",
                         "source_anchor_id": source["from_id"] if source else "",
                         "source_current_reachable": str(bool(source and source["from_id"] in seen)).lower(),
                         "missing_hub": str(hub), "missing_local_route": str(local),
                         "closed_or_multifloor": str(closed).lower(), "estimated_new_geo_checks": str(estimate),
                         "selection_status": "", "selection_reason": ""})
    for low, high in BANDS:
        band = f"{low}-{high}"
        group = [row for row in rows if row["band"] == band]
        group.sort(key=lambda row: (row["closed_or_multifloor"] == "true", row["destination_connector_id"] == "",
                                    row["source_current_reachable"] != "true", int(row["missing_local_route"]),
                                    int(row["estimated_new_geo_checks"]), row["source_path"], row["coverage_key"]))
        for rank, row in enumerate(group, 1):
            row["rank"] = str(rank)
            row["selection_status"] = "PREFERRED" if rank == 1 and row["closed_or_multifloor"] == "false" and row["destination_connector_id"] else "INVENTORY"
            row["selection_reason"] = "CLOSED_OR_MULTIFLOOR" if row["closed_or_multifloor"] == "true" else ("FACTUAL_JOIN" if row["destination_connector_id"] else "NO_VALID_DESTINATION_CONNECTOR")
    rows.sort(key=lambda row: (next(i for i, pair in enumerate(BANDS) if row["band"] == f"{pair[0]}-{pair[1]}"), int(row["rank"])))
    return counts, rows


def hub_geo_input(module, output):
    facts = d1.read_tsv(module / "docs/phantoms/live-world/GATEKEEPER_FACTS.tsv")
    rows = []
    for npc, town in (("30848", "Town of Aden"), ("31275", "Town of Goddard")):
        arrival = next(row for row in facts if row["teleporter_npc_id"] == "31964"
                       and row["destination_name"] == town and row["availability_class"] == "NORMAL")
        source = next(row for row in facts if row["teleporter_npc_id"] == npc
                      and row["availability_class"] == "NORMAL")
        rows.append({"connector_id": "hub." + npc, "from_x": arrival["destination_x"],
                     "from_y": arrival["destination_y"], "from_z": arrival["destination_z"],
                     "from_instance": "0", "to_x": source["teleporter_spawn_x"],
                     "to_y": source["teleporter_spawn_y"], "to_z": source["teleporter_spawn_z"],
                     "to_instance": "0"})
    output.parent.mkdir(parents=True, exist_ok=True)
    d1.write_tsv(output, ("connector_id", "from_x", "from_y", "from_z", "from_instance",
                          "to_x", "to_y", "to_z", "to_instance"), rows)


def publish(module, geo_input, geo_proof):
    registry = module / "docs/phantoms/live-world"
    topology_dir = module / "dist/game/data/phantoms/topology"
    candidates, proof = d1.read_tsv(geo_input), d1.read_tsv(geo_proof)
    if [row["connector_id"] for row in candidates] != ["hub.30848", "hub.31275"] or [row["connector_id"] for row in proof] != ["hub.30848", "hub.31275"]:
        raise RuntimeError("Hub Geo proof direction drift")
    if any(row["validation_status"] not in ("VALID_DIRECT", "VALID_PATH") or row["collision_proof"] != "STATIC_XML_CLEAR" or row["door_intersections"] != "0" or row["fence_intersections"] != "0" or int(row["path_length"]) <= 0 for row in proof):
        raise RuntimeError("Hub Geo proof is not positive and collision-clear")
    facts = d1.read_tsv(registry / "GATEKEEPER_FACTS.tsv")
    old = d1.read_tsv(registry / "TARGETED_TRAVEL_CONNECTORS.tsv")
    accepted_ids = {"connector.73bea2de1609f40ad6b3230e", "connector.92ca2c480990a8b06fa07d82"}
    accepted = [row for row in old if row["connector_id"] in accepted_ids]
    if len(accepted) != 2:
        raise RuntimeError("Accepted 40-51 supplement drift")
    shard = ET.Element("topology", {"schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"})
    new_rows = []
    proof_rows = []
    for npc, town, label, candidate, result in zip(("30848", "31275"), ("Town of Aden", "Town of Goddard"), ("aden", "goddard"), candidates, proof):
        arrival = next(row for row in facts if row["teleporter_npc_id"] == "31964" and row["destination_name"] == town and row["availability_class"] == "NORMAL")
        source = next(row for row in facts if row["teleporter_npc_id"] == npc and row["availability_class"] == "NORMAL")
        xyz = tuple(arrival["destination_" + axis] for axis in ("x", "y", "z"))
        gk_xyz = tuple(source["teleporter_spawn_" + axis] for axis in ("x", "y", "z"))
        if tuple(candidate["from_" + axis] for axis in ("x", "y", "z")) != xyz or tuple(candidate["to_" + axis] for axis in ("x", "y", "z")) != gk_xyz:
            raise RuntimeError("Hub Geo endpoints/native facts drift: " + npc)
        node_id = "generated.route.live002." + label + ".arrival"
        anchor_id = node_id + ".anchor"
        dest_id = "dest." + d1.key(*xyz, "0")
        node = ET.SubElement(shard, "node", {"id": node_id, "kind": "ROUTE_AREA", "instanceId": "0", "form": "CUBOID",
            **{prefix + axis.upper(): str(int(value) + delta) for axis, value in zip(("x", "y", "z"), xyz) for prefix, delta in (("min", -1), ("max", 1))}, "tags": "route"})
        ET.SubElement(node, "source", {"path": arrival["source_path"]})
        anchor = ET.SubElement(shard, "anchor", {"id": anchor_id, "role": "ROUTE", "nodeId": node_id,
            "x": xyz[0], "y": xyz[1], "z": xyz[2], "instanceId": "0", "tolerance": "0", "tags": "route"})
        ET.SubElement(anchor, "source", {"path": arrival["source_path"]})
        distance = str(math.ceil(math.dist(tuple(map(int, xyz)), tuple(map(int, gk_xyz)))))
        common = {"instance_id": "0", "map_region_from": "", "map_region_to": ""}
        new_rows.append({**common, "connector_id": "connector." + d1.key("DEST_TO_ANCHOR", dest_id, anchor_id),
            "connector_kind": "DEST_TO_ANCHOR", "from_type": "DEST", "from_id": dest_id, "to_type": "ANCHOR", "to_id": anchor_id,
            **{"from_" + axis: value for axis, value in zip(("x", "y", "z"), xyz)},
            **{"to_" + axis: value for axis, value in zip(("x", "y", "z"), xyz)},
            "straight_distance": "0", "validation_status": "VALID_IDENTITY", "path_length": "0", "path_segments": "0",
            "source_refs": arrival["source_path"], "reason": "CANONICAL_IDENTITY"})
        new_rows.append({**common, "connector_id": "connector." + d1.key("ANCHOR_TO_GK", anchor_id, source["teleporter_spawn_key"]),
            "connector_kind": "ANCHOR_TO_GK", "from_type": "ANCHOR", "from_id": anchor_id, "to_type": "GK", "to_id": source["teleporter_spawn_key"],
            **{"from_" + axis: value for axis, value in zip(("x", "y", "z"), xyz)},
            **{"to_" + axis: value for axis, value in zip(("x", "y", "z"), gk_xyz)},
            "straight_distance": distance, "validation_status": result["validation_status"],
            "path_length": result["path_length"], "path_segments": result["path_segments"],
            "source_refs": "|".join(sorted((arrival["source_path"], source["spawn_source_path"]))), "reason": "GEOENGINE_PROVEN"})
        proof_rows.append({**candidate, **result, "anchor_id": anchor_id, "source_path": source["spawn_source_path"]})
    ET.indent(shard, space="\t")
    shard_path = topology_dir / "high-five-generated-04.xml"
    shard_path.write_bytes(b'<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(shard, encoding="utf-8") + b"\n")
    combined = sorted(accepted + new_rows, key=lambda row: row["connector_id"])
    if {row["connector_id"] for row in old} != {row["connector_id"] for row in combined} and {row["connector_id"] for row in old} != accepted_ids:
        raise RuntimeError("Unexpected targeted connector rows would be removed")
    d1.write_tsv(registry / "TARGETED_TRAVEL_CONNECTORS.tsv", d1.CONNECTOR_COLUMNS, combined)
    d1.write_tsv(registry / "HIGH_BANDS_52_85_GEO_PROOF.tsv", tuple(proof_rows[0]), proof_rows)
    manifest_path = registry / "TARGETED_TRAVEL_CONNECTORS_MANIFEST.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["generated04_sha256"] = d1.sha_file(shard_path)
    manifest["output_sha256"]["TARGETED_TRAVEL_CONNECTORS.tsv"] = d1.sha_file(registry / "TARGETED_TRAVEL_CONNECTORS.tsv")
    manifest["output_sha256"]["HIGH_BANDS_52_85_GEO_PROOF.tsv"] = d1.sha_file(registry / "HIGH_BANDS_52_85_GEO_PROOF.tsv")
    manifest["counts"]["high_band_movement_directions"] = 2
    manifest["counts"]["targeted_connectors"] = len(combined)
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8", newline="\n")
    legs = gk.build(module, module / "dist/game/data/phantoms/travel/high-five-normal-gk.xml")
    return len(combined), len(legs)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[2] / "docs/phantoms/live-world/HIGH_BANDS_52_85_PLAN.tsv")
    parser.add_argument("--hub-geo-input", type=Path)
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--hub-geo-proof", type=Path)
    args = parser.parse_args()
    module = Path(__file__).resolve().parents[2]
    counts, rows = inventory(module) if not args.publish else ({}, [])
    if args.publish:
        if not args.hub_geo_input or not args.hub_geo_proof:
            parser.error("--publish requires --hub-geo-input and --hub-geo-proof")
        print("PUBLISHED", publish(module, args.hub_geo_input, args.hub_geo_proof))
        raise SystemExit(0)
    d1.write_tsv(args.output, PLAN_COLUMNS, rows)
    if args.hub_geo_input:
        hub_geo_input(Path(__file__).resolve().parents[2], args.hub_geo_input)
    print("BASELINE", counts, "CANDIDATES", len(rows))
    for low, high in BANDS:
        selected = next((row for row in rows if row["band"] == f"{low}-{high}" and row["selection_status"] == "PREFERRED"), None)
        print(f"{low}-{high}", {key: selected[key] for key in ("source_path", "source_zone", "teleporter_npc_id", "native_destination_name", "missing_hub", "missing_local_route", "estimated_new_geo_checks") } if selected else "NONE")
