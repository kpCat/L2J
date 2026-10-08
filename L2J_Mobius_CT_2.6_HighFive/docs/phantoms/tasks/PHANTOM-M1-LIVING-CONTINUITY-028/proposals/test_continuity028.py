"""Evaluator contract fixtures only; never native server proof."""
import copy
import unittest
from continuity028 import evaluate


def fixture():
    baseline = [{"profileId": str(i), "materializedAtNanos": "100"} for i in range(1, 5)]
    frames = []
    for t in range(0, 381):
        actors = []
        for i in range(1, 5):
            count = t // 30
            row = {"profileId": str(i), "objectId": str(i + 100), "worldPresent": "true",
                   "dead": "false", "materializedAtNanos": "100", "pendingOwnedStore": "false",
                   "cleanupPhase": "NONE", "liveResourceRecovery": "false"}
            row.update({"current.nativeEvidenceEpoch": "100", "current.nativeEvidenceObjectId": str(i + 100),
                        "current.nativeEvidenceOverflow": "false", "current.nativeFirstUnprovenReason": "NONE"})
            for name in ("FarmCycleSequence", "KillSequence", "RewardSequence", "DamageSequence", "TargetSequence"):
                row["current.native" + name] = str(count)
            row["current.nativeExpGained"] = str(count * 10)
            row["current.nativeSpGained"] = str(count)
            actors.append(row)
        frames.append({"elapsedSeconds": t, "sampleNanos": t * 1_000_000_000,
                       "observer": {"runId": "one", "sessionState": "RUNNING", "present": True}, "actors": actors})
    return baseline, frames


class ContinuityContracts(unittest.TestCase):
    def test_complete_same_epoch(self):
        baseline, frames = fixture()
        result = evaluate(baseline, frames, ["1", "2"], "one", 380)
        self.assertTrue(result["pass"], result)
        self.assertEqual(result["denominator"], 4)

    def test_identity_gap_and_denominator_are_not_repaired(self):
        baseline, frames = fixture()
        for defect in ("missing", "run", "gap"):
            damaged = copy.deepcopy(frames)
            if defect == "missing":
                damaged[100]["actors"].pop()
            elif defect == "run":
                damaged[100]["observer"]["runId"] = "replacement"
            else:
                del damaged[100:110]
            with self.subTest(defect=defect):
                result = evaluate(baseline, damaged, ["1", "2"], "one", 380)
                self.assertFalse(result["pass"])
                self.assertEqual(result["denominator"], 4)

    def test_epoch_cycles_never_sum(self):
        baseline, frames = fixture()
        for frame in frames:
            for row in frame["actors"]:
                count = min(3, int(frame["elapsedSeconds"]) % 190 // 50)
                epoch = "100" if frame["elapsedSeconds"] < 190 else "200"
                row["materializedAtNanos"] = row["current.nativeEvidenceEpoch"] = epoch
                for name in ("FarmCycleSequence", "KillSequence", "RewardSequence", "DamageSequence", "TargetSequence"):
                    row["current.native" + name] = str(count)
        result = evaluate(baseline, frames, ["1", "2"], "one", 380)
        self.assertFalse(result["pass"])
        self.assertTrue(all(row["cycles"] <= 3 for row in result["rows"]))


if __name__ == "__main__":
    unittest.main()
