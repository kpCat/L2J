"""Pin the approved extra trace, strict proof, and unchanged protected inputs."""
import csv
import hashlib
import json
from pathlib import Path

TASK = Path(__file__).resolve().parents[1]
MODULE = TASK.parents[3]
EVIDENCE = TASK / 'evidence'
SCENE = EVIDENCE / 'DIAG_A2_SOURCE044'


def pin(path):
    return {'path': str(path), 'bytes': path.stat().st_size,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def main():
    output = EVIDENCE / 'ADDITIONAL_FINAL044.json'
    if output.exists():
        raise ValueError('Immutable additional result exists')
    proof_path = MODULE / '.phantom-local/ops044/DIAG_A2_CONSERVATION044.json'
    proof = read(proof_path)
    source = read(EVIDENCE / 'ADDITIONAL_SOURCE_RESULT044.json')
    frames_path = SCENE / 'full-native/full-cohort-samples.jsonl'
    frames = [json.loads(line) for line in frames_path.read_text(encoding='utf-8-sig').splitlines()]
    before = read(SCENE / 'life-native-before.json')
    original = before['actors'][0]
    quiet = []
    for frame in frames:
        actor = frame['actors'][0]
        safe = (actor['worldPresent'] == 'true' and actor['dead'] == 'false'
                and actor['materializedAtNanos'] == original['materializedAtNanos']
                and all(actor[field] == 'false' for field in ('attacking', 'casting', 'moving'))
                and actor['current.nativeOwnerOutstanding'] == '0'
                and actor['incomingTargetCount044'] == '0'
                and 'retentionPins=[' in actor['operatorAdmission']
                and 'REAL_PARTY' not in actor['operatorAdmission'])
        if safe:
            quiet.append(frame['sampleNanos'])
    preserved = list(csv.DictReader((MODULE / '.phantom-local/contract044a/original-preservation.tsv').open(encoding='utf-8-sig'), delimiter='\t'))
    for item in preserved:
        assert pin(MODULE / '.phantom-local/contract036b/runtime' / item['path'])['sha256'] == item['sha256'].lower()
    for relative, digest in read(EVIDENCE / 'OFFLINE044.json')['inputSha256'].items():
        assert pin(MODULE / relative)['sha256'] == digest
    main_input = Path('C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-G4-CAUSAL-044')
    for name in ('GOAL.md', 'TASK.md', 'CONTEXT.md', 'DESIGN.md', 'PLAN.md', 'ACCEPTANCE.md', 'SOURCE_MAP.tsv', 'SIGNAL_PATH.md', 'HANDOFF.md', 'PACKAGE_SHA256.txt'):
        assert (main_input / name).read_bytes() == (TASK / name).read_bytes()
    fields = ('nativeBirths', 'commitRecords', 'allBirthsTerminal', 'GLOBAL_DRAIN',
              'NATIVE_BOUNDARY_INTERVAL', 'FINAL_STOPPED_CUT', 'FULL_SAVE', 'pass',
              'failures', 'nativeVerifierExit', 'stoppedVerifierExit')
    result = {'contract': 'APPROVED_ADDITIONAL_SOURCE_ONLY044_CLOSEOUT',
              'nativeOutcome': 'INPUT_FAIL_SAFE_BOUNDARY_BEFORE_MOVE', 'fullG4': False,
              'sourceView': 'PASS; 55 one-profile frames; no ACTIVE source',
              'historical1189Owner': 'UNKNOWN', 'awayFrames': source['awayFrameCount'],
              'profileId': original['profileId'], 'objectId': original['objectId'],
              'epoch': original['materializedAtNanos'],
              'existingEarned': {field: original[field] for field in ('nativeFarmCycleSequence', 'nativeRewardSequence', 'nativeDamageSequence')},
              'firstQuietSampleNanos': quiet[0] if quiet else None,
              'firstQuietSecondsFromFirstFrame': (int(quiet[0]) - int(before['sampleNanos'])) / 1e9 if quiet else None,
              'conservation': {**{field: proof[field] for field in fields},
                               'nativeWitnesses': len(proof['rows']),
                               'nativePassing': sum(row.get('nativeCommit') == 'PASS' for row in proof['rows']),
                               'stoppedProfiles': len(proof['stoppedProfiles'])},
              'campaignConservation': 'NOT_VERIFIED; original RUN_END failure retained',
              'preservation': {'original036Files': len(preserved), 'original036Unchanged': True,
                               'raw043PinnedUnchanged': True, 'mainInputPacketUnchanged': True},
              'pins': [pin(path) for path in (proof_path, frames_path,
                       EVIDENCE / 'ADDITIONAL_TRACE_AUTH044.json',
                       EVIDENCE / 'DIAGNOSTIC_ADDITIONAL_INPUT044.json',
                       SCENE / 'episode-failure.txt', SCENE / 'final-synthetic-state.json',
                       EVIDENCE / 'DIAG_A2_SOURCE044-FALLBACK-STOP/typed-stop-done.properties',
                       EVIDENCE / 'DIAG_A2_SOURCE044-STOPPED_SQL/counts.tsv')]}
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: result[key] for key in ('nativeOutcome', 'sourceView', 'firstQuietSecondsFromFirstFrame', 'conservation', 'preservation')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
