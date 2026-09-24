"""Bounded LIVE-002 Plunderous chain evidence from active topology."""

import csv
import math
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict, deque
from pathlib import Path


SOURCE = "data/spawns/Others/PlunderousPlains.xml"
STARTS = (
    "generated.farm.f90942dd27180d0a2bda5235.anchor",
    "generated.farm.05aa0db26a7a3df923b2f303.anchor",
    "generated.farm.7e05f4b455d51dddfaf626dc.anchor",
)
GOALS = (
    "generated.farm.4c20d26f8b57611bdafd0d33.anchor",
    "generated.farm.6cc54f41492a8ebff56a5815.anchor",
)


def read_tsv(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def graph(module):
    live = module / "docs/phantoms/live-world"
    coverage = {r["coverage_key"]: r for r in read_tsv(live / "WORLD_COVERAGE.tsv")
                if r["source_path"] == SOURCE and r["classification"] == "ORDINARY_WORLD"
                and r["status"] == "READY_STATIC" and r["instance_id"] == "0"}
    candidate = {r["anchor_id"]: r for r in read_tsv(live / "TOPOLOGY_CANDIDATES.tsv")
                 if r["coverage_key"] in coverage}
    nodes = {}
    edges = defaultdict(list)
    shards = module / "dist/game/data/phantoms/topology"
    roots = [ET.parse(shards / name).getroot() for name in
             ("high-five-generated-01.xml", "high-five-generated-02.xml")]
    for root in roots:
        if root.get("datasetId") != "high-five-core":
            raise ValueError("unexpected topology dataset")
        for anchor in root.findall("anchor"):
            aid = anchor.get("id")
            if aid not in candidate or anchor.get("role") != "FARMING" or anchor.get("instanceId") != "0":
                continue
            if [s.get("path") for s in anchor.findall("source")] != [SOURCE]:
                continue
            row = candidate[aid]
            fact = coverage[row["coverage_key"]]
            node = root.find("node[@id='" + anchor.get("nodeId") + "']")
            if node is None or node.get("kind") != "FARMING_AREA" or node.get("instanceId") != "0":
                continue
            if [s.get("path") for s in node.findall("source")] != [SOURCE]:
                continue
            nodes[aid] = dict(id=aid, node_id=anchor.get("nodeId"), coverage=row["coverage_key"],
                              zone=fact["source_group"], level_min=int(fact["npc_level_min"]),
                              level_max=int(fact["npc_level_max"]), x=int(anchor.get("x")),
                              y=int(anchor.get("y")), z=int(anchor.get("z")))
    for root in roots:
        for edge in root.findall("edge"):
            src, dst = edge.get("fromAnchorId"), edge.get("toAnchorId")
            if (src in nodes and dst in nodes and edge.get("mode") == "BACKGROUND"
                    and edge.get("backgroundEligible") == "true" and edge.get("bidirectional") == "false"):
                edges[src].append((dst, edge.get("id")))
    for src in edges:
        edges[src].sort()
    return nodes, edges


def bfs(edges):
    queue = deque(STARTS)
    parent = {start: None for start in STARTS}
    while queue:
        current = queue.popleft()
        if current in GOALS:
            path = []
            while parent[current] is not None:
                previous, edge_id = parent[current]
                path.append((previous, current, edge_id))
                current = previous
            return list(reversed(path)), parent
        for neighbor, edge_id in edges.get(current, ()):
            if neighbor not in parent:
                parent[neighbor] = (current, edge_id)
                queue.append(neighbor)
    return None, parent


def prepare(nodes, edges, source, target, output):
    if source not in nodes or target not in nodes or any(dst == target for dst, _ in edges.get(source, ())):
        raise ValueError("candidate is not a missing same-source direction")
    start, end = nodes[source], nodes[target]
    nearby = sorted(((math.dist((start["x"], start["y"]), (n["x"], n["y"])), aid)
                     for aid, n in nodes.items() if aid != source))[:6]
    if target not in {aid for distance, aid in nearby if distance <= 7000}:
        raise ValueError("candidate exceeds nearest-six or 7000 distance")
    columns = ("connector_id", "from_x", "from_y", "from_z", "from_instance",
               "to_x", "to_y", "to_z", "to_instance")
    row = ("plunderous." + source.split(".")[2] + "." + target.split(".")[2],
           start["x"], start["y"], start["z"], 0, end["x"], end["y"], end["z"], 0)
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(columns)
        writer.writerow(row)
    print("PREPARED", start["zone"], "->", end["zone"], "distance", round(dict((aid, d) for d, aid in nearby)[target]))


def record(nodes, candidate_file, proof_file, ledger_file):
    candidate = read_tsv(candidate_file)
    proof = read_tsv(proof_file)
    if len(candidate) != 1 or len(proof) != 1 or candidate[0]["connector_id"] != proof[0]["connector_id"]:
        raise ValueError("candidate/proof mismatch")
    row, result = candidate[0], proof[0]
    matching = [(source, target) for source in nodes for target in nodes if source != target
                and (nodes[source]["x"], nodes[source]["y"], nodes[source]["z"],
                     nodes[target]["x"], nodes[target]["y"], nodes[target]["z"])
                == tuple(int(row[key]) for key in ("from_x", "from_y", "from_z", "to_x", "to_y", "to_z"))]
    if len(matching) != 1 or row["from_instance"] != "0" or row["to_instance"] != "0":
        raise ValueError("candidate is not an exact active anchor pair")
    source, target = matching[0]
    old = read_tsv(ledger_file) if ledger_file.exists() else []
    if any(r["from_anchor_id"] == source and r["to_anchor_id"] == target for r in old):
        raise ValueError("direction already checked")
    if len(old) >= 120:
        raise ValueError("120 directed check budget exhausted")
    columns = ("from_anchor_id", "to_anchor_id", "connector_id", "validation_status", "path_length",
               "path_segments", "required_buffer", "max_pathfind_buffer")
    combined = dict(from_anchor_id=source, to_anchor_id=target, **result)
    with ledger_file.open("w", encoding="utf-8", newline="\n") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(old + [combined])
    print("RECORDED", nodes[source]["zone"], "->", nodes[target]["zone"], result["validation_status"])


def proven_edges(edges, ledger):
    added = defaultdict(list, {source: list(rows) for source, rows in edges.items()})
    for row in ledger:
        if row["validation_status"] in ("VALID_DIRECT", "VALID_PATH"):
            added[row["from_anchor_id"]].append((row["to_anchor_id"], row["connector_id"]))
    for source in added:
        added[source].sort()
    return added


def eligible_neighbors(nodes, source):
    start = nodes[source]
    nearest = sorted(((math.dist((start["x"], start["y"]), (node["x"], node["y"])), aid)
                      for aid, node in nodes.items() if aid != source))[:6]
    return [(aid, distance) for distance, aid in nearest if distance <= 7000]


def components(nodes, edges):
    neighbors = defaultdict(set)
    for source, rows in edges.items():
        for target, _ in rows:
            neighbors[source].add(target)
            neighbors[target].add(source)
    labels = {}
    for anchor in sorted(nodes):
        if anchor in labels:
            continue
        queue = deque([anchor])
        while queue:
            current = queue.popleft()
            if current in labels:
                continue
            labels[current] = anchor
            queue.extend(sorted(neighbors[current] - labels.keys()))
    return labels


def inventory(nodes, edges, output):
    labels = components(nodes, edges)
    columns = ("anchor_id", "coverage_key", "spawn_zone", "level_min", "level_max", "x", "y", "z",
               "current_component", "is_start", "is_level40_goal")
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(columns)
        for aid in sorted(nodes):
            node = nodes[aid]
            writer.writerow((aid, node["coverage"], node["zone"], node["level_min"], node["level_max"],
                             node["x"], node["y"], node["z"], labels[aid],
                             str(aid in STARTS).lower(), str(aid in GOALS).lower()))
    print("INVENTORY", len(nodes), "components", len(set(labels.values())))


def optimistic_edges(nodes, edges, ledger):
    excluded = {(row["from_anchor_id"], row["to_anchor_id"]) for row in ledger
                if row["validation_status"] not in ("VALID_DIRECT", "VALID_PATH")}
    result = defaultdict(list, {aid: list(rows) for aid, rows in edges.items()})
    for source in nodes:
        existing = {target for target, _ in result[source]}
        for target, _ in eligible_neighbors(nodes, source):
            if target not in existing and (source, target) not in excluded:
                result[source].append((target, "OPTIMISTIC_UNPROVEN"))
        result[source].sort()
    return result


if __name__ == "__main__":
    module = Path(__file__).resolve().parents[2]
    nodes, edges = graph(module)
    if not set(STARTS + GOALS) <= nodes.keys():
        raise SystemExit("missing published start/goal")
    if len(sys.argv) == 5 and sys.argv[1] == "record":
        record(nodes, Path(sys.argv[2]), Path(sys.argv[3]), Path(sys.argv[4]))
        raise SystemExit
    if len(sys.argv) == 3 and sys.argv[1] == "inventory":
        inventory(nodes, edges, Path(sys.argv[2]))
        raise SystemExit
    ledger_path = Path(sys.argv[5]) if len(sys.argv) == 6 and sys.argv[1] == "prepare" else Path(sys.argv[2]) if len(sys.argv) == 3 and sys.argv[1] == "inspect" else None
    ledger = read_tsv(ledger_path) if ledger_path is not None and ledger_path.exists() else []
    edges = proven_edges(edges, ledger)
    if len(sys.argv) == 6 and sys.argv[1] == "prepare":
        if bfs(edges)[0] is not None:
            raise SystemExit("target already reachable; stop probing")
        if sys.argv[2] not in bfs(edges)[1]:
            raise SystemExit("candidate source is not reachable from start")
        if any(row["from_anchor_id"] == sys.argv[2] and row["to_anchor_id"] == sys.argv[3] for row in ledger):
            raise SystemExit("direction already checked")
        prepare(nodes, edges, sys.argv[2], sys.argv[3], Path(sys.argv[4]))
        raise SystemExit
    path, reached = bfs(edges)
    print("nodes=", len(nodes), "edges=", sum(map(len, edges.values())))
    print("reachable=", len(reached), "goal=", path[-1][1] if path else "NONE")
    if ledger and path is None:
        optimistic, possible = bfs(optimistic_edges(nodes, edges, ledger))
        print("optimistic_goal=", optimistic[-1][1] if optimistic else "NONE", "optimistic_reachable=", len(possible))
        for goal in GOALS:
            incoming = [(nodes[source]["zone"], round(distance), source in reached,
                         next((row["validation_status"] for row in ledger if row["from_anchor_id"] == source
                               and row["to_anchor_id"] == goal), "UNPROVEN"))
                        for source in nodes for target, distance in eligible_neighbors(nodes, source) if target == goal]
            print("GOAL_INBOUND", nodes[goal]["zone"], sorted(incoming))
    if path:
        for edge in path:
            print(*edge, sep="\t")
    else:
        for aid in sorted(nodes):
            node = nodes[aid]
            print("FRONTIER", aid, node["zone"], node["x"], node["y"], "reachable=" + str(aid in reached))
