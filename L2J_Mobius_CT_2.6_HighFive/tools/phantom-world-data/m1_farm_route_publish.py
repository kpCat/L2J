"""Promote a native-proven ordinary farm corridor into the High Five topology.

The input TSVs come from PhantomTravelGeoProbe. Promotion is explicit and
rejects missing, changed, or unproven directions.
"""

import argparse
import csv
import hashlib
import json
import xml.etree.ElementTree as ET
from collections import defaultdict, deque
from pathlib import Path

from travel_backbone import sha_source


def digest(*parts):
    return hashlib.sha256("|".join(parts).encode("utf-8")).hexdigest()[:24]


def read_tsv(path):
    with path.open(encoding="utf-8", newline="") as source:
        return list(csv.DictReader(source, delimiter="\t"))


def write_tsv(path, columns, rows):
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=columns, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def sha(path):
    return sha_source(path)


def native_anchors(module):
    result = {}
    for path in sorted((module / "dist/game/data/phantoms/topology").glob("*.xml")):
        for anchor in ET.parse(path).getroot().findall("anchor"):
            result[anchor.get("id")] = anchor
    return result


def native_fact(module, fact_key):
    facts = read_tsv(module / "docs/phantoms/live-world/GATEKEEPER_FACTS.tsv")
    matching = [fact for fact in facts if fact["fact_key"] == fact_key]
    if len(matching) != 1 or matching[0]["teleport_type"] != "NORMAL" or matching[0]["availability_class"] != "NORMAL":
        raise ValueError("Source fact is not one available native NORMAL teleport")
    return matching[0]


def native_waypoint(module, point, heights, endpoints):
    x, y, z = (int(point[axis]) for axis in "xyz")
    if point["id"].startswith("grid."):
        parts = point["id"].split(".")
        if len(parts) not in (4, 6) or int(parts[1]) not in (512, 1024, 2048) or int(parts[2]) != x or int(parts[3]) != y:
            return False
        step = int(parts[1])
        sx, sy = (int(endpoints["source"][axis]) for axis in "xy")
        tx, ty = (int(endpoints["target"][axis]) for axis in "xy")
        if len(parts) == 6:
            sz, tz = int(endpoints["source"]["z"]), int(endpoints["target"]["z"])
            if parts[4] != "seed" or int(parts[5]) not in (sz, tz, round((sz + tz) / 2)):
                return False
        proof = heights.get(point["id"])
        return ((x - sx) % step == 0 and (y - sy) % step == 0
                and min(sx, tx) - 5000 <= x <= max(sx, tx) + 5000
                and min(sy, ty) - 5000 <= y <= max(sy, ty) + 5000
                and proof is not None and proof["stable"] == "true"
                and all(proof[axis] == point[axis] for axis in "xyz"))
    source = module / "dist/game" / point["source_path"]
    for group in ET.parse(source).getroot().findall("spawn"):
        if any(all(npc.get(axis) == point[axis] for axis in "xyz") for npc in group.findall("npc")):
            return True
        territory = group.find("territory")
        if territory is not None:
            proof = heights.get(point["id"])
            if proof is not None and proof["stable"] == "true" and all(proof[axis] == point[axis] for axis in "xyz") and any(node.get("x") == str(x) and node.get("y") == str(y) for node in territory.findall("node")):
                return True
    return False


def proven_path(work, previous_work=None, exclude_endpoint_direct=False):
    candidates = read_tsv(work / "adaptive-geo-input.tsv")
    proofs = read_tsv(work / "adaptive-geo-proof.tsv")
    points = {row["id"]: row for row in read_tsv(work / "corridor-points.tsv")}
    if previous_work:
        candidates += read_tsv(previous_work / "adaptive-geo-input.tsv")
        proofs += read_tsv(previous_work / "adaptive-geo-proof.tsv")
        for point in read_tsv(previous_work / "corridor-points.tsv"):
            if points.get(point["id"]) != point:
                raise ValueError("Refinement changed a previously proven point")
    candidate_by_id = {row["connector_id"]: row for row in candidates}
    proof_by_id = {row["connector_id"]: row for row in proofs}
    if len(candidate_by_id) != len(candidates) or len(proof_by_id) != len(proofs) or candidate_by_id.keys() != proof_by_id.keys() or {"source", "target"} - points.keys():
        raise ValueError("Native corridor proof accounting mismatch")
    adjacent = defaultdict(list)
    for identity, candidate in candidate_by_id.items():
        start, end = identity.split(".to.", 1)
        if start not in points or end not in points:
            raise ValueError("Corridor proof references a missing point")
        if tuple(candidate["from_" + axis] for axis in "xyz") != tuple(points[start][axis] for axis in "xyz") or tuple(candidate["to_" + axis] for axis in "xyz") != tuple(points[end][axis] for axis in "xyz"):
            raise ValueError("Corridor point/proof coordinates differ")
        proof = proof_by_id[identity]
        if proof["validation_status"] in ("VALID_DIRECT", "VALID_PATH"):
            if int(proof["path_length"]) <= 0 or int(proof["path_segments"]) <= 0 or proof["collision_proof"] != "STATIC_XML_CLEAR":
                raise ValueError("Native path proof is incomplete")
            if not (exclude_endpoint_direct and start == "source" and end == "target"):
                adjacent[start].append((end, candidate, proof))
    previous = {"source": None}
    queue = deque(["source"])
    while queue and "target" not in previous:
        for end, candidate, proof in sorted(adjacent[queue.popleft()], key=lambda value: value[0]):
            if end not in previous:
                previous[end] = (candidate, proof)
                queue.append(end)
    if "target" not in previous:
        raise ValueError("No source-to-target native-proven corridor")
    path = []
    cursor = "target"
    while cursor != "source":
        candidate, proof = previous[cursor]
        start, end = candidate["connector_id"].split(".to.", 1)
        path.append((start, end, candidate, proof))
        cursor = start
    path.reverse()
    return points, path


