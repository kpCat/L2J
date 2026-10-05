import csv
import re
import subprocess
from pathlib import Path

task = Path(__file__).resolve().parent
module = task.parents[3]
repo = module.parent
source = module / 'java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java'
tests = module / 'test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java'
allowlist = {source.relative_to(repo).as_posix(), tests.relative_to(repo).as_posix()}
changed = subprocess.check_output(['git', 'diff', '--name-only'], cwd=repo, text=True).splitlines()
if not set(changed).issubset(allowlist):
    raise SystemExit('Production/test scope violation: ' + repr(changed))
rows = list(csv.DictReader((task / 'OWNERSHIP_CENSUS.tsv').open(encoding='utf-8'), delimiter='\t'))
assert len(rows) == 10000
assert len({r['profileId'] for r in rows}) == 10000
for profile in ('110', '175'):
    row = next(r for r in rows if r['profileId'] == profile)
    assert row['classification'] == 'ORPHAN_EXACT_ADOPTABLE'
    assert row['seedMatch'] == 'true'
    assert row['historicalFrom'] == row['ecologyCursor']
    assert int(row['historicalTarget']) > int(row['ecologyCursor'])
assert all(not r['ecologyRequestHash'] or re.fullmatch('[0-9a-f]{12}', r['ecologyRequestHash']) for r in rows)
assert all(not r['historicalRequestHash'] or re.fullmatch('[0-9a-f]{12}', r['historicalRequestHash']) for r in rows)
for name, total in [('ECOLOGY_GREEN_RESULTS.txt', 30), ('HANDOFF_GREEN_RESULTS.txt', 6)]:
    result = (task / name).read_text(encoding='utf-8-sig')
    assert f'total={total}\n' in result and f'passed={total}\n' in result and 'failed=0\n' in result
code = source.read_text(encoding='utf-8')
helper = code.split('private boolean recoverOrphanHistoricalOwnership', 1)[1].split('private int advanceRequest', 1)[0]
assert 'state.beginRequest(historical.requestId(), historical.targetEpochMinute())' in helper
assert '_historical.begin(' not in helper and '_historical.advance(' not in helper
assert 'advanceCalendar(' not in helper and 'completeRequest(' not in helper
assert '_store.load(profileId)' in helper and 'persist(profileId, ecology,' in helper
assert 'OWNERSHIP_RECOVERY_TURN) { break; }' in code
assert not re.search(r'\b(?:110|175)\b', helper)
print('CENSUS_SCOPE_HASHES_FROM_TARGET_SEED=PASS')
print('ECOLOGY_30_30_HANDOFF_6_6=PASS')
print('PRODUCTION_EXACT_ALLOWLIST_AND_RECOVERY_FENCES=PASS')
files = [source, tests] + [p for p in task.rglob('*') if p.is_file()]
markers = [chr(0x420) + chr(cp) for cp in (0x45f, 0x45c, 0x45b, 0x2022, 0x40e, 0x203a, 0x00a4, 0x45a, 0x408, 0x459, 0x491, 0xb5, 0xb0, 0xbb, 0x405, 0x455)]
markers += [chr(0x421) + chr(cp) for cp in (0x40f, 0x20ac, 0x402, 0x2039, 0x40a, 0x201a, 0x453, 0x2021, 0x2026, 0x2020)]
markers += [chr(0xfffd)]
for path in files:
    value = path.read_text(encoding='utf-8-sig')
    if any(marker in value for marker in markers):
        raise SystemExit('Mojibake marker: ' + str(path))
print('MOJIBAKE_MARKERS=PASS')
for path in files:
    if re.search(r'\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};', path.read_text(encoding='utf-8-sig')):
        raise SystemExit('Escaped Cyrillic: ' + str(path))
print('ESCAPED_CYRILLIC=PASS')
