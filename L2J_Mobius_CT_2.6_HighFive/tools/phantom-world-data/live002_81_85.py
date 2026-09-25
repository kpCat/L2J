"""Publish one factual Sel Mahum point farm and its bounded Oren travel proof."""

import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

import normal_gk_catalog as gk
import travel_backbone as d1


KEY = "7617d478280b73549ec67bfd3c697cf422243d3545197efd42908486a281fb70"
NODE = "generated.farm." + KEY[:24]
ANCHOR = NODE + ".anchor"
OREN = "generated.route.live002.oren.arrival"
SOURCE = "data/spawns/Oren/SelMahums.xml"
POINT = (84902, 60870, -3440)
LOCAL = (84904, 60980, -3440)
ARRIVAL = (82971, 53207, -1488)
GK = (82992, 53171, -1492)
DESTINATION = (87448, 61460, -3664)
SHARDS = ("high-five-core.xml", "high-five-siege.xml", *(f"high-five-generated-{i:02}.xml" for i in range(1, 6)))
POINT_COLUMNS = ("coverage_key", "source_path", "source_group", "npc_id", "npc_level", "source_x", "source_y", "source_z", "geo_has_geo", "geo_height_first", "geo_height_second", "normalized_x", "normalized_y", "normalized_z", "node_id", "anchor_id", "radius", "local_witness_npc_id", "local_witness_x", "local_witness_y", "local_witness_z", "local_validation", "local_path_length", "local_path_segments", "transition_id", "destination_id", "destination_x", "destination_y", "destination_z", "destination_connector_id", "destination_validation", "destination_path_length", "destination_path_segments", "mapping_result", "collision_proof")


def positive(row):
    if row["validation_status"] not in ("VALID_DIRECT", "VALID_PATH") or row["collision_proof"] != "STATIC_XML_CLEAR" or row["door_intersections"] != "0" or row["fence_intersections"] != "0" or int(row["path_length"]) <= 0 or int(row["path_segments"]) <= 0:
        raise RuntimeError("Hermetic direction not positive: " + row["connector_id"])


def native(module):
    registry = module / "docs/phantoms/live-world"
    coverage = next(row for row in d1.read_tsv(registry / "WORLD_COVERAGE.tsv") if row["coverage_key"] == KEY)
    if any(coverage[field] != value for field, value in {"classification": "ORDINARY_WORLD", "status": "READY_STATIC", "instance_id": "0", "source_path": SOURCE, "source_group": "smtg_drill_group_09", "npc_level_min": "83", "npc_level_max": "84", "geometry_kind": "POINT"}.items()):
        raise RuntimeError("Sel Mahum coverage drift")
    root = ET.parse(module / "dist/game" / SOURCE).getroot()
    group = next(s for s in root.findall("spawn") if s.get("name") == "smtg_drill_group_09")
    facts = {(int(n.get("id")), *(int(n.get(axis)) for axis in ("x", "y", "z"))) for n in group.findall("npc")}
    if (22782, *POINT) not in facts or (22782, *LOCAL) not in facts:
        raise RuntimeError("Exact native Sel Mahum point drift")
    npc = next(n for n in ET.parse(module / "dist/game/data/stats/npcs/22700-22799.xml").getroot().findall("npc") if n.get("id") == "22782")
    if npc.get("level") != "83" or npc.get("type") != "Monster":
        raise RuntimeError("Native Monster/level drift")
    facts = d1.read_tsv(registry / "GATEKEEPER_FACTS.tsv")
    oren = next(row for row in facts if row["teleporter_npc_id"] == "31964" and row["destination_name"] == "Town of Oren" and row["destination_x"] == str(ARRIVAL[0]) and row["destination_y"] == str(ARRIVAL[1]) and row["destination_z"] == str(ARRIVAL[2]))
    center = next(row for row in facts if row["teleporter_npc_id"] == "30177" and row["destination_name"] == "Sel Mahum Training Grounds (Center)")
    if oren["fee_count"] != "59000" or oren["castle_ids"] != "4" or center["fee_count"] != "1800" or tuple(int(center["teleporter_spawn_" + axis]) for axis in ("x", "y", "z")) != GK or tuple(int(center["destination_" + axis]) for axis in ("x", "y", "z")) != DESTINATION:
        raise RuntimeError("Native Oren travel drift")
    transitions = {row["transition_id"]: row for row in d1.read_tsv(registry / "TRAVEL_TRANSITIONS.tsv")}
    for transition, source, dest in (("transition.d0ea8a9408f232160c3b78fb", oren, "dest.27eb3b47617b2db73b423713"), ("transition.107143092d3473f5ec928e09", center, "dest.2413c81a656c8275e4550c56")):
        row = transitions[transition]
        if row["from_gk_fact_key"] != source["teleporter_spawn_key"] or row["to_destination_fact_key"] != dest or row["transition_status"] != "FACTUAL_NORMAL":
            raise RuntimeError("Factual transition drift: " + transition)
    return oren, center


