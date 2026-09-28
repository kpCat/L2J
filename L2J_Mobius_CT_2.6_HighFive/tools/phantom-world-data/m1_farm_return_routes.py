"""Derive and explicitly promote native-proven return directions of walk edges.

NORMAL GK transitions are never reversed. Every added direction is independently
checked by the existing native GeoEngine/static collision probe.
"""

import argparse
import csv
from collections import defaultdict, deque
import json
import math
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from m1_farm_route_publish import digest, native_anchors, proven_path, publish, read_tsv, sha, write_tsv


def merge_proof(base, refined, destination):
    """Carry validated proof accounting forward for a finer local frontier pass."""
    try:
        proven_path(refined, base)
    except ValueError as failure:
        if str(failure) != "No source-to-target native-proven corridor":
            raise
    destination.mkdir(parents=True, exist_ok=True)
    for name, key in (("corridor-points.tsv", "id"), ("height-proof.tsv", "point_id"),
                      ("adaptive-geo-input.tsv", "connector_id"), ("adaptive-geo-proof.tsv", "connector_id")):
        rows = read_tsv(base / name) + read_tsv(refined / name)
        combined = {}
        for row in rows:
            if row[key] in combined and combined[row[key]] != row:
                raise ValueError("Refinement changed an existing native proof row")
            combined[row[key]] = row
        write_tsv(destination / name, tuple(rows[0]), [combined[identity] for identity in sorted(combined)])


def onward_candidates(module, work):
    anchors = native_anchors(module)
    adjacent = defaultdict(set)
    for path in sorted((module / "dist/game/data/phantoms/topology").glob("*.xml")):
        for edge in ET.parse(path).getroot().findall("edge"):
            if edge.get("mode") != "BACKGROUND" or edge.get("backgroundEligible") != "true":
                continue
            start, end = edge.get("fromAnchorId"), edge.get("toAnchorId")
            if start and end:
                adjacent[start].add(end)
                if edge.get("bidirectional") == "true":
                    adjacent[end].add(start)
    legs = ET.parse(module / "dist/game/data/phantoms/travel/high-five-normal-gk.xml").getroot().findall("leg")
    departures = {leg.get("fromAnchorId") for leg in legs}
    arrivals = {leg.get("toAnchorId") for leg in legs}
    for leg in legs:
        adjacent[leg.get("fromAnchorId")].add(leg.get("toAnchorId"))

    reverse = defaultdict(set)
    for start, ends in adjacent.items():
        for end in ends:
            reverse[end].add(start)
    connected, pending = set(departures), deque(sorted(departures))
    while pending:
        for start in sorted(reverse[pending.popleft()]):
            if start not in connected:
                connected.add(start)
                pending.append(start)
    connected.intersection_update(anchors)

    def reachable(start):
        visited, pending = {start}, deque([start])
        while pending:
            for end in sorted(adjacent[pending.popleft()]):
                if end not in visited:
                    visited.add(end)
                    pending.append(end)
        return frozenset(visited)

    components = defaultdict(list)
    for arrival in sorted(arrivals):
        component = reachable(arrival)
        if not component.intersection(departures):
            components[component].append(arrival)
    rows = []
    for component, members in components.items():
        # A nearest connected farm can lie across a mountain. Keep a bounded
        # alternative to a native NORMAL departure instead of treating straight
        # line distance as evidence that the first corridor is traversable.
        alternatives = set()
        for destinations in (connected, departures.intersection(anchors)):
            choices = ((math.dist(tuple(int(anchors[start].get(axis)) for axis in "xyz"),
                                  tuple(int(anchors[end].get(axis)) for axis in "xyz")), start, end)
                       for start in sorted(component.intersection(anchors)) for end in sorted(destinations))
            alternatives.add(min(choices))
        for distance, start, end in sorted(alternatives):
            rows.append(dict(corridor_id="onward." + digest(start, end), from_anchor=start,
                             to_anchor=end, distance=int(distance), arrivals="|".join(members)))
    work.mkdir(parents=True, exist_ok=True)
    write_tsv(work / "onward-corridors.tsv", ("corridor_id", "from_anchor", "to_anchor", "distance", "arrivals"),
              sorted(rows, key=lambda row: (row["distance"], row["corridor_id"])))
    print(f"M1_ONWARD_COMPONENTS arrivals={len(arrivals)} departures={len(departures)} missing={len(components)} candidates={len(rows)}")
    return rows


