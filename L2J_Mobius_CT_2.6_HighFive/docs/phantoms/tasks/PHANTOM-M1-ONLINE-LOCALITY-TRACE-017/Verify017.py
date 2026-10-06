"""Exact artifact guard, diagnostic-only source proof and two encoding checks."""
import re
import subprocess
from pathlib import Path

TASK = Path(__file__).resolve().parent
ROOT = TASK.parents[4]
BASE = '0f16f29eff4d78d42a8f4da48fc0bc44ad466ad7'
PREFIX = 'L2J_Mobius_CT_2.6_HighFive/'
SOURCE = PREFIX + 'java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java'
TEST = PREFIX + 'test/java/org/l2jmobius/tests/phantoms/PhantomHumanLocalityTraceSuite.java'
ARTIFACTS = PREFIX + 'docs/phantoms/tasks/PHANTOM-M1-ONLINE-LOCALITY-TRACE-017/'
def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode('utf-8').strip()
paths = set(git('diff', BASE, '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
assert SOURCE in paths and TEST in paths, 'Required source/test absent'
assert all(p in (SOURCE, TEST) or p.startswith(ARTIFACTS) for p in paths), sorted(paths)
before = git('show', BASE + ':' + SOURCE).replace('\r\n', '\n')
after = (ROOT / SOURCE).read_text(encoding='utf-8').strip()
old = '\t\t\t\trecorder.watch(profileId);\n\t\t\t\trecorder.record(profileId, "LOCAL_CANDIDATE", online ? "ONLINE" : "OFFLINE", "", "topology.eligible", human.x(), human.y(), human.z());'
new = '\t\t\t\tif (online && recorder.isRecording())\n\t\t\t\t{\n\t\t\t\t\trecorder.watch(profileId);\n\t\t\t\t\trecorder.record(profileId, "LOCAL_CANDIDATE", "ONLINE", "", "topology.eligible", human.x(), human.y(), human.z());\n\t\t\t\t}'
assert before.count(old) == 1 and before.replace(old, new) == after, 'Non-diagnostic source changes'
print('EXACT_SCOPE_PASS', len(paths), 'production=1 test=1')
print('DIAGNOSTIC_ONLY_SOURCE_PASS')
pairs = [(0x420, n) for n in (0x45F,0x45C,0x45B,0x2022,0x40E,0x203A,0xA4,0x45A,0x408,0x459,0x491,0xB5,0xB0,0xBB,0x405,0x455)]
pairs += [(0x421, n) for n in (0x40F,0x20AC,0x402,0x2039,0x40A,0x201A,0x453,0x2021,0x2026,0x2020)]
markers = [chr(a)+chr(b) for a,b in pairs] + [chr(0xFFFD)]
escaped = re.compile(r'\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};')
for path in sorted(paths):
    content = (ROOT / path).read_text(encoding='utf-8-sig')
    assert not any(m in content for m in markers), 'Mojibake: '+path
print('MOJIBAKE_MARKERS_PASS')
for path in sorted(paths):
    assert not escaped.search((ROOT / path).read_text(encoding='utf-8-sig')), 'Escaped Cyrillic: '+path
print('ESCAPED_CYRILLIC_PASS')
subprocess.run(['git','diff',BASE,'--check'],cwd=ROOT,check=True)
print('DIFF_CHECK_PASS')
