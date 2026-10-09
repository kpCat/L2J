"""Tests only the offline receipt ledger, never Mobius or a native lifecycle."""
import copy
import unittest
from audit_lifetimes030 import audit


def lifetime(run='run-a', profile=1, epoch=1, start=2):
    common = dict(run_id=run, profile_id=profile, object_id=1000+profile, epoch=epoch)
    return [
        dict(common, seq=start, kind='REGISTER'),
        dict(common, seq=start+1, kind='PREPARED', receipt='a'*64),
        dict(common, seq=start+2, kind='FINALIZED', receipt='a'*64),
        dict(common, seq=start+3, kind='TERMINAL', receipt='a'*64,
             owner_state='DETACHED', permanent=True, outstanding=0, timers=0),
    ]


def run(n=1, name='run-a', same_profile=False):
    events=[dict(run_id=name, seq=1, kind='RUN_START', code_sha='b'*40, incarnation='pid@start')]
    for i in range(n):
        events += lifetime(name, 1 if same_profile else i+1, i+1, 2+4*i)
    events += [dict(run_id=name, seq=2+4*n, kind='RUN_END', native_births=n,
                    dropped=0, failure='', exporter_drained=True)]
    return events


class LedgerTest(unittest.TestCase):
    def test_basic(self):
        self.assertTrue(audit(run())['coverage_pass'])
    def test_more_than_old_profile_and_lifetime_caps(self):
        r=audit(run(512)); self.assertTrue(r['coverage_pass']); self.assertEqual(r['registered'],512)
    def test_repeated_profile_epochs(self):
        r=audit(run(160,same_profile=True)); self.assertTrue(r['coverage_pass']); self.assertEqual(r['terminals'],160)
    def test_incarnation_namespace_not_epoch_alone(self):
        r=audit(run(name='run-a')+run(name='run-b')); self.assertTrue(r['coverage_pass']); self.assertEqual(r['registered'],2)
    def test_missing_register_is_not_reconstructed(self):
        e=run(); del e[1]; self.assertFalse(audit(e)['coverage_pass'])
    def test_final_without_prepare(self):
        e=run(); del e[2]; self.assertFalse(audit(e)['coverage_pass'])
    def test_temporary_seal_not_terminal(self):
        e=run(); e[-2]['owner_state']='SEALED'; e[-2]['permanent']=False
        self.assertFalse(audit(e)['coverage_pass'])
    def test_exact_duplicate_event(self):
        e=run(); e.insert(3,copy.deepcopy(e[2])); self.assertTrue(audit(e)['coverage_pass'])
    def test_conflicting_duplicate(self):
        e=run(); x=copy.deepcopy(e[2]); x['receipt']='c'*64; e.append(x)
        self.assertFalse(audit(e)['coverage_pass'])
    def test_missing_birth_counter(self):
        e=run(); e[-1]['native_births']=2; self.assertFalse(audit(e)['coverage_pass'])
    def test_exporter_failure_sticky(self):
        e=run(); e[-1]['failure']='capacity'; self.assertFalse(audit(e)['coverage_pass'])
    def test_no_run_end(self):
        self.assertFalse(audit(run()[:-1])['coverage_pass'])
    def test_unfinished_second_epoch_does_not_get_old_receipt(self):
        e=run(); e=e[:-1]+lifetime(epoch=2,start=10)[:1]+[dict(e[-1],seq=20,native_births=2)]
        self.assertFalse(audit(e)['coverage_pass'])
    def test_no_outstanding_at_terminal(self):
        e=run(); e[-2]['outstanding']=1; self.assertFalse(audit(e)['coverage_pass'])
    def test_nonempty_pending_prepare_prevents_terminal_pass(self):
        e=run(); e.insert(-2,dict(e[2],seq=100,receipt='c'*64))
        self.assertFalse(audit(e)['coverage_pass'])
    def test_bad_identity(self):
        e=run(); e[1]['profile_id']=-1; self.assertFalse(audit(e)['coverage_pass'])
    def test_reordered_files_keep_sequence_semantics(self):
        self.assertTrue(audit(list(reversed(run())))['coverage_pass'])
    def test_terminal_receipt_must_be_last_finalized(self):
        e=run(); e[-2]['receipt']='c'*64; self.assertFalse(audit(e)['coverage_pass'])

if __name__=='__main__': unittest.main()
