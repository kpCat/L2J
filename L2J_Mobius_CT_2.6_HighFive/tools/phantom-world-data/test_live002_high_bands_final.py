import tempfile
import unittest
from pathlib import Path

import live002_high_bands_final as final


class HighBandsFinalTest(unittest.TestCase):
    def test_current_ordinary_reachability_and_blocker(self):
        module = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as temporary:
            counts, statuses = final.prove(module, Path(temporary) / "proof.tsv")
        self.assertEqual(1, counts["40-51"])
        self.assertGreaterEqual(counts["52-60"], 1)
        self.assertGreaterEqual(counts["61-75"], 1)
        self.assertGreaterEqual(counts["76-80"], 1)
        self.assertEqual(0, counts["81-85"])
        self.assertEqual("NO_OPEN_ORDINARY_CANDIDATE", statuses["81-85"])

    def test_native_level_drift_is_rejected(self):
        module = Path(__file__).resolve().parents[2]
        row = next(row for row in final.read_tsv(module / "docs/phantoms/live-world/WORLD_COVERAGE.tsv")
                   if row["coverage_key"].startswith("079a0cb66fadcb6801c9a79b"))
        self.assertEqual((55, 55), final.validate_native_target(module, row))
        with self.assertRaisesRegex(RuntimeError, "level drift"):
            final.validate_native_target(module, dict(row, npc_level_min="56"))


if __name__ == "__main__":
    unittest.main()
