"""Read-only correlation: exact same epoch rewards after an observed completed checkpoint."""
import argparse, json
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("directory", type=Path)
a = p.parse_args()
samples = json.loads((a.directory / "all-samples.json").read_text(encoding="utf-8-sig"))
baseline = json.loads((a.directory / "baseline-cohort.json").read_text(encoding="utf-8-sig"))
rows = []
transitions = []
fields = ("travelReason", "liveAutoPlayRegistered", "nativeOwnerState", "nativeCheckpointOutcome", "nativeCheckpointStage", "nativeOwnerWork", "nativePhase")
for actor in baseline:
    observed = [(s["elapsedSeconds"], r) for s in samples for r in s["actors"] if r["profileId"] == actor["profileId"] and r["materializedAtNanos"] == actor["materializedAtNanos"]]
    completed = next(((t, r) for t, r in observed if r.get("nativeCheckpointOutcome") == "RESUME" and r.get("nativeCheckpointStage") == "COMPLETED"), None)
    resumed_rewards = 0 if completed is None or not observed else int(observed[-1][1]["nativeRewardSequence"]) - int(completed[1]["nativeRewardSequence"])
    previous = None
    for t, r in observed:
        current = tuple(r.get(f, "") for f in fields[:-2])
        if current != previous:
            transitions.append(dict(profileId=actor["profileId"], seconds=t, rewards=r.get("nativeRewardSequence"), x=r.get("x"), y=r.get("y"), z=r.get("z"), **{f:r.get(f, "") for f in fields}))
            previous = current
    rows.append(dict(profileId=actor["profileId"], epoch=actor["materializedAtNanos"], completedAt=None if completed is None else completed[0], rewardsAfterCompleted=resumed_rewards,
                     firstCauses=sorted({r.get("nativeCheckpointFirstMessage", "") for _, r in observed if r.get("nativeCheckpointFirstMessage")}),
                     orphanSealed=any(r.get("nativeOwnerState") == "SEALED" and r.get("pendingOwnedStore") == "false" and r.get("nativeOwnerCheckpoint") == "false" and r.get("nativeCheckpointOutcome") not in ("PUBLISH_COMMITTED", "VERIFY_WRITE_OUTCOME", "TERMINAL_RETAIN") for _, r in observed)))
result = dict(source="READ_ONLY_NATIVE_CENSUS", finalSceneClaim=False, rows=rows)
(a.directory / "continuation-correlation.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
(a.directory / "native-control-transitions.json").write_text(json.dumps(transitions, indent=2), encoding="utf-8")
print(json.dumps(result))
