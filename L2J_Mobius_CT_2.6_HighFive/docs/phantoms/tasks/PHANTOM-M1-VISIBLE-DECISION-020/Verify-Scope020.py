"""Exact TASK020 artifact guard, with independent mojibake and escaped-Cyrillic checks."""
import json
from pathlib import Path
import re
import subprocess
import sys

root = Path(sys.argv[1]).resolve()
module = 'L2J_Mobius_CT_2.6_HighFive/'
task_relative = module + 'docs/phantoms/tasks/PHANTOM-M1-VISIBLE-DECISION-020/'
task = root / task_relative
sources = [
    module + 'java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java',
    module + 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
    module + 'java/org/l2jmobius/gameserver/localplay/LocalPlayPilotActions.java',
    module + 'test/java/org/l2jmobius/tests/phantoms/PhantomVisibleDecisionAdmissionSuite.java',
    module + 'test/java/org/l2jmobius/tests/phantoms/PhantomNativeContextHandoffSuite.java',
]
names = '''ACCEPTANCE.md CONTRACT.md DIAGNOSTICS.md GIT.md GOAL.md HANDOFF.md MODEL.md PACKAGE_MANIFEST.json PLAN.md README.md RESULT_TEMPLATE.md SOURCE_MAP.tsv TESTS.md
ADMISSION_PRESTATE.json ADMISSION_PRESTATE.md Inspect-Prestate020.py ENGINEERING_REVIEW.md PROGRESS.md RESULT.md GIT_USAGE.md Run-Engineering.ps1
RED-COMPILE.log RED.log GREEN-COMPILE.log GREEN.log TASK018-GREEN.log population-ecology-goal033-GREEN.log population-ecology-handoff-regression-GREEN.log background-lifecycle-GREEN.log decision-core-GREEN.log decision-persistence-GREEN.log recorder-GREEN.log BUILD.log
Prepare-Runtime.ps1 Restore-Private-Catalogs.py Stop-and-Verify.ps1 Verify-Scope020.py ARTIFACT_ALLOWLIST.json SCOPE_ENCODING.log
CONFIG_OVERRIDES.tsv RUNTIME_PREPARE.log RUNTIME_START.log RUNTIME_READY.log RUNTIME_PRECHECK.log PUBLICATION.json Observe-Visible020.ps1 OBSERVE020.log EVIDENCE020.json LOGOUT_BEFORE_STOP.tsv LOGOUT_AFTER_STOP.tsv GRACEFUL_STOP.log STOP_VERIFY.log'''.split()
expected = set(sources + [task_relative + name for name in names])
def git(*args): return subprocess.run(['git', '-C', str(root), *args], check=True, capture_output=True, text=True).stdout
changed = set(git('diff', '--name-only', '29f4b32509535bad5d73d93bbf7fac1daf6b6b53').splitlines())
assert changed <= expected, ('Foreign tracked changes', sorted(changed - expected))
assert {path.relative_to(task).as_posix() for path in task.rglob('*') if path.is_file()} <= set(names), 'Unexpected TASK020 artifact'
assert len([path for path in changed if '/java/' in path and '/test/' not in path]) == 3
git('diff', '--check')
artifacts = sources + [task_relative + name for name in names if (root / (task_relative + name)).is_file()]
if task_relative + 'ARTIFACT_ALLOWLIST.json' not in artifacts: artifacts.append(task_relative + 'ARTIFACT_ALLOWLIST.json')
(task / 'ARTIFACT_ALLOWLIST.json').write_text(json.dumps({'base': '29f4b32509535bad5d73d93bbf7fac1daf6b6b53', 'exactPaths': sorted(artifacts)}, indent=2) + '\n', encoding='utf-8')
# Codepoint pairs keep the verifier's own technical marker catalog out of its input matches.
pairs = [(1056,1119),(1056,1117),(1056,1118),(1056,8226),(1056,1038),(1056,8250),(1056,164),(1056,1114),(1056,1032),(1056,1113),(1056,1169),(1056,181),(1056,176),(1056,187),(1056,1029),(1056,1109),(1057,1039),(1057,8364),(1057,1026),(1057,8249),(1057,1034),(1057,8218),(1057,1107),(1057,8225),(1057,8230),(1057,8224)]
markers = [''.join(map(chr, pair)) for pair in pairs] + [chr(65533)]
escaped = re.compile(r'\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};')
moji, escapes = [], []
for relative in artifacts:
    if relative.endswith('SCOPE_ENCODING.log'): continue
    text = (root / relative).read_text(encoding='utf-8-sig')
    if any(marker in text for marker in markers): moji.append(relative)
    if escaped.search(text): escapes.append(relative)
assert not moji, ('Mojibake markers', moji)
assert not escapes, ('Escaped Cyrillic', escapes)
print('MOJIBAKE_MARKERS_CHANGED_FILES=PASS')
print('ESCAPED_CYRILLIC_CHANGED_FILES=PASS')
print('EXACT_SCOPE=PASS; source files=5; semantic production=2; diagnostic production=1; artifacts=' + str(len(artifacts)))
