import copy
import unittest
from classify_cohort026 import summarize

class CohortSummaryTests(unittest.TestCase):
    def row(self, **updates):
        base={"profileId":"876", "objectId":"268492234", "primary":False,
              "sameEpoch":True,"missingSamples":0,"pass":False,"cycles":5,
              "kills":7,"rewards":7,"tail120Rewards":0,"maxIdleSeconds":197.5,
              "last":{"nativeOwnerState":"OPEN","nativeCheckpointStage":"COMPLETED",
              "pendingOwnedStore":"false","autoPlay":"false","mp":"75", "maxMp":"75",
              "travelReason":"travel.journey_deadline","visibleRecoveryReason":"LOCAL_FARM_UNAVAILABLE"}}
        base.update(updates); return base
    def test_preserves_all_actors(self):
        self.assertEqual(2,len(summarize([self.row(), self.row(profileId="872")],"A")))
    def test_no_mutations(self):
        data=[self.row()]; before=copy.deepcopy(data); summarize(data); self.assertEqual(before,data)
    def test_travel_not_claimed_root(self):
        result=summarize([self.row()])[0]
        self.assertIn("LOCAL_TRAVEL_TERMINAL",result["observed_flags"])
        self.assertEqual("UNKNOWN",result["root_cause"])
    def test_false_string_not_true(self):
        r=self.row(**{"pass":"false", "sameEpoch":"true"})
        self.assertFalse(summarize([r])[0]["reported_pass"])
    def test_missing_sample(self):
        result=summarize([self.row(last=None,missingSamples=20,sameEpoch=False)])[0]
        self.assertIn("LIFECYCLE_OR_VISIBILITY_UNRESOLVED",result["observed_flags"])
    def test_no_false_positive_from_cycles(self):
        result=summarize([self.row(cycles=54,kills=54,rewards=54,tail120Rewards=11)])[0]
        self.assertFalse(result["reported_pass"])
    def test_low_mp_is_fact_not_cause(self):
        r=self.row(); r["last"]["mp"]="17"; r["last"]["maxMp"]="181"
        result=summarize([r])[0]
        self.assertIn("LOW_MP_HEURISTIC_NOT_CAUSE",result["observed_flags"])
        self.assertEqual("UNKNOWN",result["root_cause"])
    def test_phase_deadline(self):
        r=self.row();r["last"]["nativeEvidenceOverflow"]="true"
        self.assertIn("EVIDENCE_UNPROVEN",summarize([r])[0]["observed_flags"])
    def test_duplicate_rejected(self):
        with self.assertRaises(ValueError): summarize([self.row(),self.row()])
    def test_bad_boolean(self):
        with self.assertRaises(ValueError): summarize([self.row(**{"pass":"maybe"})])
    def test_invalid_root(self):
        with self.assertRaises(ValueError): summarize({"not":"list"})
    def test_bad_last(self):
        with self.assertRaises(ValueError): summarize([self.row(last=[])])
    def test_empty_not_pass(self):
        self.assertEqual([],summarize([]))

if __name__=="__main__": unittest.main()
