"""Publish the five accepted hermetic movement rows as a bounded supplement."""

import argparse
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path

import travel_backbone as d1


KNOWN = (
    ("plunderous_06_to_05", "generated.route.live002.40-51.01", "generated.farm.7e05f4b455d51dddfaf626dc.anchor", "generated.farm.28e320ef344762d4fcb9c7bc.anchor", "VALID_DIRECT"),
    ("plunderous_02_to_route_a", "generated.route.live002.40-51.02", "generated.farm.e20a44e2413cc5ec51f0b15c.anchor", "generated.route.plunderous.40.route-a.anchor", "VALID_DIRECT"),
    ("plunderous_route_a_to_route_b", "generated.route.live002.40-51.03", "generated.route.plunderous.40.route-a.anchor", "generated.route.plunderous.40.route-b.anchor", "VALID_PATH"),
    ("plunderous_route_b_to_30", "generated.route.live002.40-51.04", "generated.route.plunderous.40.route-b.anchor", "generated.farm.4c20d26f8b57611bdafd0d33.anchor", "VALID_PATH"),
    ("schuttgart_route_to_bilia", "", "generated.route.schuttgart.40.arrival.anchor", "spawn.3f94b4ec9d3dbb2e1b66b6de", "VALID_DIRECT"),
)
COORDS = (
    (110516, -157305, -2056, 112606, -158386, -1728),
    (119549, -161828, -1296, 119922, -160430, -976),
    (119922, -160430, -976, 123156, -160164, -1192),
    (123156, -160164, -1192, 124885, -159590, -1288),
    (87126, -143520, -1288, 87048, -143448, -1293),
)


def generated03(path, proof):
    root = ET.Element("topology", {"schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"})
    points = (
        ("generated.route.plunderous.40.route-a", 119922, -160430, -976, "data/spawns/Others/PlunderousPlains.xml"),
        ("generated.route.plunderous.40.route-b", 123156, -160164, -1192, "data/spawns/Others/PlunderousPlains.xml"),
        ("generated.route.schuttgart.40.arrival", 87126, -143520, -1288, "data/teleporters/town/30540.xml"),
    )
    for key, x, y, z, source in points:
        node = ET.SubElement(root, "node", {"id": key, "kind": "ROUTE_AREA", "instanceId": "0", "form": "CUBOID", "minX": str(x - 1), "maxX": str(x + 1), "minY": str(y - 1), "maxY": str(y + 1), "minZ": str(z - 1), "maxZ": str(z + 1), "tags": "route"})
        ET.SubElement(node, "source", {"path": source})
        anchor = ET.SubElement(root, "anchor", {"id": key + ".anchor", "role": "ROUTE", "nodeId": key, "x": str(x), "y": str(y), "z": str(z), "instanceId": "0", "tolerance": "0", "tags": "route"})
        ET.SubElement(anchor, "source", {"path": source})
    for row, known in zip(proof[:4], KNOWN[:4]):
        edge = ET.SubElement(root, "edge", {"id": known[1], "fromNodeId": known[2].removesuffix(".anchor"), "toNodeId": known[3].removesuffix(".anchor"), "mode": "BACKGROUND", "bidirectional": "false", "baseCost": "1", "baseTravelMillis": str(int(row["path_length"]) * 10), "backgroundEligible": "true", "channels": "COMBAT,TARGETABILITY", "fromAnchorId": known[2], "toAnchorId": known[3]})
        ET.SubElement(edge, "source", {"path": "data/spawns/Others/PlunderousPlains.xml"})
    ET.indent(root, space="\t")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(root, encoding="utf-8") + b"\n")


