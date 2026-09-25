"""Independent DB-free active route and exact native point farm validator."""

import argparse
import csv
import hashlib
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

import live002_high_bands_final as previous


BANDS = ((40, 51), (52, 60), (61, 75), (76, 80), (81, 85))
SHARDS = ("high-five-core.xml", "high-five-siege.xml", *(f"high-five-generated-{i:02}.xml" for i in range(1, 6)))
COLUMNS = ("band", "status", "start_ingress", "ordered_step", "edge_type", "edge_id", "from_anchor_id", "to_anchor_id", "transition_id", "teleporter_npc_id", "native_destination", "coverage_key", "npc_id", "npc_level", "source_path", "source_group", "source_x", "source_y", "source_z", "mapping_result", "collision_proof")
KEY = "7617d478280b73549ec67bfd3c697cf422243d3545197efd42908486a281fb70"
NODE = "generated.farm." + KEY[:24]
ANCHOR = NODE + ".anchor"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def graph(module):
    folder = module / "dist/game/data/phantoms/topology"
    anchors, edges = {}, []
    for name in SHARDS:
        root = ET.parse(folder / name).getroot()
        if root.attrib != {"schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"}:
            raise RuntimeError("Active topology version drift")
        for anchor in root.findall("anchor"):
            if anchor.get("id") in anchors:
                raise RuntimeError("Duplicate active anchor")
            anchors[anchor.get("id")] = anchor
        for edge in root.findall("edge"):
            if edge.get("mode") == "BACKGROUND" and edge.get("backgroundEligible") == "true":
                link = (edge.get("fromAnchorId"), edge.get("toAnchorId"), "BACKGROUND", edge.get("id"))
                edges.append(link)
                if edge.get("bidirectional") == "true":
                    edges.append((link[1], link[0], "BACKGROUND", link[3] + ":reverse"))
    selected = ET.parse(folder / SHARDS[-1]).getroot()
    if len(selected.findall("node")) != 2 or len(selected.findall("anchor")) != 2 or selected.findall("edge"):
        raise RuntimeError("Generated-05 scope drift")
    node = next((n for n in selected.findall("node") if n.get("id") == NODE), None)
    anchor = anchors.get(ANCHOR)
    if node is None or anchor is None or any(node.get(field) != value for field, value in {"kind": "FARMING_AREA", "form": "POINT_RADIUS", "radius": "1", "instanceId": "0"}.items()) or any(anchor.get(field) != value for field, value in {"role": "FARMING", "nodeId": NODE, "instanceId": "0", "tolerance": "0", "npcId": "22782"}.items()):
        raise RuntimeError("Point farm topology contract drift")
    if any(node.get(axis) != anchor.get(axis) for axis in ("x", "y", "z")):
        raise RuntimeError("Point farm anchor drift")
    if digest(folder / SHARDS[-1]) != __import__("json").loads((module / "docs/phantoms/live-world/TARGETED_TRAVEL_CONNECTORS_MANIFEST.json").read_text(encoding="utf-8"))["generated05_sha256"]:
        raise RuntimeError("Generated-05 provenance drift")
    return anchors, edges, node


def native_point(module, node, anchor):
    registry = module / "docs/phantoms/live-world"
    coverage = next(row for row in previous.read_tsv(registry / "WORLD_COVERAGE.tsv") if row["coverage_key"] == KEY)
    if any(coverage[field] != value for field, value in {"classification": "ORDINARY_WORLD", "status": "READY_STATIC", "instance_id": "0", "source_path": "data/spawns/Oren/SelMahums.xml", "source_group": "smtg_drill_group_09", "geometry_kind": "POINT", "npc_level_min": "83", "npc_level_max": "84"}.items()):
        raise RuntimeError("Native coverage drift")
    group = next(s for s in ET.parse(module / "dist/game/data/spawns/Oren/SelMahums.xml").getroot().findall("spawn") if s.get("name") == "smtg_drill_group_09")
    xyz = tuple(node.get(axis) for axis in ("x", "y", "z"))
    if xyz != ("84902", "60870", "-3440") or not any(n.get("id") == "22782" and tuple(n.get(axis) for axis in ("x", "y", "z")) == xyz for n in group.findall("npc")) or tuple(anchor.get(axis) for axis in ("x", "y", "z")) != xyz:
        raise RuntimeError("Exact native spawn drift")
    npc = next(n for n in ET.parse(module / "dist/game/data/stats/npcs/22700-22799.xml").getroot().findall("npc") if n.get("id") == "22782")
    if npc.get("type") != "Monster" or npc.get("level") != "83":
        raise RuntimeError("Native Monster level drift")
    proof = previous.read_tsv(registry / "POINT_FARM_81_85_PROOF.tsv")
    if len(proof) != 1:
        raise RuntimeError("Point proof count drift")
    proof = proof[0]
    if proof["coverage_key"] != KEY or proof["anchor_id"] != ANCHOR or proof["source_z"] != proof["geo_height_first"] or proof["source_z"] != proof["geo_height_second"] or proof["geo_has_geo"] != "true" or proof["local_validation"] not in ("VALID_DIRECT", "VALID_PATH") or proof["destination_validation"] not in ("VALID_DIRECT", "VALID_PATH") or proof["collision_proof"] != "STATIC_XML_CLEAR" or int(proof["local_path_length"]) <= 0 or int(proof["destination_path_length"]) <= 0:
        raise RuntimeError("Point Geo proof drift")
    with tempfile.TemporaryDirectory(prefix="live002-final-nodes-") as name:
        for shard in SHARDS:
            root = ET.parse(module / "dist/game/data/phantoms/topology" / shard).getroot()
            reduced = ET.Element("topology", root.attrib)
            for item in root.findall("node"):
                reduced.append(item)
            ET.ElementTree(reduced).write(Path(name) / shard, encoding="utf-8", xml_declaration=True)
        cp = ";".join((str(module.parent / "build/bin"), str(module.parent / "build/phantom-test/bin"), str(module / "dist/libs/*")))
        result = subprocess.run(["java", "-cp", cp, "org.l2jmobius.tests.phantoms.PhantomPointFarmProof", *xyz, "22782", NODE, name], cwd=module / "dist/game", text=True, capture_output=True)
        if result.returncode or "POINT_MAPPING node=" + NODE not in result.stdout or proof["mapping_result"] != result.stdout.strip().splitlines()[-1]:
            raise RuntimeError("POINT_FARM_NODE_AMBIGUOUS: " + result.stdout + result.stderr)
    return coverage, proof


