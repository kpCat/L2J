"""Build/check an exact TASK028 final publication allowlist; never stages a directory."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

TASK = Path(__file__).resolve().parents[1]
ROOT = TASK.parents[4]
PREFIX = TASK.relative_to(ROOT).as_posix() + '/'
OUTPUT = TASK / 'FINAL_STAGING_ALLOWLIST.json'
ROOT_EXTENSIONS = {'.md', '.txt', '.json', '.tsv', '.ps1', '.java', '.log', '.gitattributes'}
EVIDENCE = (
    'BASE027_PROVENANCE.tsv', 'BASE027_PROOF_INPUTS.json', 'FINAL_SCOPE_GUARD.json',
    'FINAL_OWNED_JVMS.json', 'FINAL_RESTART_CANONICAL_AUDIT.json', 'WHOLE_GROUP_028_AUDIT.json',
    'LONGITUDINAL_FINAL_07C2_REPORT.json', 'FINAL_07C2_REGRESSION-matrix.tsv',
    'FINAL_07C2_EXTRA_EXIT.json', 'FINAL_ENCODING.log', 'GIT_COMMANDS_EXACT.json',
    'FINAL_SCENE_07C2_F1_BOUNDARY_EXACT.json', 'FINAL_SCENE_07C2_F1_BOUNDARY_PERSIST.json',
    'FINAL_AWAY_07C2_F3_BOUNDARY_EXACT.json', 'FINAL_AWAY_07C2_F3_BOUNDARY_PERSIST.json')


def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, check=True, capture_output=True, text=True, encoding='utf-8').stdout


def collect():
    paths = {p for p in TASK.iterdir() if p.is_file() and (p.suffix in ROOT_EXTENSIONS or p.name == '.gitattributes')}
    paths.update(p for p in (TASK / 'proposals').iterdir() if p.is_file() and p.suffix in ('.py', '.md'))
    paths.update(p for p in (TASK / 'archives').iterdir() if p.is_file() and p.name in ('RAW_EVIDENCE_028.zip', 'ARCHIVE_MANIFEST.json'))
    paths.update(TASK / 'evidence' / name for name in EVIDENCE)
    for scene in ('FINAL_SCENE_07C2_F1', 'FINAL_SCENE_07C2_F2', 'FINAL_AWAY_07C2_F3'):
        for name in ('capture-result.json', 'continuity-v2.json', 'legacy-result.json', 'soft-return-v2.json'):
            p = TASK / 'evidence' / scene / name
            if p.exists():
                paths.add(p)
    paths.add(OUTPUT)
    rows = []
    for path in sorted(paths):
        if path != OUTPUT and not path.is_file():
            raise ValueError(f'Expected exact publication file missing: {path}')
        rows.append({'path': path.relative_to(ROOT).as_posix(),
                     'sha256': None if path == OUTPUT else hashlib.sha256(path.read_bytes()).hexdigest(),
                     'selfInventory': path == OUTPUT})
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if not args.check:
        OUTPUT.write_text(json.dumps({'kind': 'EXACT_TASK028_PUBLICATION_ALLOWLIST', 'files': collect(),
                                      'directoryStageAllowed': False, 'nonTaskStageAllowed': False}, indent=2)+'\n', encoding='utf-8')
        print(json.dumps({'allowlistFiles': len(json.loads(OUTPUT.read_text(encoding='utf-8'))['files'])}))
        return
    rows = json.loads(OUTPUT.read_text(encoding='utf-8'))['files']
    allowed = {r['path'] for r in rows}
    staged = set(git('diff', '--cached', '--name-only').splitlines())
    changed_allowed = {p for p in allowed if git('diff', '--cached', '--name-only', '--', p).strip()}
    outside = sorted(p for p in staged if p not in allowed or not p.startswith(PREFIX))
    missing = sorted(p for p in allowed if p != OUTPUT.relative_to(ROOT).as_posix()
                     and hashlib.sha256((ROOT / p).read_bytes()).hexdigest() != next(r['sha256'] for r in rows if r['path'] == p))
    unstaged = sorted(p for p in git('diff', '--name-only', 'HEAD').splitlines() if p not in allowed)
    if outside or missing or unstaged or staged != changed_allowed:
        raise ValueError(json.dumps({'outside': outside, 'hashChanged': missing, 'unlistedChanges': unstaged}))
    print(json.dumps({'stagedFiles': len(staged), 'listedFiles': len(allowed), 'outsideScope': [], 'hashesExact': True}))


if __name__ == '__main__':
    main()
