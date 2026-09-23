import tempfile
import unittest
from pathlib import Path

import travel_backbone as travel


class TravelBackboneFixture(unittest.TestCase):
    def test_native_direction_fee_order_and_multiple_spawns(self):
        with tempfile.TemporaryDirectory() as temporary:
            data = Path(temporary)
            (data / "teleporters").mkdir()
            (data / "spawns").mkdir()
            (data / "teleporters" / "one.xml").write_text(
                '<list><npc id="10"><teleport type="NORMAL"><location name="Ruins of Despair" x="100" y="200" z="-10" feeCount="610" />'
                '<location name="Town" x="300" y="400" z="-10" feeId="57" feeCount="900" /></teleport>'
                '<teleport type="NOBLES_TOKEN"><location name="Arena" x="500" y="600" z="-10" feeId="13722" feeCount="1" /></teleport></npc></list>', encoding="utf-8")
            (data / "spawns" / "one.xml").write_text(
                '<list enabled="true"><spawn name="town"><npc id="10" x="0" y="0" z="-10" />'
                '<npc id="10" x="5" y="0" z="-10" /></spawn></list>', encoding="utf-8")
            facts = travel.extract_facts(data)
            self.assertEqual(6, len(facts))
            self.assertEqual({0, 1}, {int(f["destination_index"]) for f in facts if f["teleport_type"] == "NORMAL"})
            self.assertEqual({"0", "5"}, {f["teleporter_spawn_x"] for f in facts})
            self.assertEqual({"610"}, {f["fee_count"] for f in facts if f["destination_name"] == "Ruins of Despair"})
            self.assertEqual({"57"}, {f["fee_id"] for f in facts if f["destination_name"] == "Town"})
            self.assertEqual({"FACTUAL_NORMAL", "FACTUAL_CONDITIONAL"}, {travel.transition_status(f) for f in facts})
            self.assertFalse(any(f["destination_x"] == "0" for f in facts))
            self.assertEqual(2, len([f for f in facts if f["destination_name"] == "Ruins of Despair"]))

    def test_spatial_bounded_and_requires_proof(self):
        anchors = [{"id": str(i), "x": i * 5, "y": 0, "z": 0, "instance": 0, "role": "ROUTE"} for i in range(20)]
        candidates = travel.indexed_candidates({"x": 0, "y": 0, "z": 0, "instance": 0}, travel.build_index(anchors))
        self.assertEqual(8, len(candidates))
        self.assertEqual([str(i) for i in range(8)], [a["id"] for a in candidates])
        self.assertFalse(travel.is_proven({"validation_status": "NO_PATH"}))
        self.assertTrue(travel.is_proven({"validation_status": "VALID_DIRECT"}))
        self.assertEqual([], travel.indexed_candidates({"x": 0, "y": 0, "instance": 1}, travel.build_index(anchors)))
        self.assertEqual([], travel.indexed_candidates({"x": 999999, "y": 0, "instance": 0}, travel.build_index(anchors)))

    def test_walk_and_normal_gk_differ_with_deterministic_witness(self):
        edges = [("start", "gk", "local"), ("gk", "dest", "gk:10:0"), ("dest", "farm", "local")]
        walk = travel.shortest_paths("start", edges, False)
        normal = travel.shortest_paths("start", edges, True)
        self.assertNotIn("farm", walk)
        self.assertEqual(["local", "gk:10:0", "local"], normal["farm"][1])
        self.assertEqual(normal, travel.shortest_paths("start", list(reversed(edges)), True))

    def test_noblesse_and_unspawned_source_are_excluded(self):
        rows = [{"from_gk_fact_key": "spawn.a", "to_destination_fact_key": "dest.a", "transition_id": "n", "transition_status": "FACTUAL_NORMAL"},
                {"from_gk_fact_key": "spawn.a", "to_destination_fact_key": "dest.b", "transition_id": "c", "transition_status": "FACTUAL_CONDITIONAL"},
                {"from_gk_fact_key": "", "to_destination_fact_key": "", "transition_id": "u", "transition_status": "BLOCKED_SOURCE"}]
        self.assertEqual([("spawn.a", "dest.a", "gk:n")], travel.normal_transition_edges(rows))
        with tempfile.TemporaryDirectory() as temporary:
            data = Path(temporary)
            (data / "teleporters").mkdir()
            (data / "spawns").mkdir()
            (data / "teleporters" / "other.xml").write_text(
                '<list><npc id="20"><npcs><npc id="21" /></npcs><teleport type="NORMAL">'
                '<location name="Test" x="1" y="2" z="3" /></teleport></npc></list>', encoding="utf-8")
            facts = travel.extract_facts(data)
            self.assertEqual({"20", "21"}, {fact["teleporter_npc_id"] for fact in facts})
            self.assertEqual({"BLOCKED_SOURCE"}, {fact["availability_class"] for fact in facts})
            self.assertEqual({"57"}, {fact["fee_id"] for fact in facts})
            self.assertEqual({"0"}, {fact["fee_count"] for fact in facts})

    def test_source_hash_ignores_checkout_line_endings(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "source.py"
            path.write_bytes(b"a\nb\n")
            lf_hash = travel.sha_source(path)
            path.write_bytes(b"a\r\nb\r\n")
            self.assertEqual(lf_hash, travel.sha_source(path))


if __name__ == "__main__":
    unittest.main()
