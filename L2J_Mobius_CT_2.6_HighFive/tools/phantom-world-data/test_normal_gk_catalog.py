import csv
import tempfile
import unittest
from pathlib import Path

import normal_gk_catalog as catalog


class NormalGatekeeperCatalogTest(unittest.TestCase):
    def test_only_proven_normal_compound_legs_are_emitted(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "travel.xml"
            legs = catalog.build(catalog.module_root(), output)
            self.assertEqual(5, len(legs))
            self.assertEqual(5, len({leg["id"] for leg in legs}))
            self.assertEqual({"transition.e4b886ff65a767c9d77f3d3f", "transition.e904d3aed33a9cecd852c15c"}, {leg["transitionId"] for leg in legs})
            self.assertEqual({"57"}, {leg["feeId"] for leg in legs})
            first = output.read_bytes()
            catalog.build(catalog.module_root(), output)
            self.assertEqual(first, output.read_bytes())

    def test_unproven_and_conditional_rows_fail_closed(self):
        transitions = [{"transition_id": "conditional", "transition_status": "FACTUAL_CONDITIONAL", "teleport_type": "NOBLES_TOKEN", "from_gk_fact_key": "spawn", "to_destination_fact_key": "dest"}]
        source = [{"connector_kind": "ANCHOR_TO_GK", "validation_status": "NO_PATH", "to_id": "spawn", "from_id": "anchor", "instance_id": "0"}]
        destination = [{"connector_kind": "DEST_TO_ANCHOR", "validation_status": "VALID_PATH", "from_id": "dest", "to_id": "farm", "instance_id": "0"}]
        self.assertEqual([], catalog.join(transitions, source + destination, []))


if __name__ == "__main__":
    unittest.main()
