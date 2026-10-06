"""Read-only retained observe017 evidence; field order follows repository codecs."""
import csv
import hashlib
import io
import pathlib
import struct
import subprocess
import sys

class Reader:
    def __init__(self, value):
        self.data = bytes.fromhex(value)
        self.pos = 0
    def take(self, size):
        value = self.data[self.pos:self.pos + size]
        assert len(value) == size
        self.pos += size
        return value
    def number(self, fmt):
        return struct.unpack('>' + fmt, self.take(struct.calcsize('>' + fmt)))[0]
    def text(self):
        return self.take(self.number('H')).decode('utf-8')

query = "SELECT profile_id,component_type,row_version,HEX(payload) AS payload_hex FROM phantom_profile_components WHERE profile_id IN (110,142,175) ORDER BY profile_id,component_type"
result = subprocess.run([
    r'C:\Program Files\MariaDB 11.4\bin\mariadb.exe',
    r'--defaults-extra-file=C:\Users\ZBook\.codex\worktrees\m1-online-trace-017\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\observe017\secrets\client.cnf',
    '--batch', 'l2jmobiush5_localplay_observe017', '-e', query], check=True, capture_output=True, text=True)
facts = {}
for row in csv.DictReader(io.StringIO(result.stdout), delimiter='\t'):
    profile = facts.setdefault(row['profile_id'], {})
    kind = row['component_type']
    r = Reader(row['payload_hex'])
    values = {'rowVersion': int(row['row_version']), 'sha256': hashlib.sha256(r.data).hexdigest()}
    if kind == 'background.catchup':
        assert r.number('I') == 0x50424331 and r.number('I') == 1
        values['status'], values['request'] = r.text(), r.text()
        for name in ('seed','from','target','cursor','planOrdinal','intervalOrdinal','generation','knowledgeGeneration','topologyGeneration','goalId','goalRevision'):
            values[name] = r.number('q')
        values['planIdentity'], values['modelVersion'] = r.text(), r.number('I')
        for name in ('knowledge','topology','progression','commerce','failureReason'):
            values[name] = r.text()
        assert r.pos == len(r.data)
    elif kind == 'population.ecology':
        assert r.number('I') == 0x50454331
        values['catalogHash'], values['preset'] = r.text(), r.number('B')
        for name in ('generation','ordinal','assignedAt','virtualJoin','cursor','initialTarget'):
            values[name] = r.number('q')
        r.take(6)
        r.take(r.number('B') * 6)
        values['schedule'], values['disposition'] = r.text(), r.number('B')
        r.take(16)
        values['request'], values['target'] = r.text(), r.number('q')
        r.take(16)
        values['archiveReason'] = r.text()
        assert r.pos == len(r.data)
    elif kind == 'background.native-context':
        assert r.number('I') == 0x504e4331 and r.number('I') == 1
        values['profileId'], values['objectId'] = r.number('q'), r.number('i')
        r.take(12)
        values['phase'] = ('UNKNOWN','COMPLETED','PENDING')[r.number('B')]
        values['beforePoints'], values['beforeEligibility'] = r.number('i'), r.number('B')
        values['afterPoints'] = r.number('i')
        values['eligibility'] = ('UNKNOWN','SUPPORTED','VITALITY_REQUIRES_NATIVE')[r.number('B')]
        values['stateVersion'], values['stateDigest'] = r.number('q'), r.text()
        values['simulationEligible'] = values['phase'] == 'COMPLETED' and values['eligibility'] == 'SUPPORTED' and values['afterPoints'] == 1
    elif kind == 'background.state':
        assert r.number('I') == 0x50424731
        r.take(4)
        values['state'] = ('MATERIALIZED','READY','VERIFY_PENDING','DEAD','INCONSISTENT')[r.number('B')]
    elif kind == 'goal.runtime':
        assert r.number('I') == 0x50475731
        r.take(4)
        values['goalId'], values['revision'], values['statusCode'], values['type'] = r.number('q'), r.number('q'), r.number('B'), r.text()
    else:
        continue
    profile[kind] = values
rows = []
for profile_id, profile in facts.items():
    h, e, b, n, g = (profile[k] for k in ('background.catchup','population.ecology','background.state','background.native-context','goal.runtime'))
    assert h['status'] != 'COMPLETE' and h['request'] == e['request'] and h['from'] == e['cursor'] and h['target'] == e['target'] and e['disposition'] == 0
    assert h['goalId'] == g['goalId'] and h['goalRevision'] == g['revision'] and g['statusCode'] == 1 and b['state'] == 'READY'
    assert n['stateVersion'] == b['rowVersion'] and n['stateDigest'] == b['sha256'] and not n['simulationEligible']
    rows.append({'profileId': profile_id, 'requestId': h['request'], 'catchupStatus': h['status'], 'catchupVersion': h['rowVersion'], 'from': h['from'], 'target': h['target'], 'historicalCursor': h['cursor'], 'intervalOrdinal': h['intervalOrdinal'], 'ecologyVersion': e['rowVersion'], 'ecologyCursor': e['cursor'], 'ecologyTarget': e['target'], 'background': b['state'], 'backgroundVersion': b['rowVersion'], 'nativePhase': n['phase'], 'nativeEligibility': n['eligibility'], 'nativePoints': n['afterPoints'], 'goalId': g['goalId'], 'goalRevision': g['revision'], 'goalStatus': 'ACTIVE', 'relation': 'EXACT'})
assert set(facts) == {'110','142','175'}
stream = io.StringIO()
writer = csv.DictWriter(stream, fieldnames=rows[0].keys(), delimiter='\t', lineterminator='\n')
writer.writeheader()
writer.writerows(rows)
if len(sys.argv) == 2:
    pathlib.Path(sys.argv[1]).write_text(stream.getvalue(), encoding='utf-8')
print(stream.getvalue(), end='')
