"""Exact043 review inventory; full raw, DB, JAR, secrets and old task families excluded."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

task = Path(__file__).resolve().parent
repo = task.parents[4]
module = task.parents[3]
ops = module / '.phantom-local/ops043'
owner = module / 'test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java'
proposal = task / 'proposals/ops043'
if not proposal.exists():
    proposal.mkdir(parents=True)
    names = ('Control032.ps1','Observe032.ps1','Contract032Observer.java','Read-SharedJson032.ps1',
        'ReadDryRoute032.java','ReadDryPath043.java','Select-Primary043.ps1','All-Birth-Cut043.ps1',
        'SplitSql039.py','Prepare032.ps1','Invoke-Git043.ps1','Build-StockShutdown043.ps1',
        'Stop-ExactOwnedGracefully.ps1','RequestShutdown.java','RuntimeShutdownAgent.java')
    pins = []
    for name in names:
        shutil.copyfile(ops / name, proposal / name)
        pins.append(dict(name=name,sha256=hashlib.sha256((proposal/name).read_bytes()).hexdigest()))
    (proposal/'validation').mkdir()
    for source in (ops/'validation').iterdir():
        if source.is_file() and source.suffix in ('.py','.java'):
            target = proposal/'validation'/source.name
            shutil.copyfile(source,target)
            pins.append(dict(name='validation/'+source.name,sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
    (proposal/'SOURCE_PINS043.json').write_text(json.dumps(pins,indent=2),encoding='utf-8')
diff = subprocess.run(['git','-C',str(repo),'diff','9a378d0e8984b3673b41538a9f5f9c9823507317','--',owner.relative_to(repo).as_posix()],capture_output=True,text=True,check=True).stdout
source_diff = task/'SOURCE_DIFF043.patch'
if diff:
    source_diff.write_text(diff,encoding='utf-8',newline='\n')
paths = [owner]
paths += [p for p in task.iterdir() if p.is_file() and p.suffix in ('.md','.tsv','.py','.ps1','.java','.txt','.json','.patch')]
paths += [p for p in proposal.rglob('*') if p.is_file()]
names = ('B2_CAUSE043.json','PREFLIGHT043.json','G2_REUSE043.json','FINAL_OWNERSHIP043.json',
    'GUARD_WrongManifest043.json','GUARD_MixedOwners043.json','NORMAL_GUARD043.json',
    'RUNNER_BOUNDARY043.json','SOURCE_PINS043-A1.json','SOURCE_PINS043-A2.json',
    'SOURCE_PINS043-B1.json','SOURCE_PINS043-C1.json','SOURCE_PINS043-D1.json',
    'CURRENT93_043-matrix.tsv','REGRESSIONS043.json','VALIDATOR_NEGATIVES043.json','CONSERVATION_INDEX043.json',
    'BACKGROUND189043_V2.json','C1-LIFE043.json','D1-LIFE043.json','ENCODING043.json',
    'LIFE_A1_043-PROSPECTIVE.json','LIFE_A2_043-PROSPECTIVE.json','LIFE_B1_043-PROSPECTIVE.json',
    'LIFE_C1_043-PROSPECTIVE.json','LIFE_D1_043-PROSPECTIVE.json')
paths += [task/'evidence'/name for name in names if (task/'evidence'/name).is_file()]
for label in ('LIFE_A1_043','LIFE_A2_043','LIFE_B1_043','LIFE_C1_043','LIFE_D1_043'):
    scene = task/'evidence'/label
    for name in ('episode-failure.txt','primary.json','capture-result.json','stock-stop-verified.json','frozen-predeparture043.json'):
        if (scene/name).is_file(): paths.append(scene/name)
    for stop in (scene/'stock-stop',task/'evidence'/(label+'-FALLBACK-STOP')):
        if (stop/'typed-stop-done.properties').is_file(): paths.append(stop/'typed-stop-done.properties')
paths += sorted((task/'evidence').glob('CURRENT93_043_*.provenance.json'))
for label in ('TESTLANE_RED043','TESTLANE_GREEN043','LIFECYCLE11_043'):
    paths.append(task/'evidence'/(label+'.provenance.json'))
paths += [task/name for name in ('REVIEW_ALLOWLIST043.txt','SOURCE_DIFF043.patch','TASK043-review.zip',
    'TASK043-review.sha256','ZIP_VERIFIED043.json','PUBLICATION1_043.json','GIT_COMMANDS043.json')]
assert all(p.is_relative_to(repo) and (p==owner or p.is_relative_to(task)) for p in paths)
relative = sorted({p.relative_to(repo).as_posix() for p in paths})
(task/'REVIEW_ALLOWLIST043.txt').write_text('\n'.join(relative)+'\n',encoding='utf-8',newline='\n')
print(f'Exact043 allowlist: {len(relative)} paths; one TEST owner + bounded043 family; no full raw/DB/JAR/secrets.')
