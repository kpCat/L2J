"""Reuse reviewed exact publication/ZIP owners; freeze only bounded043 source family."""
from pathlib import Path
import hashlib
import json

task = Path(__file__).resolve().parent
module = task.parents[3]
old = module / 'docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042'
ops = module / '.phantom-local/ops043'
for name in ('Publish042.py', 'Build-ReviewZip042.py'):
    target = task / name.replace('042', '043')
    assert not target.exists()
    text = (old / name).read_text(encoding='utf-8-sig').replace('042', '043')
    text = text.replace('a5674811479d087ec7f67ca62921ffa85d298263', '9a378d0e8984b3673b41538a9f5f9c9823507317')
    text = text.replace('TASK043: preserve native G2 proof and honest G4 combat failure', 'phantom(task-043): preserve bounded native handoff proofs and gates')
    text = text.replace('TASK043: verify compact review delivery and final gate reports', 'phantom(task-043): verify final review publication receipts')
    target.write_text(text, encoding='utf-8', newline='\n')
print('Exact ordinary publisher and CRC/member-SHA ZIP builder staged; no Git mutation.')
