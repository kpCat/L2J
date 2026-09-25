"""Independent fail-closed audit of LIVE-002 published content access."""

import csv
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

import live002_ruins_final as ruins


FAMILIES = {
    "SSQ_CATACOMBS_NECROPOLIS": (429, "high-five-closed-ssq-01.xml"),
    "DEVILS_ISLE": (96, "high-five-closed-devils-isle.xml"),
    "TOWER_OF_INSOLENCE": (92, "high-five-closed-toi.xml"),
    "IVORY_TOWER": (39, "high-five-closed-ivory.xml"),
    "IMPERIAL_TOMB": (20, "high-five-closed-imperial.xml"),
}
VALID = {"REACHABLE_ORDINARY", "REACHABLE_CONDITIONAL", "DOOR_STATE_BLOCKED", "GEODATA_INTERNAL_BLOCKED", "NATIVE_CONDITION_UNMODELED", "CONTENT_TELEPORT_SEMANTIC_BLOCKED"}
COLUMNS = ("metric", "value", "evidence")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def verify(module):
    registry = module / "docs/phantoms/live-world"
    game = module / "dist/game"
    topology = game / "data/phantoms/topology"
    ruins_counts, _, targeted_sha = ruins.validate(module, registry / "LIVE002_RUINS_FINAL.tsv")
    historical = read(registry / "CLOSED_AREA_ACCESS.tsv")
    plan = read(registry / "FINAL_CONTENT_ACCESS_PLAN.tsv")
    proof = read(registry / "FINAL_CONTENT_ACCESS_PROOF.tsv")
    farms = read(registry / "FINAL_CONTENT_FARM_ANCHORS.tsv")
    conditional = read(registry / "CONDITIONAL_CONTENT_ACCESS.tsv")
    if Counter((row["family"], row["teleport_type"]) for row in conditional) != Counter({("SSQ_CATACOMBS_NECROPOLIS", "OTHER"): 28, ("TOWER_OF_INSOLENCE", "NOBLES_TOKEN"): 5, ("TOWER_OF_INSOLENCE", "NOBLES_ADENA"): 5, ("TOWER_OF_INSOLENCE", "DOOR"): 3, ("IVORY_TOWER", "OTHER"): 8, ("IMPERIAL_TOMB", "NOBLES_TOKEN"): 1, ("IMPERIAL_TOMB", "NOBLES_ADENA"): 1}):
        raise RuntimeError("Conditional family native scope drift")
    groups = [row for row in proof if row["row_kind"] == "GROUP"]
    family_rows = [row for row in proof if row["row_kind"] == "FAMILY"]
    keys = [row["coverage_key"] for row in groups]
    expected = {row["coverage_key"] for row in historical}
    if (len(historical), len(plan), len(groups), len(farms), len(family_rows), len(keys), len(set(keys))) != (676, 676, 676, 676, 5, 676, 676):
        raise RuntimeError("Final closed row cardinality drift")
    if set(keys) != expected or {row["coverage_key"] for row in plan} != expected or {row["coverage_key"] for row in farms} != expected:
        raise RuntimeError("Final closed row key mismatch")
    if set(FAMILIES) != {row["family"] for row in family_rows} or any(row["status"] not in VALID or not row["status"] or "UNPROVEN" in row["status"] for row in groups):
        raise RuntimeError("Unknown final content status")
    if any(sum(row["family"] == label for row in groups) != count for label, (count, _) in FAMILIES.items()):
        raise RuntimeError("Five-family coverage drift")
    geo = {row["coverage_key"]: row for row in read(game / "data/phantoms/evidence/live002-content-anchor-geo.tsv")}
    coverage = {row["coverage_key"]: row for row in read(registry / "WORLD_COVERAGE.tsv")}
    if len(geo) != 676 or any(geo[key]["status"] != "VALID" or geo[key]["collision_proof"] != "STATIC_XML_CLEAR" for key in expected):
        raise RuntimeError("Final farm coordinate has no hermetic Geo proof")
    source_cache = {}
    plan_by_key = {row["coverage_key"]: row for row in plan}
    farm_by_key = {row["coverage_key"]: row for row in farms}
    for row in groups:
        key, family = row["coverage_key"], row["family"]
        p, farm = plan_by_key[key], farm_by_key[key]
        fact = coverage.get(key)
        if fact is None or fact["classification"] != "ORDINARY_WORLD" or fact["status"] != "READY_STATIC" or fact["geometry_kind"] != "NPOLY" or "Monster" not in fact["npc_types"].split("|") or fact["source_path"] != p["source_path"] or fact["source_group"] != p["source_group"]:
            raise RuntimeError("Farming anchor lacks factual native Monster group: " + key)
        if p["family"] != family or farm["family"] != family or farm["access_status"] != row["status"] or row["farm_anchor_id"] != farm["anchor_id"] or not row["internal_component_id"]:
            raise RuntimeError("Group/farm ledger mismatch: " + key)
        if any(geo[key]["anchor_" + axis] != farm["anchor_" + axis] for axis in "xyz"):
            raise RuntimeError("Farm/Geo coordinate drift: " + key)
        path = game / p["source_path"]
        if p["source_path"] not in source_cache:
            if sha(path) != p["source_sha256"]:
                raise RuntimeError("Native Monster owner SHA drift")
            source_cache[p["source_path"]] = {s.get("zone", s.get("name")): s for s in ET.parse(path).getroot().findall("spawn")}
        spawn = source_cache[p["source_path"]].get(p["source_group"])
        territory = spawn.find("territory") if spawn is not None else None
        native = None if territory is None else "territory:" + territory.get("minZ") + ":" + territory.get("maxZ") + ":" + "|".join(n.get("x") + "," + n.get("y") for n in territory.findall("node"))
        if native != p["room_territory"] or native != farm["room_territory"]:
            raise RuntimeError("Native NPOLY territory drift: " + key)
    manifest = json.loads((registry / "TARGETED_TRAVEL_CONNECTORS_MANIFEST.json").read_text(encoding="utf-8"))
    if manifest["output_sha256"]["TARGETED_TRAVEL_CONNECTORS.tsv"] != targeted_sha or manifest["counts"]["targeted_connectors"] != 13:
        raise RuntimeError("Targeted supplement provenance drift")
    java = (module / "java/org/l2jmobius/gameserver/phantoms/background/PhantomNormalGatekeeperTravel.java").read_text(encoding="utf-8")
    if re.search(r'TARGETED_CONNECTORS_SHA = "([0-9a-f]{64})"', java).group(1) != targeted_sha:
        raise RuntimeError("Production targeted SHA pin drift")
    native_conditional = set()
    native_doors = {door.get("id"): door for door in ET.parse(game / "data/Doors.xml").getroot().findall("door")}
    for row in conditional:
        if row["family"] not in FAMILIES or row["background_eligible"] != "0" or not row["exact_required_state"]:
            raise RuntimeError("Unbounded conditional content catalog")
        if row["teleport_type"] == "DOOR":
            ids = row["exact_required_state"].split(";", 1)[0].removeprefix("door_ids=").split("|")
            if row["family"] != "TOWER_OF_INSOLENCE" or row["source_owner"] != "data/Doors.xml" or "default_status=close" not in row["exact_required_state"] or any(native_doors.get(ident) is None or native_doors[ident].get("default_status") != "close" for ident in ids):
                raise RuntimeError("Native closed door catalog drift")
            continue
        owner = game / row["source_owner"]
        npc = next((n for n in ET.parse(owner).getroot().findall("npc") if n.get("id") == row["teleporter_npc_id"]), None)
        teleport = next((t for t in npc.findall("teleport") if t.get("type") == row["teleport_type"]), None) if npc is not None else None
        index = int(row["destination_index"]) - 1
        locations = teleport.findall("location") if teleport is not None else []
        if index < 0 or index >= len(locations):
            raise RuntimeError("Conditional native holder missing")
        location = locations[index]
        if (",".join(location.get(axis) for axis in "xyz") != row["destination_xyz"] or location.get("feeId", "") != row["fee_id"] or location.get("feeCount", "") != row["fee_count"]):
            raise RuntimeError("Conditional native destination/fee drift")
        native_conditional.add(row["native_identity"])
    if len(native_conditional) != len(conditional) - 3:
        raise RuntimeError("Conditional catalog identity duplicate or door drift")
    all_nodes, all_anchors, all_edges = set(), set(), set()
    status_counts = Counter()
    for label, (_, filename) in FAMILIES.items():
        path = topology / filename
        if path.stat().st_size >= 4 * 1024 * 1024 or manifest["closed_family_shards_sha256"][label] != sha(path):
            raise RuntimeError("Family shard size/provenance drift: " + label)
        root = ET.parse(path).getroot()
        if root.attrib != {"schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"}:
            raise RuntimeError("Family topology schema drift")
        nodes = {item.get("id"): item for item in root.findall("node")}
        anchors = {item.get("id"): item for item in root.findall("anchor")}
        edges = {item.get("id"): item for item in root.findall("edge")}
        if len(nodes) != len(root.findall("node")) or len(anchors) != len(root.findall("anchor")) or len(edges) != len(root.findall("edge")):
            raise RuntimeError("Duplicate family topology IDs")
        if all_nodes.intersection(nodes) or all_anchors.intersection(anchors) or all_edges.intersection(edges):
            raise RuntimeError("Cross-family topology ID collision")
        all_nodes.update(nodes); all_anchors.update(anchors); all_edges.update(edges)
        for row in (item for item in groups if item["family"] == label):
            status_counts[(label, row["status"])] += 1
            anchor = anchors.get(row["farm_anchor_id"])
            if row["status"].startswith("REACHABLE_") != (anchor is not None):
                if label != "SSQ_CATACOMBS_NECROPOLIS" or row["status"] != "NATIVE_CONDITION_UNMODELED":
                    raise RuntimeError("Unproven or missing published farm: " + row["coverage_key"])
            if anchor is not None and any(anchor.get(axis) != farm_by_key[row["coverage_key"]]["anchor_" + axis] for axis in "xyz"):
                raise RuntimeError("Published farm coordinate mismatch")
        for edge in edges.values():
            source, target = edge.get("fromAnchorId"), edge.get("toAnchorId")
            if source not in anchors or target not in anchors or edge.get("mode") != "BACKGROUND" or edge.get("bidirectional") != "false":
                raise RuntimeError("Family edge endpoint/mode drift")
            if edge.get("backgroundEligible") != ("true" if label == "DEVILS_ISLE" else "false"):
                raise RuntimeError("Conditional edge exposed to arbitrary background")
            references = [ref.get("path") for ref in edge.findall("source")]
            proofs = [ref for ref in references if ref and ref.endswith("-proof.tsv")]
            if len(proofs) != 1:
                raise RuntimeError("Published edge lacks exact Geo proof reference")
            match = next((item for item in read(game / proofs[0]) if item["connector_id"] == edge.get("id")), None)
            if match is None or match["validation_status"] not in ("VALID_DIRECT", "VALID_PATH") or match["collision_proof"] != "STATIC_XML_CLEAR" or any(match[field] != "0" for field in ("door_intersections", "fence_intersections")) or int(match["required_buffer"]) > 500 or match["max_pathfind_buffer"] != "500":
                raise RuntimeError("Published edge lacks production-buffer hermetic proof")
    if len(list(topology.glob("*.xml"))) > 64:
        raise RuntimeError("Topology loader file policy exceeded")
    if not any(row.get("teleporterNpcId") == "30080" and (row.get("destinationX"), row.get("destinationY"), row.get("destinationZ")) == ("43408", "206881", "-3752") for row in ET.parse(game / "data/phantoms/travel/high-five-normal-gk.xml").getroot().findall("leg")):
        raise RuntimeError("Devil NORMAL leg missing")
    output = registry / "LIVE002_FINAL_CONTENT_VALIDATION.tsv"
    summary = [{"metric": "RUINS_OF_DESPAIR_REACHABLE_FARM", "value": "1", "evidence": sha(registry / "LIVE002_RUINS_FINAL.tsv")},
               {"metric": "LIVE002_CLOSED_EXACT_ACCOUNTED", "value": "676", "evidence": sha(registry / "FINAL_CONTENT_ACCESS_PROOF.tsv")},
               {"metric": "LIVE002_UNKNOWN_UNPROVEN", "value": "0", "evidence": "exact_final_status_allowlist"},
               {"metric": "LIVE002_CONDITIONAL_CATALOG", "value": str(len(conditional)), "evidence": sha(registry / "CONDITIONAL_CONTENT_ACCESS.tsv")}]
    summary.extend({"metric": "GLOBAL_ORDINARY_" + band, "value": str(count), "evidence": "active_topology_and_normal_catalog"} for band, count in ruins_counts.items())
    summary.extend({"metric": "FAMILY_" + label + "_" + status, "value": str(count), "evidence": FAMILIES[label][1]} for (label, status), count in sorted(status_counts.items()))
    summary.append({"metric": "LIVE002_DATA_COMPLETE", "value": "1", "evidence": "ruins_green;676_exact_accounted;zero_unknown"})
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        writer = csv.DictWriter(stream, fieldnames=COLUMNS, delimiter="\t", lineterminator="\n")
        writer.writeheader(); writer.writerows(summary)
    print("LIVE002_DATA_COMPLETE=1 GREEN_PARTIAL", dict(status_counts), "conditional=" + str(len(conditional)), "sha=" + sha(output))


if __name__ == "__main__":
    verify(Path(__file__).resolve().parents[2])
