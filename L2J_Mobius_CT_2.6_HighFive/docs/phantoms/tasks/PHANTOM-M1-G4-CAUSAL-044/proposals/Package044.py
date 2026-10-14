"""Exact bounded044 artifact inventory, encoding checks, and verified compact ZIP."""
import ast
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

TASK = Path(__file__).resolve().parents[1]
MODULE = TASK.parents[3]
REPO = MODULE.parent
OPS = MODULE / '.phantom-local/ops044'
ROOT_FILES = ['GOAL.md', 'TASK.md', 'CONTEXT.md', 'DESIGN.md', 'PLAN.md', 'ACCEPTANCE.md', 'SOURCE_MAP.tsv', 'SIGNAL_PATH.md', 'HANDOFF.md', 'PACKAGE_SHA256.txt',
              'CAUSE044.md', 'RESULT044.md', 'GATES044.tsv', 'REVIEW044.md', 'CHECKPOINT044.md', 'GIT_COMMANDS044.md', 'PRECONDITIONS044.md', 'SOURCE_DIFF044.patch',
              'Verify044.ps1', 'Run-Diagnostic044.ps1', 'Run-SourceDiagnostic044a.ps1', 'PRECONDITIONS_SOURCE044a.md', 'Run-Dry044.ps1', 'Run-Life044.ps1', 'Check-LifeBoundary044.ps1', 'Verify-Life044.py', 'Decode-LifeReceipts044.java']
PROPOSALS = ['Bootstrap044.ps1', 'Invoke-Git044.ps1', 'TraceRaw043.py', 'TraceAdditional044.py', 'FinalizeAdditional044.py', 'FinalizeEvidence044.py', 'Package044.py', 'Publish044.ps1']
INDICES = ['OFFLINE044.json', 'REGRESSIONS044.json', 'CONSERVATION044.json', 'PRESERVATION044.json', 'HELPER_GIT_COMMANDS044.json', 'CURRENT93_044-matrix.tsv', 'DIAGNOSTIC_INPUT044.json', 'ADDITIONAL_TRACE_AUTH044.json', 'DIAGNOSTIC_ADDITIONAL_INPUT044.json', 'ADDITIONAL_SOURCE_RESULT044.json', 'ADDITIONAL_FINAL044.json', 'ADDITIONAL_RUNTIME_FINAL044.json']
HELPERS = ['Prepare032.ps1', 'Control032.ps1', 'Observe032.ps1', 'Contract032Observer.java', 'Read-SharedJson032.ps1', 'ReadDryRoute032.java', 'ReadDryPath044.java',
           'Select-Primary044.ps1', 'All-Birth-Cut044.ps1', 'Build-StockShutdown044.ps1', 'Stop-ExactOwnedGracefully.ps1', 'RuntimeShutdownAgent044.java', 'RequestShutdown.java', 'SplitSql039.py', 'SignalSnapshot044.java', 'Invoke-SignalSnapshot044.ps1', 'Test-SourceSelection044.ps1']
