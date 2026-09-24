"""Focused LIVE-002 Plunderous source and bounded-cut controls."""

import unittest
from pathlib import Path

import plunderous_internal as subject


MODULE = Path(__file__).resolve().parents[2]


class PlunderousInternalTest(unittest.TestCase):
    def test_active_same_source_graph_and_exact_endpoints(self):
        nodes, edges = subject.graph(MODULE)
        self.assertEqual(20, len(nodes))
        self.assertEqual(34, sum(map(len, edges.values())))
        self.assertTrue(set(subject.STARTS + subject.GOALS) <= nodes.keys())
        self.assertTrue(all(node["zone"].startswith("PlunderousPlains_") for node in nodes.values()))
        self.assertTrue(all(nodes[goal]["level_min"] <= 40 <= nodes[goal]["level_max"]
                            for goal in subject.GOALS))
        self.assertIsNone(subject.bfs(edges)[0])

    def test_cap_blocked_goal_cut_with_optimistic_remaining_pairs(self):
        nodes, edges = subject.graph(MODULE)
        failed = [{"from_anchor_id": "generated.farm.e20a44e2413cc5ec51f0b15c.anchor",
                   "to_anchor_id": subject.GOALS[0],
                   "validation_status": "DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE"}]
        self.assertIsNone(subject.bfs(subject.optimistic_edges(nodes, edges, failed))[0])
        incoming = {goal: [source for source in nodes
                           if goal in dict(subject.eligible_neighbors(nodes, source))]
                    for goal in subject.GOALS}
        self.assertEqual([failed[0]["from_anchor_id"]], incoming[subject.GOALS[0]])
        self.assertEqual(["generated.farm.12923521d58d7301f63fd56a.anchor"], incoming[subject.GOALS[1]])
        self.assertTrue(all(len(subject.eligible_neighbors(nodes, source)) <= 6 for source in nodes))

    def test_unproven_direction_is_not_added_to_directed_graph(self):
        nodes, edges = subject.graph(MODULE)
        source = "generated.farm.e20a44e2413cc5ec51f0b15c.anchor"
        rejected = [{"from_anchor_id": source, "to_anchor_id": subject.GOALS[0],
                     "connector_id": "rejected", "validation_status": "DIRECT_BLOCKED_PATHFINDER_OUT_OF_RANGE"}]
        self.assertNotIn((subject.GOALS[0], "rejected"), subject.proven_edges(edges, rejected)[source])


if __name__ == "__main__":
    unittest.main()
