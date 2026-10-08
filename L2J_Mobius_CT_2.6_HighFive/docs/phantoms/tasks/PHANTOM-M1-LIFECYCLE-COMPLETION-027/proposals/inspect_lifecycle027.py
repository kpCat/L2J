"""Read-only extractor. Keeps the original cohort verdict; never certifies M1.

python inspect_lifecycle027.py --cohort cohort-result.json --log java0.log
Input: existing task026 JSON arrays and native logs. Output: stdout JSON only.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Any


def boolean(value: Any) -> bool | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return value
    if isinstance(value, str) and value.lower() in ("true", "false"):
        return value.lower() == "true"
    raise ValueError(f"not a boolean: {value!r}")


def classify(row: dict[str, Any]) -> str:
    last = row.get("last")
    if last is None:
        return "ABSENT_OR_NOT_CAPTURED_CAUSE_UNKNOWN"
    if not isinstance(last, dict):
        raise ValueError("last must be an object or null")
    if last.get("nativeFirstIncident") or last.get("nativeOwnerFailure"):
        return "NATIVE_INCIDENT_RECORDED"
    if boolean(last.get("dead")) is True:
        return "VISIBLE_DEAD_NEEDS_LIFECYCLE"
    if last.get("nativeOwnerState") in ("SEALED", "DRAINING"):
        return "CONTROL_OR_DRAIN_STATE_NOT_A_ROOT_CAUSE"
    reason = " ".join(str(last.get(k, "")) for k in
                      ("runtimeReason", "travelReason", "travelFailureReason"))
    if "travel.route_absent" in reason or "travel.journey_deadline" in reason:
        return "VISIBLE_ROUTE_BLOCK_RECORDED"
    if boolean(last.get("nativeEvidenceOverflow")) is True:
        return "OBSERVATION_UNPROVEN_NOT_AUTOMATIC_GAMEPLAY_STOP"
    return "LIVE_OTHER_NEEDS_TIMELINE"


def summarize(rows: Any) -> dict[str, Any]:
    if not isinstance(rows, list) or not all(isinstance(r, dict) for r in rows):
        raise ValueError("cohort input must be a JSON array of records")
    result = []
    for i, row in enumerate(rows):
        if "profileId" not in row:
            raise ValueError(f"row {i} lacks profileId")
        last = row.get("last") or {}
        result.append({
            "row": i,
            "profileId": str(row["profileId"]),
            "objectId": row.get("objectId"),
            "originalPass": boolean(row.get("pass")),
            "sameEpoch": boolean(row.get("sameEpoch")),
            "missingSamples": row.get("missingSamples"),
            "classification": classify(row),
            "cycles": row.get("cycles"),
            "tail120Rewards": row.get("tail120Rewards"),
            "nativeOwnerState": last.get("nativeOwnerState"),
            "pendingOwnedStore": boolean(last.get("pendingOwnedStore")),
        })
    return {
        "purpose": "READ_ONLY_CLASSIFICATION_NOT_ACCEPTANCE",
        "rows": result,
        "denominator": len(rows),
        "originalPassCount": sum(r["originalPass"] is True for r in result),
        "originalUnknownCount": sum(r["originalPass"] is None for r in result),
        "classes": dict(Counter(r["classification"] for r in result)),
        "m1Verdict": "NOT_EVALUATED",
    }


STAMP = re.compile(r"^(\d{4}\.\d{2}\.\d{2} \d{2}:\d{2}:\d{2},\d{3})")
MARKERS = (
    ("Initial subsystem drain remains incomplete", "INITIAL_INCOMPLETE"),
    ("Final subsystem drain is incomplete", "FINAL_INCOMPLETE"),
    ("Initial subsystem drain completed", "INITIAL_COMPLETED"),
    ("ThreadPool: Shutting down all thread pools", "POOL_SHUTDOWN"),
    ("NATIVE_WORK_DRAIN_INTERRUPTED", "DRAIN_INTERRUPTED"),
)


def shutdown_events(text: str) -> list[dict[str, Any]]:
    events = []
    for line_no, line in enumerate(text.splitlines(), 1):
        stamp = STAMP.match(line)
        if stamp is None:
            continue
        for needle, event in MARKERS:
            if needle not in line:
                continue
            dt = datetime.strptime(stamp.group(1), "%Y.%m.%d %H:%M:%S,%f")
            duration = re.search(r"\((\d+)ms\)", line)
            count = re.search(r"retainedMaterializationEntries=(\d+)", line)
            state = re.search(r"materializationServiceState=([A-Z_]+)", line)
            events.append({"line": line_no, "asLoggedTime": dt.isoformat(timespec="milliseconds"),
                           "timezone": "UNSPECIFIED_AS_LOGGED", "event": event,
                           "reportedMilliseconds": int(duration.group(1)) if duration else None,
                           "retainedEntries": int(count.group(1)) if count else None,
                           "materializationState": state.group(1) if state else None})
    return events


def read(path: Path) -> tuple[str, str]:
    raw = path.read_bytes()
    return raw.decode("utf-8-sig", errors="strict"), hashlib.sha256(raw).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cohort", type=Path, action="append", default=[])
    parser.add_argument("--log", type=Path, action="append", default=[])
    args = parser.parse_args()
    if not args.cohort and not args.log:
        parser.error("provide at least one --cohort or --log")
    outputs = []
    try:
        for path in args.cohort:
            text, digest = read(path)
            outputs.append({"path": str(path), "sha256": digest, "summary": summarize(json.loads(text))})
        for path in args.log:
            text, digest = read(path)
            outputs.append({"path": str(path), "sha256": digest, "events": shutdown_events(text)})
    except (OSError, UnicodeError, ValueError) as ex:
        parser.exit(2, f"Invalid input: {ex}\n")
    print(json.dumps(outputs, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
