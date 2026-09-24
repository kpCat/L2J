import unittest

from live002_40_51_final import route


class Live002FinalRouteTest(unittest.TestCase):
    def test_two_sequential_gk_edges_reconstruct_in_order(self):
        edges = [("ingress", "first", "BACKGROUND", "walk1"), ("first", "second", "NORMAL_GATEKEEPER", "gk1"), ("second", "third", "NORMAL_GATEKEEPER", "gk2"), ("third", "farm", "BACKGROUND", "walk2")]
        self.assertEqual(["walk1", "gk1", "gk2", "walk2"], [step[3] for step in route("ingress", "farm", edges)])
        self.assertIsNone(route("farm", "ingress", edges))


if __name__ == "__main__":
    unittest.main()
