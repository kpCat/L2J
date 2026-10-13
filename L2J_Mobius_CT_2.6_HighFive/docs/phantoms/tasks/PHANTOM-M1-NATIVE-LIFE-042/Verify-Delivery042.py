"""Read-only actual ZIP/source/publication verification after final commit."""
from pathlib import Path
import hashlib
import json
import zipfile

task = Path(__file__).resolve().parent
repo = task.parents[4]
digest = lambda data: hashlib.sha256(data).hexdigest()
path = task/'TASK042-review.zip'
receipt = json.loads((task/'ZIP_VERIFIED042.json').read_text(encoding='utf-8'))
assert digest(path.read_bytes()) == receipt['sha256']
assert (task/'TASK042-review.sha256').read_text(encoding='utf-8').split()[0] == receipt['sha256']
with zipfile.ZipFile(path) as archive:
    assert archive.testzip() is None
    hashes = json.loads(archive.read('HASHES042.json'))
    assert len(archive.namelist()) == len(set(archive.namelist())) == len(hashes)+1
    for name, row in hashes.items():
        data = archive.read(name)
        assert digest(data) == row['sha256'] and len(data) == row['bytes']
        assert data == (repo/name).read_bytes(), name
delivery = task/'DELIVERY042.json'
if delivery.exists():
    final = json.loads(delivery.read_text(encoding='utf-8'))
    assert final['HEAD'] == final['remote'] and final['trackedClean']
    assert final['zipSha256'] == receipt['sha256']
print(json.dumps(dict(zipSHA256=receipt['sha256'], actualMembers=len(hashes)+1,
      CRC='PASS', everyMemberSHA256='PASS', everyMemberMatchesOwnSource='PASS',
      finalPublication='PASS' if delivery.exists() else 'REQUIRED')))
