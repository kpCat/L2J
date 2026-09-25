"""Machine accounting and bounded native-territory candidates for LIVE-002 closed content."""

import argparse
import csv
import hashlib
import math
from pathlib import Path
import xml.etree.ElementTree as ET

import travel_backbone as d1


FAMILIES = ("SSQ_CATACOMBS_NECROPOLIS", "DEVILS_ISLE", "TOWER_OF_INSOLENCE", "IVORY_TOWER", "IMPERIAL_TOMB")
EXPECTED = dict(zip(FAMILIES, (429, 96, 92, 39, 20)))
CANDIDATE_COLUMNS = ("coverage_key", "family", "candidate_index", "x", "y", "z_hint", "min_z", "max_z", "local_x", "local_y")
PLAN_COLUMNS = ("family", "coverage_key", "source_family", "source_path", "source_group", "floor_key", "room_territory", "entrance_class", "entrance_owner", "entrance_x", "entrance_y", "entrance_z", "native_condition", "candidate_count", "source_sha256")


def family(source):
    if source.startswith(("Catacomb", "Necropolis")):
        return FAMILIES[0]
    return {"DevilsIsle": FAMILIES[1], "TowerOfInsolence": FAMILIES[2], "IvoryTower": FAMILIES[3], "ImperialTomb": FAMILIES[4]}[source]


def polygon_contains(vertices, x, y):
    inside = False
    for index, (ax, ay) in enumerate(vertices):
        bx, by = vertices[index - 1]
        cross = (x - ax) * (by - ay) - (y - ay) * (bx - ax)
        if cross == 0 and min(ax, bx) <= x <= max(ax, bx) and min(ay, by) <= y <= max(ay, by):
            return True
        if (ay > y) != (by > y) and x < ax + (bx - ax) * (y - ay) / (by - ay):
            inside = not inside
    return inside


def native_territory(spawn):
    territory = spawn.find("territory")
    if territory is None:
        raise RuntimeError("Native room territory missing")
    vertices = [(int(node.get("x")), int(node.get("y"))) for node in territory.findall("node")]
    if len(vertices) < 3:
        raise RuntimeError("Native polygon has fewer than three vertices")
    encoded = "territory:" + territory.get("minZ") + ":" + territory.get("maxZ") + ":" + "|".join(f"{x},{y}" for x, y in vertices)
    return encoded, int(territory.get("minZ")), int(territory.get("maxZ")), vertices


