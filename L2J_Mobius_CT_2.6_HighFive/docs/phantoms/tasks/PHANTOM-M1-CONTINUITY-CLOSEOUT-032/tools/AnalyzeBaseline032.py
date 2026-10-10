"""Read immutable final031 samples; emit only selected scalar timeline and hashes."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("source", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
if args.output.exists():
    raise SystemExit("Immutable analysis output exists")
raw = args.source.read_bytes()
frames = json.loads(raw.decode("utf-8-sig"))
fields = (
    "materializedAtNanos", "objectId", "hp", "mp", "x", "y", "z",
    "moving", "casting", "attacking", "targetObjectId", "aiAttackTargetDead",
    "continuationSession", "liveResourceRecovery", "liveResourceReason",
    "liveResourceSinceNanos", "liveFirstStopNanos", "liveFirstStopReason",
    "liveFirstStopCaller", "nativePhase", "nativePhaseSinceNanos",
    "nativePhaseDeadlineNanos", "nativeLastProgressNanos", "nativeOwnerWork",
)
result = {"source": str(args.source), "sha256": hashlib.sha256(raw).hexdigest(),
          "sampling": "NONATOMIC_VOLATILE", "actors": {}}
for identity in ("43", "117"):
    points = [(float(frame["elapsedSeconds"]), int(frame["sampleNanos"]), row)
              for frame in frames for row in frame["actors"]
              if str(row["profileId"]) == identity]
    timeline, gaps = [], []
    previous = None
    last_useful = points[0][0]
    gap_start = 0
    for index, (elapsed, nanos, row) in enumerate(points):
        current = tuple(int(row["current.native" + name]) for name in
                        ("DamageSequence", "FarmCycleSequence", "RewardSequence", "ExpGained"))
        useful = previous is not None and (current[0] > previous[0] or
                 current[1] > previous[1] or (current[2] > previous[2] and current[3] > previous[3]))
        if useful:
            if elapsed - last_useful > 45:
                gaps.append({"from": last_useful, "to": elapsed,
                             "seconds": elapsed - last_useful,
                             "firstAbove90": next((p[0] for p in points[gap_start:index+1]
                                                   if p[0] - last_useful > 90), None),
                             "timelineIndexes": [gap_start, index]})
            last_useful, gap_start = elapsed, index
        timeline.append({"elapsed": elapsed, "sampleNanos": nanos,
                         "useful": useful, "counters": current,
                         **{field: row.get(field, "MISSING") for field in fields}})
        previous = current
    if points[-1][0] - last_useful > 45:
        gaps.append({"from": last_useful, "to": points[-1][0],
                     "seconds": points[-1][0] - last_useful,
                     "timelineIndexes": [gap_start, len(points)-1]})
    result["actors"][identity] = {"gaps": gaps, "timeline": timeline}
args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
for identity, actor in result["actors"].items():
    print(identity, "gaps", json.dumps(actor["gaps"]))
    for gap in actor["gaps"]:
        start, end = gap["timelineIndexes"]
        if gap["seconds"] > 90:
            for index in (start, min(start+1, end), (start+end)//2, end):
                row = actor["timeline"][index]
                print(json.dumps({key: row[key] for key in ("elapsed", "counters", "mp",
                                 "casting", "targetObjectId", "liveResourceRecovery",
                                 "liveResourceReason", "continuationSession", "liveFirstStopReason")},
                                 ensure_ascii=False))