def query(module):
    folder = module / "dist/game/data/phantoms/topology"
    with tempfile.TemporaryDirectory(prefix="live002-point-nodes-") as name:
        temporary = Path(name)
        for shard in SHARDS:
            root = ET.parse(folder / shard).getroot()
            reduced = ET.Element("topology", root.attrib)
            for node in root.findall("node"):
                reduced.append(node)
            ET.ElementTree(reduced).write(temporary / shard, encoding="utf-8", xml_declaration=True)
        cp = ";".join((str(module.parent / "build/bin"), str(module.parent / "build/phantom-test/bin"), str(module / "dist/libs/*")))
        result = subprocess.run(["java", "-cp", cp, "org.l2jmobius.tests.phantoms.PhantomPointFarmProof", *(str(value) for value in POINT), "22782", NODE, str(temporary)], cwd=module / "dist/game", text=True, capture_output=True)
        if result.returncode or "POINT_MAPPING node=" + NODE not in result.stdout:
            raise RuntimeError("POINT_FARM_NODE_AMBIGUOUS: " + result.stdout + result.stderr)
        return result.stdout.strip().splitlines()[-1]


def publish(module, first_geo, selected_geo):
    registry = module / "docs/phantoms/live-world"
    folder = module / "dist/game/data/phantoms/topology"
    oren, center = native(module)
    first = {row["connector_id"]: row for row in d1.read_tsv(first_geo)}
    selected = {row["connector_id"]: row for row in d1.read_tsv(selected_geo)}
    if set(first) != {"local.group09", "hub.oren", "dest.center.group09"} or set(selected) != {"local.group09.22782", "dest.center.group09.22782"}:
        raise RuntimeError("Bounded hermetic direction set drift")
    for name in ("hub.oren",):
        positive(first[name])
    for name in selected:
        positive(selected[name])
    if first["local.group09"]["validation_status"] not in ("VALID_DIRECT", "VALID_PATH") or first["dest.center.group09"]["validation_status"] not in ("VALID_DIRECT", "VALID_PATH"):
        raise RuntimeError("Initial candidate direction drift")
    shard = ET.Element("topology", {"schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"})
    route = ET.SubElement(shard, "node", {"id": OREN, "kind": "ROUTE_AREA", "instanceId": "0", "form": "CUBOID", **{prefix + axis.upper(): str(value + delta) for axis, value in zip(("x", "y", "z"), ARRIVAL) for prefix, delta in (("min", -1), ("max", 1))}, "tags": "route"})
    ET.SubElement(route, "source", {"path": oren["source_path"]})
    route_anchor = ET.SubElement(shard, "anchor", {"id": OREN + ".anchor", "role": "ROUTE", "nodeId": OREN, **dict(zip(("x", "y", "z"), map(str, ARRIVAL))), "instanceId": "0", "tolerance": "0", "tags": "route"})
    ET.SubElement(route_anchor, "source", {"path": oren["source_path"]})
    farm = ET.SubElement(shard, "node", {"id": NODE, "kind": "FARMING_AREA", "instanceId": "0", "form": "POINT_RADIUS", **dict(zip(("x", "y", "z"), map(str, POINT))), "radius": "1", "tags": "outdoor-farming"})
    ET.SubElement(farm, "source", {"path": SOURCE})
    farm_anchor = ET.SubElement(shard, "anchor", {"id": ANCHOR, "role": "FARMING", "nodeId": NODE, **dict(zip(("x", "y", "z"), map(str, POINT))), "instanceId": "0", "tolerance": "0", "npcId": "22782", "tags": "outdoor-farming"})
    ET.SubElement(farm_anchor, "source", {"path": SOURCE})
    ET.indent(shard, space="\t")
    shard_path = folder / "high-five-generated-05.xml"
    shard_path.write_bytes(b'<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(shard, encoding="utf-8") + b"\n")
    mapping = query(module)
    existing = d1.read_tsv(registry / "TARGETED_TRAVEL_CONNECTORS.tsv")
    if len(existing) not in (6, 9) or len({row["connector_id"] for row in existing}) != len(existing):
        raise RuntimeError("Accepted targeted connector drift")
    def connector(kind, source_type, source_id, target_type, target_id, source_xyz, target_xyz, proof, refs, identity=False):
        return {"connector_id": "connector." + d1.key(kind, source_id, target_id), "connector_kind": kind, "from_type": source_type, "from_id": source_id, "to_type": target_type, "to_id": target_id, "instance_id": "0", **{"from_" + axis: str(value) for axis, value in zip(("x", "y", "z"), source_xyz)}, **{"to_" + axis: str(value) for axis, value in zip(("x", "y", "z"), target_xyz)}, "map_region_from": "", "map_region_to": "", "straight_distance": "0" if identity else str(math.ceil(math.dist(source_xyz, target_xyz))), "validation_status": "VALID_IDENTITY" if identity else proof["validation_status"], "path_length": "0" if identity else proof["path_length"], "path_segments": "0" if identity else proof["path_segments"], "source_refs": refs, "reason": "CANONICAL_IDENTITY" if identity else "GEOENGINE_PROVEN"}
    destination_id = "dest.2413c81a656c8275e4550c56"
    added = [connector("DEST_TO_ANCHOR", "DEST", "dest.27eb3b47617b2db73b423713", "ANCHOR", OREN + ".anchor", ARRIVAL, ARRIVAL, None, oren["source_path"], True), connector("ANCHOR_TO_GK", "ANCHOR", OREN + ".anchor", "GK", center["teleporter_spawn_key"], ARRIVAL, GK, first["hub.oren"], "|".join(sorted((oren["source_path"], center["spawn_source_path"])))), connector("DEST_TO_ANCHOR", "DEST", destination_id, "ANCHOR", ANCHOR, DESTINATION, POINT, selected["dest.center.group09.22782"], "|".join(sorted((SOURCE, center["source_path"]))))]
    added_ids = {row["connector_id"] for row in added}
    old = [row for row in existing if row["connector_id"] not in added_ids]
    if len(old) != 6 or (len(existing) == 9 and {row["connector_id"] for row in existing if row["connector_id"] in added_ids} != added_ids):
        raise RuntimeError("Accepted targeted connector overwrite")
    d1.write_tsv(registry / "TARGETED_TRAVEL_CONNECTORS.tsv", d1.CONNECTOR_COLUMNS, sorted(old + added, key=lambda row: row["connector_id"]))
    point_row = {"coverage_key": KEY, "source_path": SOURCE, "source_group": "smtg_drill_group_09", "npc_id": "22782", "npc_level": "83", **{"source_" + axis: str(value) for axis, value in zip(("x", "y", "z"), POINT)}, "geo_has_geo": "true", "geo_height_first": "-3440", "geo_height_second": "-3440", **{"normalized_" + axis: str(value) for axis, value in zip(("x", "y", "z"), POINT)}, "node_id": NODE, "anchor_id": ANCHOR, "radius": "1", "local_witness_npc_id": "22782", **{"local_witness_" + axis: str(value) for axis, value in zip(("x", "y", "z"), LOCAL)}, "local_validation": selected["local.group09.22782"]["validation_status"], "local_path_length": selected["local.group09.22782"]["path_length"], "local_path_segments": selected["local.group09.22782"]["path_segments"], "transition_id": "transition.107143092d3473f5ec928e09", "destination_id": destination_id, **{"destination_" + axis: str(value) for axis, value in zip(("x", "y", "z"), DESTINATION)}, "destination_connector_id": added[2]["connector_id"], "destination_validation": selected["dest.center.group09.22782"]["validation_status"], "destination_path_length": selected["dest.center.group09.22782"]["path_length"], "destination_path_segments": selected["dest.center.group09.22782"]["path_segments"], "mapping_result": mapping, "collision_proof": "STATIC_XML_CLEAR"}
    proof_path = registry / "POINT_FARM_81_85_PROOF.tsv"
    d1.write_tsv(proof_path, POINT_COLUMNS, [point_row])
    manifest_path = registry / "TARGETED_TRAVEL_CONNECTORS_MANIFEST.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["generated05_sha256"] = d1.sha_file(shard_path)
    manifest["output_sha256"]["POINT_FARM_81_85_PROOF.tsv"] = d1.sha_file(proof_path)
    manifest["output_sha256"]["TARGETED_TRAVEL_CONNECTORS.tsv"] = d1.sha_file(registry / "TARGETED_TRAVEL_CONNECTORS.tsv")
    manifest["counts"]["point_farm_new_movement_directions"] = 5
    manifest["counts"]["targeted_connectors"] = 9
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8", newline="\n")
    legs = gk.build(module, module / "dist/game/data/phantoms/travel/high-five-normal-gk.xml")
    return len(legs), d1.sha_file(shard_path), d1.sha_file(registry / "TARGETED_TRAVEL_CONNECTORS.tsv")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--first-geo", type=Path, required=True)
    parser.add_argument("--selected-geo", type=Path, required=True)
    args = parser.parse_args()
    print("PUBLISHED", publish(Path(__file__).resolve().parents[2], args.first_geo, args.selected_geo))
