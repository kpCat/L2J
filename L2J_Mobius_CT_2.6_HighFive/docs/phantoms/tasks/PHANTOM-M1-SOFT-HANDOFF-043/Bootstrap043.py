"""Stage exact normal042 analogues only into the new authorized043 namespace."""
from pathlib import Path
import hashlib
import json

TASK = Path(__file__).resolve().parent
MODULE = TASK.parents[3]
EXPECTED = Path(r'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive')
assert MODULE.resolve() == EXPECTED.resolve()
OPS = MODULE / '.phantom-local/ops043'
OLD = MODULE / '.phantom-local/ops042'
PREVIOUS = MODULE / 'docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042'
assert not OPS.exists(), 'No existing043 overwrite'
OPS.mkdir()
(TASK / 'evidence').mkdir(exist_ok=False)
pins = []

def stage(source, target):
    assert not target.exists()
    raw = source.read_bytes()
    text = raw.decode('utf-8-sig').replace('PHANTOM-M1-NATIVE-LIFE-042', 'PHANTOM-M1-SOFT-HANDOFF-043').replace('042', '043')
    if source.name == 'Prepare032.ps1':
        text = text.replace('7793+$portIndex', '7795+$portIndex').replace('2123+$portIndex', '2125+$portIndex').replace('9033+$portIndex', '9035+$portIndex')
        text = text.replace("if($Episode -ceq 'b')", "if($Episode -ceq 'a')")
    if source.name in ('Verify042.ps1', 'Check-LaneGuards042.ps1'):
        text = text.replace('contract043b', 'contract043a').replace('test_043b', 'test_043a')
    target.write_text(text, encoding='utf-8-sig' if source.suffix == '.ps1' else 'utf-8', newline='\n')
    pins.append(dict(source=str(source), sourceSha256=hashlib.sha256(raw).hexdigest(), target=str(target), targetSha256=hashlib.sha256(target.read_bytes()).hexdigest()))

names = ('Control032.ps1', 'Observe032.ps1', 'Contract032Observer.java', 'Read-SharedJson032.ps1',
         'ReadDryRoute032.java', 'ReadDryPath042.java', 'Select-Primary042.ps1',
         'All-Birth-Cut042.ps1', 'SplitSql039.py', 'Prepare032.ps1', 'Invoke-Git042.ps1',
         'Build-StockShutdown042.ps1', 'Stop-ExactOwnedGracefully.ps1', 'RequestShutdown.java', 'RuntimeShutdownAgent.java')
for name in names:
    stage(OLD / name, OPS / name.replace('042', '043'))
for name in ('Verify042.ps1', 'Check-LaneGuards042.ps1', 'Read-Frame042.ps1'):
    stage(PREVIOUS / name, TASK / name.replace('042', '043'))
(OPS / 'validation').mkdir()
for source in (OLD / 'validation').iterdir():
    if source.is_file() and source.suffix in ('.py', '.java'):
        stage(source, OPS / 'validation' / source.name.replace('042', '043'))
(OPS / 'staged-inputs.json').write_text(json.dumps(pins, indent=2), encoding='utf-8')
assert 'TASK043_NORMAL_ONLY' in (OPS / 'Control032.ps1').read_text(encoding='utf-8-sig')
assert 'TASK043_NORMAL_ONLY' in (OPS / 'Contract032Observer.java').read_text(encoding='utf-8')
print(f'043 normal-only sources staged: {len(pins)}; no DB/runtime started; protected042 unmodified.')
