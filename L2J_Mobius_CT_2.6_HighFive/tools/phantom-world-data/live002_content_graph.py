"""Select bounded native-entry spanning checks and retain only hermetic proof."""

import argparse
from collections import Counter, defaultdict
import math
from pathlib import Path
import xml.etree.ElementTree as ET

import travel_backbone as d1


INPUT_COLUMNS = ("connector_id", "from_x", "from_y", "from_z", "from_instance", "to_x", "to_y", "to_z", "to_instance")
EDGE_COLUMNS = ("connector_id", "family", "source_family", "from_id", "to_id", "from_x", "from_y", "from_z", "to_x", "to_y", "to_z", "entrance_semantic", "entrance_owner", "selection", "straight_distance")
LIMITS = {"SSQ_CATACOMBS_NECROPOLIS": 1024, "DEVILS_ISLE": 512, "TOWER_OF_INSOLENCE": 512, "IVORY_TOWER": 512, "IMPERIAL_TOMB": 512}


def native_extra_seeds(module):
    data = module / "dist/game/data/teleporters"
    toi, ivory = [], []
    root = ET.parse(data / "town/30848.xml").getroot()
    for npc in root.findall("npc"):
        if npc.get("id") != "30848":
            continue
        for tele in npc.findall("teleport"):
            if tele.get("type") not in ("NOBLES_TOKEN", "NOBLES_ADENA"):
                continue
            for index, loc in enumerate(tele.findall("location")):
                xyz = tuple(int(loc.get(axis)) for axis in "xyz")
                ident = "entry.toi.nobles." + d1.key(*xyz)
                if not any(seed[0] == ident for seed in toi):
                    toi.append((ident, xyz, "NOBLESSE_CONDITIONAL", "data/teleporters/town/30848.xml"))
    for file in sorted((data / "others/IvoryTower").glob("*.xml")):
        root = ET.parse(file).getroot()
        for tele in root.iter("teleport"):
            if tele.get("type") != "NORMAL":
                continue
            for loc in tele.findall("location"):
                xyz = tuple(int(loc.get(axis)) for axis in "xyz")
                if not (84000 <= xyz[0] <= 87000 and 14000 <= xyz[1] <= 18000):
                    continue
                ident = "entry.ivory.internal." + d1.key(*xyz)
                if not any(seed[0] == ident for seed in ivory):
                    ivory.append((ident, xyz, "NORMAL_INTERNAL", "data/teleporters/others/IvoryTower/" + file.name))
    return toi, ivory


def prepare(module):
    registry = module / "docs/phantoms/live-world"
    evidence = module / "dist/game/data/phantoms/evidence"
    plan = d1.read_tsv(registry / "FINAL_CONTENT_ACCESS_PLAN.tsv")
    geo = {row["coverage_key"]: row for row in d1.read_tsv(evidence / "live002-content-anchor-geo.tsv")}
    if len(plan) != 676 or len(geo) != 676 or any(geo[row["coverage_key"]]["status"] != "VALID" or geo[row["coverage_key"]]["collision_proof"] != "STATIC_XML_CLEAR" for row in plan):
        raise RuntimeError("Final content anchor proof incomplete")
    toi, ivory = native_extra_seeds(module)
    groups = defaultdict(list)
    for row in plan:
        groups[(row["family"], row["source_family"])].append(row)
    selected = []
    for (family, source_family), rows in sorted(groups.items()):
        anchors = {row["coverage_key"]: tuple(int(geo[row["coverage_key"]]["anchor_" + axis]) for axis in "xyz") for row in rows}
        entrance = min(rows, key=lambda row: (math.dist(tuple(int(row["entrance_" + axis]) for axis in "xyz"), anchors[row["coverage_key"]]), row["coverage_key"]))
        xyz = tuple(int(entrance["entrance_" + axis]) for axis in "xyz")
        seeds = [("entry." + d1.key(family, source_family, entrance["entrance_owner"], *xyz), xyz, entrance["entrance_class"], entrance["entrance_owner"])]
        if family == "TOWER_OF_INSOLENCE":
            seeds.extend(toi)
        if family == "IVORY_TOWER":
            seeds.extend(ivory)
        visited = {item[0]: item for item in seeds}
        pending = dict(anchors)
        while pending:
            distance, source_id, target_id = min((math.dist(source[1], target_xyz), source_id, target_id)
                                                 for source_id, source in visited.items()
                                                 for target_id, target_xyz in pending.items())
            source_xyz = visited[source_id][1]
            target_xyz = pending.pop(target_id)
            semantic = visited[source_id][2]
            owner = visited[source_id][3]
            from_id = source_id if source_id.startswith("entry.") else "generated.farm." + source_id[:24] + ".anchor"
            to_id = "generated.farm." + target_id[:24] + ".anchor"
            connector_id = "content.edge." + d1.key(family, source_family, from_id, to_id)
            selected.append({"connector_id": connector_id, "family": family, "source_family": source_family,
                             "from_id": from_id, "to_id": to_id,
                             **{"from_" + axis: str(value) for axis, value in zip("xyz", source_xyz)},
                             **{"to_" + axis: str(value) for axis, value in zip("xyz", target_xyz)},
                             "entrance_semantic": semantic, "entrance_owner": owner,
                             "selection": "NATIVE_ENTRY_TO_FIRST_ROOM" if source_id.startswith("entry.") else "NATIVE_ROOM_SPANNING",
                             "straight_distance": str(math.ceil(distance))})
            visited[target_id] = (target_id, target_xyz, semantic, owner)
    counts = Counter(row["family"] for row in selected)
    if len(selected) != 676 or any(counts[label] > limit for label, limit in LIMITS.items()):
        raise RuntimeError("Content directed-check budget exceeded")
    selected.sort(key=lambda row: row["connector_id"])
    d1.write_tsv(registry / "FINAL_CONTENT_GEO_CANDIDATES.tsv", EDGE_COLUMNS, selected)
    d1.write_tsv(evidence / "live002-content-geo-input.tsv", INPUT_COLUMNS,
                 [{"connector_id": row["connector_id"], "from_x": row["from_x"], "from_y": row["from_y"], "from_z": row["from_z"], "from_instance": "0", "to_x": row["to_x"], "to_y": row["to_y"], "to_z": row["to_z"], "to_instance": "0"} for row in selected])
    print("CONTENT_GRAPH_CANDIDATES", dict(counts), "total=" + str(len(selected)), "sha=" + d1.sha_file(registry / "FINAL_CONTENT_GEO_CANDIDATES.tsv"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    args = parser.parse_args()
    prepare(Path(__file__).resolve().parents[2])
