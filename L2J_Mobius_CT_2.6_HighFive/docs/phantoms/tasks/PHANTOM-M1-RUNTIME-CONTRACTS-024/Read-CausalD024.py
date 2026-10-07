"""Read retained TASK023 observations without mutating actors, receipts or counters."""
import json
import pathlib
import sys

source = pathlib.Path(sys.argv[1])
samples = json.loads(source.read_text(encoding="utf-8-sig"))
fields = ("profileId", "x", "y", "z", "instanceId", "anchor", "goalRevision", "currentActionGuard", "nativeOwnerState", "shortTargets", "longTargets", "visibleRecoveryReason", "travelFailureReason", "travelFailureSegmentWitness", "travelReason", "liveFirstStopReason", "liveAutoPlayRegistered", "targetObjectId", "exp", "sp")
for profile in ("281", "459"):
    last_signature = None
    for sample in samples:
        actor = next((a for a in sample["actors"] if str(a.get("profileId")) == profile), None)
        if actor is None:
            continue
        facts = {key: actor[key] for key in fields if key in actor}
        signature = (facts.get("visibleRecoveryReason"), facts.get("travelFailureReason"), facts.get("liveFirstStopReason"), facts.get("liveAutoPlayRegistered"))
        if signature != last_signature or sample is samples[-1]:
            print(json.dumps({"elapsed": sample["elapsedSeconds"], **facts}, ensure_ascii=False))
        last_signature = signature
print("availableFields=" + ",".join(key for key in samples[-1]["actors"][0] if any(word in key.lower() for word in ("anchor", "goal", "recovery", "travel"))))
