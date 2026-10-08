"""Tests for offline helpers only; these do not validate Mobius."""
import hashlib
from pathlib import Path
import tempfile
import unittest
from xml.sax.saxutils import quoteattr
from mailbox_audit import Expected, AuditError, inspect_mailbox
from window_budget import budget

RID='11111111-2222-4333-8444-555555555555'
SID='aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
OTHER='aaaaaaaa-bbbb-4ccc-8ddd-ffffffffffff'

class MailboxTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.root=Path(self.tmp.name)
        for d in ['results','inbox','processing','journal']: (self.root/d).mkdir()
        self.e=Expected(RID,SID,SID,12,'MOVE_SELF',101)
    def tearDown(self): self.tmp.cleanup()
    def xml(self, status='ACCEPTED', **extra):
        data=dict(version='1',requestId=RID,sessionId=SID,runId=SID,sequence='12',
                  operation='MOVE_SELF',actorObjectId='101',status=status,reason='ARRIVAL_PENDING')
        data.update(extra)
        body='<pilotResult '+' '.join(k+'='+quoteattr(v) for k,v in data.items())+' />'
        (self.root/'results'/f'{RID}.xml').write_text(body,encoding='utf-8')
    def request(self):
        body=f'<pilotRequest version="1" requestId="{RID}" sessionId="{SID}" runId="{SID}" sequence="12" operation="MOVE_SELF" />'
        (self.root/'processing'/f'{RID}.xml').write_text(body)
    def test_missing_is_unknown(self):
        r=inspect_mailbox(self.root,self.e); self.assertEqual(r['classification'],'RESULT_NOT_FOUND');self.assertFalse(r['replay_allowed'])
    def test_claim_does_not_prove_execution(self):
        (self.root/'journal'/f'{RID}.properties').write_text(f'requestId={RID}\nstate=CLAIMED\n')
        self.assertEqual(inspect_mailbox(self.root,self.e)['classification'],'CLAIMED_WITHOUT_RESULT')
    def test_processing_without_claim(self):
        self.request();self.assertEqual(inspect_mailbox(self.root,self.e)['classification'],'PROCESSING_OUTCOME_UNKNOWN')
    def test_accepted_does_not_mean_arrived(self):
        self.xml();r=inspect_mailbox(self.root,self.e)
        self.assertEqual(r['classification'],'ACTION_ACCEPTED_NOT_ARRIVED');self.assertFalse(r['gameplay_proven'])
    def test_native_uncertain_never_replay(self):
        self.xml('UNCERTAIN',reason='SYNTHETIC_NATIVE_EXCEPTION:x')
        r=inspect_mailbox(self.root,self.e);self.assertEqual(r['classification'],'SERVER_UNCERTAIN');self.assertFalse(r['replay_allowed'])
    def test_read_response(self):
        self.xml('SUCCEEDED',operation='STATUS',reason='SNAPSHOT')
        e=Expected(RID,SID,SID,12,'STATUS',101)
        self.assertEqual(inspect_mailbox(self.root,e)['classification'],'READ_RESULT_AVAILABLE')
    def test_successful_move_not_gameplay_proof(self):
        self.xml('SUCCEEDED');r=inspect_mailbox(self.root,self.e)
        self.assertEqual(r['classification'],'ACTION_RESULT_AVAILABLE');self.assertFalse(r['gameplay_proven'])
    def test_wrong_run_rejected(self):
        self.xml(runId=OTHER)
        with self.assertRaises(AuditError):inspect_mailbox(self.root,self.e)
    def test_wrong_sequence_rejected(self):
        self.xml(sequence='13')
        with self.assertRaises(AuditError):inspect_mailbox(self.root,self.e)
    def test_wrong_actor_rejected(self):
        self.xml(actorObjectId='102')
        with self.assertRaises(AuditError):inspect_mailbox(self.root,self.e)
    def test_result_late_does_not_dispatch_or_write(self):
        self.request();self.xml('SUCCEEDED')
        before={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in self.root.rglob('*') if p.is_file()}
        r=inspect_mailbox(self.root,self.e)
        after={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual(before,after);self.assertFalse(r['replay_allowed'])
    def test_oversize_not_truncated(self):
        (self.root/'results'/f'{RID}.xml').write_bytes(b'x'*65537)
        with self.assertRaises(AuditError):inspect_mailbox(self.root,self.e)
    def test_dtd_rejected(self):
        (self.root/'results'/f'{RID}.xml').write_text('<!DOCTYPE a [<!ENTITY b "c">]><pilotResult/>')
        with self.assertRaises(AuditError):inspect_mailbox(self.root,self.e)
    def test_duplicate_properties_rejected(self):
        (self.root/'journal'/f'{RID}.properties').write_text('state=CLAIMED\nstate=DONE\n')
        with self.assertRaises(AuditError):inspect_mailbox(self.root,self.e)
    def test_invalid_uuid_rejected(self):
        with self.assertRaises(AuditError):Expected('../bad',SID,SID,1,'STATUS',101)
    def test_temporary_result_not_accepted(self):
        (self.root/'results'/f'{RID}.xml.tmp').write_text('<pilotResult/>')
        r=inspect_mailbox(self.root,self.e);self.assertEqual(r['classification'],'RESULT_NOT_FOUND');self.assertTrue(r['temporary_result_present'])
    def test_namespace_rejected(self):
        (self.root/'results'/f'{RID}.xml').write_text('<pilotResult xmlns="other"/>')
        with self.assertRaises(AuditError):inspect_mailbox(self.root,self.e)
    def test_symlink_rejected(self):
        target=self.root/'source.xml';target.write_text('<pilotResult/>')
        try:(self.root/'results'/f'{RID}.xml').symlink_to(target)
        except OSError:self.skipTest('symlinks unavailable')
        with self.assertRaises(AuditError):inspect_mailbox(self.root,self.e)

class BudgetTests(unittest.TestCase):
    def test_380s_window_fits(self):
        r=budget(0,{'setup':60,'scene':380},commands=10);self.assertTrue(r['fits']);self.assertEqual(r['headroom_seconds'],0)
    def test_elapsed_counts_from_native_start(self):
        self.assertFalse(budget(20,{'setup':60,'scene':380},commands=10)['fits'])
    def test_too_many_commands(self):
        self.assertFalse(budget(0,{'scene':380},commands=351)['fits'])
    def test_negative_time_rejected(self):
        with self.assertRaises(ValueError):budget(-1,{'scene':380},commands=1)
    def test_repeated_heartbeat_cannot_renew_budget(self):
        self.assertFalse(budget(500,{'tail':20},commands=1)['fits'])
    def test_nan_rejected(self):
        with self.assertRaises(ValueError):budget(float('nan'),{'scene':380},commands=1)
    def test_soft_plan(self):
        r=budget(0,{'setup':25,'near':30,'away':55,'background':60,'return':55,'farm':120},commands=180)
        self.assertTrue(r['fits']);self.assertGreater(r['headroom_seconds'],0)

if __name__=='__main__':unittest.main(verbosity=2)
