"""Independent, DB-free active topology and two-GK 40-51 route proof."""

import argparse
import csv
import hashlib
import json
import xml.etree.ElementTree as ET
from collections import defaultdict, deque
from pathlib import Path

import travel_backbone as d1


TOPOLOGY = ("high-five-core.xml", "high-five-siege.xml", "high-five-generated-01.xml", "high-five-generated-02.xml", "high-five-generated-03.xml")
TARGET = "generated.farm.4c20d26f8b57611bdafd0d33.anchor"
EXPECTED_TRANSITIONS = ("transition.da2bfda52a944f78cd61ab30", "transition.bb1ab636c13e8cbfaee92385")


def route(start, target, edges):
    adjacent = defaultdict(list)
    for edge in edges:
        adjacent[edge[0]].append(edge)
    previous = {start: None}
    queue = deque([start])
    while queue:
        for edge in sorted(adjacent[queue.popleft()], key=lambda value: (value[3], value[1])):
            if edge[1] not in previous:
                previous[edge[1]] = edge
                queue.append(edge[1])
    if target not in previous:
        return None
    result = []
    cursor = target
    while cursor != start:
        edge = previous[cursor]
        result.append(edge)
        cursor = edge[0]
    return list(reversed(result))


def topology(module):
    directory = module / "dist/game/data/phantoms/topology"
    anchors = {}
    edges = []
    baseline_edges = []
    for name in TOPOLOGY:
        root = ET.parse(directory / name).getroot()
        if root.get("schemaVersion") != "1" or root.get("datasetVersion") != "4":
            raise RuntimeError("Topology schema/version drift: " + name)
        for anchor in root.findall("anchor"):
            key = anchor.get("id")
            if key in anchors:
                raise RuntimeError("Duplicate topology anchor: " + key)
            anchors[key] = anchor
        for element in root.findall("edge"):
            if element.get("mode") == "BACKGROUND" and element.get("backgroundEligible") == "true":
                edge = (element.get("fromAnchorId"), element.get("toAnchorId"), "BACKGROUND", element.get("id"))
                if not edge[0] or not edge[1]:
                    raise RuntimeError("Background edge lacks canonical anchors")
                edges.append(edge)
                if name != "high-five-generated-03.xml":
                    baseline_edges.append(edge)
                if element.get("bidirectional") == "true":
                    reverse = (edge[1], edge[0], "BACKGROUND", edge[3] + ":reverse")
                    edges.append(reverse)
                    if name != "high-five-generated-03.xml":
                        baseline_edges.append(reverse)
    if any(a not in anchors or b not in anchors for a, b, _, _ in edges):
        raise RuntimeError("Active topology edge has missing anchor")
    shard = ET.parse(directory / "high-five-generated-03.xml").getroot()
    if len(shard.findall("anchor")) != 3 or len(shard.findall("edge")) != 4:
        raise RuntimeError("Generated-03 exact scope drift")
    return anchors, edges, baseline_edges


def native_facts(module, catalog):
    registry = module / "docs/phantoms/live-world"
    facts = {"transition." + d1.key(row["fact_key"]): row for row in d1.read_tsv(registry / "GATEKEEPER_FACTS.tsv") if row["teleport_type"] == "NORMAL" and row["availability_class"] == "NORMAL"}
    connectors = {row["connector_id"]: row for name in ("TRAVEL_CONNECTORS.tsv", "TARGETED_TRAVEL_CONNECTORS.tsv") for row in d1.read_tsv(registry / name)}
    for leg in catalog:
        fact = facts.get(leg["transitionId"])
        if fact is None or leg["sourceConnectorId"] not in connectors or leg["destinationConnectorId"] not in connectors:
            raise RuntimeError("Catalog leg has no factual transition/connector")
        for left, right in (("teleporterNpcId", "teleporter_npc_id"), ("teleportListName", "list_name"), ("destinationIndex", "destination_index"), ("destinationX", "destination_x"), ("destinationY", "destination_y"), ("destinationZ", "destination_z"), ("sourceX", "teleporter_spawn_x"), ("sourceY", "teleporter_spawn_y"), ("sourceZ", "teleporter_spawn_z"), ("feeId", "fee_id"), ("feeCount", "fee_count"), ("destinationCastleIds", "castle_ids")):
            if leg[left] != fact[right]:
                raise RuntimeError("Catalog/native TSV drift: " + leg["id"] + ":" + left)
        if connectors[leg["sourceConnectorId"]]["from_id"] != leg["fromAnchorId"] or connectors[leg["destinationConnectorId"]]["to_id"] != leg["toAnchorId"]:
            raise RuntimeError("Catalog connector endpoint drift")
        xml = ET.parse(module / "dist/game" / fact["source_path"]).getroot()
        npc = next((item for item in xml.findall("npc") if item.get("id") == fact["teleporter_npc_id"]), None)
        normal = next((item for item in npc.findall("teleport") if item.get("type") == "NORMAL"), None) if npc is not None else None
        locations = normal.findall("location") if normal is not None else []
        index = int(fact["destination_index"])
        if index >= len(locations):
            raise RuntimeError("Native teleporter index drift")
        location = locations[index]
        for field in ("x", "y", "z", "feeCount"):
            expected = fact["destination_" + field] if field != "feeCount" else fact["fee_count"]
            if location.get(field) != expected:
                raise RuntimeError("Native teleporter XML drift: " + field)
        if location.get("castleId", "") != fact["castle_ids"]:
            raise RuntimeError("Native destination castle XML drift")
    return connectors


