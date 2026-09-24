"""Independent DB-free final reachability and native target audit for LIVE-002."""

import argparse
import csv
import hashlib
import json
import re
from collections import defaultdict, deque
from pathlib import Path
import xml.etree.ElementTree as ET


BANDS = ((40, 51), (52, 60), (61, 75), (76, 80), (81, 85))
SHARDS = ("high-five-core.xml", "high-five-siege.xml", "high-five-generated-01.xml",
          "high-five-generated-02.xml", "high-five-generated-03.xml", "high-five-generated-04.xml")
EXCLUDED = ("catacomb", "necropolis", "towerofinsolence", "toii", "krateiscube",
            "monasteryofsilence", "forgeofthegods", "giantscave", "stakatonest", "mithrilmines")
COLUMNS = ("band", "status", "start_ingress", "ordered_step", "edge_type", "edge_id",
           "from_anchor_id", "to_anchor_id", "transition_id", "teleporter_npc_id",
           "native_destination", "coverage_key", "npc_level_min", "npc_level_max",
           "source_path", "collision_proof", "blocker")


def read_tsv(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def write_tsv(path, rows):
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        writer = csv.DictWriter(stream, fieldnames=COLUMNS, delimiter="\t", lineterminator="\n", extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def graph(module):
    folder = module / "dist/game/data/phantoms/topology"
    anchors, edges = {}, []
    for name in SHARDS:
        root = ET.parse(folder / name).getroot()
        if root.attrib != {"schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"}:
            raise RuntimeError("Topology schema/version drift: " + name)
        for anchor in root.findall("anchor"):
            aid = anchor.get("id")
            if aid in anchors:
                raise RuntimeError("Duplicate active anchor: " + aid)
            anchors[aid] = anchor
        for edge in root.findall("edge"):
            if edge.get("mode") == "BACKGROUND" and edge.get("backgroundEligible") == "true":
                src, dst, eid = edge.get("fromAnchorId"), edge.get("toAnchorId"), edge.get("id")
                edges.append((src, dst, "BACKGROUND", eid))
                if edge.get("bidirectional") == "true":
                    edges.append((dst, src, "BACKGROUND", eid + ":reverse"))
    if any(src not in anchors or dst not in anchors for src, dst, _, _ in edges):
        raise RuntimeError("Unresolved active background edge")
    shard = ET.parse(folder / "high-five-generated-04.xml").getroot()
    if len(shard.findall("anchor")) != 2 or len(shard.findall("node")) != 2 or len(shard.findall("edge")) != 0 or any(a.get("role") != "ROUTE" for a in shard.findall("anchor")):
        raise RuntimeError("High-band shard scope drift")
    return anchors, edges


def paths(starts, edges):
    adjacent = defaultdict(list)
    for edge in edges:
        adjacent[edge[0]].append(edge)
    previous = {start: None for start in starts}
    origin = {start: start for start in starts}
    queue = deque(sorted(starts))
    while queue:
        current = queue.popleft()
        for edge in sorted(adjacent[current], key=lambda item: (item[1], item[3])):
            if edge[1] not in previous:
                previous[edge[1]] = edge
                origin[edge[1]] = origin[current]
                queue.append(edge[1])
    return previous, origin


def reconstruct(target, previous):
    if target not in previous:
        return None
    route = []
    while previous[target] is not None:
        edge = previous[target]
        route.append(edge)
        target = edge[0]
    return list(reversed(route))


def validate_native_target(module, row):
    spawn_path = module / "dist/game" / row["source_path"]
    group = next((item for item in ET.parse(spawn_path).getroot().findall("spawn")
                  if item.get("zone") == row["source_group"]), None)
    if group is None:
        raise RuntimeError("Native spawn group missing: " + row["coverage_key"])
    native_ids = {item.get("id") for item in group.findall("npc")}
    coverage_ids = set(row["npc_ids"].split("|"))
    if not coverage_ids or not coverage_ids.issubset(native_ids):
        raise RuntimeError("Native spawn NPC membership drift: " + row["coverage_key"])
    levels = []
    for npc_id in coverage_ids:
        prefix = int(npc_id) // 100 * 100
        stat_file = module / "dist/game/data/stats/npcs" / f"{prefix}-{prefix + 99}.xml"
        npc = next((item for item in ET.parse(stat_file).getroot().findall("npc") if item.get("id") == npc_id), None)
        if npc is None:
            raise RuntimeError("Native NPC level source missing: " + npc_id)
        levels.append(int(npc.get("level")))
    if (min(levels), max(levels)) != (int(row["npc_level_min"]), int(row["npc_level_max"])):
        raise RuntimeError("Coverage/native level drift: " + row["coverage_key"])
    return min(levels), max(levels)


def validate_catalog(module, anchors):
    registry = module / "docs/phantoms/live-world"
    manifest = json.loads((registry / "TARGETED_TRAVEL_CONNECTORS_MANIFEST.json").read_text(encoding="utf-8"))
    for name, digest in manifest["output_sha256"].items():
        if sha(registry / name) != digest:
            raise RuntimeError("Supplement/proof hash drift: " + name)
    for shard_name, manifest_key in (("high-five-generated-03.xml", "generated03_sha256"), ("high-five-generated-04.xml", "generated04_sha256")):
        if sha(module / "dist/game/data/phantoms/topology" / shard_name) != manifest[manifest_key]:
            raise RuntimeError("Shard hash drift: " + shard_name)
    base = registry / "TRAVEL_CONNECTORS.tsv"
    targeted = registry / "TARGETED_TRAVEL_CONNECTORS.tsv"
    catalog = ET.parse(module / "dist/game/data/phantoms/travel/high-five-normal-gk.xml").getroot()
    if catalog.get("connectorsSha256") != sha(base) or catalog.get("targetedConnectorsSha256") != sha(targeted):
        raise RuntimeError("Catalog connector provenance drift")
    java = (module / "java/org/l2jmobius/gameserver/phantoms/background/PhantomNormalGatekeeperTravel.java").read_text(encoding="utf-8")
    pin = re.search(r'TARGETED_CONNECTORS_SHA = "([0-9a-f]{64})"', java)
    if pin is None or pin.group(1) != sha(targeted):
        raise RuntimeError("Production targeted connector SHA pin drift")
    connectors = {row["connector_id"]: row for file in (base, targeted) for row in read_tsv(file)}
    facts = {row["fact_key"]: row for row in read_tsv(registry / "GATEKEEPER_FACTS.tsv")}
    transitions = {row["transition_id"]: row for row in read_tsv(registry / "TRAVEL_TRANSITIONS.tsv")}
    legs = {leg.get("id"): leg.attrib for leg in catalog.findall("leg")}
    if len(legs) != len(catalog.findall("leg")) or len(legs) < 12:
        raise RuntimeError("Catalog leg identity/count drift")
    for leg in legs.values():
        transition = transitions.get(leg["transitionId"])
        src = connectors.get(leg["sourceConnectorId"])
        dst = connectors.get(leg["destinationConnectorId"])
        if transition is None or transition["transition_status"] != "FACTUAL_NORMAL" or src is None or dst is None:
            raise RuntimeError("Catalog leg lacks factual provenance")
        fact = next((row for row in facts.values() if row["teleporter_spawn_key"] == transition["from_gk_fact_key"]
                     and row["teleporter_npc_id"] == transition["teleporter_npc_id"]
                     and row["destination_x"] == leg["destinationX"] and row["destination_y"] == leg["destinationY"]
                     and row["destination_z"] == leg["destinationZ"] and row["destination_index"] == leg["destinationIndex"]), None)
        if fact is None or fact["availability_class"] != "NORMAL" or fact["source_path"] != transition["source_path"]:
            raise RuntimeError("Catalog native fact mismatch: " + leg["id"])
        if any(leg["source" + axis.upper()] != fact["teleporter_spawn_" + axis]
               or leg["destination" + axis.upper()] != fact["destination_" + axis]
               for axis in ("x", "y", "z")):
            raise RuntimeError("Catalog/native coordinates drift: " + leg["id"])
        spawn = ET.parse(module / "dist/game" / fact["spawn_source_path"]).getroot()
        if not any(item.get("id") == fact["teleporter_npc_id"] and all(item.get(axis) == fact["teleporter_spawn_" + axis] for axis in ("x", "y", "z")) for item in spawn.iter("npc")):
            raise RuntimeError("Native Gatekeeper spawn drift: " + leg["id"])
        root = ET.parse(module / "dist/game" / fact["source_path"]).getroot()
        npc = next((item for item in root.findall("npc") if item.get("id") == fact["teleporter_npc_id"]), None)
        teleport = next((item for item in npc.findall("teleport") if item.get("type") == "NORMAL"), None) if npc is not None else None
        locations = teleport.findall("location") if teleport is not None else []
        if int(fact["destination_index"]) >= len(locations):
            raise RuntimeError("Native teleporter index drift")
        location = locations[int(fact["destination_index"])]
        if any(location.get(axis) != fact["destination_" + axis] for axis in ("x", "y", "z")) or location.get("castleId", "") != leg["destinationCastleIds"]:
            raise RuntimeError("Native destination drift: " + leg["id"])
        if src["connector_kind"] != "ANCHOR_TO_GK" or src["validation_status"] not in ("VALID_DIRECT", "VALID_PATH") or int(src["path_length"]) <= 0 or src["from_id"] != leg["fromAnchorId"] or src["to_id"] != transition["from_gk_fact_key"]:
            raise RuntimeError("Invalid source connector: " + leg["id"])
        if any(src["to_" + axis] != fact["teleporter_spawn_" + axis] or src["from_" + axis] != anchors[leg["fromAnchorId"]].get(axis) for axis in ("x", "y", "z")):
            raise RuntimeError("Source connector coordinate drift: " + leg["id"])
        if dst["connector_kind"] != "DEST_TO_ANCHOR" or dst["validation_status"] not in ("VALID_DIRECT", "VALID_PATH", "VALID_IDENTITY") or dst["to_id"] != leg["toAnchorId"] or dst["from_id"] != transition["to_destination_fact_key"]:
            raise RuntimeError("Invalid destination connector: " + leg["id"])
        if any(dst["from_" + axis] != fact["destination_" + axis] or dst["to_" + axis] != anchors[leg["toAnchorId"]].get(axis) for axis in ("x", "y", "z")):
            raise RuntimeError("Destination connector coordinate drift: " + leg["id"])
        if dst["validation_status"] == "VALID_IDENTITY" and (dst["reason"] != "CANONICAL_IDENTITY" or any(dst[field] != "0" for field in ("straight_distance", "path_length", "path_segments")) or any(dst["from_" + axis] != dst["to_" + axis] or dst["to_" + axis] != anchors[dst["to_id"]].get(axis) for axis in ("x", "y", "z"))):
            raise RuntimeError("Invalid identity connector: " + leg["id"])
        if leg["fromAnchorId"] not in anchors or leg["toAnchorId"] not in anchors:
            raise RuntimeError("Catalog endpoint missing")
    return legs, connectors


def prove(module, output):
    registry = module / "docs/phantoms/live-world"
    anchors, edges = graph(module)
    legs, connectors = validate_catalog(module, anchors)
    edges.extend((leg["fromAnchorId"], leg["toAnchorId"], "NORMAL_GATEKEEPER", leg["id"]) for leg in legs.values())
    starts = {aid for aid in anchors if aid.startswith("population.ingress.dwarf.")}
    if len(starts) != 6:
        raise RuntimeError("Ingress drift")
    previous, origins = paths(starts, edges)
    coverage = read_tsv(registry / "WORLD_COVERAGE.tsv")
    plan = read_tsv(registry / "HIGH_BANDS_52_85_PLAN.tsv")
    transitions = {row["transition_id"]: row for row in read_tsv(registry / "TRAVEL_TRANSITIONS.tsv")}
    selected = {row["band"]: row for row in plan if row["selection_status"] == "PREFERRED"}
    proof_by_anchor = {row["anchor_id"]: row for row in read_tsv(registry / "HIGH_BANDS_52_85_GEO_PROOF.tsv")}
    counts, rows, statuses = {}, [], {}
    for low, high in BANDS:
        band = f"{low}-{high}"
        eligible = []
        for row in coverage:
            target = "generated.farm." + row["coverage_key"][:24] + ".anchor"
            anchor = anchors.get(target)
            if row["classification"] == "ORDINARY_WORLD" and row["status"] == "READY_STATIC" and row["instance_id"] == "0" and int(row["npc_level_max"]) >= low and int(row["npc_level_min"]) <= high and not any(word in row["source_path"].lower().replace("_", "") for word in EXCLUDED) and anchor is not None and anchor.get("role") == "FARMING" and any(source.get("path") == row["source_path"] for source in anchor.findall("source")):
                eligible.append((target, row))
        reached = [(target, row) for target, row in eligible if target in previous]
        counts[band] = len(reached)
        if low == 40:
            if not reached:
                raise RuntimeError("GLOBAL ordinary 40-51 regression")
            continue
        choice = selected.get(band)
        if choice is None:
            blocker = "NO_OPEN_ORDINARY_CANDIDATE" if not eligible else "NO_VALID_DESTINATION_CONNECTOR"
            if reached:
                raise RuntimeError("Unselected high-band farm unexpectedly reachable: " + band)
            rows.append({"band": band, "status": "BLOCKED", "blocker": blocker})
            statuses[band] = blocker
            continue
        target = choice["target_anchor_id"]
        pair = next(((aid, row) for aid, row in reached if aid == target), None)
        if pair is None:
            raise RuntimeError("Selected high-band target is not reachable: " + band)
        native_min, native_max = validate_native_target(module, pair[1])
        if native_max < low or native_min > high:
            raise RuntimeError("Native NPC level does not overlap: " + band)
        statuses[band] = "GREEN"
        route = reconstruct(target, previous)
        for index, step in enumerate(route, 1):
            leg = legs.get(step[3])
            geo = proof_by_anchor.get(leg["fromAnchorId"]) if leg else None
            rows.append({"band": band, "status": "GREEN", "start_ingress": origins[target],
                         "ordered_step": str(index), "edge_type": step[2], "edge_id": step[3],
                         "from_anchor_id": step[0], "to_anchor_id": step[1],
                         "transition_id": leg["transitionId"] if leg else "",
                         "teleporter_npc_id": leg["teleporterNpcId"] if leg else "",
                         "native_destination": transitions[leg["transitionId"]]["destination_name"] if leg else "",
                         "coverage_key": pair[1]["coverage_key"] if index == len(route) else "",
                         "npc_level_min": str(native_min) if index == len(route) else "",
                         "npc_level_max": str(native_max) if index == len(route) else "",
                         "source_path": pair[1]["source_path"] if index == len(route) else "",
                         "collision_proof": geo["collision_proof"] if geo else "", "blocker": "NONE"})
    if not any(status == "GREEN" for status in statuses.values()):
        raise RuntimeError("No high-band farm was proven")
    write_tsv(output, rows)
    return counts, statuses


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[2] / "docs/phantoms/live-world/HIGH_BANDS_52_85_PROOF.tsv")
    args = parser.parse_args()
    counts, statuses = prove(Path(__file__).resolve().parents[2], args.output)
    print("GLOBAL", counts, "STATUS", statuses, "SHA256", sha(args.output))
