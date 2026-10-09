"""Hash exact source dependencies of retained027 native proofs; never relabel runs."""
import csv
import hashlib
import json
from pathlib import Path
import subprocess

TASK = Path(__file__).resolve().parents[1]
MODULE = TASK.parents[3]
ROOT = MODULE.parent
OBSERVED = '141295edc5c78d41d03b360043cc1ec6a149aa6d'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def blob(revision, path):
    return subprocess.check_output(['git', 'show', f'{revision}:{path}'], cwd=ROOT)


def main():
    paths = sorted((MODULE / 'java/org/l2jmobius/gameserver/phantoms/player').glob('*.java'))
    paths += [MODULE / path for path in (
        'java/org/l2jmobius/gameserver/model/actor/Player.java',
        'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java',
        'java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java',
        'java/org/l2jmobius/gameserver/phantoms/background/PhantomNativeContext.java',
        'java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java')]
    output = TASK / 'evidence/BASE027_PROVENANCE.tsv'
    with output.open('x', encoding='utf-8', newline='') as stream:
        writer = csv.writer(stream, delimiter='\t')
        writer.writerow(('observedSha', 'dependency', 'observedGitBlobSha256', 'currentGitBlobSha256', 'currentFileSha256', 'reuse', 'limitation'))
        for path in paths:
            relative = path.relative_to(ROOT).as_posix()
            old, current = blob(OBSERVED, relative), blob('HEAD', relative)
            writer.writerow((OBSERVED, relative, sha(old), sha(current), sha(path.read_bytes()),
                'BASE027_SOURCE_IDENTICAL' if old == current else 'OPEN_SOURCE_CHANGED', 'Retained native proof; not a new final-SHA crash PASS.'))
    previous = TASK.parent / 'PHANTOM-M1-LIFECYCLE-COMPLETION-027/evidence'
    evidence = []
    for name in ('R12_E_NATIVE_CRASH_RECOVERY_EXACT.json', 'R12_F_FINALIZE_CRASH_EXACT.json', 'R12_F_FINALIZE_CRASH_RESTART_EXACT.json'):
        path = previous / name
        proof = json.loads(path.read_text(encoding='utf-8-sig'))
        evidence.append({'path': str(path), 'sha256': sha(path.read_bytes()), 'observedSha': proof['sourceSha'],
                         'recordedPass': proof.get('pass_'), 'reuse': 'BASE027_PROOF', 'newCrashRun': False})
    with (TASK / 'evidence/BASE027_PROOF_INPUTS.json').open('x', encoding='utf-8') as stream:
        json.dump({'proofs': evidence, 'sourceDependencies': str(output),
                   'changedSystemAndSoftSignalBindingsCoveredByNewTestsAndServerStops': 'REQUIRED',
                   'newFinalShaCrashPassClaimed': False}, stream, indent=2)
    print(json.dumps({'nativeSourceDependencies': len(paths), 'retainedProofs': len(evidence)}))


if __name__ == '__main__':
    main()
