"""Focused contracts for the LIVE-002 final gap diagnosis."""

import unittest
import xml.etree.ElementTree as ET

import final_geo


class FinalGeoClassificationTest(unittest.TestCase):
    def test_every_ordinary_group_gets_one_explicit_status(self):
        coverage = [
            {"coverage_key": "open", "classification": "ORDINARY_WORLD", "status": "READY_STATIC", "source_path": "data/spawns/Foo.xml", "npc_level_min": "6", "npc_level_max": "10", "sample_x": "0", "sample_y": "0"},
            {"coverage_key": "closed", "classification": "ORDINARY_WORLD", "status": "READY_STATIC", "source_path": "data/spawns/Catacombs/Bar.xml", "npc_level_min": "20", "npc_level_max": "39", "sample_x": "1", "sample_y": "1"},
            {"coverage_key": "excluded", "classification": "INSTANCE", "status": "READY_STATIC"},
        ]
        candidates = {key: {"coverage_key": key, "anchor_id": key + ".anchor", "source_refs": "source"} for key in ("open", "closed")}
        proofs = {"open": {"status": "VALID"}, "closed": {"status": "CLOSED_SOURCE_NO_DOOR_PATH"}}
        anchors = {"open.anchor": {"x": 0, "y": 0, "role": "FARMING"}}
        rows = final_geo.classify_rows(coverage, candidates, proofs, anchors, {"open.anchor": "component.1"}, set(), set(), [])
        self.assertEqual([row["coverage_key"] for row in rows], ["closed", "open"])
        self.assertEqual(rows[0]["primary_blocker"], "CLOSED_SOURCE_ENTRANCE_REQUIRED")
        self.assertEqual(rows[1]["primary_blocker"], "OPEN_COMPONENT_DISCONNECTED")

    def test_duplicate_ordinary_key_is_rejected(self):
        coverage = [{"coverage_key": "same", "classification": "ORDINARY_WORLD", "status": "READY_STATIC"}] * 2
        with self.assertRaisesRegex(ValueError, "duplicate ordinary coverage key"):
            final_geo.classify_rows(coverage, {}, {}, {}, {}, set(), set(), [])

    def test_adaptive_search_is_bounded_and_only_yields_unproven_candidates(self):
        anchors = {
            "root": {"id": "root", "x": 0, "y": 0, "z": 0, "role": "ROUTE", "instance": 0, "source": "native"},
            "target": {"id": "target", "x": 12000, "y": 0, "z": 0, "role": "FARMING", "instance": 0, "source": "native"},
            "floor": {"id": "floor", "x": 12000, "y": 0, "z": -5000, "role": "FARMING", "instance": 0, "source": "native"},
        }
        components = {"root": "root-component", "target": "target-component", "floor": "floor-component"}
        candidates = final_geo.adaptive_candidates(anchors, components, {"root"}, {"target-component"}, max_checks=2)
        self.assertEqual(len(candidates), 2)
        self.assertEqual({row["search_radius"] for row in candidates}, {"16384"})
        self.assertEqual({row["validation_status"] for row in candidates}, {"UNPROVEN"})
        self.assertNotIn("floor", {row["to_id"] for row in candidates})

    def test_radius_cannot_publish_a_bridge_without_geodata_proof(self):
        candidate = {"connector_id": "bridge.one", "from_id": "root", "to_id": "farm", "search_radius": "32768", "candidate_rank": "1"}
        self.assertEqual(final_geo.proven_bridges([candidate], [{"connector_id": "bridge.one", "validation_status": "NO_PATH", "path_length": "0", "path_segments": "0"}]), [])
        self.assertEqual(len(final_geo.proven_bridges([candidate], [{"connector_id": "bridge.one", "validation_status": "VALID_PATH", "path_length": "14000", "path_segments": "4"}])), 1)

    def test_one_way_geodata_proof_publishes_one_direction(self):
        row = {"connector_id": "bridge.one", "from_id": "root", "to_id": "farm", "from_instance": "0", "to_instance": "0", "path_length": "123", "path_segments": "1", "validation_status": "VALID_DIRECT", "source_refs": "data/spawns/one.xml"}
        root = ET.fromstring(final_geo.bridge_xml([row], {"root": "root.node", "farm": "farm.node"}))
        edges = root.findall("edge")
        self.assertEqual(len(edges), 1)
        self.assertEqual(edges[0].get("fromAnchorId"), "root")
        self.assertEqual(edges[0].get("toAnchorId"), "farm")
        self.assertEqual(edges[0].get("bidirectional"), "false")
        self.assertEqual(edges[0].get("baseTravelMillis"), "1230")

    def test_closed_floor_door_proximity_does_not_admit_entrance(self):
        candidate = {"coverage_key": "closed", "source_geometry": "territory:-5500:-5300:100,100|200,100|200,200|100,200", "source_refs": "data/spawns/Catacombs/Room.xml"}
        doors = [{"id": "floor-one", "x": 150, "y": 150, "z": -5400, "status": "close"}, {"id": "other-floor", "x": 150, "y": 150, "z": -2400, "status": "open"}]
        row = final_geo.closed_access_row(candidate, doors)
        self.assertEqual(row["door_ids"], "floor-one")
        self.assertEqual(row["entrance_status"], "UNPROVEN")
        self.assertEqual(row["geodata_status"], "NOT_PROBED_CLOSED")
        self.assertEqual(row["inside_anchor"], "")

    def test_final_witness_requires_admitted_active_path(self):
        roots = {"dwarf": ["ingress"]}
        farms = [{"coverage_key": "farm", "anchor": "farm.anchor", "minimum": 6, "maximum": 10}]
        rows = final_geo.reachability_rows(roots, farms, [], [], [])
        global_band = next(row for row in rows if row["start_family"] == "GLOBAL" and row["level_band"] == "6-10")
        self.assertEqual(global_band["final_reachable"], 0)
        self.assertEqual(global_band["witness_coverage_key"], "")
        rows = final_geo.reachability_rows(roots, farms, [("ingress", "farm.anchor", "geo.bridge")], [], [])
        global_band = next(row for row in rows if row["start_family"] == "GLOBAL" and row["level_band"] == "6-10")
        self.assertEqual(global_band["final_reachable"], 1)
        self.assertEqual(global_band["witness_coverage_key"], "farm")


if __name__ == "__main__":
    unittest.main()
