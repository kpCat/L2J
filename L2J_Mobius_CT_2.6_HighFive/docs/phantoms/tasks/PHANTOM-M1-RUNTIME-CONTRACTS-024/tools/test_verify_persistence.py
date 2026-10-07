import copy
import unittest
from verify_persistence import verify


def valid():
    snapshot = {"level": 8, "exp": 26919, "sp": 1827, "expBeforeDeath": 0,
                "x": 45280, "y": 40867, "z": -3504, "heading": 123,
                "classIndex": 0, "classId": 10, "race": 0,
                "hp": 231.0, "maxHp": 231.0, "mp": 90.0, "maxMp": 90.0,
                "cp": 100.0, "maxCp": 100.0, "inventoryHash": "a" * 64,
                "skillsHash": "b" * 64}
    return {"version": 1, "sourceSha": "c" * 40,
            "database": "l2jmobiush5_localplay_contract024a",
            "barrier": "QUIESCENT_NATIVE_PREPARE", "rawSnapshotSha256": "d" * 64,
            "rawSqlSha256": "e" * 64, "requiredProfiles": [281], "retainedEntries": 0,
            "pendingOwnedStores": 0, "forcedStop": False,
            "rows": [{"profileId": 281, "objectId": 268487210, "epoch": 61404415574100,
                      "source": "native-sealed-snapshot", "nativeFinalized": True,
                      "projectionReady": False, "expected": snapshot, "saved": copy.deepcopy(snapshot)}]}

class VerificationTest(unittest.TestCase):
    def test_exact_off_anchor_save_is_valid(self):
        self.assertTrue(verify(valid())["pass"])
    def test_actual_023_exp_sp_loss_is_rejected(self):
        d = valid(); d["rows"][0]["saved"].update(exp=25937, sp=1720)
        result = verify(d)
        self.assertFalse(result["pass"])
        self.assertTrue(any("exp" in x for x in result["errors"]))
    def test_native_xyz_must_not_be_snapped(self):
        d = valid(); d["rows"][0]["saved"]["x"] += 100
        self.assertFalse(verify(d)["pass"])
    def test_death_loss_is_not_assumed_monotonic(self):
        d = valid()
        d["rows"][0]["expected"].update(exp=20000, hp=0.0)
        d["rows"][0]["saved"].update(exp=20000, hp=0.0)
        self.assertTrue(verify(d)["pass"])
    def test_no_reselection(self):
        d = valid(); d["requiredProfiles"].append(411)
        self.assertFalse(verify(d)["pass"])
    def test_extra_actor_not_silently_ignored(self):
        d = valid(); r = copy.deepcopy(d["rows"][0]); r["profileId"] = 411; d["rows"].append(r)
        self.assertFalse(verify(d)["pass"])
    def test_duplicate_actor_rejected(self):
        d = valid(); d["rows"].append(copy.deepcopy(d["rows"][0]))
        self.assertFalse(verify(d)["pass"])
    def test_empty_scene_rejected(self):
        d = valid(); d["requiredProfiles"] = []; d["rows"] = []
        self.assertFalse(verify(d)["pass"])
    def test_live_sample_is_not_a_store_barrier(self):
        d = valid(); d["barrier"] = "LAST_CENSUS"
        self.assertFalse(verify(d)["pass"])
    def test_both_hashes_are_required(self):
        for name in ["rawSnapshotSha256", "rawSqlSha256"]:
            d = valid(); del d[name]
            self.assertFalse(verify(d)["pass"])
    def test_retained_is_failure(self):
        d = valid(); d["retainedEntries"] = 5
        self.assertFalse(verify(d)["pass"])
    def test_pending_is_failure(self):
        d = valid(); d["pendingOwnedStores"] = 1
        self.assertFalse(verify(d)["pass"])
    def test_force_is_failure(self):
        d = valid(); d["forcedStop"] = True
        self.assertFalse(verify(d)["pass"])
    def test_unfinalized_is_failure(self):
        d = valid(); d["rows"][0]["nativeFinalized"] = False
        self.assertFalse(verify(d)["pass"])
    def test_inventory_difference_is_failure(self):
        d = valid(); d["rows"][0]["saved"]["inventoryHash"] = "f" * 64
        self.assertFalse(verify(d)["pass"])
    def test_boolean_exp_is_not_integer(self):
        d = valid(); d["rows"][0]["expected"]["exp"] = True
        self.assertFalse(verify(d)["pass"])
    def test_nan_is_rejected(self):
        d = valid(); d["rows"][0]["expected"]["hp"] = float("nan")
        self.assertFalse(verify(d)["pass"])
    def test_real_play_db_rejected(self):
        d = valid(); d["database"] = "l2jmobiush5_localplay3"
        self.assertFalse(verify(d)["pass"])
    def test_unknown_fields_not_a_pass_substitute(self):
        self.assertFalse(verify({"pass": True})["pass"])

if __name__ == "__main__":
    unittest.main()
