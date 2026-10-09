"""Parser tests only. These synthetic rows are NOT GameServer evidence."""
import unittest
from audit_census031 import audit

def row(i,**kw):
    r=dict(profileId=str(i),objectId='42',state='READY',calendarOnline='true',
           admitted='false',farmAllowed='false',readinessComplete='false',
           firstGuard='native_context.required',sampleNanos=str(i*1000))
    r.update(kw)
    return r

class CensusTests(unittest.TestCase):
    def test_not_equivalent(self):
        a=audit([row(1),row(2,farmAllowed='true'),row(3,farmAllowed='true',readinessComplete='true')])
        self.assertEqual(3,a['ready_online']);self.assertEqual(1,a['old_setup_matches'])
        self.assertEqual(0,a['reported_admitted'])
    def test_keep_dead_and_inconsistent(self):
        a=audit([row(1,state='DEAD'),row(2,state='INCONSISTENT')])
        self.assertEqual(2,a['rows']);self.assertEqual(0,a['ready_online'])
    def test_offline(self):
        self.assertEqual(0,audit([row(1,calendarOnline='false')])['ready_online'])
    def test_duplicate(self):
        with self.assertRaises(ValueError):audit([row(1),row(1)])
    def test_missing(self):
        with self.assertRaises(ValueError):audit([{'profileId':'1'}])
    def test_bad_bool(self):
        with self.assertRaises(ValueError):audit([row(1,farmAllowed='maybe')])
    def test_empty(self):
        self.assertEqual(0,audit([])['rows'])
    def test_not_atomic(self):
        self.assertFalse(audit([row(1)])['atomic_global_snapshot'])
if __name__=='__main__':unittest.main()