def publish(module, proof_path, output, topology_output=None):
    proof = d1.read_tsv(proof_path)
    if len(proof) != len(KNOWN) or [row["connector_id"] for row in proof] != [known[0] for known in KNOWN]:
        raise RuntimeError("BLOCKED_HERMETIC_GEO_PROOF_DRIFT: exact direction set changed")
    for row, known in zip(proof, KNOWN):
        if row["validation_status"] != known[4] or row["collision_proof"] != "STATIC_XML_CLEAR" or row["door_intersections"] != "0" or row["fence_intersections"] != "0" or int(row["path_length"]) <= 0 or int(row["path_segments"]) <= 0:
            raise RuntimeError("BLOCKED_HERMETIC_GEO_PROOF_DRIFT: " + known[0])
    coordinate_columns = ("from_x", "from_y", "from_z", "to_x", "to_y", "to_z")
    for row, coordinates in zip(proof, COORDS):
        if any(column in row and row[column] != str(value) for column, value in zip(coordinate_columns, coordinates)):
            raise RuntimeError("BLOCKED_HERMETIC_GEO_PROOF_DRIFT: endpoint coordinates")
    proof = [dict(row, **{key: str(value) for key, value in zip(coordinate_columns, coordinates)}) for row, coordinates in zip(proof, COORDS)]
    topology = topology_output or module / "dist/game/data/phantoms/topology/high-five-generated-03.xml"
    generated03(topology, proof)
    output.mkdir(parents=True, exist_ok=True)
    proof_columns = ("connector_id", *coordinate_columns, *[column for column in proof[0] if column != "connector_id" and column not in coordinate_columns])
    d1.write_tsv(output / "LIVE002_40_51_HERMETIC_GEO_PROOF.tsv", proof_columns, proof)
    route = [dict(row, edge_id=known[1], from_anchor_id=known[2], to_anchor_id=known[3]) for row, known in zip(proof[:4], KNOWN[:4])]
    d1.write_tsv(output / "PLUNDEROUS_40_FINAL_ROUTE.tsv", ("edge_id", "from_anchor_id", "to_anchor_id", *proof_columns), route)
    schutt = proof[4]
    if schutt["path_length"] != "107" or schutt["path_segments"] != "1":
        raise RuntimeError("BLOCKED_HERMETIC_GEO_PROOF_DRIFT: Schuttgart local proof")
    route_id = "generated.route.schuttgart.40.arrival.anchor"
    destination_id = "dest.4ef0e443abec483110ff147f"
    gk_id = "spawn.3f94b4ec9d3dbb2e1b66b6de"
    rows = [
        {"connector_id": "connector." + d1.key("DEST_TO_ANCHOR", destination_id, route_id), "connector_kind": "DEST_TO_ANCHOR", "from_type": "DEST", "from_id": destination_id, "to_type": "ANCHOR", "to_id": route_id, "instance_id": "0", "from_x": "87126", "from_y": "-143520", "from_z": "-1288", "to_x": "87126", "to_y": "-143520", "to_z": "-1288", "straight_distance": "0", "validation_status": "VALID_IDENTITY", "path_length": "0", "path_segments": "0", "source_refs": "data/teleporters/town/30540.xml", "reason": "CANONICAL_IDENTITY"},
        {"connector_id": "connector." + d1.key("ANCHOR_TO_GK", route_id, gk_id), "connector_kind": "ANCHOR_TO_GK", "from_type": "ANCHOR", "from_id": route_id, "to_type": "GK", "to_id": gk_id, "instance_id": "0", "from_x": "87126", "from_y": "-143520", "from_z": "-1288", "to_x": "87048", "to_y": "-143448", "to_z": "-1293", "straight_distance": "107", "validation_status": schutt["validation_status"], "path_length": schutt["path_length"], "path_segments": schutt["path_segments"], "source_refs": "data/teleporters/town/30540.xml|data/spawns/Others/22_13.xml", "reason": "GEOENGINE_PROVEN"},
    ]
    rows.sort(key=lambda row: row["connector_id"])
    d1.write_tsv(output / "TARGETED_TRAVEL_CONNECTORS.tsv", d1.CONNECTOR_COLUMNS, rows)
    manifest = {
        "schema": "LIVE-002-40-51/1",
        "base_d1_connectors_sha256": "fe0c0433e8975ff43f397470eca5caf0ae3b545e4d66d0ece2096d1ed97c1926",
        "output_sha256": {name: d1.sha_file(output / name) for name in ("LIVE002_40_51_HERMETIC_GEO_PROOF.tsv", "PLUNDEROUS_40_FINAL_ROUTE.tsv", "TARGETED_TRAVEL_CONNECTORS.tsv")},
        "generated03_sha256": d1.sha_file(topology),
        "counts": {"movement_directions": 5, "targeted_connectors": 2, "plunderous_edges": 4},
    }
    (output / "TARGETED_TRAVEL_CONNECTORS_MANIFEST.json").write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8", newline="\n")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--proof", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[2] / "docs/phantoms/live-world")
    parser.add_argument("--topology-output", type=Path)
    args = parser.parse_args()
    result = publish(Path(__file__).resolve().parents[2], args.proof, args.output, args.topology_output)
    print("TARGETED_CONNECTORS=2 SHA256=" + result["output_sha256"]["TARGETED_TRAVEL_CONNECTORS.tsv"])
