"""Derive bounded farm-corridor probe candidates from native spawn facts.

Run `heights`, the existing GeoEngine height probe, `routes`, then the native
route probe. m1_farm_route_publish.py is the explicit promotion step.
"""

import argparse
import csv
from collections import defaultdict, deque
import hashlib
import math
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("source")
parser.add_argument("target")
parser.add_argument("mode", choices=("heights", "routes"))
parser.add_argument("work", type=Path)
parser.add_argument("--grid-step", type=int, choices=(0, 512, 1024, 2048), default=0)
parser.add_argument("--grid-layers", action="store_true",
                    help="Probe source, destination and midpoint native height seeds")
parser.add_argument("--refine-from", type=Path,
                    help="Reuse an existing native proof and refine only its closest disconnected frontiers")
parser.add_argument("--frontier-count", type=int, choices=(1, 4), default=1)
args = parser.parse_args()
module = Path(__file__).resolve().parents[2]
source_id, target_id, mode = args.source, args.target, args.mode
root = args.work.resolve()
root.mkdir(parents=True, exist_ok=True)
anchors = {node.get("id"): node.attrib
           for file in (module / "dist/game/data/phantoms/topology").glob("*.xml")
           for node in ET.parse(file).getroot().findall("anchor")}
if source_id.startswith("fact."):
    with (module / "docs/phantoms/live-world/GATEKEEPER_FACTS.tsv").open(encoding="utf-8", newline="") as facts:
        fact = next(row for row in csv.DictReader(facts, delimiter="\t") if row["fact_key"] == source_id)
    source = {axis: fact["destination_" + axis] for axis in "xyz"}
else:
    source = anchors[source_id]
target = anchors[target_id]
sx, sy = int(source["x"]), int(source["y"])
tx, ty = int(target["x"]), int(target["y"])
margin = 5000
lower_x, upper_x = min(sx, tx) - margin, max(sx, tx) + margin
lower_y, upper_y = min(sy, ty) - margin, max(sy, ty) + margin
points = [dict(id="source", source_path="", **{axis: source[axis] for axis in "xyz"}),
          dict(id="target", source_path="", **{axis: target[axis] for axis in "xyz"})]
previous_candidates = set()
refinement_bounds = None
if args.refine_from:
    def read_previous(name):
        with (args.refine_from / name).open(encoding="utf-8", newline="") as file:
            return list(csv.DictReader(file, delimiter="\t"))

    previous_points = {row["id"]: row for row in read_previous("corridor-points.tsv")}
    if any(tuple(points[index][axis] for axis in "xyz") != tuple(previous_points[key][axis] for axis in "xyz")
           for index, key in enumerate(("source", "target"))):
        raise ValueError("Refinement endpoints differ from the existing proof")
    previous_candidates = {row["connector_id"] for row in read_previous("adaptive-geo-input.tsv")}
    previous_proofs = read_previous("adaptive-geo-proof.tsv")
    if previous_candidates != {row["connector_id"] for row in previous_proofs}:
        raise ValueError("Refinement proof accounting mismatch")
    forward, reverse = defaultdict(list), defaultdict(list)
    for row in previous_proofs:
        if row["validation_status"] in ("VALID_DIRECT", "VALID_PATH"):
            start, end = row["connector_id"].split(".to.", 1)
            forward[start].append(end)
            reverse[end].append(start)

    def reachable(start, adjacent):
        visited, pending = {start}, deque([start])
        while pending:
            for end in adjacent[pending.popleft()]:
                if end not in visited:
                    visited.add(end)
                    pending.append(end)
        return visited

    start_component, end_component = reachable("source", forward), reachable("target", reverse)
    if "target" in start_component:
        raise ValueError("Existing proof already contains a complete corridor")
    radius = 4 * args.grid_step
    if radius == 0:
        raise ValueError("Refinement requires a grid step")
    pairs = sorted((math.dist(tuple(int(previous_points[start][axis]) for axis in "xyz"),
                             tuple(int(previous_points[end][axis]) for axis in "xyz")), start, end)
                   for start in sorted(start_component) for end in sorted(end_component))
    centers = []
    for distance, from_point, to_point in pairs:
        if distance > pairs[0][0] + 2 * radius:
            break
        midpoint = tuple(round((int(previous_points[from_point][axis]) + int(previous_points[to_point][axis])) / 2)
                         for axis in "xy")
        if any(math.dist(midpoint, center) < radius for center in centers):
            continue
        centers.append(midpoint)
        if len(centers) == args.frontier_count:
            break
    refinement_bounds = [(x - radius, x + radius, y - radius, y + radius) for x, y in centers]
    points = list(previous_points.values())
    print(f"refine components={len(start_component)}/{len(end_component)} centers={centers}")
