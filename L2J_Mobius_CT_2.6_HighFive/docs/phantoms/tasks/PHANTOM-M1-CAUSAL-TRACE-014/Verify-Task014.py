"""Exact TASK014 scope, recorder static invariants and two separate encoding checks."""
import re
import subprocess
import csv
import json
from pathlib import Path

TASK = Path(__file__).resolve().parent
ROOT = TASK.parents[4]
MODULE = 'L2J_Mobius_CT_2.6_HighFive/'
CODE = [
    'java/org/l2jmobius/gameserver/phantoms/diagnostics/PhantomRuntimeFlightRecorder.java',
    'java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java',
    'java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java',
    'java/org/l2jmobius/gameserver/phantoms/activity/PhantomReconcileFirstActivityPort.java',
    'java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationServiceActivityPort.java',
    'java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java',
    'java/org/l2jmobius/gameserver/localplay/LocalPlayPilotActions.java',
    'java/org/l2jmobius/gameserver/localplay/LocalPlayPilotProtocol.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomRuntimeFlightRecorderSuite.java',
]

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, encoding='utf-8')

task_prefix = str(TASK.relative_to(ROOT)).replace('\\', '/') + '/'
paths = [line[3:].strip('"') for line in git('status', '--porcelain', '--untracked-files=all').splitlines()]
paths = sorted(set(paths) | set(git('diff', '--name-only', 'ccacd6c5bf8fa1234a5559ece708ee3a35efd536').splitlines()))
unexpected = [p for p in paths if p not in [MODULE + c for c in CODE] and not p.startswith(task_prefix)]
if unexpected:
    raise SystemExit('SCOPE_FAIL: ' + repr(unexpected))
subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
recorder = (ROOT / MODULE / CODE[0]).read_text(encoding='utf-8')
for forbidden in ('synchronized', 'java.io', 'java.nio.file', 'DatabaseFactory', 'Thread.sleep', '.wait(', 'LockSupport', 'LOGGER'):
    if forbidden in recorder:
        raise SystemExit('RECORDER_STATIC_FAIL: ' + forbidden)
markers = 'Рџ Рќ Рћ Р• РЎ Р› Р¤ Рњ РЈ Рљ Рґ Рµ Р° Р» РЅ Рѕ СЏ С€ СЂ С‹ СЊ С‚ Сѓ С‡ С… С† �'.split()
escaped = re.compile(r'\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};')
moji, escapes = [], []
for path in paths:
    file = ROOT / path
    if not file.is_file() or file.suffix.lower() not in ('.java', '.py', '.ps1', '.md', '.tsv', '.json', '.log', '.txt'):
        continue
    content = file.read_text(encoding='utf-8-sig')
    for number, line in enumerate(content.splitlines(), 1):
        # The marker definition is technical scanner data, not a user-facing string.
        if file == Path(__file__).resolve() and line.startswith('markers = '):
            continue
        if any(marker in line for marker in markers):
            moji.append((path, number))
        if escaped.search(line):
            escapes.append((path, number))
print('SCOPE_PASS: exact code allowlist + TASK014 artifacts; changed paths=' + str(len(paths)))
print('RECORDER_STATIC_PASS: no I/O, waits, gameplay locks or logger')
print('MOJIBAKE_CHECK: ' + repr(moji))
print('ESCAPED_CYRILLIC_CHECK: ' + repr(escapes))
if moji or escapes:
    raise SystemExit(1)
trace_path = TASK / 'CAUSAL_TRACE.tsv'
if trace_path.exists():
    rows = list(csv.DictReader(trace_path.open(encoding='utf-8-sig'), delimiter='\t'))
    meta = json.loads((TASK / 'CAUSAL_META.json').read_text(encoding='utf-8'))
    seqs = [int(r['seq']) for r in rows]
    watched = json.loads(meta['watched'])
    assert len(rows) == int(meta['retained']) == 3739 and int(meta['dropped']) == 0
    assert seqs == list(range(1, int(meta['attempts']) + 1))
    assert len(watched) <= 8 and len(rows) <= 8192 and meta['active'] == 'false'
    assert all(None not in r and len(r) == 11 and len(r['reason']) <= 96 and (int(r['profileId']) == 0 or int(r['profileId']) in watched) for r in rows)
    assert sum(r['event']=='READY_ECOLOGY_DEFER' and r['reason']=='native_context.required:coalesced' for r in rows) == 223
    assert not any(r['event'] in ('READY_PASS', 'MATERIALIZE_CALL', 'MATERIALIZE_RESULT') or r['event'].startswith('MAT_') for r in rows)
    suspects = list(csv.DictReader((TASK / 'SUSPECT_COMMITS.tsv').open(encoding='utf-8'), delimiter='\t'))
    assert len(suspects) == 5 and len({r['sha'] for r in suspects}) == 5
    print('TRACE_EXPORT_PASS: 3739 contiguous events, dropped0, max8, bounded11 fields, stopped; 223 exact defer outcomes; five unique suspects')
