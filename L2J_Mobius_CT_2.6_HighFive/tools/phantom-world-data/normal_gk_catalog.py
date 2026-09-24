"""Generate the bounded NORMAL Gatekeeper runtime subset from accepted D1 proof."""

import argparse
import hashlib
import json
import math
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

import travel_backbone as d1


ACCEPTED_D1 = {
    "GATEKEEPER_FACTS.tsv": "a8318075ef6ea3c3f266aa975ef1565fb8d6c93d2f074f076a059ed5d2ea2fe4",
    "TRAVEL_CONNECTORS.tsv": "fe0c0433e8975ff43f397470eca5caf0ae3b545e4d66d0ece2096d1ed97c1926",
    "TRAVEL_TRANSITIONS.tsv": "d378de9ddb395914c7e36480f149143f3cb9a2b84284ca625708d5c880f3e09d",
    "TRAVEL_REACHABILITY.tsv": "21a1e2d6fbe5f4e8dc1c18ea2b40a5b251d59661d4e32271ade13de065753421",
    "TRAVEL_BACKBONE_MANIFEST.json": "9da8da25ff76490ee0492f7a85279ade444981d5858ca1a1f043811e105fdec3",
}


def module_root():
    return Path(__file__).resolve().parents[2]


def destination_eligible(row, anchor_points):
    if row.get("connector_kind") != "DEST_TO_ANCHOR" or row.get("from_type") != "DEST" or row.get("to_type") != "ANCHOR" or row.get("instance_id") != "0":
        return False
    if row.get("validation_status") in ("VALID_DIRECT", "VALID_PATH"):
        return int(row.get("path_length", "0")) > 0 and int(row.get("path_segments", "0")) > 0
    if row.get("validation_status") != "VALID_IDENTITY" or row.get("reason") != "CANONICAL_IDENTITY":
        return False
    if any(row.get(field) != "0" for field in ("straight_distance", "path_length", "path_segments")):
        return False
    point = anchor_points.get(row.get("to_id"))
    return point is not None and point[3] == "0" and all(row.get("from_" + axis) == row.get("to_" + axis) == point[index] for index, axis in enumerate(("x", "y", "z")))


def canonical_castle_ids(value):
    if not value:
        return ""
    parts = value.split(";")
    if any(not part.isdecimal() or part.startswith("0") or int(part) <= 0 for part in parts) or [int(part) for part in parts] != sorted({int(part) for part in parts}):
        raise ValueError("Noncanonical destination castle IDs")
    return value


def join(transitions, connectors, facts, anchor_points=None):
    anchor_points = anchor_points or {}
    sources = defaultdict(list)
    destinations = defaultdict(list)
    for row in connectors:
        if row.get("connector_kind") == "ANCHOR_TO_GK" and row.get("from_type") == "ANCHOR" and row.get("to_type") == "GK" and row.get("instance_id") == "0" and row.get("validation_status") in ("VALID_DIRECT", "VALID_PATH") and int(row.get("path_length", "0")) > 0:
            sources[row["to_id"]].append(row)
        if destination_eligible(row, anchor_points):
            destinations[row["from_id"]].append(row)
    native = {"transition." + d1.key(row["fact_key"]): row for row in facts if row.get("availability_class") == "NORMAL" and row.get("teleport_type") == "NORMAL"}
    result = []
    for transition in transitions:
        if transition.get("transition_status") != "FACTUAL_NORMAL" or transition.get("teleport_type") != "NORMAL" or transition.get("fee_id") not in ("0", "57"):
            continue
        fact = native.get(transition["transition_id"])
        if fact is None or any(fact[field] != transition[value] for field, value in (("teleporter_spawn_key", "from_gk_fact_key"), ("teleporter_npc_id", "teleporter_npc_id"), ("fee_id", "fee_id"), ("fee_count", "fee_count"))):
            continue
        destination_id = "dest." + d1.key(fact["destination_x"], fact["destination_y"], fact["destination_z"], fact["teleporter_instance_id"])
        if destination_id != transition["to_destination_fact_key"] or fact["teleporter_instance_id"] != "0":
            continue
        for source in sources[transition["from_gk_fact_key"]]:
            if any(source["to_" + axis] != fact["teleporter_spawn_" + axis] for axis in ("x", "y", "z")):
                continue
            for destination in destinations[destination_id]:
                if any(destination["from_" + axis] != fact["destination_" + axis] for axis in ("x", "y", "z")):
                    continue
                identity = "|".join((source["connector_id"], transition["transition_id"], destination["connector_id"]))
                result.append({
                    "id": "leg." + hashlib.sha256(identity.encode("ascii")).hexdigest()[:24],
                    "fromAnchorId": source["from_id"], "toAnchorId": destination["to_id"],
                    "sourceConnectorId": source["connector_id"], "transitionId": transition["transition_id"],
                    "destinationConnectorId": destination["connector_id"],
                    "teleporterNpcId": fact["teleporter_npc_id"], "teleportListName": fact["list_name"],
                    "destinationIndex": fact["destination_index"],
                    "destinationX": fact["destination_x"], "destinationY": fact["destination_y"], "destinationZ": fact["destination_z"],
                    "sourceX": fact["teleporter_spawn_x"], "sourceY": fact["teleporter_spawn_y"], "sourceZ": fact["teleporter_spawn_z"],
                    "feeId": fact["fee_id"], "feeCount": fact["fee_count"],
                    "destinationCastleIds": canonical_castle_ids(fact["castle_ids"]),
                    "travelMillis": str(max(1000, math.ceil((int(source["path_length"]) + int(destination["path_length"])) / 100) * 1000 + 1000)),
                    "sourceRefs": "|".join(sorted(set((fact["source_path"], fact["spawn_source_path"], *source["source_refs"].split("|"), *destination["source_refs"].split("|"))))),
                })
    result.sort(key=lambda leg: leg["id"])
    if len(result) > 128 or len({leg["id"] for leg in result}) != len(result):
        raise RuntimeError("Invalid bounded NORMAL Gatekeeper catalog.")
    return result