def publish(module, work, from_anchor, from_fact, to_anchor, previous_work=None):
    if bool(from_anchor) == bool(from_fact):
        raise ValueError("Select exactly one source anchor or native NORMAL fact")
    anchors = native_anchors(module)
    if to_anchor not in anchors or (from_anchor and from_anchor not in anchors):
        raise ValueError("Selected endpoint is not a published topology anchor")
    same_node = bool(from_anchor) and anchors[from_anchor].get("nodeId") == anchors[to_anchor].get("nodeId")
    points, path = proven_path(work, previous_work, same_node)
    heights = {row["point_id"]: row for row in read_tsv(work / "height-proof.tsv")}
    if previous_work:
        for proof in read_tsv(previous_work / "height-proof.tsv"):
            if proof["point_id"] in heights and heights[proof["point_id"]] != proof:
                raise ValueError("Refinement changed a previously proven native height")
            heights[proof["point_id"]] = proof
    if to_anchor not in anchors:
        raise ValueError("Target is not a published topology anchor")
    fact = native_fact(module, from_fact) if from_fact else None
    if from_anchor and from_anchor not in anchors:
        raise ValueError("Source is not a published topology anchor")
    source_xyz = tuple(fact["destination_" + axis] for axis in "xyz") if fact else tuple(anchors[from_anchor].get(axis) for axis in "xyz")
    target_xyz = tuple(anchors[to_anchor].get(axis) for axis in "xyz")
    if tuple(points["source"][axis] for axis in "xyz") != source_xyz or tuple(points["target"][axis] for axis in "xyz") != target_xyz:
        raise ValueError("Selected endpoints do not match native topology/fact coordinates")

    shard = module / "dist/game/data/phantoms/topology/high-five-generated-07.xml"
    root = ET.parse(shard).getroot() if shard.exists() else ET.Element("topology", {
        "schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"})
    existing_nodes = {node.get("id"): node for node in root.findall("node")}
    existing_anchors = {anchor.get("id"): anchor for anchor in root.findall("anchor")}
    existing_edges = {edge.get("id"): edge for edge in root.findall("edge")}
    existing_rows = read_tsv(module / "docs/phantoms/live-world/M1_FARM_ROUTE_PROOF.tsv") if (module / "docs/phantoms/live-world/M1_FARM_ROUTE_PROOF.tsv").exists() else []
    proof_rows = {row["edge_id"]: row for row in existing_rows}
    replaced = [identity for identity, edge in existing_edges.items()
                if same_node and edge.get("fromAnchorId") == from_anchor
                and edge.get("toAnchorId") == to_anchor and edge.get("fromNodeId") == edge.get("toNodeId")]
    for identity in replaced:
        if identity not in proof_rows:
            raise ValueError("Same-node replacement lacks task-owned published proof")
        del existing_edges[identity]
        del proof_rows[identity]

    def refs(identity):
        if identity == "source" and fact:
            values = (fact["source_path"], fact["spawn_source_path"])
        elif identity in ("source", "target"):
            anchor = anchors[from_anchor if identity == "source" else to_anchor]
            values = tuple(source.get("path") for source in anchor.findall("source"))
        elif identity.startswith("grid."):
            values = refs("source") + refs("target")
        else:
            values = (points[identity]["source_path"],)
        result = tuple(sorted(set(value for value in values if value)))
        for value in result:
            if not value.startswith("data/") or not (module / "dist/game" / value).is_file():
                raise ValueError("Corridor source path is not a native data file: " + value)
        return result

    def topology_ids(identity):
        if identity == "source" and from_anchor:
            anchor = anchors[from_anchor]
            return anchor.get("nodeId"), from_anchor
        if identity == "target":
            return anchors[to_anchor].get("nodeId"), to_anchor
        if identity != "source" and not native_waypoint(module, points[identity], heights, points):
            raise ValueError("Waypoint no longer matches its native spawn source: " + identity + " " + points[identity]["source_path"])
        node_id = "m1.route." + (digest("normal-arrival", from_fact) if identity == "source" else digest(identity) if identity.startswith("grid.") else identity.removeprefix("waypoint."))
        anchor_id = node_id + ".anchor"
        point = points[identity]
        if node_id in existing_nodes:
            if any(existing_nodes[node_id].get(axis) != point[axis] for axis in "xyz"):
                raise ValueError("Published route point changed")
        else:
            node = ET.Element("node", {"id": node_id, "kind": "ROUTE_AREA", "instanceId": "0",
                                       "form": "POINT_RADIUS", "x": point["x"], "y": point["y"],
                                       "z": point["z"], "radius": "1", "tags": "route"})
            anchor = ET.Element("anchor", {"id": anchor_id, "role": "ROUTE", "nodeId": node_id,
                                           "x": point["x"], "y": point["y"], "z": point["z"],
                                           "instanceId": "0", "tolerance": "0", "tags": "route"})
            for value in refs(identity):
                ET.SubElement(node, "source", {"path": value})
                ET.SubElement(anchor, "source", {"path": value})
            existing_nodes[node_id] = node
            existing_anchors[anchor_id] = anchor
        return node_id, anchor_id

    for start, end, candidate, proof in path:
        from_node, from_id = topology_ids(start)
        to_node, to_id = topology_ids(end)
        if from_node == to_node:
            raise ValueError("Native corridor would publish a topology self-edge")
        edge_id = "m1.walk." + digest(from_id, to_id, candidate["connector_id"])
        sources = tuple(sorted(set(refs(start) + refs(end))))
        row = {"edge_id": edge_id, "from_anchor": from_id, "to_anchor": to_id,
               "candidate_id": candidate["connector_id"], "validation_status": proof["validation_status"],
               "path_length": proof["path_length"], "path_segments": proof["path_segments"],
               "source_refs": "|".join(sources)}
        if edge_id in proof_rows and proof_rows[edge_id] != row:
            raise ValueError("Published proof row changed")
        proof_rows[edge_id] = row
        if edge_id not in existing_edges:
            edge = ET.Element("edge", {"id": edge_id, "fromNodeId": from_node, "toNodeId": to_node,
                                       "mode": "BACKGROUND", "bidirectional": "false", "baseCost": "1",
                                       "baseTravelMillis": str(max(1000, int(proof["path_length"]) * 10)),
                                       "backgroundEligible": "true", "channels": "COMBAT,TARGETABILITY",
                                       "fromAnchorId": from_id, "toAnchorId": to_id})
            for value in sources:
                ET.SubElement(edge, "source", {"path": value})
            existing_edges[edge_id] = edge

    root[:] = ([existing_nodes[key] for key in sorted(existing_nodes)]
               + [existing_anchors[key] for key in sorted(existing_anchors)]
               + [existing_edges[key] for key in sorted(existing_edges)])
    ET.indent(root, space="\t")
    xml = b'<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(root, encoding="utf-8") + b"\n"
    if len(xml) >= 4 * 1024 * 1024:
        raise ValueError("Generated route shard exceeds topology loader bound")
    temporary = shard.with_suffix(".xml.tmp")
    temporary.write_bytes(xml)
    temporary.replace(shard)
    registry = module / "docs/phantoms/live-world"
    proof_file = registry / "M1_FARM_ROUTE_PROOF.tsv"
    columns = ("edge_id", "from_anchor", "to_anchor", "candidate_id", "validation_status",
               "path_length", "path_segments", "source_refs")
    write_tsv(proof_file, columns, [proof_rows[key] for key in sorted(proof_rows)])
    sources = sorted({value for row in proof_rows.values() for value in row["source_refs"].split("|") if value})
    manifest = {"schema": "PHANTOM-LIVE-M1-FINAL-LIVING-WORLD-003/1",
                "hash_policy": "UTF-8, LF-normalized before SHA-256",
                "topology_sha256": sha(shard), "proof_sha256": sha(proof_file),
                "source_sha256": {value: sha(module / "dist/game" / value) for value in sources}}
    (registry / "M1_FARM_ROUTE_MANIFEST.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    if replaced:
        print(f"M1_SELF_EDGE_REPLACED count={len(replaced)}")
    print(f"M1_FARM_ROUTE_PUBLISHED steps={len(path)} total_edges={len(proof_rows)} source={from_anchor or from_fact} target={to_anchor}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--module", type=Path, required=True)
    parser.add_argument("--work", type=Path, required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--from-anchor")
    source.add_argument("--from-fact")
    parser.add_argument("--to-anchor", required=True)
    parser.add_argument("--previous-work", type=Path)
    args = parser.parse_args()
    publish(args.module.resolve(), args.work.resolve(), args.from_anchor, args.from_fact, args.to_anchor,
            args.previous_work.resolve() if args.previous_work else None)
