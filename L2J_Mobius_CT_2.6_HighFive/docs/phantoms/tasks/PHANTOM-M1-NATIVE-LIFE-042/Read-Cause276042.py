"""Bounded read-only reconstruction of the historical C8 release; preserve its FAIL."""
from pathlib import Path
import hashlib
import json
import zipfile

TASK = Path(__file__).resolve().parent
MODULE = TASK.parents[3]
HISTORY = MODULE / 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/evidence'
SCENE = HISTORY / 'C8_GROUP_A2_041'
STARTUP = HISTORY / 'STARTUP032a-4380ed4d269f437eb2678e75d171d334'

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    output = TASK / 'evidence/CAUSE276042.json'
    if output.exists():
        raise ValueError('Immutable analysis exists.')
    frames = json.loads((SCENE / 'all-samples.json').read_text(encoding='utf-8-sig'))
    keys = ('profileId', 'objectId', 'materializedAtNanos', 'x', 'y', 'z', 'worldPresent',
            'observerPrewarm', 'observerNativeVisible', 'physicalDemand', 'recentHumanAgeSeconds',
            'nativeFarmCycleSequence', 'nativePhase', 'nativeOwnerOutstanding', 'nativeOwnerWork', 'dead')
    samples = []
    for frame in frames:
        actor = next(row for row in frame['actors'] if str(row['profileId']) == '276')
        samples.append(dict(elapsed=frame['elapsedSeconds'], observer=frame['observer'],
                            actor={key: actor[key] for key in keys if key in actor}))
    transitions = [samples[0]]
    previous = (True, True, True)
    for sample in samples[1:]:
        actor = sample['actor']
        current = tuple(actor.get(key) == 'true' for key in ('observerPrewarm', 'observerNativeVisible', 'worldPresent'))
        if current != previous:
            transitions.append(sample)
            previous = current
    receipts = []
    for line in (STARTUP / 'lifetimes.jsonl').open(encoding='utf-8-sig'):
        if '590209020958800' not in line:
            continue
        row = json.loads(line)
        if row.get('profile_id') != 276 or row.get('epoch') != 590209020958800 or row['kind'] not in ('REGISTER', 'PREPARED', 'FINALIZED', 'TERMINAL'):
            continue
        archive_path = STARTUP / row['raw_archive']
        if digest(archive_path) != row['raw_archive_sha256']:
            raise ValueError('Historical archive changed.')
        with zipfile.ZipFile(archive_path) as archive:
            if archive.testzip() is not None:
                raise ValueError('Historical CRC failure.')
            text = next(json.loads(item)['text'] for item in archive.read('records.jsonl').decode('utf-8').splitlines() if json.loads(item)['file'] == row['raw_file'])
        if hashlib.sha256(text.encode('utf-8')).hexdigest() != row['raw_sha256']:
            raise ValueError('Historical raw changed.')
        fields = dict(item.split('=', 1) for item in text.splitlines() if '=' in item)
        terminal_keys = ('kind', 'source', 'terminalNanos', 'checkpointStage', 'nativeOwnerState',
                         'nativeOwnerPermanentSeal', 'nativeOwnerFailure', 'nativeFirstIncident',
                         'terminalOutstanding', 'terminalPendingTimers', 'nativeLastCompletedEvent')
        receipts.append(dict(ledger=row, fields={key: fields[key] for key in terminal_keys if key in fields}))
    terminal = next(row['fields'] for row in receipts if row['ledger']['kind'] == 'TERMINAL')
    assert terminal['terminalOutstanding'] == '0' and terminal['terminalPendingTimers'] == '0'
    assert terminal['nativeOwnerState'] == 'DETACHED' and terminal['nativeOwnerPermanentSeal'] == 'true'
    assert not terminal.get('nativeFirstIncident') and 'ON_PLAYER_LOGOUT:COMPLETED' in terminal['nativeLastCompletedEvent']
    assert all(sample['observer'] == samples[0]['observer'] for sample in samples)
    result = dict(classification='LEGAL_SOFT_RELEASE', historicalVerdict='FAIL_PRESERVED',
                  profileId=276, objectId=268487174, epoch=590209020958800,
                  rule='Native live region prewarm distance2 / visible distance1; RECENT_HUMAN60000ms expires; hard native work drains before detach.',
                  transitions=transitions, receipts=receipts,
                  inputs={str(path): digest(path) for path in (SCENE / 'all-samples.json', SCENE / 'continuity-v2.json', STARTUP / 'lifetimes.jsonl')})
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print('CAUSE276042: LEGAL_SOFT_RELEASE; original FAIL preserved; receipts SHA/CRC verified.')

if __name__ == '__main__':
    main()