def prove(module, output):
    registry = module / "docs/phantoms/live-world"
    manifest = json.loads((registry / "TARGETED_TRAVEL_CONNECTORS_MANIFEST.json").read_text(encoding="utf-8"))
    for name, expected in manifest["output_sha256"].items():
        if d1.sha_file(registry / name) != expected:
            raise RuntimeError("Hermetic proof/supplement hash drift: " + name)
    if d1.sha_file(module / "dist/game/data/phantoms/topology/high-five-generated-03.xml") != manifest["generated03_sha256"]:
        raise RuntimeError("Generated-03 hash drift")
    geo_rows = d1.read_tsv(registry / "LIVE002_40_51_HERMETIC_GEO_PROOF.tsv")
    if len(geo_rows) != 5 or any(row["collision_proof"] != "STATIC_XML_CLEAR" or row["door_intersections"] != "0" or row["fence_intersections"] != "0" for row in geo_rows):
        raise RuntimeError("Hermetic collision proof drift")
    anchors, background, baseline_background = topology(module)
    route_proof = d1.read_tsv(registry / "PLUNDEROUS_40_FINAL_ROUTE.tsv")
    if len(route_proof) != 4:
        raise RuntimeError("Plunderous exact proof scope drift")
    shard_edges = {edge.get("id"): edge for edge in ET.parse(module / "dist/game/data/phantoms/topology/high-five-generated-03.xml").getroot().findall("edge")}
    for evidence, movement in zip(route_proof, geo_rows[:4]):
        if evidence["connector_id"] != movement["connector_id"] or evidence["validation_status"] != movement["validation_status"] or evidence["collision_proof"] != "STATIC_XML_CLEAR":
            raise RuntimeError("Published edge/hermetic proof mismatch")
        edge = shard_edges.get(evidence["edge_id"])
        if edge is None or edge.get("fromAnchorId") != evidence["from_anchor_id"] or edge.get("toAnchorId") != evidence["to_anchor_id"]:
            raise RuntimeError("Published topology/proof endpoint mismatch")
        for prefix, key in (("from", "from_anchor_id"), ("to", "to_anchor_id")):
            anchor = anchors[evidence[key]]
            if any(evidence[prefix + "_" + axis] != anchor.get(axis) for axis in ("x", "y", "z")):
                raise RuntimeError("Published proof coordinate drift")
    bilia = next(row for row in d1.read_tsv(registry / "GATEKEEPER_FACTS.tsv") if row["teleporter_npc_id"] == "31964" and row["teleporter_spawn_key"] == "spawn.3f94b4ec9d3dbb2e1b66b6de" and row["destination_x"] == "111965")
    if any(geo_rows[4]["from_" + axis] != anchors["generated.route.schuttgart.40.arrival.anchor"].get(axis) or geo_rows[4]["to_" + axis] != bilia["teleporter_spawn_" + axis] for axis in ("x", "y", "z")):
        raise RuntimeError("Schuttgart/Bilia hermetic proof endpoint drift")
    catalog_path = module / "dist/game/data/phantoms/travel/high-five-normal-gk.xml"
    catalog_root = ET.parse(catalog_path).getroot()
    if catalog_root.get("connectorsSha256") != manifest["base_d1_connectors_sha256"] or catalog_root.get("targetedConnectorsSha256") != manifest["output_sha256"]["TARGETED_TRAVEL_CONNECTORS.tsv"]:
        raise RuntimeError("Catalog connector provenance drift")
    catalog = [leg.attrib for leg in catalog_root.findall("leg")]
    if len(catalog) != 12:
        raise RuntimeError("Catalog leg count drift")
    native_facts(module, catalog)
    gk_edges = [(leg["fromAnchorId"], leg["toAnchorId"], "NORMAL_GATEKEEPER", leg["id"]) for leg in catalog]
    baseline_gk = [edge for edge, leg in zip(gk_edges, catalog) if leg["sourceConnectorId"] != "connector.92ca2c480990a8b06fa07d82" and leg["destinationConnectorId"] != "connector.73bea2de1609f40ad6b3230e"]
    starts = sorted(key for key in anchors if key.startswith("population.ingress.dwarf."))
    if len(starts) != 6:
        raise RuntimeError("Factual Dwarf ingress set drift")
    if any(route(start, TARGET, baseline_background + baseline_gk) is not None for start in starts):
        raise RuntimeError("Baseline 40-51 unexpectedly reachable")
    witnesses = [(start, route(start, TARGET, background + gk_edges)) for start in starts]
    witnesses = [(start, path) for start, path in witnesses if path is not None]
    if not witnesses:
        raise RuntimeError("GLOBAL ordinary 40-51 remains unreachable")
    start, path = min(witnesses, key=lambda item: (len(item[1]), item[0]))
    legs_by_id = {leg["id"]: leg for leg in catalog}
    transitions = tuple(legs_by_id[step[3]]["transitionId"] for step in path if step[2] == "NORMAL_GATEKEEPER")
    if transitions != EXPECTED_TRANSITIONS:
        raise RuntimeError("Two-GK ordered route drift: " + str(transitions))
    spawn = ET.parse(module / "dist/game/data/spawns/Others/PlunderousPlains.xml").getroot()
    group = next((element for element in spawn.findall("spawn") if element.get("zone") == "PlunderousPlains_30"), None)
    npc = ET.parse(module / "dist/game/data/stats/npcs/22000-22099.xml").getroot()
    captain = next((element for element in npc.findall("npc") if element.get("id") == "22026"), None)
    if group is None or not any(element.get("id") == "22026" for element in group.findall("npc")) or captain is None or captain.get("level") != "40":
        raise RuntimeError("Factual ordinary level-40 target drift")
    coverage = d1.read_tsv(registry / "WORLD_COVERAGE.tsv")
    eligible = {"generated.farm." + row["coverage_key"][:24] + ".anchor" for row in coverage if row["classification"] == "ORDINARY_WORLD" and row["status"] == "READY_STATIC" and int(row["npc_level_max"]) >= 40 and int(row["npc_level_min"]) <= 51}
    baseline_reachable = {target for candidate in starts for target in eligible if route(candidate, target, baseline_background + baseline_gk) is not None}
    if baseline_reachable:
        raise RuntimeError("Baseline GLOBAL ordinary 40-51 is not zero")
    reachable = {target for candidate in starts for target in eligible if route(candidate, target, background + gk_edges) is not None}
    if TARGET not in reachable:
        raise RuntimeError("GLOBAL ordinary 40-51 target missing")
    rows = []
    for index, step in enumerate(path, 1):
        leg = legs_by_id.get(step[3])
        rows.append({"step": str(index), "start_anchor_id": start, "edge_type": step[2], "edge_id": step[3], "from_anchor_id": step[0], "to_anchor_id": step[1], "transition_id": leg["transitionId"] if leg else "", "fee_count": leg["feeCount"] if leg else "", "destination_castle_ids": leg["destinationCastleIds"] if leg else "", "target_npc_id": "22026", "target_level": "40", "global_ordinary_40_51": str(len(reachable))})
    columns = ("step", "start_anchor_id", "edge_type", "edge_id", "from_anchor_id", "to_anchor_id", "transition_id", "fee_count", "destination_castle_ids", "target_npc_id", "target_level", "global_ordinary_40_51")
    output.parent.mkdir(parents=True, exist_ok=True)
    d1.write_tsv(output, columns, rows)
    return start, len(path), len(reachable)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[2] / "docs/phantoms/live-world/LIVE002_40_51_FINAL.tsv")
    args = parser.parse_args()
    start, steps, count = prove(Path(__file__).resolve().parents[2], args.output)
    print(f"GLOBAL_ORDINARY_40_51={count} STEPS={steps} START={start} SHA256={d1.sha_file(args.output)}")
