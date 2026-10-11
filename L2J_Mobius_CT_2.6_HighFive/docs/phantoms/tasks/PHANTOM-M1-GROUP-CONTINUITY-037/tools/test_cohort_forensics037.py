"""Проверки анализатора, а не прохождение M1. Используют неизменённый CONTINUITY_V2."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

import cohort_forensics037 as helper


def evaluator_path():
    override = os.environ.get("CONTINUITY_EVALUATOR037")
    if override:
        return Path(override)
    for p in Path(__file__).resolve().parents:
        if p.name == "L2J_Mobius_CT_2.6_HighFive":
            return p / "docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/continuity028.py"
    raise RuntimeError("Запустите из репозитория или задайте CONTINUITY_EVALUATOR037.")


def inputs():
    baseline = [{"profileId": i} for i in range(1, 7)]
    primary = [{"profileId": 1}, {"profileId": 2}]
    frames = []
    for t in range(0, 381, 5):
        actors = []
        for i in range(1, 7):
            row = {"profileId": i, "objectId": 1000 + i, "materializedAtNanos": str(i),
                   "worldPresent": "true", "dead": "false", "pendingOwnedStore": "false",
                   "cleanupPhase": "NONE", "current.nativeEvidenceEpoch": str(i),
                   "current.nativeEvidenceObjectId": str(1000 + i),
                   "current.nativeEvidenceOverflow": "false", "current.nativeFirstUnprovenReason": "NONE"}
            for key in helper.COUNTERS:
                row["current.native" + key] = str(t // 20)
            actors.append(row)
        frames.append({"elapsedSeconds": t, "sampleNanos": 1_000_000_000 + t * 1_000_000_000,
                       "observer": {"runId": "unit-run", "sessionState": "RUNNING", "present": True},
                       "actors": actors})
    capture = {"seconds": 380, "runId": "unit-run", "sha": "fixture-only", "kind": "Scene",
               "telemetryMailboxCommands": 0, "sameSession": True}
    return baseline, frames, primary, capture


class ForensicsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evaluator = helper.load_evaluator(evaluator_path())

    def examine(self, data=None):
        return helper.analyze(*(inputs() if data is None else data), self.evaluator)

    def test_01_preserves_original_pass(self):
        self.assertTrue(self.examine()["originalEvaluation"]["pass"])

    def test_02_absent_is_not_sql_corruption(self):
        data = inputs(); data[1][30]["actors"][2]["worldPresent"] = "false"
        result = self.examine(data)
        self.assertIn("COMPLETE_LINEAGE_REQUIRED", result["originalEvaluation"]["rows"][2]["faults"])
        self.assertEqual("NOT_ASSESSED", result["actors"][2]["sqlConservation"])
        self.assertIsNotNone(result["actors"][2]["firstAbsent"])

    def test_03_preserves_native_reason(self):
        data = inputs()
        data[1][10]["actors"][5].update({"current.nativeFirstUnprovenReason": "PHASE_DEADLINE", "current.nativeEvidenceOverflow": "true"})
        r = self.examine(data)
        self.assertEqual("PHASE_DEADLINE", r["actors"][5]["firstNativeFault"]["reason"])
        self.assertFalse(r["originalEvaluation"]["pass"])

    def test_04_no_source_mutation(self):
        data = inputs(); before = copy.deepcopy(data); self.examine(data)
        self.assertEqual(before, data)

    def test_05_counter_rollback_recorded(self):
        data = inputs(); data[1][35]["actors"][1]["current.nativeDamageSequence"] = "0"
        self.assertTrue(self.examine(data)["actors"][1]["counterRegressions"])

    def test_06_epoch_not_summed(self):
        data = inputs()
        for frame in data[1][40:]:
            frame["actors"][0]["materializedAtNanos"] = "101"
            frame["actors"][0]["current.nativeEvidenceEpoch"] = "101"
        r = self.examine(data)
        self.assertEqual(2, len(r["originalEvaluation"]["rows"][0]["segments"]))
        self.assertEqual(1, len(r["actors"][0]["epochTransitions"]))

    def test_07_missing_actor_not_removed(self):
        data = inputs(); data[1][3]["actors"].pop()
        r = self.examine(data)
        self.assertEqual(6, len(r["actors"]))
        self.assertIn("ENROLLED_IDENTITY_MISSING_OR_REPLACED", r["originalEvaluation"]["problems"])

    def test_08_tail_and_debt_not_forgiven(self):
        data = inputs()
        for frame in data[1][20:]:
            for key in helper.COUNTERS:
                frame["actors"][3]["current.native" + key] = "4"
        faults = self.examine(data)["originalEvaluation"]["rows"][3]["faults"]
        self.assertIn("USEFUL_PROGRESS_DEBT", faults); self.assertIn("TAIL120_REWARD", faults)

    def test_09_telemetry_commands_contract(self):
        data = inputs(); data[3]["telemetryMailboxCommands"] = 1
        self.assertIn("CAPTURE_CONTRACT", self.examine(data)["originalEvaluation"]["problems"])

    def test_10_wrong_evaluator_denied(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "other.py"; path.write_text("raise Exception('must not execute')")
            with self.assertRaises(ValueError): helper.load_evaluator(path)

    def test_11_exclusive_output(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "report.json"; helper.write_new(path, {"x": 1})
            with self.assertRaises(FileExistsError): helper.write_new(path, {"x": 2})
            self.assertEqual({"x": 1}, json.loads(path.read_text()))

    def test_12_unknown_reason_stays_unknown(self):
        data = inputs(); data[1][2]["actors"][0].pop("current.nativeFirstUnprovenReason")
        r = self.examine(data)
        self.assertIn("NATIVE_SAMPLE_INCOMPLETE", r["originalEvaluation"]["rows"][0]["faults"])
        self.assertEqual("MISSING", r["actors"][0]["firstNativeFault"]["reason"])

    def test_13_raw_window_is_bounded_and_keeps_identity(self):
        data = inputs(); data[1][40]["actors"][1]["pendingOwnedStore"] = "true"
        r = self.examine(data); window = r["actors"][1]["window"]
        self.assertLessEqual(len(window), helper.WINDOW_LIMIT)
        self.assertTrue(all(str(x["row"]["profileId"]) == "2" for x in window))

    def test_14_mismatched_object_is_visible(self):
        data = inputs(); data[1][2]["actors"][0]["current.nativeEvidenceObjectId"] = "999"
        r = self.examine(data)
        self.assertTrue(r["actors"][0]["identityFaults"])
        self.assertIn("CURRENT_NATIVE_IDENTITY", r["originalEvaluation"]["rows"][0]["faults"])

    def test_15_old_fault_not_claimed_new(self):
        data = inputs(); data[1][0]["actors"][0]["current.nativeFirstUnprovenReason"] = "TARGET_CAP"
        self.assertTrue(self.examine(data)["actors"][0]["firstNativeFault"]["alreadyPresentAtFirstSample"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
