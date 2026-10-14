import unittest
from complete_chain043 import check_complete_chain


class CompleteChain043(unittest.TestCase):
    def setUp(self):
        self.key = dict(database='l2jmobiush5_phantom_test_043a', profileId=68,
                        objectId=268495600, component='background.state')
        self.edges = [{**self.key, 'beforeVersion': i, 'afterVersion': i + 1,
                       'beforeDigest': f'{i:064x}', 'afterDigest': f'{i+1:064x}',
                       'status': 'COMMITTED', 'producer': 'actual-owning-commit',
                       'operationId': f'actual-tx:{i}', 'evidenceId': f'commit-{i}'}
                      for i in range(6000)]
        self.origin = {**self.key, 'version': 0, 'digest': f'{0:064x}'}
        self.latest = {**self.key, 'version': 6000, 'digest': f'{6000:064x}'}

    def test_complete_original_edges_with_existing_page_budget(self):
        result = check_complete_chain(self.origin, self.latest, self.edges)
        self.assertEqual(('LINKED', 6000, False),
                         (result['status'], result['links'], result['fullSaveProven']))

    def test_boundary_hash_and_missing_original_edge_reject(self):
        self.edges[4096]['beforeDigest'] = 'f' * 64
        self.assertNotEqual('LINKED', check_complete_chain(self.origin, self.latest, self.edges)['status'])
        self.edges.pop(4096)
        self.assertNotEqual('LINKED', check_complete_chain(self.origin, self.latest, self.edges)['status'])

    def test_reused_actual_operation_across_pages_rejects(self):
        self.edges[5000]['operationId'] = self.edges[1]['operationId']
        self.assertEqual('INVALID', check_complete_chain(self.origin, self.latest, self.edges)['status'])


if __name__ == '__main__':
    unittest.main()
