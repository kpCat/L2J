"""Stage the existing offline proof checker; no acceptance rule changes."""
from pathlib import Path
import hashlib
import json

task = Path(__file__).resolve().parent
module = task.parents[3]
source = module / 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/proposals/ops041/validation'
target = module / '.phantom-local/ops042/validation'
target.mkdir(exist_ok=False)
names = ('commit_chain039.py', 'complete_chain041.py', 'raw_archive041.py',
         'verify_commit_run039.py', 'VerifyNativeCommit039.java', 'VerifyStoppedCut039.java',
         'DecodeStateReceipt041.java', 'test_commit_chain039.py', 'test_complete_chain041.py')
pins = []
for name in names:
    original = (source / name).read_bytes()
    text = original.decode('utf-8-sig').replace('041', '042')
    text = text.replace('(?:[abt]|x72|x73)', '[ab]')
    staged = target / name.replace('041', '042')
    staged.write_text(text, encoding='utf-8', newline='\n')
    pins.append({'source': str(source / name), 'sha256': hashlib.sha256(original).hexdigest(),
                 'staged': str(staged), 'stagedSha256': hashlib.sha256(staged.read_bytes()).hexdigest()})
(target / 'provenance.json').write_text(json.dumps(pins, indent=2), encoding='utf-8')
print('Staged nine existing validation owners; original039 chain limit4096 unchanged.')
