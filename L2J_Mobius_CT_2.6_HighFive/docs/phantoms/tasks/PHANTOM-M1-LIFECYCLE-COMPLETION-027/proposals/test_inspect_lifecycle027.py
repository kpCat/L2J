import copy
import unittest
from inspect_lifecycle027 import boolean, classify, summarize, shutdown_events


class ExtractorTests(unittest.TestCase):
    def test_dead_not_dropped_or_counted_healthy(self):
        rows = [{"profileId": "275", "pass": False, "last": None},
                {"profileId": "175", "pass": True, "last": {"dead": "false", "nativeOwnerState": "OPEN"}}]
        r = summarize(rows)
        self.assertEqual(2, r["denominator"])
        self.assertEqual(1, r["originalPassCount"])
        self.assertEqual("ABSENT_OR_NOT_CAPTURED_CAUSE_UNKNOWN", r["rows"][0]["classification"])

    def test_phase_not_claimed_cause(self):
        row = {"profileId": "110", "pass": False, "last": {"nativeEvidenceOverflow": "true"}}
        self.assertEqual("OBSERVATION_UNPROVEN_NOT_AUTOMATIC_GAMEPLAY_STOP", classify(row))
        self.assertIs(False, summarize([row])["rows"][0]["originalPass"])

    def test_known_route_and_no_automatic_fix(self):
        row = {"profileId": "447", "pass": False, "last": {"travelReason": "travel.route_absent"}}
        self.assertEqual("VISIBLE_ROUTE_BLOCK_RECORDED", classify(row))
        self.assertEqual("NOT_EVALUATED", summarize([row])["m1Verdict"])

    def test_visible_dead_separate(self):
        self.assertEqual("VISIBLE_DEAD_NEEDS_LIFECYCLE", classify({"last": {"dead": "true"}}))

    def test_no_mutation_or_dedup(self):
        rows = [{"profileId": "7", "last": {"nativeOwnerState": "DRAINING"}},
                {"profileId": "7", "last": None}]
        before = copy.deepcopy(rows)
        r = summarize(rows)
        self.assertEqual(before, rows)
        self.assertEqual(2, r["denominator"])
        self.assertEqual(2, r["originalUnknownCount"])

    def test_invalid_inputs_not_zero(self):
        for value in (1, "yes", "null", {}):
            with self.assertRaises(ValueError): boolean(value)
        with self.assertRaises(ValueError): summarize({})
        with self.assertRaises(ValueError): summarize([{}])
        with self.assertRaises(ValueError): summarize([{"profileId": 1, "last": "bad"}])

    def test_actual_log_shapes(self):
        text = "\n".join([
            "2026.10.08 17:25:27,923 WARNING Phantom World: Initial subsystem drain remains incomplete; materializationServiceState=RUNNING, retainedMaterializationEntries=8(33ms).",
            "2026.10.08 17:25:27,941 SEVERE Phantom World: Final subsystem drain is incomplete; materializationServiceState=RUNNING, retainedMaterializationEntries=8(17ms).",
            "2026.10.08 17:25:27,942 INFO ThreadPool: Shutting down all thread pools.",
            "2026.10.08 17:25:27,949 WARNING NATIVE_WORK_DRAIN_INTERRUPTED",
            " at NATIVE_WORK_DRAIN_INTERRUPTED"])
        result = shutdown_events(text)
        self.assertEqual(4, len(result))
        self.assertEqual([33, 17], [e["reportedMilliseconds"] for e in result[:2]])
        self.assertEqual(8, result[1]["retainedEntries"])
        self.assertEqual("POOL_SHUTDOWN", result[2]["event"])
        self.assertEqual("UNSPECIFIED_AS_LOGGED", result[0]["timezone"])

    def test_unknown_log_not_pass(self):
        self.assertEqual([], shutdown_events("success!\n at stack no timestamp"))


if __name__ == "__main__":
    unittest.main()