def build(module, output):
    d1.verify_c(module)
    registry = module / "docs/phantoms/live-world"
    for name, expected in ACCEPTED_D1.items():
        if d1.sha_file(registry / name) != expected:
            raise RuntimeError("BLOCKED_INPUT_DRIFT: " + name)
    facts = d1.read_tsv(registry / "GATEKEEPER_FACTS.tsv")
    manifest = json.loads((registry / "TARGETED_TRAVEL_CONNECTORS_MANIFEST.json").read_text(encoding="utf-8"))
    if manifest.get("schema") != "LIVE-002-40-51/1" or manifest.get("base_d1_connectors_sha256") != ACCEPTED_D1["TRAVEL_CONNECTORS.tsv"]:
        raise RuntimeError("BLOCKED_CONNECTOR_PROVENANCE: manifest binding")
    for name, expected in manifest["output_sha256"].items():
        if d1.sha_file(registry / name) != expected:
            raise RuntimeError("BLOCKED_CONNECTOR_PROVENANCE: " + name)
    if d1.sha_file(module / "dist/game/data/phantoms/topology/high-five-generated-03.xml") != manifest["generated03_sha256"]:
        raise RuntimeError("BLOCKED_CONNECTOR_PROVENANCE: generated-03")
    if "generated04_sha256" in manifest and d1.sha_file(module / "dist/game/data/phantoms/topology/high-five-generated-04.xml") != manifest["generated04_sha256"]:
        raise RuntimeError("BLOCKED_CONNECTOR_PROVENANCE: generated-04")
    supplement = d1.read_tsv(registry / "TARGETED_TRAVEL_CONNECTORS.tsv")
    accepted = {"connector.73bea2de1609f40ad6b3230e", "connector.92ca2c480990a8b06fa07d82"}
    if len(supplement) != len({row["connector_id"] for row in supplement}) or not accepted.issubset({row["connector_id"] for row in supplement}):
        raise RuntimeError("BLOCKED_CONNECTOR_PROVENANCE: accepted targeted connectors missing or duplicate")
    connectors = d1.read_tsv(registry / "TRAVEL_CONNECTORS.tsv") + supplement
    transitions = d1.read_tsv(registry / "TRAVEL_TRANSITIONS.tsv")
    anchors = d1.topology(module)[0]
    extra_shards = ("high-five-generated-02.xml", "high-five-generated-03.xml") + (("high-five-generated-04.xml",) if "generated04_sha256" in manifest else ())
    for name in extra_shards:
        root = ET.parse(module / "dist/game/data/phantoms/topology" / name).getroot()
        for anchor in root.findall("anchor"):
            anchors[anchor.get("id")] = {"x": int(anchor.get("x")), "y": int(anchor.get("y")), "z": int(anchor.get("z")), "instance": int(anchor.get("instanceId"))}
    anchor_points = {key: (str(value["x"]), str(value["y"]), str(value["z"]), str(value["instance"])) for key, value in anchors.items()}
    legs = join(transitions, connectors, facts, anchor_points)
    if any(leg["fromAnchorId"] not in anchor_points or leg["toAnchorId"] not in anchor_points for leg in legs):
        raise RuntimeError("NORMAL Gatekeeper catalog references unknown topology anchor.")
    root = ET.Element("travel", {"schema": "LIVE-002-D2/1", "factsSha256": ACCEPTED_D1["GATEKEEPER_FACTS.tsv"], "connectorsSha256": ACCEPTED_D1["TRAVEL_CONNECTORS.tsv"], "targetedConnectorsSha256": manifest["output_sha256"]["TARGETED_TRAVEL_CONNECTORS.tsv"], "transitionsSha256": ACCEPTED_D1["TRAVEL_TRANSITIONS.tsv"]})
    for leg in legs:
        ET.SubElement(root, "leg", leg)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(ET.tostring(root, encoding="utf-8", xml_declaration=True) + b"\n")
    return legs


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=module_root() / "dist/game/data/phantoms/travel/high-five-normal-gk.xml")
    args = parser.parse_args()
    produced = build(module_root(), args.output)
    print(f"NORMAL_GATEKEEPER_LEGS={len(produced)} SHA256={d1.sha_file(args.output)}")
