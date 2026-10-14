"""Проверки вспомогательного структурного анализа, не тесты GameServer."""
import copy
import unittest
from commit_chain039 import check_chain


def stamp(v, h):
    return dict(database='owned039a', profileId=27, objectId=123,
                component='background.state', version=v, digest=h * 64)


def edge(v, a, b, **overrides):
    x = dict(database='owned039a', profileId=27, objectId=123,
             component='background.state', beforeVersion=v, beforeDigest=a * 64,
             afterVersion=v + 1, afterDigest=b * 64, status='COMMITTED',
             operationId=f'op-{v}', producer='existing-transaction',
             evidenceId=f'immutable-record-{v}')
    x.update(overrides)
    return x


class ChainTests(unittest.TestCase):
    def test_exact(self):
        self.assertEqual(check_chain(stamp(10, 'a'), stamp(10, 'a'), [])['status'], 'EXACT')

    def test_one_leg(self):
        self.assertEqual(check_chain(stamp(10, 'a'), stamp(11, 'b'), [edge(10,'a','b')])['status'], 'LINKED')

    def test_two_legs_unordered(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(12,'c'), [edge(11,'b','c'), edge(10,'a','b')])['status'], 'LINKED')

    def test_later_sql_without_witness_is_gap(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(11,'b'), [])['status'], 'GAP')

    def test_version_gap(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(12,'c'), [edge(11,'b','c')])['status'], 'GAP')

    def test_same_version_different_payload(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(10,'b'), [])['status'], 'CONFLICT')

    def test_before_digest_conflict(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(11,'c'), [edge(10,'b','c')])['status'], 'CONFLICT')

    def test_final_digest_conflict(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(11,'c'), [edge(10,'a','b')])['status'], 'CONFLICT')

    def test_duplicate_operation_conflict(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(12,'c'), [edge(10,'a','b'), edge(11,'b','c', operationId='op-10')])['status'], 'CONFLICT')

    def test_branched_version_conflict(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(11,'b'), [edge(10,'a','b'), edge(10,'a','c', operationId='different')])['status'], 'CONFLICT')

    def test_prepared_is_not_committed(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(11,'b'), [edge(10,'a','b', status='PREPARED')])['status'], 'INVALID')

    def test_unknown_is_not_committed(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(11,'b'), [edge(10,'a','b', status='COMMIT_OUTCOME_UNKNOWN')])['status'], 'INVALID')

    def test_missing_evidence_id(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(11,'b'), [edge(10,'a','b', evidenceId='')])['status'], 'INVALID')

    def test_wrong_database(self):
        x=stamp(11,'b'); x['database']='PLAY'
        self.assertEqual(check_chain(stamp(10,'a'), x, [edge(10,'a','b')])['status'], 'INVALID')

    def test_wrong_object(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(11,'b'), [edge(10,'a','b', objectId=999)])['status'], 'INVALID')

    def test_wrong_component(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(11,'b'), [edge(10,'a','b', component='goal.runtime')])['status'], 'INVALID')

    def test_backward_version(self):
        self.assertEqual(check_chain(stamp(11,'b'), stamp(10,'a'), [])['status'], 'INVALID')

    def test_version_jump_in_edge(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(12,'b'), [edge(10,'a','b', afterVersion=12)])['status'], 'INVALID')

    def test_invalid_hash(self):
        x=stamp(10,'a'); x['digest']='not-a-hash'
        self.assertEqual(check_chain(x, stamp(10,'a'), [])['status'], 'INVALID')

    def test_immutable_inputs(self):
        args=(stamp(10,'a'), stamp(11,'b'), [edge(10,'a','b')]); before=copy.deepcopy(args)
        check_chain(*args)
        self.assertEqual(args, before)

    def test_not_full_save_proof(self):
        value=check_chain(stamp(10,'a'), stamp(11,'b'), [edge(10,'a','b')])
        self.assertEqual(value['scope'], 'STRUCTURAL_CHAIN_ONLY')
        self.assertFalse(value['fullSaveProven'])

    def test_bounded(self):
        self.assertEqual(check_chain(stamp(10,'a'), stamp(11,'b'), [edge(10,'a','b')], maximum_links=0)['status'], 'INVALID')

    def test_duplicate_literal_edge_is_not_silently_deduplicated(self):
        e=edge(10,'a','b')
        self.assertEqual(check_chain(stamp(10,'a'), stamp(11,'b'), [e,e])['status'], 'CONFLICT')


if __name__ == '__main__':
    unittest.main(verbosity=2)
