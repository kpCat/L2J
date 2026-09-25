import tempfile
import unittest
from pathlib import Path

import live002_high_bands_final as final
import live002_81_85_final as current


class HighBandsFinalTest(unittest.TestCase):
    def test_current_ordinary_reachability_and_point_farm(self):
        module = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as temporary:
            counts, length = current.validate(module, Path(temporary) / "proof.tsv")
        self.assertGreaterEqual(counts["40-51"], 1)
        self.assertGreaterEqual(counts["52-60"], 10)
        self.assertGreaterEqual(counts["61-75"], 15)
        self.assertGreaterEqual(counts["76-80"], 3)
        self.assertGreaterEqual(counts["81-85"], 1)
        self.assertGreaterEqual(length, 2)

    def test_native_level_drift_is_rejected(self):
        module = Path(__file__).resolve().parents[2]
        row = next(row for row in final.read_tsv(module / "docs/phantoms/live-world/WORLD_COVERAGE.tsv")
                   if row["coverage_key"].startswith("079a0cb66fadcb6801c9a79b"))
        self.assertEqual((55, 55), final.validate_native_target(module, row))
        with self.assertRaisesRegex(RuntimeError, "level drift"):
            final.validate_native_target(module, dict(row, npc_level_min="56"))


if __name__ == "__main__":
    unittest.main()
