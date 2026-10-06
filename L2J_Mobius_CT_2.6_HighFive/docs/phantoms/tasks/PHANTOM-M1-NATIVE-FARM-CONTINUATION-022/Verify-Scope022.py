"""Exact TASK022 allowlist; independent source mojibake/escaped-Cyrillic checks."""
import json
from pathlib import Path
import re
import subprocess
import sys

root = Path(sys.argv[1]).resolve()
module = 'L2J_Mobius_CT_2.6_HighFive/'
task_relative = module + 'docs/phantoms/tasks/PHANTOM-M1-NATIVE-FARM-CONTINUATION-022/'
task = root / task_relative
base = '0205d04bc7763fafcbb776e6da8887c1f2912d8b'
sources = [module + value for value in (
    'java/org/l2jmobius/gameserver/model/actor/PlayerNativeEvidence.java',
    'java/org/l2jmobius/gameserver/model/actor/PlayerNativeWork.java',
    'dist/game/data/scripts/quests/Q00255_Tutorial/Q00255_Tutorial.java',
    'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
    'java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java',
    'java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java',
    'java/org/l2jmobius/gameserver/taskmanagers/AutoPlayTaskManager.java',
    'java/org/l2jmobius/gameserver/taskmanagers/AutoUseTaskManager.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomNativeFarmContinuation022Suite.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomAutoPlayOwnership022Suite.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomNativeEvidenceContinuation022Suite.java',
)]
names = '''ACCEPTANCE.md DESIGN.md GIT.md GOAL.md HANDOFF.md OBSERVABILITY.md PACKAGE_MANIFEST.json PATCH_GUIDE.md PLAN.md README.md ROOT_CAUSES.md RUNBOOK.md SCENARIOS.md SOURCE_MAP.tsv SOURCES.md START-CODEX.txt TASK.md
PROGRESS.md ROOT_CAUSE_PROOF.md PATCH_LEDGER.tsv REVIEW_NOTES.md ENGINEERING_REVIEW.md RESULT.md GIT_USAGE.md COVERAGE.tsv
Run-Engineering.ps1 Prepare-Runtime.ps1 Restore-Private-Catalogs.py Stop-and-Verify.ps1 Observe-Visible022.ps1 Build-Baseline-Probe.py Verify-Scope022.py
ARTIFACT_ALLOWLIST.json SCOPE_ENCODING.log THREAD_DUMP_HASHES.json THREAD_SUMMARY.md GOLDEN-NATIVE.txt TEST_GUARD_VERIFY.tsv RAW_LOG_HASHES.tsv TEST_RESULTS.tsv HANDOFF_RESULT.md
RED-COMPILE.log RED-COMPOSED.log RED-EVIDENCE.log RED-OWNERSHIP.log INVALID-OWNERSHIP-FIXTURE.log BASE-COMPOSED-MAGE.log BASE-COMPOSED-MELEE.txt COMPOSED-HANDLERS.log
ROUND1-COMPILE.log ROUND1-DIAGNOSTICS-COMPILE.log ROUND1-PhantomAutoPlayOwnership022Suite.log ROUND1-PhantomNativeEvidenceContinuation022Suite.log ROUND1-PhantomNativeFarmContinuation022Suite.log
GREEN-COMPILE.log GREEN-PhantomAutoPlayOwnership022Suite.log GREEN-PhantomNativeEvidenceContinuation022Suite.log GREEN-PhantomNativeFarmContinuation022Suite.log GREEN-PhantomVisibleIntentRecoverySuite.log GREEN-PhantomLocalFarmRecoverySuite.log GREEN-PhantomVisibleDecisionAdmissionSuite.log GREEN-PhantomNativeContextHandoffSuite.log
ENGINEERING.log population-ecology-goal033-GREEN.log population-ecology-handoff-regression-GREEN.log background-lifecycle-GREEN.log decision-core-GREEN.log decision-persistence-GREEN.log recorder-GREEN.log
native-ownership-GREEN.log native-native-phase-GREEN.log native-closure-GREEN.log native-loot-GREEN.log
ROUND2-STALE-RED.log ROUND2-COMPILE.log ROUND2-PhantomAutoPlayOwnership022Suite-GREEN.log ROUND2-PhantomNativeFarmContinuation022Suite-GREEN.log BASE-PROBE-COMPILE.log BASE-native-loot.log CONTROL-COMPILE.log CONTROL-GREEN.log
BUILD.log PUBLICATION.json CONFIG_OVERRIDES.tsv RUNTIME_PREPARE.log RUNTIME_START.log RUNTIME_READY.log RUNTIME_PRECHECK.log EVIDENCE022a.json OBSERVE022a.log LOGOUT_BEFORE_STOP.tsv LOGOUT_AFTER_STOP.tsv GRACEFUL_STOP.log STOP_VERIFY.log
EVIDENCE022b.json OBSERVE022b.log ROUND3-RED.log ROUND3-GREEN.log ROUND3-COMPILE.log BUILD022b.log PUBLICATION022b.json CONFIG_OVERRIDES022b.tsv RUNTIME_PREPARE022b.log RUNTIME_START022b.log RUNTIME_PRECHECK022b.log LOGOUT_BEFORE_STOP022b.tsv LOGOUT_AFTER_STOP022b.tsv GRACEFUL_STOP022b.log STOP_VERIFY022b.log ROUND3-REGRESSIONS.tsv'''.split()
expected = set(sources + [task_relative + name for name in names])
def git(*args):
    return subprocess.run(['git', '-C', str(root), *args], check=True, capture_output=True, text=True).stdout
