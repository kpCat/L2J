"""Bounded read-only payload inventory. Missing links remain OPEN, never reconstructed."""
import argparse
import base64
import hashlib
import json
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("--receipts", type=Path, required=True)
p.add_argument("--stream", type=Path, required=True)
p.add_argument("--sql", type=Path, required=True)
p.add_argument("--output", type=Path, required=True)
a = p.parse_args()
allowed = Path(__file__).resolve().parent.parent / "evidence"
for path in (a.receipts, a.stream, a.sql, a.output):
    if not path.resolve().is_relative_to(allowed):
        raise ValueError("Exact TASK032 evidence required")

def props(path):
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8-sig").splitlines() if "=" in line)

states = {}
for line in (a.sql / "durable-hex.tsv").read_text(encoding="utf-8-sig").splitlines():
    fields = line.split("\t")
    if fields[2] == "background.state":
        states[int(fields[0])] = (int(fields[1]), hashlib.sha256(bytes.fromhex(fields[3])).hexdigest())
rows = []
for receipt in sorted(a.receipts.glob("*-finalized.properties")):
    native = props(receipt)
    pid = int(native["profileId"])
    if native["checkpointStage"] != "FINALIZED" or native["nativeOwnerSealed"] != "true":
        raise ValueError("Exact native FINALIZED required")
    payload = base64.b64decode(native["fullStateBase64"], validate=True)
    digest = hashlib.sha256(payload).hexdigest()
    if digest != native["afterPayloadSha256"]:
        raise ValueError("Actual native payload hash differs")
    version = int(native["preparedRowVersion"]) + 1
    links = []
    for path in sorted(a.stream.glob(f"{pid}-*.properties")):
        edge = props(path)
        if edge.get("kind") not in ("PROJECTION_COMMIT", "BACKGROUND_COMMIT", "BACKGROUND_REPLAY"):
            continue
        before = int(edge["beforeRowVersion"])
        if before < version:
            continue
        for side in ("before", "after"):
            if hashlib.sha256(bytes.fromhex(edge[side + "PayloadHex"])).hexdigest() != edge[side + "PayloadSha256"]:
                raise ValueError("Observed edge payload hash differs")
        links.append(dict(file=path.name, kind=edge["kind"], beforeVersion=before,
                          beforeHash=edge["beforePayloadSha256"], afterHash=edge["afterPayloadSha256"],
                          action=edge.get("actionKind"), worldPresent=edge.get("worldPresent")))
    target_version, target_hash = states[pid]
    rows.append(dict(profileId=pid, epoch=int(native["epoch"]), nativeVersion=version,
                     nativeHash=digest, sqlVersion=target_version, sqlHash=target_hash,
                     directExact=(version == target_version and digest == target_hash),
                     observedLinks=links, fullLineageClaimed=False))
with a.output.open("x", encoding="utf-8") as stream:
    json.dump(dict(kind="ACTUAL_PAYLOAD_LINEAGE_INVENTORY", rows=rows), stream, indent=2)
print(json.dumps([dict(profileId=r["profileId"], nativeVersion=r["nativeVersion"],
                      sqlVersion=r["sqlVersion"], directExact=r["directExact"], links=len(r["observedLinks"])) for r in rows]))
