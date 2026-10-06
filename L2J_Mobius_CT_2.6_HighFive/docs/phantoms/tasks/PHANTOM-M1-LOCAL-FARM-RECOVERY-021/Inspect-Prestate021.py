"""Read-only retained observe020 components using repository binary codec fields."""
import csv
import hashlib
import io
import json
from pathlib import Path
import struct
import subprocess
import sys

class Reader:
    def __init__(self, data): self.data, self.pos = data, 0
    def take(self, count):
        result = self.data[self.pos:self.pos + count]
        assert len(result) == count
        self.pos += count
        return result
    def number(self, fmt): return struct.unpack('>' + fmt, self.take(struct.calcsize('>' + fmt)))[0]
    def text(self): return self.take(self.number('H')).decode('utf-8')

query = "SELECT profile_id,component_type,row_version,HEX(payload) AS payload_hex FROM phantom_profile_components WHERE profile_id=110 AND component_type IN ('background.catchup','goal.runtime','background.state') ORDER BY component_type"
option = r'C:\Users\ZBook\.codex\worktrees\m1-visible-decision-020\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\observe020\secrets\client.cnf'
result = subprocess.run([r'C:\Program Files\MariaDB 11.4\bin\mariadb.exe', '--defaults-extra-file=' + option, '--batch', 'l2jmobiush5_localplay_observe020', '-e', query], check=True, capture_output=True, text=True)
facts = {}
for row in csv.DictReader(io.StringIO(result.stdout), delimiter='\t'):
    data = bytes.fromhex(row['payload_hex'])
    r = Reader(data)
    kind = row['component_type']
    value = {'rowVersion': int(row['row_version']), 'payloadSha256': hashlib.sha256(data).hexdigest()}
    if kind == 'background.catchup':
        assert r.number('I') == 0x50424331 and r.number('I') == 1
        value['status'], value['requestId'] = r.text(), r.text()
        for key in ('seed','from','target','cursor','planOrdinal','intervalOrdinal','generation','knowledgeGeneration','topologyGeneration','goalId','goalRevision'): value[key] = r.number('q')
        value['planIdentity'], value['modelVersion'] = r.text(), r.number('I')
        for key in ('knowledge','topology','progression','commerce','failureReason'): value[key] = r.text()
        assert r.pos == len(data)
    elif kind == 'background.state':
        assert r.number('I') == 0x50424731
        r.take(4)
        value['state'] = ('MATERIALIZED','READY','VERIFY_PENDING','DEAD','INCONSISTENT')[r.number('B')]
    else:
        assert r.number('I') == 0x50475731
        r.take(4)
        value['goalId'], value['revision'], value['statusCode'], value['type'] = r.number('q'), r.number('q'), r.number('B'), r.text()
        def ref(): return [r.text(), r.text()]
        def optional_ref(): return ref() if r.number('B') else None
        def optional_text(): return r.text() if r.number('B') else None
        value['subject'], value['targetRef'] = optional_ref(), optional_ref()
        value['required'], value['current'] = r.number('q'), r.number('q')
        value['acquisitionMethod'] = optional_text()
        value['sources'] = [ref() for _ in range(r.number('B'))]
        value['anchor'] = optional_ref()
        value['purpose'], value['priority'] = r.text(), r.number('H')
        value['risk'], value['expense'], value['deadline'] = r.number('q'), r.number('q'), r.number('q')
        value['constraints'] = {r.text(): r.number('q') for _ in range(r.number('B'))}
        value['reason'] = r.text()
    facts[kind] = value
h, g = facts['background.catchup'], facts['goal.runtime']
assert h['status'] != 'COMPLETE' and h['goalId'] == g['goalId'] and h['goalRevision'] == g['revision'] and g['statusCode'] == 1
facts['normalAdmission'] = False
facts['postStopState'] = True
text = json.dumps(facts, indent=2, ensure_ascii=False) + '\n'
Path(sys.argv[1]).write_text(text, encoding='utf-8')
print(text)