vertices = []
for file in sorted((module / "dist/game/data/spawns").rglob("*.xml")):
    source_path = "data/spawns/" + file.relative_to(module / "dist/game/data/spawns").as_posix()
    for group_index, group in enumerate(ET.parse(file).getroot().findall("spawn")):
        territory = group.find("territory")
        if territory is not None and all(key in territory.attrib for key in ("minZ", "maxZ")):
            seed = str(round((int(territory.get("minZ")) + int(territory.get("maxZ"))) / 2))
            for vertex_index, node in enumerate(territory.findall("node")):
                x, y = int(node.get("x")), int(node.get("y"))
                if lower_x <= x <= upper_x and lower_y <= y <= upper_y:
                    key = f"{source_path}|{group_index}|vertex|{vertex_index}"
                    vertices.append(dict(id="waypoint." + hashlib.sha256(key.encode()).hexdigest()[:24],
                                         x=str(x), y=str(y), z=seed, source_path=source_path))
        for npc_index, npc in enumerate(group.findall("npc")):
            if not all(axis in npc.attrib for axis in "xyz"):
                continue
            x, y = int(npc.get("x")), int(npc.get("y"))
            if lower_x <= x <= upper_x and lower_y <= y <= upper_y:
                key = f"{source_path}|{group_index}|npc|{npc_index}"
                points.append(dict(id="waypoint." + hashlib.sha256(key.encode()).hexdigest()[:24],
                                   x=str(x), y=str(y), z=npc.get("z"), source_path=source_path))
if args.grid_step:
    step = args.grid_step
    midpoint = round((int(source["z"]) + int(target["z"])) / 2)
    seeds = sorted({int(source["z"]), int(target["z"]), midpoint}) if args.grid_layers else [midpoint]
    for ix in range(math.ceil((lower_x - sx) / step), math.floor((upper_x - sx) / step) + 1):
        for iy in range(math.ceil((lower_y - sy) / step), math.floor((upper_y - sy) / step) + 1):
            x, y = sx + ix * step, sy + iy * step
            if refinement_bounds and not any(bounds[0] <= x <= bounds[1] and bounds[2] <= y <= bounds[3]
                                             for bounds in refinement_bounds):
                continue
            for seed_z in seeds:
                identity = f"grid.{step}.{x}.{y}" + (f".seed.{seed_z}" if args.grid_layers else "")
                vertices.append(dict(id=identity, x=str(x), y=str(y),
                                     z=str(seed_z), source_path=""))
if mode == "heights":
    if args.refine_from:
        previous_height_ids = {row["point_id"] for row in read_previous("height-proof.tsv")}
        vertices = [row for row in vertices if row["id"] not in previous_height_ids]
    with (root / "height-input.tsv").open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, delimiter="\t", lineterminator="\n")
        writer.writerow(("point_id", "x", "y", "seed_z"))
        writer.writerows((row["id"], row["x"], row["y"], row["z"]) for row in vertices)
    print(f"fixed={len(points)-2} vertices={len(vertices)}")
    raise SystemExit()
if not args.refine_from:
    fixed_coordinates = set()
    distinct_points = []
    for point in points:
        coordinate = tuple(point[axis] for axis in "xyz")
        if point["id"] in ("source", "target") or coordinate not in fixed_coordinates:
            distinct_points.append(point)
            fixed_coordinates.add(coordinate)
    points = distinct_points
with (root / "height-proof.tsv").open(encoding="utf-8", newline="") as source:
    heights = {row["point_id"]: row for row in csv.DictReader(source, delimiter="\t") if row["stable"] == "true"}
unique_points = {}
for point in points:
    if point["id"] in unique_points and unique_points[point["id"]] != point:
        raise ValueError("Refinement source point changed")
    unique_points[point["id"]] = point
points = list(unique_points.values())
coordinates = {tuple(row[axis] for axis in "xyz") for row in points}
point_ids = {row["id"] for row in points}
for row in sorted(vertices, key=lambda value: value["id"]):
    if row["id"] not in heights:
        continue
    resolved = dict(row, z=heights[row["id"]]["z"])
    coordinate = tuple(resolved[axis] for axis in "xyz")
    if coordinate not in coordinates and row["id"] not in point_ids:
        points.append(resolved)
        coordinates.add(coordinate)
        point_ids.add(row["id"])
with (root / "corridor-points.tsv").open("w", encoding="utf-8", newline="") as output:
    writer = csv.DictWriter(output, fieldnames=("id", "x", "y", "z", "source_path"),
                            delimiter="\t", lineterminator="\n")
    writer.writeheader()
    writer.writerows(sorted(points, key=lambda row: row["id"]))
rows = []
for start in points:
    for end in points:
        if start is end or math.dist((int(start["x"]), int(start["y"])),
                                     (int(end["x"]), int(end["y"]))) > 3300:
            continue
        if start["id"] + ".to." + end["id"] in previous_candidates:
            continue
        rows.append((start["id"] + ".to." + end["id"], start["x"], start["y"],
                     start["z"], "0", end["x"], end["y"], end["z"], "0"))
with (root / "adaptive-geo-input.tsv").open("w", encoding="utf-8", newline="") as output:
    writer = csv.writer(output, delimiter="\t", lineterminator="\n")
    writer.writerow(("connector_id", "from_x", "from_y", "from_z", "from_instance",
                     "to_x", "to_y", "to_z", "to_instance"))
    writer.writerows(sorted(rows))
print(f"source={source_id} target={target_id} points={len(points)} directions={len(rows)}")