def repair_onward(module, work):
    """Repair every missing NORMAL-arrival return component through the same native derivation."""
    planned = onward_candidates(module, work)
    results = []
    candidate_tool = module / "tools/phantom-world-data/m1_farm_route_candidates.py"
    probe_tool = module / "tools/phantom-world-data/Probe-FinalGeo.ps1"

    def probe(start, end, destination, options):
        required = ("height-proof.tsv", "corridor-points.tsv", "adaptive-geo-input.tsv", "adaptive-geo-proof.tsv")
        if all((destination / name).is_file() for name in required):
            anchors = native_anchors(module)
            points = {point["id"]: point for point in read_tsv(destination / "corridor-points.tsv")}
            if any(tuple(points[key][axis] for axis in "xyz") != tuple(anchors[identity].get(axis) for axis in "xyz")
                   for key, identity in (("source", start), ("target", end))):
                raise ValueError("Reusable corridor endpoints changed")
            previous = Path(options[options.index("--refine-from") + 1]) if "--refine-from" in options else None
            try:
                proven_path(destination, previous)
            except ValueError as failure:
                if str(failure) != "No source-to-target native-proven corridor":
                    raise
            print(f"M1_ONWARD_REUSE {destination}", flush=True)
            return
        for mode, switch in (("heights", "-HeightOnly"), ("routes", "-ProofOnly")):
            subprocess.run([sys.executable, "-B", str(candidate_tool), start, end, mode,
                            str(destination), *options], cwd=module, check=True)
            subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                            str(probe_tool), "-WorkDirectory", str(destination), "-SkipCompile", switch],
                           cwd=module, check=True)

    for index, row in enumerate(sorted(planned, key=lambda value: (value["distance"], value["corridor_id"])), 1):
        start, end = row["from_anchor"], row["to_anchor"]
        current = onward_candidates(module, work / "current")
        if not any(set(row["arrivals"].split("|")).intersection(value["arrivals"].split("|")) for value in current):
            results.append(dict(row, status="CONNECTED_BY_PRIOR_CORRIDOR", steps=0))
            write_tsv(work / "onward-results.tsv",
                      ("corridor_id", "from_anchor", "to_anchor", "distance", "arrivals", "status", "steps"), results)
            continue
        print(f"M1_ONWARD_BEGIN {index}/{len(planned)} {row['corridor_id']}", flush=True)
        corridor = work / row["corridor_id"]
        published = False
        for step in (0, 2048):
            base = corridor / ("native" if step == 0 else "grid")
            options = [] if step == 0 else ["--grid-step", str(step), "--grid-layers"]
            probe(start, end, base, options)
            try:
                _, path = proven_path(base)
            except ValueError as failure:
                if str(failure) != "No source-to-target native-proven corridor":
                    raise
            else:
                publish(module, base, start, None, end)
                results.append(dict(row, status="NATIVE_PUBLISHED", steps=len(path)))
                published = True
                break
            refined = corridor / ("native-refined" if step == 0 else "grid-refined")
            probe(start, end, refined, ["--grid-step", "1024", "--grid-layers", "--refine-from", str(base)])
            try:
                _, path = proven_path(refined, base)
            except ValueError as failure:
                if str(failure) != "No source-to-target native-proven corridor":
                    raise
            else:
                publish(module, refined, start, None, end, base)
                results.append(dict(row, status="NATIVE_PUBLISHED", steps=len(path)))
                published = True
                break
        if not published:
            merged = corridor / "grid-merged"
            merge_proof(corridor / "grid", corridor / "grid-refined", merged)
            fine = corridor / "grid-fine"
            probe(start, end, fine, ["--grid-step", "512", "--grid-layers", "--refine-from", str(merged)])
            try:
                _, path = proven_path(fine, merged)
            except ValueError as failure:
                if str(failure) != "No source-to-target native-proven corridor":
                    raise
            else:
                publish(module, fine, start, None, end, merged)
                results.append(dict(row, status="NATIVE_PUBLISHED", steps=len(path)))
                published = True
        if not published:
            merged_frontiers = corridor / "grid-fine-merged"
            merge_proof(merged, fine, merged_frontiers)
            frontiers = corridor / "grid-frontiers"
            probe(start, end, frontiers, ["--grid-step", "512", "--grid-layers", "--frontier-count", "4", "--refine-from", str(merged_frontiers)])
            try:
                _, path = proven_path(frontiers, merged_frontiers)
            except ValueError as failure:
                if str(failure) != "No source-to-target native-proven corridor":
                    raise
            else:
                publish(module, frontiers, start, None, end, merged_frontiers)
                results.append(dict(row, status="NATIVE_PUBLISHED", steps=len(path)))
                published = True
        if not published:
            results.append(dict(row, status="NO_NATIVE_CORRIDOR", steps=0))
        write_tsv(work / "onward-results.tsv",
                  ("corridor_id", "from_anchor", "to_anchor", "distance", "arrivals", "status", "steps"), results)
    remaining = onward_candidates(module, work / "final")
    print(f"M1_ONWARD_FINISHED planned={len(planned)} remaining={len({row['arrivals'] for row in remaining})}", flush=True)


