"""Exact TASK021 artifact guard, with independent mojibake and escaped-Cyrillic checks."""
import json
from pathlib import Path
import re
import subprocess
import sys

root = Path(sys.argv[1]).resolve()
module = 'L2J_Mobius_CT_2.6_HighFive/'
task_relative = module + 'docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/'
task = root / task_relative
sources = [
    module + 'java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java',
    module + 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
    module + 'java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java',
    module + 'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java',
    module + 'java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java',
    module + 'test/java/org/l2jmobius/tests/phantoms/PhantomVisibleIntentRecoverySuite.java',
    module + 'test/java/org/l2jmobius/tests/phantoms/PhantomLocalFarmRecoverySuite.java',
]
names = '''ACCEPTANCE.md DESIGN.md GOAL.md HANDOFF.md PACKAGE_MANIFEST.json PATCH_GUIDE.md PLAN.md REVIEW_NOTES.md RUNBOOK.md SCENARIOS.md SOURCE_MAP.tsv TASK.md
PRESTATE021.json Inspect-Prestate021.py ENGINEERING_REVIEW.md PROGRESS.md RESULT.md GIT_USAGE.md Run-Engineering.ps1
RED-COMPILE.log RED-LOCAL-COMPILE.log RED.log RED-PhantomVisibleIntentRecoverySuite.log RED-PhantomLocalFarmRecoverySuite.log GREEN-COMPILE.log GREEN-PhantomVisibleIntentRecoverySuite.log GREEN-PhantomLocalFarmRecoverySuite.log REGRESSIONS-PhantomVisibleDecisionAdmissionSuite.log REGRESSIONS-PhantomNativeContextHandoffSuite.log population-ecology-goal033-GREEN.log population-ecology-handoff-regression-GREEN.log background-lifecycle-GREEN.log decision-core-GREEN.log decision-persistence-GREEN.log normal-gatekeeper-travel-GREEN.log m1-native-lifecycle-GREEN.log recorder-GREEN.log BUILD.log
Prepare-Runtime.ps1 Restore-Private-Catalogs.py Stop-and-Verify.ps1 Verify-Scope021.py ARTIFACT_ALLOWLIST.json SCOPE_ENCODING.log
BASE-normal-gatekeeper-travel.log BASE-m1-native-lifecycle.log
CONFIG_OVERRIDES.tsv RUNTIME_PREPARE.log RUNTIME_START.log RUNTIME_READY.log RUNTIME_PRECHECK.log PUBLICATION.json Observe-Visible021.ps1 OBSERVE021.log EVIDENCE021.json LOGOUT_BEFORE_STOP.tsv LOGOUT_AFTER_STOP.tsv GRACEFUL_STOP.log STOP_VERIFY.log'''.split()
expected = set(sources + [task_relative + name for name in names])
def git(*args): return subprocess.run(['git', '-C', str(root), *args], check=True, capture_output=True, text=True).stdout
changed = set(git('diff', '--name-only', '2bf2936083bf6b081104c3e03c254ab8f07afc87').splitlines())
assert changed <= expected, ('Foreign tracked changes', sorted(changed - expected))
assert {path.relative_to(task).as_posix() for path in task.rglob('*') if path.is_file()} <= set(names), 'Unexpected TASK021 artifact'
assert len([path for path in changed if '/java/' in path and '/test/' not in path]) == 5
git('diff', '--check')
artifacts = sources + [task_relative + name for name in names if (root / (task_relative + name)).is_file()]
if task_relative + 'ARTIFACT_ALLOWLIST.json' not in artifacts: artifacts.append(task_relative + 'ARTIFACT_ALLOWLIST.json')
(task / 'ARTIFACT_ALLOWLIST.json').write_text(json.dumps({'base': '2bf2936083bf6b081104c3e03c254ab8f07afc87', 'exactPaths': sorted(artifacts)}, indent=2) + '\n', encoding='utf-8')
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
print('EXACT_SCOPE=PASS; source files=7; semantic production=5; artifacts=' + str(len(artifacts)))
