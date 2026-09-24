import csv
import tempfile
import unittest
from pathlib import Path

import normal_gk_catalog as catalog


class NormalGatekeeperCatalogTest(unittest.TestCase):
    def test_targeted_two_stage_legs_are_derived_from_facts(self):
        with tempfile.TemporaryDirectory() as temporary:
            legs = catalog.build(catalog.module_root(), Path(temporary) / "travel.xml")
            self.assertEqual(12, len(legs))
            schuttgart = next(leg for leg in legs if leg["transitionId"] == "transition.da2bfda52a944f78cd61ab30")
            plunderous = next(leg for leg in legs if leg["transitionId"] == "transition.bb1ab636c13e8cbfaee92385" and leg["destinationConnectorId"] == "connector.60e086bc4f8c97db861efd4d")
            self.assertEqual("9", schuttgart["destinationCastleIds"])
            self.assertEqual("", plunderous["destinationCastleIds"])
            self.assertEqual(schuttgart["toAnchorId"], plunderous["fromAnchorId"])

    def test_identity_requires_exact_canonical_destination(self):
        identity = {"connector_kind": "DEST_TO_ANCHOR", "from_type": "DEST", "to_type": "ANCHOR", "to_id": "route", "instance_id": "0", "from_x": "87126", "from_y": "-143520", "from_z": "-1288", "to_x": "87126", "to_y": "-143520", "to_z": "-1288", "straight_distance": "0", "validation_status": "VALID_IDENTITY", "path_length": "0", "path_segments": "0", "reason": "CANONICAL_IDENTITY"}
        anchors = {"route": ("87126", "-143520", "-1288", "0")}
        self.assertTrue(catalog.destination_eligible(identity, anchors))
        for changes in ({"connector_kind": "ANCHOR_TO_GK"}, {"instance_id": "1"}, {"to_z": "-1287"}, {"path_length": "1"}, {"path_segments": "1"}, {"straight_distance": "1"}, {"reason": "GEOENGINE_PROVEN"}):
            self.assertFalse(catalog.destination_eligible(dict(identity, **changes), anchors))

    def test_only_proven_normal_compound_legs_are_emitted(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "travel.xml"
            legs = catalog.build(catalog.module_root(), output)
            self.assertEqual(12, len(legs))
            self.assertEqual(12, len({leg["id"] for leg in legs}))
            self.assertTrue({"transition.e4b886ff65a767c9d77f3d3f", "transition.e904d3aed33a9cecd852c15c"}.issubset({leg["transitionId"] for leg in legs}))
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
