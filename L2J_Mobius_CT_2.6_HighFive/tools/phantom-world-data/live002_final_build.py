"""Deterministic LIVE-002 package rebuild from fixed hermetic Geo evidence."""

import hashlib
import json
from pathlib import Path

import live002_content_graph as graph
import live002_content_publish as publish
import live002_final_content_access as access
import live002_final_content_validator as validate
import live002_imperial_oversized as imperial
import live002_ruins_publish as ruins


def rebuild(module):
    ruins.publish(module)
    access.prepare(module)
    graph.prepare(module)
    imperial.prepare(module)
    publish.publish(module)
    validate.verify(module)
    registry = module / "docs/phantoms/live-world"
    game = module / "dist/game"
    files = [
        module / "java/org/l2jmobius/gameserver/phantoms/background/PhantomNormalGatekeeperTravel.java",
        game / "data/phantoms/topology/high-five-generated-06.xml",
        game / "data/phantoms/travel/high-five-normal-gk.xml",
        *(game / "data/phantoms/topology" / name for name in publish.SHARDS.values()),
        *(registry / name for name in ("TARGETED_TRAVEL_CONNECTORS.tsv", "TARGETED_TRAVEL_CONNECTORS_MANIFEST.json", "FINAL_CONTENT_ACCESS_PLAN.tsv", "FINAL_CONTENT_GEO_CANDIDATES.tsv", "FINAL_CONTENT_IMPERIAL_OVERSIZED_HOPS.tsv", "FINAL_CONTENT_FARM_ANCHORS.tsv", "CONDITIONAL_CONTENT_ACCESS.tsv", "FINAL_CONTENT_ACCESS_PROOF.tsv", "LIVE002_RUINS_FINAL.tsv", "LIVE002_FINAL_CONTENT_VALIDATION.tsv")),
        *(game / "data/phantoms/evidence" / name for name in ("live002-content-anchor-candidates.tsv", "live002-content-anchor-geo.tsv", "live002-content-geo-input.tsv", "live002-content-geo-proof.tsv", "live002-imperial-oversized-01.tsv", "live002-imperial-oversized-hops-input.tsv", "live002-imperial-oversized-hops-proof.tsv")),
        *registry.glob("FINAL_CONTENT_*_BRIDGE_*_CANDIDATES.tsv"),
        *registry.glob("FINAL_CONTENT_*_SEED_RETRY_CANDIDATES.tsv"),
        *(game / "data/phantoms/evidence").glob("live002-content-*-bridge-*-*.tsv"),
        *(game / "data/phantoms/evidence").glob("live002-content-*-seed-retry-*.tsv"),
    ]
    if len(files) != len(set(files)) or any(not file.is_file() or file.stat().st_size == 0 for file in files):
        raise RuntimeError("Determinism artifact inventory incomplete")
    digests = {file.relative_to(module).as_posix(): hashlib.sha256(file.read_bytes()).hexdigest() for file in sorted(files)}
    output = registry / "LIVE002_DETERMINISM.json"
    output.write_text(json.dumps(digests, sort_keys=True, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("LIVE002_REBUILT artifacts=" + str(len(digests)) + " aggregate_sha=" + hashlib.sha256(output.read_bytes()).hexdigest())


if __name__ == "__main__":
    rebuild(Path(__file__).resolve().parents[2])
