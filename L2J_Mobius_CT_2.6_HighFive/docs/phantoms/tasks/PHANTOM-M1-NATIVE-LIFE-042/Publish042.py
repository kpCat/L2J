"""Authorized ordinary exact-path commits; reject external HEAD/remote divergence."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
from datetime import datetime, timezone

task = Path(__file__).resolve().parent
repo = task.parents[4]
assert str(repo).lower() == r'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius'.lower()
base = 'a5674811479d087ec7f67ca62921ffa85d298263'
ref = 'refs/heads/experiment/m1-candidate007-observe008'
parser = argparse.ArgumentParser()
parser.add_argument('--phase', choices=('1', '2'), required=True)
parser.add_argument('--resume-exact-stage', action='store_true')
args = parser.parse_args()
records = []
def git(*arguments):
    argv = ['git', '-C', str(repo), *arguments]
    result = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding='utf-8')
    records.append(dict(argv=argv, exitCode=result.returncode))
    if result.returncode:
        raise RuntimeError(dict(argv=argv, exitCode=result.returncode, stderr=result.stderr))
    return result.stdout.strip()
def equal(expected):
    head = git('rev-parse', 'HEAD')
    remote = git('ls-remote', 'origin', ref).split()[0]
    assert head == remote == expected, dict(reason='EXACT_MISMATCH_STOP_NO_RESET_FORCE', expected=expected, HEAD=head, remote=remote)
    return head
receipt1 = task / 'PUBLICATION1_042.json'
expected = base if args.phase == '1' else json.loads(receipt1.read_text(encoding='utf-8'))['HEAD']
equal(expected)
prior_staged = git('diff', '--cached', '--name-only').splitlines()
allowlist = (task / 'REVIEW_ALLOWLIST042.txt').read_text(encoding='utf-8').splitlines()
existing = [name for name in allowlist if (repo / name).is_file()]
assert not prior_staged or (args.resume_exact_stage and set(prior_staged) <= set(existing)), 'Pre-existing staged changes; stop'
missing = set(allowlist) - set(existing)
assert not missing or (args.phase == '1' and {Path(name).name for name in missing} == {'PUBLICATION1_042.json','GIT_COMMANDS042.json'})
owner = 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java'
assert set(git('diff', '--name-only').splitlines()) <= set(allowlist), 'Foreign tracked diff; stop'
if args.phase == '1':
    assert (task / 'SOURCE_DIFF042.patch').read_text(encoding='utf-8-sig').strip() == git('diff', base, '--', owner), 'Exact reviewed TEST owner diff'
pathspec = repo / 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops042/publication-pathspec042.txt'
pathspec.write_text('\n'.join(existing) + '\n', encoding='utf-8', newline='\n')
git('add', '--pathspec-from-file=' + str(pathspec))
staged = git('diff', '--cached', '--name-only').splitlines()
assert staged and set(staged) <= set(allowlist), 'Staged exact scope guard'
if args.phase == '1':
    assert set(staged) == set(existing), 'First commit exact reviewed inventory'
# Preserve local CRLF and literal unified-diff context; frozen stock helper has its inherited blank EOF.
git('-c', 'core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol',
    'diff', '--cached', '--check', '--', '.',
    ':(exclude)' + (task/'SOURCE_DIFF042.patch').relative_to(repo).as_posix(),
    ':(exclude)' + (task/'proposals/ops042/Stop-ExactOwnedGracefully.ps1').relative_to(repo).as_posix())
equal(expected)
message = 'TASK042: preserve native G2 proof and honest G4 combat failure' if args.phase == '1' else 'TASK042: verify compact review delivery and final gate reports'
git('commit', '-m', message)
head = git('rev-parse', 'HEAD')
assert git('ls-remote', 'origin', ref).split()[0] == expected, 'Remote changed before push; stop without force'
git('push', 'origin', 'HEAD:' + ref)
equal(head)
assert not git('status', '--porcelain=v1', '--untracked-files=no'), 'Tracked changes after publication'
receipt = dict(utc=datetime.now(timezone.utc).isoformat(), phase=args.phase, BASE=base, HEAD=head,
               remote=head, ref=ref, ordinaryNonForce=True, trackedClean=True,
               zipSha256=hashlib.sha256((task/'TASK042-review.zip').read_bytes()).hexdigest(), argv=records)
dest = receipt1 if args.phase == '1' else task / 'DELIVERY042.json'
assert not dest.exists(), 'Publication receipt immutable'
dest.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
if args.phase == '1':
    (task/'GIT_COMMANDS042.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
print(json.dumps({key:receipt[key] for key in ('phase','HEAD','remote','trackedClean','zipSha256')}))
