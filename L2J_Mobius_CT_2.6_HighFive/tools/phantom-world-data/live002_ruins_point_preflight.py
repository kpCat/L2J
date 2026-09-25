"""Build a temporary node-only snapshot for the bounded normalized Ruins point proof."""

import argparse
import csv
from pathlib import Path
import xml.etree.ElementTree as ET


KEY = "b729b4b54a41170f2da2aa85307ff726b04e894296e26e9d3d85be8899dbd4f3"
SOURCE = "data/spawns/Others/18_22.xml"
NATIVE = ("-33539", "137701", "-3479")
NODE = "generated.farm." + KEY[:24]
SHARDS = ("high-five-core.xml", "high-five-siege.xml", *(f"high-five-generated-{index:02}.xml" for index in range(1, 6)))


def prepare(module, output, geo_z):
    registry = module / "docs/phantoms/live-world"
    with (registry / "WORLD_COVERAGE.tsv").open(encoding="utf-8", newline="") as stream:
        coverage = next(row for row in csv.DictReader(stream, delimiter="\t") if row["coverage_key"] == KEY)
    if any(coverage[field] != value for field, value in {"source_path": SOURCE, "source_group": "18_22", "classification": "ORDINARY_WORLD", "status": "READY_STATIC", "geometry_kind": "POINT", "instance_id": "0"}.items()):
        raise RuntimeError("Ruins ordinary coverage drift")
    spawn = ET.parse(module / "dist/game" / SOURCE).getroot()
    group = next(item for item in spawn.findall("spawn") if item.get("name") == "18_22")
    if not any(npc.get("id") == "20059" and tuple(npc.get(axis) for axis in ("x", "y", "z")) == NATIVE for npc in group.findall("npc")):
        raise RuntimeError("Native Ruins spawn drift")
    stats = ET.parse(module / "dist/game/data/stats/npcs/20000-20099.xml").getroot()
    npc = next(item for item in stats.findall("npc") if item.get("id") == "20059")
    if (npc.get("type"), npc.get("level"), npc.get("name")) != ("Monster", "22", "Hungry Eye"):
        raise RuntimeError("Native Monster drift")
    radius = max(1, abs(int(NATIVE[2]) - geo_z))
    if radius > 4:
        raise RuntimeError("NO_BOUNDED_NORMALIZED_POINT_FARM")
    output.mkdir(parents=True, exist_ok=True)
    source_dir = module / "dist/game/data/phantoms/topology"
    for shard in SHARDS:
        source_root = ET.parse(source_dir / shard).getroot()
        reduced = ET.Element("topology", source_root.attrib)
        for node in source_root.findall("node"):
            reduced.append(node)
        ET.ElementTree(reduced).write(output / shard, encoding="utf-8", xml_declaration=True)
    root = ET.Element("topology", {"schemaVersion": "1", "datasetId": "high-five-core", "datasetVersion": "4"})
    node = ET.SubElement(root, "node", {"id": NODE, "kind": "FARMING_AREA", "instanceId": "0", "form": "POINT_RADIUS", "x": NATIVE[0], "y": NATIVE[1], "z": NATIVE[2], "radius": str(radius), "tags": "normalized-point-farm,outdoor-farming"})
    ET.SubElement(node, "source", {"path": SOURCE})
    anchor = ET.SubElement(root, "anchor", {"id": NODE + ".anchor", "role": "FARMING", "nodeId": NODE, "x": NATIVE[0], "y": NATIVE[1], "z": str(geo_z), "instanceId": "0", "tolerance": "0", "npcId": "20059", "tags": "normalized-point-farm,outdoor-farming"})
    ET.SubElement(anchor, "source", {"path": SOURCE})
    ET.ElementTree(root).write(output / "high-five-generated-06.xml", encoding="utf-8", xml_declaration=True)
    return NODE, radius


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--geo-z", type=int, required=True)
    args = parser.parse_args()
    print("TEMP_NORMALIZED_POINT", *prepare(Path(__file__).resolve().parents[2], args.output_dir, args.geo_z))
