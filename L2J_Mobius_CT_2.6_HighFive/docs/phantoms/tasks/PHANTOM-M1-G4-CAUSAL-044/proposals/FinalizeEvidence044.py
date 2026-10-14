"""Produce bounded044 indices; immutable raw/native/SQL inputs stay local."""
import csv
import hashlib
import json
from pathlib import Path
import re

TASK = Path(__file__).resolve().parents[1]
MODULE = TASK.parents[3]
OPS = MODULE / '.phantom-local/ops044'
EVIDENCE = TASK / 'evidence'


def pin(path):
    path = Path(path)
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def save(name, value):
    target = EVIDENCE / name
    if target.exists():
        raise ValueError('Immutable output exists: ' + str(target))
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def read_log(path):
    raw = path.read_bytes()
    return raw.decode('utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig')


def main():
    matrix_path = EVIDENCE / 'CURRENT93_044-matrix.tsv'
    matrix = list(csv.DictReader(matrix_path.open(encoding='utf-8-sig'), delimiter='\t'))
    totals = [tuple(map(int, re.search(r'total=(\d+) passed=(\d+) failed=(\d+)', row['summary']).groups())) for row in matrix]
    assert len(matrix) == 18 and sum(value[0] for value in totals) == 93
    assert all(row['exitCode'] == '0' for row in matrix) and all(t == p and f == 0 for t, p, f in totals)
    jar = MODULE / '.phantom-local/contract044a/runtime/libs/GameServer.jar'
    jar_hash = pin(jar)['sha256']
    provenance = sorted(EVIDENCE.glob('CURRENT93_044_*.provenance.json'))
    assert len(provenance) == 18
    for path in provenance:
        data = json.loads(path.read_text(encoding='utf-8-sig'))
        assert data['jarSha256'].lower() == jar_hash and not data['developmentBinFirst']
    focused = {}
    for label, log, total in [('scheduler28', OPS / 'SNAPSHOT_JAR_GREEN044.log', 28),
                              ('soft3', EVIDENCE / 'TEST_OWNER_GREEN044.log', 3),
                              ('lifecycle11', EVIDENCE / 'LIFECYCLE11_FINAL044.log', 11)]:
        summary = re.findall(r'SUMMARY:.*total=(\d+) passed=(\d+) failed=(\d+)', read_log(log))[-1]
        assert tuple(map(int, summary)) == (total, total, 0)
        focused[label] = {'total': total, 'passed': total, 'failed': 0, 'log': pin(log)}
    save('REGRESSIONS044.json', {'jar': pin(jar), 'jarFirst': True, 'matrix': matrix, 'focused': focused,
                                 'existingValidatorNegatives': {'total': 26, 'passed': 26, 'log': pin(OPS / 'CHAIN_NEGATIVES044.log')},
                                 'G2Historical': 'PASS_ON_ORIGINAL_5c4ae8bf', 'G2Current': 'NOT_VERIFIED'})
    proof_path = OPS / 'DIAG_CONSERVATION044.json'
    proof = json.loads(proof_path.read_text(encoding='utf-8-sig'))
    startup = EVIDENCE / 'STARTUP032a-7f1b59f7658e47a0a3d000a8dd721939'
    ledger = [json.loads(line) for line in (startup / 'lifetimes.jsonl').read_text(encoding='utf-8-sig').splitlines()]
    fields = ('nativeBirths', 'commitRecords', 'allBirthsTerminal', 'GLOBAL_DRAIN', 'NATIVE_BOUNDARY_INTERVAL', 'FINAL_STOPPED_CUT', 'FULL_SAVE', 'pass', 'failures', 'nativeVerifierExit', 'stoppedVerifierExit')
    save('CONSERVATION044.json', {**{key: proof[key] for key in fields}, 'closedRunEnd': ledger[-1],
                                'nativeWitnesses': len(proof['rows']), 'nativePassing': sum(row.get('nativeCommit') == 'PASS' for row in proof['rows']),
                                'stoppedProfiles': len(proof['stoppedProfiles']),
                                'proof': pin(proof_path), 'ledger': pin(startup / 'lifetimes.jsonl'),
                                'typedStop': pin(EVIDENCE / 'DIAG_A1_044-STOCK-REPAIR/typed-stop-done.properties'),
                                'counts': pin(EVIDENCE / 'DIAG_A1_044-STOPPED_SQL/counts.tsv'),
                                'originalDiagnosticFailure': pin(EVIDENCE / 'DIAG_A1_044/episode-failure.txt')})
    preservation = list(csv.DictReader((MODULE / '.phantom-local/contract044a/original-preservation.tsv').open(encoding='utf-8-sig'), delimiter='\t'))
    original = MODULE / '.phantom-local/contract036b/runtime'
    for item in preservation:
        assert pin(original / item['path'])['sha256'].lower() == item['sha256'].lower()
    offline = json.loads((EVIDENCE / 'OFFLINE044.json').read_text(encoding='utf-8-sig'))
    for relative, digest in offline['inputSha256'].items():
        assert pin(MODULE / relative)['sha256'] == digest
    inputs = ['GOAL.md', 'TASK.md', 'CONTEXT.md', 'DESIGN.md', 'PLAN.md', 'ACCEPTANCE.md', 'SOURCE_MAP.tsv', 'SIGNAL_PATH.md', 'HANDOFF.md', 'PACKAGE_SHA256.txt']
    main_input = Path('C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-G4-CAUSAL-044')
    for name in inputs:
        assert (main_input / name).read_bytes() == (TASK / name).read_bytes()
    save('PRESERVATION044.json', {'original036Files': len(preservation), 'original036Unchanged': True,
                                'raw043PinnedInputsUnchanged': True, 'mainInputPacketUnchanged': True,
                                'evaluator028': pin(MODULE / 'docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/continuity028.py'),
                                'protected040a': 'PID18688/30312; original runtime/start times; final census only those JVMs; no actions'})
    git_log = [json.loads(line) for line in (OPS / 'GIT044.jsonl').read_text(encoding='utf-8-sig').splitlines()]
    save('HELPER_GIT_COMMANDS044.json', [{key: row[key] for key in ('utc', 'argv', 'exitCode')} for row in git_log])
    print('REGRESSIONS 93/93,28/28,11/11,3/3; conservation strict FAIL preserved; preservation', len(preservation), 'files PASS')


if __name__ == '__main__':
    main()
