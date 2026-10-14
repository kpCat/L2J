"""Verify the accepted G2 proofs against the actual unchanged043 candidate."""
from pathlib import Path
import hashlib
import json
import subprocess

task = Path(__file__).resolve().parent
module = task.parents[3]
repo = module.parent
old = module / 'docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042/evidence'
sources = [old / 'GROUPS042.json', old / 'C24_REUSE042.json',
    module / 'docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042/evidence/A5_NATIVE_WORLD_INPUT_GROUP_A_042/continuity-v2.json',
    module / 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/evidence/C24_GROUP_B_041/continuity-v2.json']
groups = json.loads(sources[0].read_text(encoding='utf-8-sig'))
jar = module / '.phantom-local/contract043a/runtime/libs/GameServer.jar'
evaluator = module / 'docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/continuity028.py'
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
assert groups['G2'] == 'PASS'
assert sha(jar) == groups['gameJarSha256'] == '5c4ae8bfff0568ad2fd204f6c3be79268d03c55ed95862c5b24619ad3719e98c'
assert sha(evaluator) == groups['evaluatorSha256']
paths = [f'{module.name}/java', f'{module.name}/dist/game/config', f'{module.name}/dist/game/data/phantoms']
commands = [
    ['git', '-C', str(repo), 'diff', '--name-only', '92400bebb9126ec4364ac57b05f8c453fe42690d', '9a378d0e8984b3673b41538a9f5f9c9823507317', '--', *paths],
    ['git', '-C', str(repo), 'diff', '--name-only', '9a378d0e8984b3673b41538a9f5f9c9823507317', '--', *paths]]
for command in commands:
    checked = subprocess.run(command, capture_output=True, text=True, check=True)
    assert not checked.stdout.strip(), 'Product semantics changed; both G2 groups must rerun'
value = dict(G2='PASS_REUSED_EXACT_CANDIDATE', productEdits=False, jarSha256=sha(jar),
    evaluatorSha256=sha(evaluator), sourcePins=[dict(path=str(path), sha256=sha(path)) for path in sources],
    commands=commands, semanticDiffEmpty=True, currentProductDiffEmpty=True,
    historicalA5='8/8 PASS 382.3743138s', independentC24='8/8 PASS 381.6816272s',
    historicalFailuresRetained=True, rerunRequiredOnlyForSemanticChange=True)
with (task / 'evidence/G2_REUSE043.json').open('x', encoding='utf-8') as stream:
    json.dump(value, stream, ensure_ascii=False, indent=2)
print('G2_REUSE043 PASS: exact candidate/evaluator; semantic and current product diffs empty; both original proofs pinned.')
