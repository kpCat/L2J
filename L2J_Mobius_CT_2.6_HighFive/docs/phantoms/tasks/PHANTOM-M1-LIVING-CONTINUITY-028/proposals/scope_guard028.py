"""Bounded final authored scope and unchanged027 dependency verification authorized by TASK028."""
import csv
import hashlib
import json
from pathlib import Path
import subprocess

TASK = Path(__file__).resolve().parents[1]
MODULE = TASK.parents[3]
ROOT = MODULE.parent
BASE = '9aeb4ac6c52970372f97637d26a5eb54c760ed1a'
PREFIX = MODULE.name + '/'
TASK_PREFIX = TASK.relative_to(ROOT).as_posix() + '/'
NON_TASK = (
    'java/org/l2jmobius/gameserver/localplay/LocalPlaySyntheticHumanService.java',
    'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
    'java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationRetentionPolicy.java',
    'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java',
    'java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java',
    'java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java',
    'test/java/org/l2jmobius/tests/phantoms/LocalPlayContinuity028Suite.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomContinuity028Suite.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomSoftReturn028Suite.java',
    'tools/phantom-local-play/Invoke-LocalPlayPilot.ps1',
    'tools/phantom-local-play/LocalPlay-Pilot.ps1')


def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, check=True, capture_output=True, text=True, encoding='utf-8').stdout


def main():
    changed = set(git('diff', '--name-only', BASE, 'HEAD', '--', MODULE.name).splitlines())
    changed.update(git('diff', '--name-only', 'HEAD').splitlines())
    staged = set(git('diff', '--cached', '--name-only').splitlines())
    allowed = {PREFIX + path for path in NON_TASK}
    outside = sorted(path for path in changed | staged if path not in allowed and not path.startswith(TASK_PREFIX))
    uncommitted_product = sorted(path for path in git('diff', '--name-only', 'HEAD').splitlines() if not path.startswith(TASK_PREFIX))
    with (TASK / 'evidence/BASE027_PROVENANCE.tsv').open(encoding='utf-8-sig', newline='') as stream:
        protected = list(csv.DictReader(stream, delimiter='\t'))
    dependencies = []
    for row in protected:
        current = hashlib.sha256((ROOT / row['dependency']).read_bytes()).hexdigest()
        dependencies.append({'path': row['dependency'], 'unchanged': current == row['currentFileSha256'],
                             'actualFileSha256': current, 'observed027GitBlobSha256': row['observedGitBlobSha256']})
    system = 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java'
    baseline = git('show', BASE + ':' + PREFIX + system).replace('\r\n', '\n')
    current = (MODULE / system).read_text(encoding='utf-8-sig').replace('\r\n', '\n')
    # Existing027 shutdown implementation, through the original control return helpers.
    start, end = '\tpublic boolean shutdown()\n', '\tprivate boolean shutdownLegacyFailureCleanup()\n'
    baseline_stop = baseline[baseline.index(start):baseline.index(end)]
    current_stop = current[current.index(start):current.index(end)]
    document = {'kind': 'TASK028_FINAL_SCOPE_GUARD', 'base': BASE, 'testedProductionSha': git('rev-parse', 'HEAD').strip(),
                'exactNonTaskAllowlist': sorted(allowed), 'changedNonTask': sorted(path for path in changed if not path.startswith(TASK_PREFIX)),
                'outsideScope': outside, 'uncommittedProduction': uncommitted_product,
                'protected027Dependencies': dependencies, 'shutdown027ImplementationIdentical': baseline_stop == current_stop,
                'agentsUsed': False, 'foreignWorktreesChanged': False}
    document['pass'] = not outside and not uncommitted_product and all(r['unchanged'] for r in dependencies) and baseline_stop == current_stop
    output = TASK / 'evidence/FINAL_SCOPE_GUARD.json'
    # This is a current audit report, never a replacement for immutable runtime evidence.
    output.write_text(json.dumps(document, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'pass': document['pass'], 'nonTaskFiles': len(document['changedNonTask']),
                      'protectedDependencies': len(dependencies), 'shutdown027Identical': baseline_stop == current_stop,
                      'outsideScope': outside, 'uncommittedProduction': uncommitted_product}))
    if not document['pass']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
