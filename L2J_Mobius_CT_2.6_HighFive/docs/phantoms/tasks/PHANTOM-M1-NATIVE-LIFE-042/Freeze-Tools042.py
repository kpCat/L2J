"""Preserve only the exact useful normal042 tools; exclude private credentials/artifacts."""
from pathlib import Path
import hashlib
import json
import shutil

task = Path(__file__).resolve().parent
module = task.parents[3]
ops = module / '.phantom-local/ops042'
target = task / 'proposals/ops042'
names = ('Control032.ps1', 'Observe032.ps1', 'Contract032Observer.java',
         'Read-SharedJson032.ps1', 'ReadDryRoute032.java', 'ReadDryPath042.java',
         'Select-Primary042.ps1', 'Select-ReadySetup042.ps1', 'Run-CensusPreparation042.ps1',
         'Run-NativeGroup042.ps1', 'All-Birth-Cut042.ps1', 'SplitSql039.py',
         'Build-StockShutdown042.ps1', 'Stop-ExactOwnedGracefully.ps1',
         'RequestShutdown.java', 'RuntimeShutdownAgent.java', 'Prepare032.ps1', 'Invoke-Git042.ps1')
target.mkdir(parents=True, exist_ok=False)
pins = []
for name in names:
    source = ops / name
    dest = target / name
    shutil.copyfile(source, dest)
    pins.append(dict(name=name, source=str(source), sha256=hashlib.sha256(source.read_bytes()).hexdigest()))
(target / 'SOURCE_PINS042.json').write_text(json.dumps(pins, indent=2), encoding='utf-8')
print('Frozen eighteen normal042 source helpers; no JAR/DB/raw/credentials.')
