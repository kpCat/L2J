"""Read actual committed189 producers from stopped A2; never promote them to G4."""
from pathlib import Path
import sys
import json
import hashlib
import subprocess

task = Path(__file__).resolve().parent
module = task.parents[3]
ops = module / '.phantom-local/ops043'
sys.path.insert(0, str(ops / 'validation'))
from raw_archive043 import raw_path
startup = task / 'evidence/STARTUP032a-6c974a75380a490db65bd500dcfd565a'
ledger = [json.loads(line) for line in (startup / 'lifetimes.jsonl').read_text(encoding='utf-8-sig').splitlines()]
assert ledger[-1]['kind'] == 'RUN_END' and ledger[-1]['dropped'] == 0
records = []
for row in ledger:
    if row['kind'] != 'TRANSACTION_COMMIT039' or row.get('profile_id') != 189:
        continue
    path = raw_path(startup, row)
    p = dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line)
    if p.get('componentName') in ('background.state', 'background.catchup'):
        assert p['commitOutcome'] == 'SUCCESS' and p['source'] == 'actual-owning-jdbc-commit'
        records.append((path, p))
assert records, 'Actual TRANSACTION_COMMIT039 records required; empty filter is not evidence'
input_file = ops / 'BACKGROUND189043_V2-input.txt'
with input_file.open('x', encoding='utf-8') as stream:
    stream.write(''.join(str(path.resolve()) + '\n' for path, _ in records))
jar = module / '.phantom-local/contract043a/runtime/libs/GameServer.jar'
command = [r'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe', '-cp', str(jar) + ';' + str(jar.parent / '*'), str(task / 'Decode-LifeReceipts043.java'), str(input_file)]
result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8')
(ops / 'BACKGROUND189043_V2-decoded.tsv').write_text(result.stdout + result.stderr, encoding='utf-8')
assert result.returncode == 0, result.stderr
decoded = {}
for line in result.stdout.splitlines():
    fields = line.split('\t')
    decoded.setdefault(fields[0], {})[fields[1]] = fields[2:]
proof = [dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
    producer=p['producer'], component=p['componentName'], commitNanos=p['commitNanos'],
    beforeVersion=p['beforeRowVersion'], afterVersion=p['afterRowVersion'], decoded=decoded.get(str(path.resolve()), {}))
    for path, p in records]
value = dict(contract='TASK043_A2_189_ACTUAL_BACKGROUND', profileId=189,
    G4='NOT_VERIFIED_NO_SOFT_AWAY_MEASUREMENT', recordCount=len(proof), records=proof,
    inputRejected='189 DEAD; not in natural living World cohort; no substitute',
    currentReadyCandidatesWithin15MinuteDebt=0, minimumReadyDebtMinutes=3864,
    noSQLWrites=True, command=command)
value['priorV1'] = 'Wrong COMMIT039 filter yielded0; retained diagnostic, not credited'
with (task / 'evidence/BACKGROUND189043_V2.json').open('x', encoding='utf-8') as stream:
    json.dump(value, stream, ensure_ascii=False, indent=2)
print(f'189 committed background records={len(proof)}; G4 remains NOT_VERIFIED; exact producer payloads decoded.')
