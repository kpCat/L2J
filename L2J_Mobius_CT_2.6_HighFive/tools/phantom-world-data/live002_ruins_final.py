"""Independent DB-free validation of active Ruins travel and farm reachability."""

import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

import live002_high_bands_final as previous


SHARDS = ("high-five-core.xml", "high-five-siege.xml", *(f"high-five-generated-{i:02}.xml" for i in range(1, 7)), "high-five-closed-ssq-01.xml", "high-five-closed-devils-isle.xml", "high-five-closed-toi.xml", "high-five-closed-ivory.xml", "high-five-closed-imperial.xml")
KEY = "b729b4b54a41170f2da2aa85307ff726b04e894296e26e9d3d85be8899dbd4f3"
FARM = "generated.farm." + KEY[:24]
TARGET = FARM + ".anchor"
GLUDIO = "generated.route.live002.gludio.arrival.anchor"
RUINS = "generated.route.live002.ruins.arrival.anchor"
BANDS = ((40, 51), (52, 60), (61, 75), (76, 80), (81, 85))
MINIMUMS = {"40-51": 5, "52-60": 13, "61-75": 15, "76-80": 3, "81-85": 1}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate(module, output):
    folder = module / "dist/game/data/phantoms/topology"
    registry = module / "docs/phantoms/live-world"
    roots = [ET.parse(folder / name).getroot() for name in SHARDS]
    if any(root.attrib != {"schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"} for root in roots):
        raise RuntimeError("Active topology version drift")
    anchors = {anchor.get("id"): anchor for root in roots for anchor in root.findall("anchor")}
    if len(anchors) != sum(len(root.findall("anchor")) for root in roots):
        raise RuntimeError("Duplicate active anchors")
    edges = []
    for root in roots:
        for edge in root.findall("edge"):
            if edge.get("mode") == "BACKGROUND" and edge.get("backgroundEligible") == "true":
                edges.append((edge.get("fromAnchorId"), edge.get("toAnchorId"), "BACKGROUND", edge.get("id")))
                if edge.get("bidirectional") == "true":
                    edges.append((edge.get("toAnchorId"), edge.get("fromAnchorId"), "BACKGROUND", edge.get("id") + ":reverse"))
    if any(source not in anchors or target not in anchors for source, target, _, _ in edges):
        raise RuntimeError("Background edge endpoint missing")
    shard = roots[7]
    nodes = {node.get("id"): node for node in shard.findall("node")}
    if len(nodes) != 8 or len(shard.findall("anchor")) != 8 or len(shard.findall("edge")) != 6:
        raise RuntimeError("Ruins shard scope drift")
    farm = nodes[FARM]
    if any(farm.get(key) != value for key, value in {"kind": "FARMING_AREA", "form": "POINT_RADIUS", "instanceId": "0", "x": "-33539", "y": "137701", "z": "-3480", "radius": "1"}.items()) or any(anchors[TARGET].get(key) != value for key, value in {"role": "FARMING", "npcId": "20059", "instanceId": "0"}.items()):
        raise RuntimeError("Ruins farm anchor drift")
    if not any(item.get("path") == "data/spawns/Others/18_22.xml" for item in farm.findall("source")):
        raise RuntimeError("Ruins native source drift")
    native = ET.parse(module / "dist/game/data/spawns/Others/18_22.xml").getroot()
    if not any(n.get("id") == "20059" and (n.get("x"), n.get("y"), n.get("z")) == ("-33539", "137701", "-3479") for n in native.iter("npc")):
        raise RuntimeError("Ruins native Monster point drift")
    npc = next(n for n in ET.parse(module / "dist/game/data/stats/npcs/20000-20099.xml").getroot().findall("npc") if n.get("id") == "20059")
    if npc.get("type") != "Monster" or npc.get("level") != "22":
        raise RuntimeError("Ruins native NPC type/level drift")
    proof_path = registry / "RUINS_GEODATA_PATH_PROOF.tsv"
    if sha(proof_path) != sha(module / "dist/game/data/phantoms/evidence/live002-ruins-geodata-path.tsv"):
        raise RuntimeError("Ruins proof copy drift")
    proof = previous.read_tsv(proof_path)
    by_id = {edge.get("id"): edge for edge in shard.findall("edge")}
    for row in proof:
        edge = by_id.get("generated.route.live002.ruins.geo.hop-" + row["hop"])
        if (edge is None or edge.get("fromNodeId") != row["from_id"] or edge.get("toNodeId") != row["to_id"]
                or edge.get("bidirectional") != "false" or edge.get("baseTravelMillis") != str(int(row["path_length"]) * 10)
                or row["collision_proof"] != "STATIC_XML_CLEAR" or row["door_intersections"] != "0"
                or row["fence_intersections"] != "0" or row["status"] not in ("VALID_DIRECT", "VALID_PATH")
                or int(row["required_buffer"]) > 500
                or any(anchors[row["to_id"] + ".anchor"].get(axis) != row["to_" + axis] for axis in "xyz")):
            raise RuntimeError("Ruins published hop lacks exact proof: " + row["hop"])
    manifest = json.loads((registry / "TARGETED_TRAVEL_CONNECTORS_MANIFEST.json").read_text(encoding="utf-8"))
    if manifest.get("generated06_sha256") != sha(folder / SHARDS[7]):
        raise RuntimeError("Ruins shard provenance drift")
    supplement = registry / "TARGETED_TRAVEL_CONNECTORS.tsv"
    new_sha = sha(supplement)
    if manifest["output_sha256"]["TARGETED_TRAVEL_CONNECTORS.tsv"] != new_sha:
        raise RuntimeError("Ruins supplement provenance drift")
    java = (module / "java/org/l2jmobius/gameserver/phantoms/background/PhantomNormalGatekeeperTravel.java").read_text(encoding="utf-8")
    pin = re.search(r'TARGETED_CONNECTORS_SHA = "([0-9a-f]{64})"', java)
    if pin is None or pin.group(1) != new_sha or new_sha == "881c6be02318523aefaaf726c343004d0991273d6d8ee75f1162e35d62e97fad":
        raise RuntimeError("Ruins production provenance pin drift")
    legs, connectors = previous.validate_catalog(module, anchors)
    if len(legs) != 54 or len(previous.read_tsv(supplement)) != 13:
        raise RuntimeError("Ruins NORMAL catalog scope drift")
    transitions = {leg["transitionId"] for leg in legs.values()}
    if not {"transition.e8a8dea18661b81ca8c39204", "transition.6e5c4bb7d4430ac8102fe4e2"}.issubset(transitions):
        raise RuntimeError("Ruins native NORMAL legs missing")
    edges.extend((leg["fromAnchorId"], leg["toAnchorId"], "NORMAL_GATEKEEPER", leg["id"]) for leg in legs.values())
    starts = {anchor for anchor in anchors if anchor.startswith("population.ingress.dwarf.")}
    reached, origins = previous.paths(starts, edges)
    if len(starts) != 6 or TARGET not in reached:
        raise RuntimeError("RUINS_OF_DESPAIR_REACHABLE_FARM=0")
    route = previous.reconstruct(TARGET, reached)
    selected = {legs[step[3]]["transitionId"] for step in route if step[2] == "NORMAL_GATEKEEPER"}
    if "transition.6e5c4bb7d4430ac8102fe4e2" not in selected or not any(step[0] == RUINS for step in route) or not any(step[1] == GLUDIO for step in route):
        raise RuntimeError("Ruins executable route lacks native entrance")
    coverage = previous.read_tsv(registry / "WORLD_COVERAGE.tsv")
    counts = {}
    for low, high in BANDS:
        band = f"{low}-{high}"
        eligible = {"generated.farm." + row["coverage_key"][:24] + ".anchor" for row in coverage
                    if row["classification"] == "ORDINARY_WORLD" and row["status"] == "READY_STATIC"
                    and row["instance_id"] == "0" and int(row["npc_level_max"]) >= low
                    and int(row["npc_level_min"]) <= high
                    and not any(word in row["source_path"].lower().replace("_", "") for word in previous.EXCLUDED)}
        counts[band] = len({anchor for anchor in eligible if anchor in reached and anchor in anchors and anchors[anchor].get("role") == "FARMING"})
        if counts[band] < MINIMUMS[band]:
            raise RuntimeError("GLOBAL ordinary regression: " + band)
    rows = [{"metric": "RUINS_OF_DESPAIR_REACHABLE_FARM", "value": "1", "evidence": origins[TARGET] + "|" + TARGET}]
    rows.extend({"metric": "GLOBAL_ORDINARY_" + band, "value": str(count), "evidence": "active_topology_and_normal_catalog"} for band, count in counts.items())
    rows.extend({"metric": "RUINS_ROUTE_STEP_" + str(index), "value": step[2], "evidence": step[3] + "|" + step[0] + "|" + step[1]} for index, step in enumerate(route, 1))
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        writer = csv.DictWriter(stream, fieldnames=("metric", "value", "evidence"), delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    return counts, len(route), new_sha


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[2] / "docs/phantoms/live-world/LIVE002_RUINS_FINAL.tsv")
    args = parser.parse_args()
    print("RUINS_OF_DESPAIR_REACHABLE_FARM=1", *validate(Path(__file__).resolve().parents[2], args.output), "SHA256=" + sha(args.output))