def validate(module, output):
    anchors, edges, node = graph(module)
    coverage, point = native_point(module, node, anchors[ANCHOR])
    legs, connectors = previous.validate_catalog(module, anchors)
    if len(connectors) != len(set(connectors)) or len(previous.read_tsv(module / "docs/phantoms/live-world/TARGETED_TRAVEL_CONNECTORS.tsv")) != 9:
        raise RuntimeError("Targeted connector count drift")
    edges.extend((leg["fromAnchorId"], leg["toAnchorId"], "NORMAL_GATEKEEPER", leg["id"]) for leg in legs.values())
    starts = {aid for aid in anchors if aid.startswith("population.ingress.dwarf.")}
    if len(starts) != 6:
        raise RuntimeError("Dwarf ingress drift")
    reached, origins = previous.paths(starts, edges)
    facts = previous.read_tsv(module / "docs/phantoms/live-world/WORLD_COVERAGE.tsv")
    counts = {}
    for low, high in BANDS:
        eligible = {"generated.farm." + row["coverage_key"][:24] + ".anchor" for row in facts if row["classification"] == "ORDINARY_WORLD" and row["status"] == "READY_STATIC" and row["instance_id"] == "0" and int(row["npc_level_max"]) >= low and int(row["npc_level_min"]) <= high and not any(word in row["source_path"].lower().replace("_", "") for word in previous.EXCLUDED)}
        counts[f"{low}-{high}"] = len({aid for aid in eligible if aid in reached and aid in anchors and anchors[aid].get("role") == "FARMING"})
    floors = {"40-51": 1, "52-60": 10, "61-75": 15, "76-80": 3, "81-85": 1}
    if any(counts[band] < minimum for band, minimum in floors.items()) or ANCHOR not in reached:
        raise RuntimeError("GLOBAL ordinary band regression: " + str(counts))
    route = previous.reconstruct(ANCHOR, reached)
    selected_legs = [legs[step[3]] for step in route if step[2] == "NORMAL_GATEKEEPER"]
    selected_transitions = [leg["transitionId"] for leg in selected_legs]
    if selected_transitions[-2:] != ["transition.d0ea8a9408f232160c3b78fb", "transition.107143092d3473f5ec928e09"] or selected_legs[-1]["toAnchorId"] != ANCHOR or selected_legs[-1]["destinationConnectorId"] != point["destination_connector_id"]:
        raise RuntimeError("Ordered factual Oren route missing")
    rows = []
    for index, step in enumerate(route, 1):
        leg = legs.get(step[3])
        rows.append({"band": "81-85", "status": "GREEN", "start_ingress": origins[ANCHOR], "ordered_step": str(index), "edge_type": step[2], "edge_id": step[3], "from_anchor_id": step[0], "to_anchor_id": step[1], "transition_id": leg["transitionId"] if leg else "", "teleporter_npc_id": leg["teleporterNpcId"] if leg else "", "native_destination": "Town of Oren" if leg and leg["transitionId"] == selected_transitions[-2] else "Sel Mahum Training Grounds (Center)" if leg and leg["transitionId"] == selected_transitions[-1] else "", "coverage_key": KEY if index == len(route) else "", "npc_id": "22782" if index == len(route) else "", "npc_level": "83" if index == len(route) else "", "source_path": coverage["source_path"] if index == len(route) else "", "source_group": coverage["source_group"] if index == len(route) else "", **{"source_" + axis: point["source_" + axis] if index == len(route) else "" for axis in ("x", "y", "z")}, "mapping_result": point["mapping_result"] if index == len(route) else "", "collision_proof": point["collision_proof"] if index == len(route) else "CATALOG_PROVEN" if leg else "NOT_APPLICABLE"})
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        writer = csv.DictWriter(stream, fieldnames=COLUMNS, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    return counts, len(route)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[2] / "docs/phantoms/live-world/LIVE002_81_85_FINAL.tsv")
    args = parser.parse_args()
    print("GLOBAL", *validate(Path(__file__).resolve().parents[2], args.output), "SHA256", digest(args.output))
