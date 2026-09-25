"""Publish the accepted Ruins witness without another oversized path search."""

import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET

import normal_gk_catalog as gk
import travel_backbone as d1


PROOF_SHA = "a686fa4a17bb7a06e81cfcf83aa0bb133b1936e5f2658d6ed1e4a2a64deab253"
FARM_KEY = "b729b4b54a41170f2da2aa85307ff726b04e894296e26e9d3d85be8899dbd4f3"
FARM = "generated.farm." + FARM_KEY[:24]
GLUDIO = "generated.route.live002.gludio.arrival"
RUINS = "generated.route.live002.ruins.arrival"
GLUDIO_XYZ = (-12787, 122779, -3112)
BELLA_XYZ = (-12736, 122816, -3114)
SOURCE = "data/spawns/Others/18_22.xml"
EVIDENCE = "data/phantoms/evidence/live002-ruins-geodata-path.tsv"


def point(root, ident, xyz, role, source, npc_id=""):
    x, y, z = xyz
    attrs = {"id": ident, "kind": "FARMING_AREA" if role == "FARMING" else "ROUTE_AREA",
             "instanceId": "0", "tags": "outdoor-farming" if role == "FARMING" else "route"}
    if role == "FARMING":
        attrs.update({"form": "POINT_RADIUS", "x": str(x), "y": str(y), "z": str(z), "radius": "1"})
    else:
        attrs.update({"form": "CUBOID", "minX": str(x - 1), "maxX": str(x + 1),
                      "minY": str(y - 1), "maxY": str(y + 1), "minZ": str(z - 1), "maxZ": str(z + 1)})
    node = ET.SubElement(root, "node", attrs)
    ET.SubElement(node, "source", {"path": source})
    anchor_attrs = {"id": ident + ".anchor", "role": role, "nodeId": ident,
                    "x": str(x), "y": str(y), "z": str(z), "instanceId": "0",
                    "tolerance": "0", "tags": attrs["tags"]}
    if npc_id:
        anchor_attrs["npcId"] = npc_id
    anchor = ET.SubElement(root, "anchor", anchor_attrs)
    ET.SubElement(anchor, "source", {"path": source})


def connector(kind, from_type, from_id, to_type, to_id, source, target, status, length, segments, refs, reason):
    return {"connector_id": "connector." + d1.key(kind, from_id, to_id),
            "connector_kind": kind, "from_type": from_type, "from_id": from_id,
            "to_type": to_type, "to_id": to_id, "instance_id": "0",
            **{"from_" + axis: str(value) for axis, value in zip("xyz", source)},
            **{"to_" + axis: str(value) for axis, value in zip("xyz", target)},
            "map_region_from": "", "map_region_to": "",
            "straight_distance": str(math.ceil(math.dist(source, target))),
            "validation_status": status, "path_length": str(length),
            "path_segments": str(segments), "source_refs": refs, "reason": reason}


