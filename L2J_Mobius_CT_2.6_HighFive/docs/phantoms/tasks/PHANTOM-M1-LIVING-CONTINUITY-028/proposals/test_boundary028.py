"""SQL evidence parser contracts only; these fixtures are not native proof."""
from pathlib import Path
import tempfile
import unittest
from boundary_sql028 import tables, native_fields_exact
import hashlib


class BoundarySqlContracts(unittest.TestCase):
    def test_query_boundaries_keep_actual_rows(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)/'window.tsv'
            path.write_text('captureNanos\t123\nQUERY\tSELECT DATABASE() AS database_name\ndatabase_name\nl2jmobiush5_localplay_contract028f\nQUERY\tSELECT state\nversion\tpayload\n42\tABCDEF\n', encoding='utf-8')
            result = tables(path)
            self.assertEqual(result['SELECT state'], [{'version':'42', 'payload':'ABCDEF'}])
            self.assertEqual(result['SELECT DATABASE() AS database_name'][0]['database_name'], 'l2jmobiush5_localplay_contract028f')

    def test_truncated_actual_sql_is_never_repaired(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)/'window.tsv'
            path.write_text('QUERY\tSELECT state\nversion\tpayload\n42\n', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Incomplete SQL row'):
                tables(path)

    def test_scalar_mismatch_cannot_hide_behind_equal_payload(self):
        p = {key:'1' for key in ('objectId','level','exp','sp','expBeforeDeath','x','y','z','heading','classIndex','classId','race','vitality','hp','maxHp','mp','maxMp','cp','maxCp')}
        p['inventoryHash'] = p['skillsHash'] = hashlib.sha256(b'').hexdigest()
        aliases = {'objectId':'charId','hp':'curHp','mp':'curMp','cp':'curCp','classId':'classid','vitality':'vitality_points'}
        row = {aliases.get(key,key):value for key,value in p.items() if key not in ('inventoryHash','skillsHash')}
        native = {'SELECT FROM characters c':[row],'SELECT FROM items ':[],'SELECT FROM character_skills ':[]}
        self.assertTrue(native_fields_exact(p,native))
        row['x'] = '2'
        self.assertFalse(native_fields_exact(p,native))


if __name__ == '__main__':
    unittest.main()
