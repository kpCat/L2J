"""CONTINUITY_V2 read-only evaluator. Native outcomes and every enrolled ID remain visible.

Epoch transitions require separate immutable lineage proof; this evaluator never invents it.
"""
import argparse
import hashlib
import json
from pathlib import Path


COUNTERS = ("FarmCycleSequence", "KillSequence", "RewardSequence", "DamageSequence",
            "TargetSequence", "ExpGained", "SpGained")


def native(row, suffix):
    return int(row["current.native" + suffix])


def segment_result(points, end):
    first, last = points[0][1], points[-1][1]
    delta = {key: native(last, key) - native(first, key) for key in COUNTERS}
    progress_at = points[0][0]
    max_idle = 0.0
    previous = first
    for elapsed, row in points[1:]:
        useful = any(native(row, key) > native(previous, key) for key in ("DamageSequence", "FarmCycleSequence"))
        useful |= native(row, "RewardSequence") > native(previous, "RewardSequence") and native(row, "ExpGained") > native(previous, "ExpGained")
        if useful:
            max_idle = max(max_idle, elapsed - progress_at)
            progress_at = elapsed
        previous = row
    max_idle = max(max_idle, points[-1][0] - progress_at)
    tail = [row for elapsed, row in points if elapsed <= end - 120]
    tail_rewards = native(last, "RewardSequence") - native(tail[-1], "RewardSequence") if tail else 0
    return {"epoch": int(first["current.nativeEvidenceEpoch"]), "from": points[0][0], "to": points[-1][0],
            "delta": delta, "cycles": delta["FarmCycleSequence"], "maxIdleSeconds": max_idle,
            "tail120Rewards": tail_rewards}


def evaluate(baseline, frames, primaries, run, seconds):
    ids = [str(row["profileId"]) for row in baseline]
    problems = []
    if not 4 <= len(ids) <= 8 or len(set(ids)) != len(ids):
        problems.append("BASELINE_COUNT_OR_DUPLICATE")
    if len(set(primaries)) != 2 or not set(primaries) <= set(ids):
        problems.append("PRIMARY_IDENTITY")
    if not 360 <= seconds <= 420:
        problems.append("SCENE_LENGTH")
    if not frames or float(frames[0]["elapsedSeconds"]) > 5 or float(frames[-1]["elapsedSeconds"]) < seconds - 5:
        problems.append("SCENE_BOUNDARY_UNPROVEN")
    grouped = {identity: [] for identity in ids}
    previous = None
    for frame in frames:
        observer = frame["observer"]
        if observer.get("runId") != run or observer.get("sessionState") != "RUNNING" or not observer.get("present"):
            problems.append("OBSERVER_IDENTITY")
        if previous is not None:
            gap = (int(frame["sampleNanos"]) - int(previous["sampleNanos"])) / 1e9
            if not 0 < gap <= 5:
                problems.append("TELEMETRY_GAP")
        previous = frame
        actors = frame["actors"]
        if len(actors) != len(ids) or {str(row["profileId"]) for row in actors} != set(ids):
            problems.append("ENROLLED_IDENTITY_MISSING_OR_REPLACED")
        for row in actors:
            identity = str(row["profileId"])
            if identity in grouped:
                grouped[identity].append((float(frame["elapsedSeconds"]), int(frame["sampleNanos"]), row))
    results = []
    for identity in ids:
        faults, segments, active, epoch = [], [], [], None
        absent = False
        for elapsed, nanos, row in grouped[identity]:
            present = row.get("worldPresent") == "true" and row.get("dead") == "false"
            if not present:
                absent = True
                if active:
                    segments.append(segment_result(active, seconds))
                    active = []
                epoch = None
                continue
            try:
                current = int(row["current.nativeEvidenceEpoch"])
                if current != int(row["materializedAtNanos"]) or int(row["current.nativeEvidenceObjectId"]) != int(row["objectId"]):
                    faults.append("CURRENT_NATIVE_IDENTITY")
                if row["current.nativeEvidenceOverflow"] != "false" or row["current.nativeFirstUnprovenReason"] != "NONE":
                    faults.append("NATIVE_UNPROVEN")
                if row.get("pendingOwnedStore") != "false" or row.get("cleanupPhase") != "NONE":
                    faults.append("PENDING_OR_CLEANUP")
                if row.get("liveResourceRecovery") == "true":
                    since = int(row["liveResourceSinceNanos"])
                    if since <= 0 or (nanos - since) / 1e9 > 45:
                        faults.append("REST_BOUND")
                if epoch is not None and current != epoch:
                    segments.append(segment_result(active, seconds))
                    active = []
                epoch = current
                active.append((elapsed, row))
            except (KeyError, ValueError):
                faults.append("NATIVE_SAMPLE_INCOMPLETE")
        if active:
            segments.append(segment_result(active, seconds))
        minimum = 5 if identity in primaries else 2
        qualifying = [segment for segment in segments if all(segment["delta"][key] >= minimum for key in COUNTERS[:5])
                      and segment["delta"]["ExpGained"] > 0 and segment["delta"]["SpGained"] > 0]
        if not qualifying:
            faults.append("SAME_SEGMENT_CYCLES_EXP_SP")
        if any(segment["maxIdleSeconds"] > 90 for segment in segments):
            faults.append("USEFUL_PROGRESS_DEBT")
        if not segments or segments[-1]["tail120Rewards"] < 1:
            faults.append("TAIL120_REWARD")
        if absent or len(segments) != 1:
            faults.append("COMPLETE_LINEAGE_REQUIRED")
        results.append({"profileId": identity, "primary": identity in primaries, "minimum": minimum,
                        "cycles": max((segment["cycles"] for segment in segments), default=0),
                        "segments": segments, "faults": sorted(set(faults)), "pass": not faults})
    return {"contract": "CONTINUITY_V2", "denominator": len(ids), "seconds": seconds,
            "problems": sorted(set(problems)), "rows": results,
            "pass": not problems and all(row["pass"] for row in results)}


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("episode", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    capture = read(args.episode / "capture-result.json")
    sources = [args.episode / name for name in ("capture-result.json", "baseline-cohort.json", "all-samples.json", "primary.json")]
    result = evaluate(read(sources[1]), read(sources[2]), [str(row["profileId"]) for row in read(sources[3])],
                      capture["runId"], float(capture["seconds"]))
    result["inputs"] = [{"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()} for path in sources]
    result["frozenSha"] = capture["sha"]
    if capture["kind"] != "Scene" or capture["telemetryMailboxCommands"] != 0 or not capture["sameSession"]:
        result["problems"].append("CAPTURE_CONTRACT")
        result["pass"] = False
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
