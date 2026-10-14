"""Freeze current bounded043 input bytes before starting the native episode."""
from pathlib import Path
import hashlib
import json
import datetime
import ast
import argparse

task = Path(__file__).resolve().parent
module = task.parents[3]
ops = module / '.phantom-local/ops043'
parser = argparse.ArgumentParser()
parser.add_argument('--attempt', type=int, choices=(1, 2), default=1)
parser.add_argument('--fixed-profile', type=int, default=0)
parser.add_argument('--run-label', choices=('A1', 'A2', 'B1', 'B2', 'C1', 'D1'))
args = parser.parse_args()
output = task / f'evidence/SOURCE_PINS043-{args.run_label or ("A" + str(args.attempt))}.json'
assert not output.exists()
paths = sorted([p for p in ops.iterdir() if p.suffix in ('.ps1', '.java', '.py') and not p.name.startswith('Contract032ObserverECF')])
paths += sorted(p for p in task.iterdir() if p.suffix in ('.md', '.ps1', '.py', '.tsv'))
paths += [module / 'test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java',
    module / '.phantom-local/contract043a/runtime/libs/GameServer.jar',
    module / '.phantom-local/contract043a/runtime/libs/LoginServer.jar']
for path in paths:
    if path.suffix == '.py':
        ast.parse(path.read_text(encoding='utf-8-sig'), filename=str(path))
pins = [dict(path=str(p), sha256=hashlib.sha256(p.read_bytes()).hexdigest(), bytes=p.stat().st_size) for p in paths]
value = dict(contract='TASK043_PROSPECTIVE_INPUT_FREEZE', utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
    normalOnly=True, positiveAttempt=args.attempt, fixedProfileId=args.fixed_profile, profileReplacementAllowed=False, pins=pins)
with output.open('x', encoding='utf-8') as stream:
    json.dump(value, stream, ensure_ascii=False, indent=2)
print(f'Run A{args.attempt} input freeze: {len(pins)} exact paths; Python syntax valid; no scene outcome yet.')