VALIDATORS = ['commit_chain039.py', 'complete_chain044.py', 'raw_archive044.py', 'verify_commit_run039.py', 'test_commit_chain039.py', 'test_complete_chain044.py', 'VerifyNativeCommit039.java', 'VerifyStoppedCut039.java']
SOURCE = ['java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java', 'test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java', 'test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java']


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def main():
    copied = []
    for folder, names in [(Path(''), HELPERS), (Path('validation'), VALIDATORS)]:
        for name in names:
            source = OPS / folder / name
            target = TASK / 'proposals/ops044' / folder / name
            target.parent.mkdir(parents=True, exist_ok=True)
            raw = source.read_bytes()
            # Published source removes only inherited trailing empty lines; original captured bytes stay local.
            text = raw.decode('utf-8-sig').rstrip('\r\n') + '\n'
            target.write_text(text, encoding='utf-8', newline='')
            copied.append({'path': str(target.relative_to(REPO)).replace('\\', '/'), 'privateSourceSha256': digest(raw), 'publishedSha256': digest(target.read_bytes()), 'normalization': 'trailing-empty-lines-only; no semantic changes'})
    write_json(TASK / 'evidence/HELPER_SOURCE_PINS044.json', copied)
    paths = [TASK / name for name in ROOT_FILES] + [TASK / 'proposals' / name for name in PROPOSALS] + [TASK / 'evidence' / name for name in INDICES]
    paths += [REPO / row['path'] for row in copied] + [TASK / 'evidence/HELPER_SOURCE_PINS044.json']
    publication = TASK / 'evidence/PUBLICATION1_RECEIPT044.json'
    if publication.exists():
        paths.append(publication)
    markers = ['Рџ','Рќ','Рћ','Р•','РЎ','Р›','Р¤','Рњ','РЈ','Рљ','Рґ','Рµ','Р°','Р»','РЅ','Рѕ','СЏ','С€','СЂ','С‹','СЊ','С‚','Сѓ','С‡','С…','С†','�']
    escaped = re.compile(r'\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};')
    mojibake_hits, escaped_hits, python_files = [], [], []
    for path in paths + [MODULE / name for name in SOURCE]:
        text = path.read_text(encoding='utf-8-sig')
        relative = str(path.relative_to(REPO)).replace('\\', '/')
        # The scanner's literal marker list is its technical definition, not user-facing text.
        scan_text = '\n'.join(line for line in text.splitlines() if not (path.name == 'Package044.py' and line.lstrip().startswith('markers = [')))
        found = [marker for marker in markers if marker in scan_text]
        if found:
            mojibake_hits.append({'path': relative, 'markers': found})
        if escaped.search(text):
            escaped_hits.append(relative)
        if path.suffix == '.py':
            ast.parse(text, filename=str(path))
            python_files.append(relative)
    if mojibake_hits or escaped_hits:
        raise ValueError({'mojibake': mojibake_hits, 'escapedCyrillic': escaped_hits})
    encoding = TASK / 'evidence/ENCODING044.json'
    write_json(encoding, {'mojibakeMarkers': 'PASS', 'escapedCyrillic': 'PASS', 'checkedFiles': len(paths) + len(SOURCE), 'technicalScannerLiteralExemption': 'Package044.py marker definitions only', 'pythonSyntax': python_files})
    paths.append(encoding)
    archive = TASK / 'TASK044-review.zip'
    allowlist = TASK / 'ALLOWLIST044.txt'
    qa = TASK / 'evidence/ZIP_QA044.json'
    names = [str(path.relative_to(REPO)).replace('\\', '/') for path in paths]
    assert len(names) == len(set(names))
    members = []
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as zipped:
        for path, name in zip(paths, names):
            payload = path.read_bytes()
            zipped.writestr(name, payload)
            members.append({'name': name, 'bytes': len(payload), 'sha256': digest(payload)})
    with zipfile.ZipFile(archive) as zipped:
        assert zipped.testzip() is None and zipped.namelist() == names
        assert all(not Path(name).is_absolute() and '..' not in Path(name).parts and '\\' not in name for name in names)
        for row, path in zip(members, paths):
            payload = zipped.read(row['name'])
            assert digest(payload) == row['sha256'] and payload == path.read_bytes()
    write_json(qa, {'contract': 'TASK044_COMPACT_REVIEW', 'memberCount': len(members), 'members': members, 'crcAll': 'PASS', 'allMemberSha256': 'PASS', 'sourceBytesEqual': 'PASS', 'duplicateTraversal': 'PASS', 'zipSha256': digest(archive.read_bytes()), 'zipBytes': archive.stat().st_size})
    # Literal, exact staging inventory; no raw directory/glob/binary or unrelated path.
    staging = paths + [archive, qa, allowlist]
    allowlist.write_text('\n'.join(str(path.relative_to(REPO)).replace('\\', '/') for path in staging) + '\n', encoding='utf-8')
    print('ZIP', len(members), 'members;', archive.stat().st_size, 'bytes; CRC/SHA/source equality PASS; separate encoding checks PASS')


if __name__ == '__main__':
    main()
