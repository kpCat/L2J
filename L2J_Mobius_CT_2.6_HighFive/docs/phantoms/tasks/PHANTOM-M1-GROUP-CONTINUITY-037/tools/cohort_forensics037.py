"""Read-only forensic index for TASK037. It never changes CONTINUITY_V2 or grants M1 PASS.

Run against extracted, hash-verified TASK036/current episode files. The original
repository evaluator is imported only after checking its exact Git blob hash.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
from typing import Any

EVALUATOR_BLOB = "c441ecc621dc5936ba3b2ea7f8e9f6e6f74eb164"
COUNTERS = ("FarmCycleSequence", "KillSequence", "RewardSequence", "DamageSequence",
            "TargetSequence", "ExpGained", "SpGained")
WINDOW_LIMIT = 31
MAX_INPUT_BYTES = 256 * 1024 * 1024


def load_evaluator(path: Path) -> Any:
    raw = path.read_bytes()
    digest = hashlib.sha1(b"blob " + str(len(raw)).encode("ascii") + b"\0" + raw).hexdigest()
    # An LF-normalized read is permitted only for Git's CRLF working-tree conversion.
    normalized = raw.replace(b"\r\n", b"\n")
    normalized_digest = hashlib.sha1(b"blob " + str(len(normalized)).encode("ascii") + b"\0" + normalized).hexdigest()
    if digest != EVALUATOR_BLOB and normalized_digest != EVALUATOR_BLOB:
        raise ValueError("CONTINUITY_V2 не совпал с pinned blob; evaluator не исполнялся.")
    spec = importlib.util.spec_from_file_location("original_continuity028_for_task037", path)
    if spec is None or spec.loader is None:
        raise ValueError("Не удалось загрузить проверенный evaluator.")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def integer(value: Any) -> int | None:
    if isinstance(value, bool):
        return None
    try:
        return int(value)
    except (TypeError, ValueError, OverflowError):
        return None


def analyze(baseline: list, frames: list, primaries: list, capture: dict, evaluator: Any) -> dict:
    """The caller retains all raw inputs. This function returns an index, not a root-cause verdict."""
    ids = [str(r["profileId"]) for r in baseline]
    if not frames or len(frames) > 5000 or not 4 <= len(ids) <= 8 or len(set(ids)) != len(ids):
        raise ValueError("Ожидалась ограниченная сцена 4–8 участников и 1–5000 кадров.")
    primary_ids = [str(r["profileId"]) for r in primaries]
    # Same function and CLI conditions as the unmodified authoritative evaluator.
    original = evaluator.evaluate(baseline, frames, primary_ids, capture["runId"], float(capture["seconds"]))
    if capture.get("kind") != "Scene" or capture.get("telemetryMailboxCommands") != 0 or not capture.get("sameSession"):
        original["problems"].append("CAPTURE_CONTRACT")
        original["pass"] = False
    grouped: dict[str, list] = {i: [] for i in ids}
    for index, frame in enumerate(frames):
        for row in frame["actors"]:
            identity = str(row["profileId"])
            if identity in grouped:
                grouped[identity].append((index, frame, row))
    outcomes = {str(r["profileId"]): r for r in original["rows"]}
    actors = []
    for identity in ids:
        points = grouped[identity]
        first_absent = first_fault = first_pending = None
        regressions, transitions, mismatches = [], [], []
        previous = None
        focus_index = None
        for frame_index, frame, row in points:
            at = {"frameIndex": frame_index, "elapsedSeconds": frame["elapsedSeconds"],
                  "sampleNanos": frame["sampleNanos"], "objectId": row.get("objectId"),
                  "epoch": row.get("current.nativeEvidenceEpoch"),
                  "sourceHint": f"all-samples.json: frameIndex={frame_index}, actor.profileId={identity}"}
            present = row.get("worldPresent") == "true" and row.get("dead") == "false"
            if not present:
                if first_absent is None:
                    first_absent = dict(at, worldPresent=row.get("worldPresent"), dead=row.get("dead"))
                    if focus_index is None: focus_index = frame_index
                previous = None
                continue
            epoch = integer(row.get("current.nativeEvidenceEpoch"))
            if epoch is None or epoch != integer(row.get("materializedAtNanos")) or integer(row.get("current.nativeEvidenceObjectId")) != integer(row.get("objectId")):
                mismatches.append(at)
                if focus_index is None: focus_index = frame_index
            reason = row.get("current.nativeFirstUnprovenReason", "MISSING")
            if first_fault is None and (reason != "NONE" or row.get("current.nativeEvidenceOverflow") != "false"):
                first_fault = dict(at, reason=reason,
                    firstUnprovenNanos=row.get("current.nativeFirstUnprovenNanos"),
                    phase=row.get("current.nativePhase", "MISSING"),
                    phaseSinceNanos=row.get("current.nativePhaseSinceNanos"),
                    alreadyPresentAtFirstSample=(frame_index == points[0][0]))
                if focus_index is None: focus_index = frame_index
            if first_pending is None and (row.get("pendingOwnedStore") != "false" or row.get("cleanupPhase") != "NONE"):
                first_pending = dict(at, pendingOwnedStore=row.get("pendingOwnedStore"), cleanupPhase=row.get("cleanupPhase"))
                if focus_index is None: focus_index = frame_index
            if previous is not None:
                prev_epoch = integer(previous.get("current.nativeEvidenceEpoch"))
                if epoch != prev_epoch:
                    transitions.append(dict(at, previousEpoch=prev_epoch))
                    if focus_index is None: focus_index = frame_index
                else:
                    for key in COUNTERS:
                        before, after = integer(previous.get("current.native" + key)), integer(row.get("current.native" + key))
                        if before is not None and after is not None and after < before:
                            regressions.append(dict(at, counter=key, before=before, after=after))
                            if focus_index is None: focus_index = frame_index
            previous = row
        if focus_index is None:
            focus_index = points[-1][0] if points else 0
        # Bounded raw window around first observed predicate; fields are copied unchanged.
        relevant = sorted(points, key=lambda x: abs(x[0] - focus_index))[:WINDOW_LIMIT]
        relevant.sort(key=lambda x: x[0])
        window = [{"frameIndex": idx, "elapsedSeconds": f["elapsedSeconds"],
                   "sampleNanos": f["sampleNanos"], "row": dict(r)} for idx, f, r in relevant]
        actors.append({"profileId": identity, "originalFaults": outcomes[identity]["faults"],
                       "originalSegments": outcomes[identity]["segments"], "samples": len(points),
                       "firstAbsent": first_absent, "firstNativeFault": first_fault,
                       "firstPendingOrCleanup": first_pending, "epochTransitions": transitions,
                       "counterRegressions": regressions, "identityFaults": mismatches,
                       "sqlConservation": "NOT_ASSESSED", "rootCause": "NOT_PROVEN_BY_THIS_TOOL",
                       "window": window})
    return {"contract": "TASK037_FORENSIC_INDEX", "diagnosisOnly": True,
            "originalEvaluation": original, "actors": actors,
            "notice": "COMPLETE_LINEAGE_REQUIRED is an absence/segment marker, not a SQL check. Original FAIL is preserved."}


def read_input(path: Path) -> Any:
    if path.stat().st_size > MAX_INPUT_BYTES:
        raise ValueError(f"Слишком большой input: {path.name}")
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_new(path: Path, data: dict) -> None:
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("episode", type=Path)
    parser.add_argument("--evaluator", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root, output = args.episode.resolve(), args.output.resolve()
    if output == root or root in output.parents:
        raise ValueError("Выход должен находиться ВНЕ immutable episode directory.")
    names = ("baseline-cohort.json", "all-samples.json", "primary.json", "capture-result.json")
    paths = [root / n for n in names]
    result = analyze(*(read_input(p) for p in paths), load_evaluator(args.evaluator))
    result["inputs"] = [{"path": str(p), "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths]
    result["evaluatorGitBlob"] = EVALUATOR_BLOB
    write_new(output, result)
    print(json.dumps({"output": str(output), "actors": len(result["actors"]),
                      "originalPass": result["originalEvaluation"]["pass"], "diagnosisOnly": True}, ensure_ascii=False))


if __name__ == "__main__":
    main()