def candidates(vertices):
    cx = round(sum(x for x, _ in vertices) / len(vertices))
    cy = round(sum(y for _, y in vertices) / len(vertices))
    points = [(cx, cy)]
    points.extend(((min(x for x, _ in vertices) + max(x for x, _ in vertices)) // 2,
                   (min(y for _, y in vertices) + max(y for _, y in vertices)) // 2) for _ in range(1))
    for index, (x, y) in enumerate(vertices):
        nx, ny = vertices[(index + 1) % len(vertices)]
        points.append((round((x + nx + 2 * cx) / 4), round((y + ny + 2 * cy) / 4)))
        points.append((round((x + cx) / 2), round((y + cy) / 2)))
    seen = set()
    result = []
    for x, y in points:
        if (x, y) in seen or not polygon_contains(vertices, x, y):
            continue
        seen.add((x, y))
        local = next(((x + dx, y + dy) for dx, dy in ((16, 0), (-16, 0), (0, 16), (0, -16), (16, 16), (-16, -16), (32, 0), (0, 32)) if polygon_contains(vertices, x + dx, y + dy)), None)
        if local is not None:
            result.append((x, y, *local))
        if len(result) == 12:
            break
    return result


def native_entrances(module):
    data = module / "dist/game/data/teleporters"
    scopes = {
        FAMILIES[1]: ("town/30080.xml", "30080", "NORMAL", (43408, 206881, -3752)),
        FAMILIES[2]: ("town/30848.xml", "30848", "NORMAL", (114649, 11115, -5120)),
        FAMILIES[3]: ("town/30177.xml", "30177", "NORMAL", (85391, 16228, -3672)),
        FAMILIES[4]: ("town/31275.xml", "31275", "NOBLES_TOKEN", (186699, -75915, -2826)),
    }
    for label, (source, npc_id, kind, xyz) in scopes.items():
        root = ET.parse(data / source).getroot()
        matches = [(npc, teleport, location) for npc in root.findall("npc") if npc.get("id") == npc_id
                   for teleport in npc.findall("teleport") if teleport.get("type") == kind
                   for location in teleport.findall("location") if tuple(int(location.get(axis)) for axis in "xyz") == xyz]
        if len(matches) != 1:
            raise RuntimeError("Native entrance owner drift: " + label)
    ziggurats = []
    for npc_id in range(31095, 31126):
        source = f"dungeon/{npc_id}.xml"
        if not (data / source).exists():
            continue
        root = ET.parse(data / source).getroot()
        matches = [(n, t, l) for n in root.findall("npc") if n.get("id") == str(npc_id)
                   for t in n.findall("teleport") if t.get("type") == "OTHER"
                   for l in t.findall("location")]
        if len(matches) != 1:
            raise RuntimeError("Ziggurat OTHER owner drift: " + source)
        ziggurats.append((npc_id, source, tuple(int(matches[0][2].get(axis)) for axis in "xyz")))
    if len(ziggurats) != 28:
        raise RuntimeError("Ziggurat owner count drift")
    ivory = data / "others/IvoryTower"
    if {path.name for path in ivory.glob("*.xml")} != {"30162.xml", "30716.xml", "30719.xml", "30722.xml", "30727.xml"}:
        raise RuntimeError("Ivory internal teleporter scope drift")
    for file in ivory.glob("*.xml"):
        ET.parse(file)
    oracle = module / "dist/game/data/scripts/ai/others/OracleTeleport/OracleTeleport.java"
    script = oracle.read_text(encoding="utf-8")
    required = ("DIMENSIONAL_FRAGMENT = 7079", "player.getLevel() < 20", "player.getAllActiveQuests().size() > 40", "!hasQuestItems(player, DIMENSIONAL_FRAGMENT)", "ziggurat_noadena.htm")
    if any(token not in script for token in required):
        raise RuntimeError("Ziggurat script condition drift")
    return scopes, ziggurats, d1.sha_source(oracle)


def prepare(module):
    registry = module / "docs/phantoms/live-world"
    evidence = module / "dist/game/data/phantoms/evidence"
    closed = d1.read_tsv(registry / "CLOSED_AREA_ACCESS.tsv")
    coverage = {row["coverage_key"]: row for row in d1.read_tsv(registry / "WORLD_COVERAGE.tsv")}
    if len(closed) != 676 or len({row["coverage_key"] for row in closed}) != 676:
        raise RuntimeError("Historical closed ledger count/key drift")
    counts = {name: sum(family(row["source_family"]) == name for row in closed) for name in FAMILIES}
    if counts != EXPECTED:
        raise RuntimeError("Historical closed family counts drift: " + str(counts))
    scopes, ziggurats, oracle_sha = native_entrances(module)
    source_cache = {}
    plan, candidates_rows = [], []
    for row in sorted(closed, key=lambda item: item["coverage_key"]):
        key = row["coverage_key"]
        fact = coverage.get(key)
        if fact is None or fact["instance_id"] != "0" or fact["classification"] != "ORDINARY_WORLD" or fact["status"] != "READY_STATIC" or fact["geometry_kind"] != "NPOLY" or "Monster" not in fact["npc_types"].split("|"):
            raise RuntimeError("Closed source coverage invalid: " + key)
        source = fact["source_path"]
        if source not in source_cache:
            native_path = module / "dist/game" / source
            if d1.sha_file(native_path) != fact["source_sha256"]:
                raise RuntimeError("Native source SHA drift: " + source)
            source_cache[source] = {spawn.get("zone", spawn.get("name")): spawn for spawn in ET.parse(native_path).getroot().findall("spawn")}
        spawn = source_cache[source].get(fact["source_group"])
        if spawn is None or not set(fact["npc_ids"].split("|")).issubset({npc.get("id") for npc in spawn.findall("npc")}):
            raise RuntimeError("Native Monster group membership drift: " + key)
        encoded, minimum, maximum, vertices = native_territory(spawn)
        if encoded != row["room_territory"] or row["floor_key"] != f"z:{minimum}:{maximum}":
            raise RuntimeError("Closed/native room geometry drift: " + key)
        label = family(row["source_family"])
        if label == FAMILIES[0]:
            mx, my = sum(x for x, _ in vertices) / len(vertices), sum(y for _, y in vertices) / len(vertices)
            npc_id, owner, entrance = min(ziggurats, key=lambda item: (math.dist((mx, my), item[2][:2]), abs((minimum + maximum) / 2 - item[2][2]), item[0]))
            entry_class, condition = "SCRIPT_CONDITIONAL", "OTHER_holder_unmodeled;OracleTeleport_rift_branch:level>=20,activeQuests<=40,item7079,levelScaledAdena;not_backgroundEligible"
        else:
            owner, npc_id, entry_class, entrance = scopes[label]
            condition = "NOBLESSE_TOKEN_13722_OR_NOBLES_ADENA_1000" if label == FAMILIES[4] else "NORMAL_NATIVE"
            owner = "data/teleporters/" + owner
        selected = candidates(vertices)
        if not selected:
            raise RuntimeError("No in-polygon candidate coordinate: " + key)
        z_hint = (minimum + maximum) // 2
        for index, (x, y, local_x, local_y) in enumerate(selected, 1):
            candidates_rows.append({"coverage_key": key, "family": label, "candidate_index": str(index), "x": str(x), "y": str(y), "z_hint": str(z_hint), "min_z": str(minimum), "max_z": str(maximum), "local_x": str(local_x), "local_y": str(local_y)})
        plan.append({"family": label, "coverage_key": key, "source_family": row["source_family"], "source_path": source, "source_group": fact["source_group"], "floor_key": row["floor_key"], "room_territory": encoded, "entrance_class": entry_class, "entrance_owner": owner, "entrance_x": str(entrance[0]), "entrance_y": str(entrance[1]), "entrance_z": str(entrance[2]), "native_condition": condition, "candidate_count": str(len(selected)), "source_sha256": fact["source_sha256"]})
    d1.write_tsv(registry / "FINAL_CONTENT_ACCESS_PLAN.tsv", PLAN_COLUMNS, plan)
    candidate_path = evidence / "live002-content-anchor-candidates.tsv"
    d1.write_tsv(candidate_path, CANDIDATE_COLUMNS, candidates_rows)
    print("CONTENT_PREPARED groups=676", counts, "candidates=" + str(len(candidates_rows)), "oracle_sha=" + oracle_sha, "candidate_sha=" + d1.sha_file(candidate_path))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    args = parser.parse_args()
    prepare(Path(__file__).resolve().parents[2])
