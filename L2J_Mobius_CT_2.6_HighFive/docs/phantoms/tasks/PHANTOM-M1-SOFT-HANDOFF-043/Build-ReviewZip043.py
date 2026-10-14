"""Build and read back a small exact allowlist ZIP, with CRC and every member SHA."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import os
import shutil
import zipfile

task = Path(__file__).resolve().parent
repo = task.parents[4]
assert str(repo).lower() == r'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius'.lower()
parser = argparse.ArgumentParser()
parser.add_argument('--replace-sha', default='')
args = parser.parse_args()
target = task / 'TASK043-review.zip'
digest = lambda data: hashlib.sha256(data).hexdigest()
if target.exists():
    assert args.replace_sha and digest(target.read_bytes()) == args.replace_sha.lower(), 'Exact own ZIP SHA required before replacing'
    backup = repo / 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops043' / ('TASK043-review-' + args.replace_sha[:16] + '.zip')
    if backup.exists():
        assert digest(backup.read_bytes()) == args.replace_sha.lower(), 'Existing own backup exact SHA'
    else:
        shutil.copyfile(target, backup)
else:
    assert not args.replace_sha, 'Initial ZIP absent'
excluded = {'TASK043-review.zip', 'TASK043-review.sha256', 'ZIP_VERIFIED043.json'}
relative = (task / 'REVIEW_ALLOWLIST043.txt').read_text(encoding='utf-8').splitlines()
members = {}
for name in relative:
    path = repo / name
    if path.name in excluded:
        continue
    if not path.exists():
        assert path.name in ('PUBLICATION1_043.json', 'GIT_COMMANDS043.json'), name
        continue
    assert path.stat().st_size < 350000, 'Bounded member size'
    members[name] = path.read_bytes()
hashes = {name: dict(sha256=digest(data), bytes=len(data)) for name, data in members.items()}
members['HASHES043.json'] = json.dumps(hashes, ensure_ascii=False, indent=2).encode('utf-8')
temporary = task / 'TASK043-review.building.zip'
assert not temporary.exists(), 'Do not overwrite unknown temporary output'
with zipfile.ZipFile(temporary, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name, data in sorted(members.items()):
        archive.writestr(name, data)
assert temporary.stat().st_size < 2_000_000, 'Compact archive maximum2MB'
with zipfile.ZipFile(temporary) as archive:
    assert archive.testzip() is None, 'Actual member CRC'
    names = archive.namelist()
    assert len(names) == len(set(names)) == len(members)
    assert all(not PurePosixPath(name).is_absolute() and '..' not in PurePosixPath(name).parts for name in names)
    actual_hashes = json.loads(archive.read('HASHES043.json'))
    for name, row in actual_hashes.items():
        data = archive.read(name)
        assert digest(data) == row['sha256'] and len(data) == row['bytes'], name
        assert data == (repo/name).read_bytes(), 'Actual archived source/evidence differs: '+name
    assert digest(archive.read('HASHES043.json')) == digest(members['HASHES043.json'])
os.replace(temporary, target)
sha = digest(target.read_bytes())
(task / 'TASK043-review.sha256').write_text(sha + '  TASK043-review.zip\n', encoding='utf-8')
result = dict(zip='TASK043-review.zip', sha256=sha, bytes=target.stat().st_size, members=len(members),
              CRC='PASS', memberSHA256='PASS', duplicateAndTraversal='PASS', maxBytes=2_000_000)
(task / 'ZIP_VERIFIED043.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
print(json.dumps(result))
