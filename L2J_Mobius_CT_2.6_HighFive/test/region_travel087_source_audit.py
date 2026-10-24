"""Source-only ingress/GK candidates for the existing native Geo probe; no publication."""
import csv
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def run(module, output):
    module, output = Path(module).resolve(), Path(output).resolve()
    if output.is_relative_to(module.parent):
        raise ValueError("External private output required")
    output.mkdir(exist_ok=False)
    sys.path.insert(0, str(module / "tools/phantom-world-data"))
    import travel_backbone as source
    facts = source.extract_facts(module / "dist/game/data")
    normal = {row["teleporter_spawn_key"]: row for row in facts
              if row["teleport_type"] == "NORMAL" and row["availability_class"] == "NORMAL"}
    starts = {"population.ingress.human-fighter.01", "population.ingress.elf.01",
              "population.ingress.orc.01", "giran.city.center"}
    candidates, provenance = [], []
    for path in sorted((module / "dist/game/data/phantoms/topology").glob("*.xml")):
        for anchor in ET.parse(path).getroot().findall("anchor"):
            if anchor.get("id") not in starts:
                continue
            x, y, z = (int(anchor.get(axis)) for axis in ("x", "y", "z"))
            nearest = min(normal.values(), key=lambda r: (
                (x-int(r["teleporter_spawn_x"]))**2 + (y-int(r["teleporter_spawn_y"]))**2,
                r["teleporter_spawn_key"]))
            key = anchor.get("id")
            candidates.append([key, x, y, z, 0, nearest["teleporter_spawn_x"],
                               nearest["teleporter_spawn_y"], nearest["teleporter_spawn_z"], 0])
            provenance.append({"candidate": key, "status": "SOURCE_CANDIDATE_NOT_VALIDATED",
                "anchor_source": path.relative_to(module).as_posix(), "npc": nearest["teleporter_npc_id"],
                "spawn_key": nearest["teleporter_spawn_key"], "teleporter_source": nearest["source_path"],
                "spawn_source": nearest["spawn_source_path"]})
    if len(candidates) != len(starts):
        raise ValueError("Exact source starts missing")
    target = output / "ingress-gk-candidates.tsv"
    with target.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(["connector_id", "from_x", "from_y", "from_z", "from_instance",
                         "to_x", "to_y", "to_z", "to_instance"])
        writer.writerows(sorted(candidates))
    sources = sorted({r[k] for r in provenance for k in ("anchor_source", "teleporter_source", "spawn_source")})
    hashes = {name: hashlib.sha256((module / ("dist/game/" + name if name.startswith("data/") else name)).read_bytes()).hexdigest()
              for name in sources}
    (output / "source-provenance.json").write_text(json.dumps({"candidates": provenance,
        "source_sha256": hashes, "candidates_sha256": hashlib.sha256(target.read_bytes()).hexdigest()},
        ensure_ascii=False, sort_keys=True, indent=2)+"\n", encoding="utf-8")
    print("TASK087_SOURCE_CANDIDATES=4; nativeAdmission=NOT_RUN; publication=NONE")


if __name__ == "__main__":
    run(*sys.argv[1:])
