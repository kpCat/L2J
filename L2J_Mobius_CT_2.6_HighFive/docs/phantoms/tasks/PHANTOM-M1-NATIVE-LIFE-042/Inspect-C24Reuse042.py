"""Pin exact historical C24 provenance without rewriting its evidence."""
from pathlib import Path
import hashlib
import json

task = Path(__file__).resolve().parent
module = task.parents[3]
c24 = module / 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/evidence/C24_GROUP_B_041'
result = json.loads((c24 / 'continuity-v2.json').read_text(encoding='utf-8-sig'))
old = json.loads((c24 / 'stopped-sql/runtime-manifest.json').read_text(encoding='utf-8-sig'))
current = json.loads((module / '.phantom-local/contract042a/runtime/local-play.json').read_text(encoding='utf-8-sig'))
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
jar = module / 'dist/libs/GameServer.jar'
evaluator = module / 'docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/continuity028.py'
keys = ('populationTarget', 'activeTarget', 'materializedCap', 'schedulerPulseMillis',
        'schedulerProfilesPerPulse', 'ecology', 'humanizedV3', 'mature', 'preset')
assert all(old[key] == current[key] for key in keys)
assert digest(jar) == old['gameJarSha256'].lower() == current['gameJarSha256'].lower()
assert digest(evaluator) == '15c153d30fe7f85719b1698059f4e28c9c7fa325caa420a02de7ac19cff35112'
assert result['pass'] and result['denominator'] == 8 and result['seconds'] >= 380
output = task / 'evidence/C24_REUSE042.json'
value = dict(historicalPass=True, currentJarExact=True, evaluatorExact=True,
             semanticCodeSha=old['gameSourceCodeSha'], sourceCodeSha=current['codeSha'],
             jarSha256=digest(jar), evaluatorSha256=digest(evaluator),
             database=old['databaseName'], currentDatabase=current['databaseName'],
             independentDatabase=old['databaseName'] != current['databaseName'],
             policy={key: old[key] for key in keys},
             seconds=result['seconds'], cohort=[{key: row[key] for key in ('profileId','primary','cycles','pass')} for row in result['rows']],
             pins=[dict(path=str(p), sha256=digest(p)) for p in
                   (c24 / 'continuity-v2.json', c24 / 'baseline-cohort.json', c24 / 'primary.json',
                    c24 / 'all-samples.json', c24 / 'stopped-sql/runtime-manifest.json', evaluator, jar)],
             qualification='Reuse valid only while product code/config/evaluator remain unchanged; separate original FAIL retained.')
with output.open('x', encoding='utf-8') as stream:
    json.dump(value, stream, indent=2)
print(json.dumps({key: value[key] for key in ('historicalPass','currentJarExact','evaluatorExact','independentDatabase','seconds')}))