changed = set(git('diff', '--name-only', base).splitlines())
untracked = set(git('ls-files', '--others', '--exclude-standard').splitlines())
assert (changed | untracked) <= expected, ('Foreign scope', sorted((changed | untracked) - expected))
assert {path.relative_to(task).as_posix() for path in task.rglob('*') if path.is_file()} <= set(names), 'Unexpected task artifact'
assert len([path for path in changed if ('/java/' in path and '/test/' not in path) or '/dist/game/data/scripts/quests/Q00255_Tutorial/' in path]) <= 8
actual = sources + [task_relative + name for name in names if (root / (task_relative + name)).is_file()]
if task_relative + 'ARTIFACT_ALLOWLIST.json' not in actual: actual.append(task_relative + 'ARTIFACT_ALLOWLIST.json')
(task / 'ARTIFACT_ALLOWLIST.json').write_text(json.dumps({'base': base, 'exactPaths': sorted(actual)}, indent=2) + '\n', encoding='utf-8')
# Raw logs preserve original output; source/report checks never silently repair raw evidence.
checked = [path for path in actual if not path.endswith(('.log', 'GOLDEN-NATIVE.txt', 'BASE-COMPOSED-MELEE.txt'))]
git('diff', '--check', '--', *checked)
pairs = [(1056,1119),(1056,1117),(1056,1118),(1056,8226),(1056,1038),(1056,8250),(1056,164),(1056,1114),(1056,1032),(1056,1113),(1056,1169),(1056,181),(1056,176),(1056,187),(1056,1029),(1056,1109),(1057,1039),(1057,8364),(1057,1026),(1057,8249),(1057,1034),(1057,8218),(1057,1107),(1057,8225),(1057,8230),(1057,8224)]
markers = [''.join(map(chr, pair)) for pair in pairs] + [chr(65533)]
escaped = re.compile(r'\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};')
moji, escapes, raw = [], [], []
for relative in actual:
    text = (root / relative).read_text(encoding='utf-8-sig')
    bad = [kind for kind, present in [('mojibake', any(marker in text for marker in markers)), ('escaped-cyrillic', bool(escaped.search(text)))] if present]
    if bad and relative not in checked: raw.append((relative, bad)); continue
    if 'mojibake' in bad: moji.append(relative)
    if 'escaped-cyrillic' in bad: escapes.append(relative)
assert not moji, ('Mojibake markers', moji)
assert not escapes, ('Escaped Cyrillic', escapes)
print('MOJIBAKE_MARKERS_CHANGED_FILES=PASS')
print('ESCAPED_CYRILLIC_CHANGED_FILES=PASS')
print('RAW_LOG_ENCODING_MATCHES_PRESERVED=' + repr(raw))
print('EXACT_SCOPE=PASS; production<=8 (round3 confirmed RED); standalone tests=3; exact paths=' + str(len(actual)))
