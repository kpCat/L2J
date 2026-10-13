"""Historical staging recipe used before native runs; reviewed staging is below."""
from pathlib import Path
import hashlib
import json

TASK = Path(__file__).resolve().parent
MODULE = TASK.parents[3]
OPS = MODULE / '.phantom-local/ops042'
OLD = MODULE / 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041'

def stage(source, target):
    if target.exists():
        raise ValueError('No overwrite: ' + str(target))
    text = source.read_text(encoding='utf-8-sig')
    text = text.replace('PHANTOM-M1-FINAL-CLOSURE-041', 'PHANTOM-M1-NATIVE-LIFE-042')
    text = text.replace('041', '042')
    if source.suffix in ('.ps1', '.java'):
        text = text.replace("'a','b','t','x72','x73'", "'a','b'")
        text = text.replace("'a','b','x72','x73'", "'a','b'")
        text = text.replace('(?:[abct]|x72|x73)', '[ab]')
        text = text.replace('(?:[abt]|x72|x73)', '[ab]')
    if source.name == 'Control032.ps1':
        marker = 'function Update-Owned032 {'
        text = text.replace(marker, "if ($Mode -in @('CrashNative','CrashFinalize') -or $StartupRecoveryProfileId -gt 0) { throw 'TASK042_NORMAL_ONLY' }\n" + marker, 1)
    if source.name == 'Contract032Observer.java':
        marker = 'final String mode = spec.getProperty("mode");'
        text = text.replace(marker, marker + '\n        if (mode.startsWith("CRASH") || mode.equals("STARTUP_RECOVERY")) { throw new IllegalStateException("TASK042_NORMAL_ONLY"); }', 1)
    if source.name == 'Prepare032.ps1':
        text = text.replace('7787+$portIndex', '7793+$portIndex').replace('2117+$portIndex', '2123+$portIndex').replace('9027+$portIndex', '9033+$portIndex')
        text = text.replace("if($Episode -in @('a','t'))", "if($Episode -ceq 'b')")
    if source.name == 'Verify041.ps1':
        text = text.replace('contract042t', 'contract042b').replace('test_042t', 'test_042b')
        text = text.replace('.phantom-local/build042', '.phantom-local/ops042/build')
    target.write_text(text, encoding='utf-8')
    return {'source': str(source), 'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
            'target': str(target), 'targetSha256': hashlib.sha256(target.read_bytes()).hexdigest()}

def historical_staging_recipe():
    OPS.mkdir(exist_ok=True)
    sources = ['Control032.ps1', 'Observe032.ps1', 'Contract032Observer.java',
               'Read-SharedJson032.ps1', 'ReadDryRoute032.java', 'ReadDryPath041.java',
               'Select-Primary041.ps1', 'Select-ReadySetup041.ps1', 'Run-CensusPreparation041.ps1',
               'Run-NativeGroup041.ps1', 'All-Birth-Cut041.ps1', 'SplitSql039.py',
               'Build-StockShutdown041.ps1', 'Stop-ExactOwnedGracefully.ps1',
               'RequestShutdown.java', 'RuntimeShutdownAgent.java']
    manifest = [stage(OLD / 'proposals/ops041' / name, OPS / name.replace('041', '042')) for name in sources]
    manifest.append(stage(MODULE / '.phantom-local/ops041/Prepare032.ps1', OPS / 'Prepare032.ps1'))
    manifest.append(stage(MODULE / '.phantom-local/ops041/Invoke-Git041.ps1', OPS / 'Invoke-Git042.ps1'))
    (TASK / 'evidence').mkdir(exist_ok=False)
    stage(OLD / 'Verify041.ps1', TASK / 'Verify042.ps1')
    (OPS / 'staged-inputs.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('TASK042 templates staged; no database or runtime started.')

if __name__ == '__main__':
    import shutil
    reviewed = TASK / 'proposals/ops042'
    pins = json.loads((reviewed / 'SOURCE_PINS042.json').read_text(encoding='utf-8'))
    expected = Path(r'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive')
    assert MODULE.resolve() == expected.resolve(), 'Exact authorized OWN HighFive required'
    for pin in pins:
        assert Path(pin['name']).name == pin['name']
        assert hashlib.sha256((reviewed / pin['name']).read_bytes()).hexdigest() == pin['sha256'], 'Reviewed tool SHA differs'
        assert not (OPS / pin['name']).exists(), 'No existing tool/runtime overwrite'
    OPS.mkdir(exist_ok=True)
    for pin in pins:
        shutil.copyfile(reviewed / pin['name'], OPS / pin['name'])
    (TASK / 'evidence').mkdir(exist_ok=True)
    (OPS / 'reviewed-source-pins.json').write_text(json.dumps(pins, indent=2), encoding='utf-8')
    print('Normal-only sources staged; no DB/JAR/process/old-lane mutation.')