def derive(module):
    anchors = native_anchors(module)
    edges = [edge for path in sorted((module / "dist/game/data/phantoms/topology").glob("*.xml"))
             for edge in ET.parse(path).getroot().findall("edge")
             if edge.get("mode") == "BACKGROUND" and edge.get("backgroundEligible") == "true"
             and edge.get("fromAnchorId") and edge.get("toAnchorId")]
    directions = {(edge.get("fromAnchorId"), edge.get("toAnchorId")) for edge in edges}
    rows, facts = {}, {}
    for edge in edges:
        start, end = edge.get("toAnchorId"), edge.get("fromAnchorId")
        if (start, end) in directions or edge.get("bidirectional") == "true":
            continue
        identity = "m1.return." + digest(start, end)
        a, b = anchors[start], anchors[end]
        rows[identity] = dict(connector_id=identity,
                             **{"from_" + axis: a.get(axis) for axis in "xyz"}, from_instance="0",
                             **{"to_" + axis: b.get(axis) for axis in "xyz"}, to_instance="0")
        facts[identity] = (a, b, edge)
    return rows, facts


def run(module, work, mode):
    if mode == "repair-onward":
        repair_onward(module, work)
        return
    if mode == "onward-candidates":
        onward_candidates(module, work)
        return
    rows, facts = derive(module)
    work.mkdir(parents=True, exist_ok=True)
    columns = ("connector_id", "from_x", "from_y", "from_z", "from_instance",
               "to_x", "to_y", "to_z", "to_instance")
    if mode == "candidates":
        write_tsv(work / "adaptive-geo-input.tsv", columns, [rows[key] for key in sorted(rows)])
        print(f"M1_RETURN_CANDIDATES directions={len(rows)}")
        return
    candidates = {row["connector_id"]: row for row in read_tsv(work / "adaptive-geo-input.tsv")}
    proofs = read_tsv(work / "adaptive-geo-proof.tsv")
    if rows != candidates or len(proofs) != len(rows) or {row["connector_id"] for row in proofs} != rows.keys():
        raise ValueError("Native return proof accounting or source changed")
    shard = module / "dist/game/data/phantoms/topology/high-five-generated-07.xml"
    root = ET.parse(shard).getroot()
    registry = module / "docs/phantoms/live-world"
    proof_file = registry / "M1_FARM_ROUTE_PROOF.tsv"
    published = {row["edge_id"]: row for row in read_tsv(proof_file)}
    count = 0
    for proof in proofs:
        if proof["validation_status"] not in ("VALID_DIRECT", "VALID_PATH"):
            continue
        if proof["collision_proof"] != "STATIC_XML_CLEAR" or int(proof["path_length"]) <= 0 or int(proof["path_segments"]) <= 0:
            raise ValueError("Incomplete native return proof")
        identity = proof["connector_id"]
        a, b, original = facts[identity]
        refs = sorted({source.get("path") for element in (a, b, original) for source in element.findall("source")})
        if not refs or any(not value.startswith(("data/", "config/")) or not (module / "dist/game" / value).is_file() for value in refs):
            raise ValueError("Return direction lacks native source references")
        edge = ET.SubElement(root, "edge", dict(id=identity, fromNodeId=a.get("nodeId"), toNodeId=b.get("nodeId"),
                            mode="BACKGROUND", bidirectional="false", baseCost="1",
                            baseTravelMillis=str(max(1000, int(proof["path_length"]) * 10)),
                            backgroundEligible="true", channels="COMBAT,TARGETABILITY",
                            fromAnchorId=a.get("id"), toAnchorId=b.get("id")))
        for value in refs:
            ET.SubElement(edge, "source", dict(path=value))
        published[identity] = dict(edge_id=identity, from_anchor=a.get("id"), to_anchor=b.get("id"),
                                   candidate_id=identity, validation_status=proof["validation_status"],
                                   path_length=proof["path_length"], path_segments=proof["path_segments"],
                                   source_refs="|".join(refs))
        count += 1
    ET.indent(root, space="\t")
    xml = b'<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(root, encoding="utf-8") + b"\n"
    if len(xml) >= 4 * 1024 * 1024:
        raise ValueError("Return shard exceeds topology loader bound")
    temporary = shard.with_suffix(".xml.tmp")
    temporary.write_bytes(xml)
    temporary.replace(shard)
    write_tsv(proof_file, ("edge_id", "from_anchor", "to_anchor", "candidate_id", "validation_status",
                           "path_length", "path_segments", "source_refs"), [published[key] for key in sorted(published)])
    sources = sorted({value for row in published.values() for value in row["source_refs"].split("|")})
    manifest = dict(schema="PHANTOM-LIVE-M1-FINAL-LIVING-WORLD-003/1", hash_policy="UTF-8, LF-normalized before SHA-256", topology_sha256=sha(shard),
                    proof_sha256=sha(proof_file), source_sha256={value: sha(module / "dist/game" / value) for value in sources})
    (registry / "M1_FARM_ROUTE_MANIFEST.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"M1_RETURN_PUBLISHED directions={count} candidates={len(rows)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("candidates", "promote", "onward-candidates", "repair-onward"))
    parser.add_argument("--module", type=Path, required=True)
    parser.add_argument("--work", type=Path, required=True)
    args = parser.parse_args()
    run(args.module.resolve(), args.work.resolve(), args.mode)
