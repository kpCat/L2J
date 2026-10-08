"""Read-only summary of TASK025 cohort-result.json. This is NOT an acceptance evaluator.

Output preserves the supplied pass flag and all actors; it labels observed symptoms,
not root causes. No rewards/counters or runtime state are changed. Standard library only.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
from typing import Any

MAX_INPUT_BYTES = 32 * 1024 * 1024

def flag(value: Any, default: bool = False) -> bool:
    if value is None:
        return default
    if type(value) is bool:
        return value
    if isinstance(value, str) and value.strip().lower() in ("true", "false"):
        return value.strip().lower() == "true"
    raise ValueError(f"Invalid evidence boolean: {value!r}")

def number(value: Any, default: float = 0.0) -> float:
    if value is None:
        return default
    if type(value) is bool:
        raise ValueError("Boolean cannot replace a numeric field")
    result = float(value)
    if not math.isfinite(result):
        raise ValueError("Evidence numeric field must be finite")
    return result

def summarize(rows: Any, scene: str = "unspecified") -> list[dict[str, Any]]:
    if not isinstance(rows, list):
        raise ValueError("Expected a cohort array")
    if len(rows) > 1024:
        raise ValueError("Cohort bound exceeded")
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("Every cohort row must be an object")
        profile = str(row.get("profileId", ""))
        if not profile.isdecimal() or int(profile) <= 0 or profile in seen:
            raise ValueError("Invalid/duplicate profile in one scene")
        seen.add(profile)
        last = row.get("last")
        if last is not None and not isinstance(last, dict):
            raise ValueError("last must be an object or null")
        sample = last if last is not None else {}
        observed: list[str] = []
        same = flag(row.get("sameEpoch"))
        missing = number(row.get("missingSamples"))
        if last is None or not same or missing > 0:
            observed.append("LIFECYCLE_OR_VISIBILITY_UNRESOLVED")
        if flag(sample.get("dead")):
            observed.append("DEAD_AT_LAST_SAMPLE")
        if flag(sample.get("pendingOwnedStore")):
            observed.append("OWNED_STORE_PENDING")
        if sample.get("nativeOwnerState") in ("SEALED", "DRAINING"):
            observed.append("OWNER_CLOSED_OBSERVATION")
        if flag(sample.get("nativeEvidenceOverflow")):
            observed.append("EVIDENCE_UNPROVEN")
        if "RESERVED" in str(sample.get("nativeOwnerWork", "")):
            observed.append("RESERVED_WORK_NOT_PROOF_OF_TIMEOUT")
        reason = str(sample.get("travelReason", ""))
        recovery = str(sample.get("visibleRecoveryReason", ""))
        if "deadline" in reason or "UNAVAILABLE" in recovery or sample.get("travelFailureReason"):
            observed.append("LOCAL_TRAVEL_TERMINAL")
        maximum_mp = number(sample.get("maxMp"))
        if maximum_mp > 0 and number(sample.get("mp")) / maximum_mp < 0.2:
            observed.append("LOW_MP_HEURISTIC_NOT_CAUSE")
        tail = number(row.get("tail120Rewards"))
        if tail <= 0:
            observed.append("NO_REWARD_IN_FINAL_120S")
        result.append({
            "scene": scene, "profileId": profile,
            "objectId": row.get("objectId"), "primary": flag(row.get("primary")),
            "reported_pass": flag(row.get("pass")), "same_epoch": same,
            "missing_samples": missing, "cycles": number(row.get("cycles")),
            "kills": number(row.get("kills")), "rewards": number(row.get("rewards")),
            "tail120_rewards": tail, "max_idle_seconds": number(row.get("maxIdleSeconds")),
            "owner_state": sample.get("nativeOwnerState", "UNKNOWN"),
            "runtime_reason": sample.get("runtimeReason", "UNKNOWN"),
            "travel_reason": reason, "recovery_reason": recovery,
            "observed_flags": observed, "root_cause": "UNKNOWN",
        })
    return result

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    all_rows = []
    sources = []
    for path in args.inputs:
        if path.stat().st_size > MAX_INPUT_BYTES:
            raise ValueError(f"Input too large: {path}")
        rows = json.loads(path.read_text(encoding="utf-8-sig"))
        all_rows.extend(summarize(rows, path.parent.name))
        sources.append(str(path.resolve()))
    payload = {"purpose": "OBSERVATION_SUMMARY_NOT_EVALUATOR", "inputs": sources,
               "rows": all_rows, "server_pass": "NOT_COMPUTED"}
    # Never overwrite old evidence or treat this summary as a new product pass.
    with args.output.open("x", encoding="utf-8", newline="\n") as output:
        json.dump(payload, output, ensure_ascii=False, indent=2)
        output.write("\n")
    print(f"Summary created: {args.output}; actors={len(all_rows)}; no PASS was computed")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
