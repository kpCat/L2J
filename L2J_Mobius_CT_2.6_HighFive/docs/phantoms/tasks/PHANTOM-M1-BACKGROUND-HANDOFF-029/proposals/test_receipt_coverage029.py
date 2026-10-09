import unittest
from copy import deepcopy
from receipt_coverage029 import audit

def event(kind, profile=872, epoch=100, **extra):
    return {'kind':kind,'profile':profile,'object':268000000+profile,'epoch':epoch,**extra}
def terminal_chain(profile=872,epoch=100,receipt='a'*64):
    return [event('ENROLL',profile,epoch),event('PREPARED',profile,epoch,receipt=receipt),
            event('FINALIZED',profile,epoch,receipt=receipt),
            event('SQL_VERIFIED',profile,epoch,receipt=receipt,proof='strict-independent-validator.json'),
            event('TERMINAL',profile,epoch,receipt=receipt,permanent_seal=True,owner_state='DETACHED')]

class CoverageTests(unittest.TestCase):
    def test_complete(self): self.assertTrue(audit(terminal_chain())['coverage_pass'])
    def test_switch_keeps_old_lifetime(self):
        a=terminal_chain(); b=terminal_chain(1272,200,'b'*64)
        rows=a[:2]+b[:2]+[{'kind':'SCENE_SELECT','scene':'B'}]+a[2:]+b[2:]
        out=audit(rows); self.assertTrue(out['coverage_pass']); self.assertEqual(out['lifetimes'],2)
    def test_missing_original_receipt_is_not_filled(self):
        out=audit([event('ENROLL')]+terminal_chain(1272,200,'b'*64)); self.assertFalse(out['coverage_pass'])
    def test_checkpoint_not_terminal(self): self.assertFalse(audit(terminal_chain()[:-1])['coverage_pass'])
    def test_distinct_epoch_not_merged(self):
        out=audit(terminal_chain()+[event('ENROLL',872,200)]); self.assertEqual(out['lifetimes'],2); self.assertFalse(out['coverage_pass'])
    def test_duplicate_same_event_not_extra(self):
        rows=terminal_chain();out=audit(rows[:2]+[deepcopy(rows[1])]+rows[2:]);self.assertTrue(out['coverage_pass']);self.assertEqual(out['receipts'],1)
    def test_finalize_without_prepare_rejected(self):
        with self.assertRaises(ValueError):audit([event('ENROLL'),event('FINALIZED',receipt='a'*64)])
    def test_unenrolled_hook_rejected(self):
        with self.assertRaises(ValueError):audit([event('PREPARED',receipt='a'*64)])
    def test_terminal_without_verified_sql(self):
        rows=terminal_chain();del rows[3];self.assertFalse(audit(rows)['coverage_pass'])
    def test_not_permanent(self):
        rows=terminal_chain();rows[-1]['permanent_seal']=False;self.assertFalse(audit(rows)['coverage_pass'])
    def test_not_detached(self):
        rows=terminal_chain();rows[-1]['owner_state']='SEALED';self.assertFalse(audit(rows)['coverage_pass'])
    def test_latest_receipt_required(self):
        rows=terminal_chain();rows.insert(4,event('PREPARED',receipt='b'*64));self.assertFalse(audit(rows)['coverage_pass'])
    def test_zero_epoch_rejected(self):
        with self.assertRaises(ValueError):audit([event('ENROLL',epoch=0)])
    def test_capacity_no_eviction(self):
        with self.assertRaises(ValueError):audit([event('ENROLL',i+1,i+1) for i in range(33)])
    def test_empty_is_not_pass(self):self.assertFalse(audit([])['coverage_pass'])
    def test_old_receipt_cannot_cover_new_epoch(self):
        rows=terminal_chain()+[event('ENROLL',872,200),event('PREPARED',872,200,receipt='a'*64)]
        with self.assertRaises(ValueError):audit(rows)
    def test_no_new_receipt_after_detach(self):
        with self.assertRaises(ValueError):audit(terminal_chain()+[event('PREPARED',receipt='b'*64)])
    def test_missing_proof_rejected(self):
        rows=terminal_chain();rows[3]['proof']=''
        with self.assertRaises(ValueError):audit(rows)
if __name__=='__main__':unittest.main()