def publish(module):
    registry = module / "docs/phantoms/live-world"
    topology = module / "dist/game/data/phantoms/topology"
    proof_path = registry / "RUINS_GEODATA_PATH_PROOF.tsv"
    if d1.sha_file(proof_path) != PROOF_SHA or d1.sha_file(module / "dist/game" / EVIDENCE) != PROOF_SHA:
        raise RuntimeError("BLOCKED_RUINS_EVIDENCE_DRIFT")
    proof = d1.read_tsv(proof_path)
    if len(proof) != 6 or [int(row["hop"]) for row in proof] != list(range(1, 7)):
        raise RuntimeError("BLOCKED_RUINS_EVIDENCE_DRIFT: hop set")
    for index, row in enumerate(proof):
        if (row["status"] not in ("VALID_DIRECT", "VALID_PATH") or row["collision_proof"] != "STATIC_XML_CLEAR"
                or row["door_intersections"] != "0" or row["fence_intersections"] != "0"
                or int(row["required_buffer"]) > int(row["production_max"]) or int(row["production_max"]) != 500
                or int(row["path_length"]) <= 0 or int(row["path_segments"]) <= 0
                or (index and (row["from_id"], row["from_x"], row["from_y"], row["from_z"]) !=
                    (proof[index - 1]["to_id"], proof[index - 1]["to_x"], proof[index - 1]["to_y"], proof[index - 1]["to_z"]))):
            raise RuntimeError("BLOCKED_RUINS_EVIDENCE_DRIFT: hop " + row["hop"])
    if (proof[0]["from_id"] != RUINS or tuple(proof[0]["from_" + axis] for axis in "xyz") != tuple(map(str, (-19120, 136816, -3752)))
            or proof[-1]["to_id"] != FARM or tuple(int(proof[-1]["to_" + axis]) for axis in "xyz") != (-33539, 137701, -3480)):
        raise RuntimeError("BLOCKED_RUINS_EVIDENCE_DRIFT: endpoints")
    coverage = next(row for row in d1.read_tsv(registry / "WORLD_COVERAGE.tsv") if row["coverage_key"] == FARM_KEY)
    if any(coverage[key] != expected for key, expected in {"classification": "ORDINARY_WORLD", "status": "READY_STATIC", "instance_id": "0", "source_path": SOURCE, "geometry_kind": "POINT"}.items()) or "20059" not in coverage["npc_ids"].split("|"):
        raise RuntimeError("BLOCKED_RUINS_FARM_SOURCE_DRIFT")
    native = ET.parse(module / "dist/game" / SOURCE).getroot()
    if not any(n.get("id") == "20059" and (n.get("x"), n.get("y"), n.get("z")) == ("-33539", "137701", "-3479") for n in native.iter("npc")):
        raise RuntimeError("BLOCKED_RUINS_FARM_SOURCE_DRIFT: native NPC")
    facts = d1.read_tsv(registry / "GATEKEEPER_FACTS.tsv")
    bilia = next(row for row in facts if row["teleporter_npc_id"] == "31964" and row["destination_name"] == "The Town of Gludio" and tuple(map(int, (row["destination_x"], row["destination_y"], row["destination_z"]))) == GLUDIO_XYZ)
    bella = next(row for row in facts if row["teleporter_npc_id"] == "30256" and row["destination_name"] == "Ruins of Despair" and (row["destination_x"], row["destination_y"], row["destination_z"]) == (proof[0]["from_x"], proof[0]["from_y"], proof[0]["from_z"]))
    if (bilia["teleport_type"], bilia["fee_count"], bilia["castle_ids"], bella["teleport_type"], bella["fee_count"], bella["castle_ids"]) != ("NORMAL", "85000", "1", "NORMAL", "610", "") or tuple(map(int, (bella["teleporter_spawn_x"], bella["teleporter_spawn_y"], bella["teleporter_spawn_z"]))) != BELLA_XYZ:
        raise RuntimeError("BLOCKED_RUINS_NATIVE_TRAVEL_DRIFT")
    shard = ET.Element("topology", {"schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"})
    point(shard, GLUDIO, GLUDIO_XYZ, "ROUTE", bilia["source_path"])
    point(shard, RUINS, (-19120, 136816, -3752), "ROUTE", bella["source_path"])
    for row in proof[:-1]:
        point(shard, row["to_id"], tuple(int(row["to_" + axis]) for axis in "xyz"), "ROUTE", EVIDENCE)
    point(shard, FARM, (-33539, 137701, -3480), "FARMING", SOURCE, "20059")
    for row in proof:
        edge = ET.SubElement(shard, "edge", {"id": "generated.route.live002.ruins.geo.hop-" + row["hop"],
            "fromNodeId": row["from_id"], "toNodeId": row["to_id"], "mode": "BACKGROUND",
            "bidirectional": "false", "baseCost": "1", "baseTravelMillis": str(int(row["path_length"]) * 10),
            "backgroundEligible": "true", "channels": "COMBAT,TARGETABILITY",
            "fromAnchorId": row["from_id"] + ".anchor", "toAnchorId": row["to_id"] + ".anchor"})
        ET.SubElement(edge, "source", {"path": EVIDENCE})
    ET.indent(shard, space="\t")
    shard_path = topology / "high-five-generated-06.xml"
    shard_path.write_bytes(b'<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(shard, encoding="utf-8") + b"\n")
    destination = lambda fact: "dest." + d1.key(fact["destination_x"], fact["destination_y"], fact["destination_z"], "0")
    added = [
        connector("DEST_TO_ANCHOR", "DEST", destination(bilia), "ANCHOR", GLUDIO + ".anchor", GLUDIO_XYZ, GLUDIO_XYZ, "VALID_IDENTITY", 0, 0, bilia["source_path"], "CANONICAL_IDENTITY"),
        connector("ANCHOR_TO_GK", "ANCHOR", GLUDIO + ".anchor", "GK", bella["teleporter_spawn_key"], GLUDIO_XYZ, BELLA_XYZ, "VALID_DIRECT", 64, 1, "|".join(sorted((bilia["source_path"], bella["spawn_source_path"]))), "GEOENGINE_PROVEN"),
        connector("DEST_TO_ANCHOR", "DEST", destination(bella), "ANCHOR", RUINS + ".anchor", (-19120, 136816, -3752), (-19120, 136816, -3752), "VALID_IDENTITY", 0, 0, bella["source_path"], "CANONICAL_IDENTITY"),
    ]
    supplement_path = registry / "TARGETED_TRAVEL_CONNECTORS.tsv"
    existing = d1.read_tsv(supplement_path)
    if len(existing) not in (9, 12, 13) or len({row["connector_id"] for row in existing}) != len(existing):
        raise RuntimeError("BLOCKED_RUINS_TARGETED_SUPPLEMENT_DRIFT")
    added_ids = {row["connector_id"] for row in added}
    old = [row for row in existing if row["connector_id"] not in added_ids]
    preserved = [row for row in old if row["source_refs"] == "data/teleporters/town/30080.xml" and row["connector_kind"] == "DEST_TO_ANCHOR" and row["validation_status"] == "VALID_IDENTITY" and (row["from_x"], row["from_y"], row["from_z"]) == ("43408", "206881", "-3752")]
    if len(old) != 9 + len(preserved) or len(preserved) > 1:
        raise RuntimeError("BLOCKED_RUINS_TARGETED_SUPPLEMENT_DRIFT: old rows")
    d1.write_tsv(supplement_path, d1.CONNECTOR_COLUMNS, sorted(old + added, key=lambda row: row["connector_id"]))
    manifest_path = registry / "TARGETED_TRAVEL_CONNECTORS_MANIFEST.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["generated06_sha256"] = d1.sha_file(shard_path)
    manifest["output_sha256"]["TARGETED_TRAVEL_CONNECTORS.tsv"] = d1.sha_file(supplement_path)
    manifest["counts"]["targeted_connectors"] = 12 + len(preserved)
    manifest["counts"]["ruins_geo_hops"] = 6
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8", newline="\n")
    java_path = module / "java/org/l2jmobius/gameserver/phantoms/background/PhantomNormalGatekeeperTravel.java"
    java = java_path.read_text(encoding="utf-8")
    old_pin = "881c6be02318523aefaaf726c343004d0991273d6d8ee75f1162e35d62e97fad"
    new_pin = d1.sha_file(supplement_path)
    phase0_pin = "c436039da63f72a93fb6d8eafe7f1b76d2e7fe033302da76be5588d31fa330bd"
    if java.count(old_pin) + java.count(phase0_pin) + java.count(new_pin) != 1:
        raise RuntimeError("BLOCKED_RUINS_PRODUCTION_PIN_DRIFT")
    if old_pin in java or phase0_pin in java:
        java_path.write_text(java.replace(old_pin, new_pin).replace(phase0_pin, new_pin), encoding="utf-8", newline="")
    legs = gk.build(module, module / "dist/game/data/phantoms/travel/high-five-normal-gk.xml")
    return len(legs), new_pin, d1.sha_file(shard_path)


if __name__ == "__main__":
    print("RUINS_PUBLISHED", *publish(Path(__file__).resolve().parents[2]))
