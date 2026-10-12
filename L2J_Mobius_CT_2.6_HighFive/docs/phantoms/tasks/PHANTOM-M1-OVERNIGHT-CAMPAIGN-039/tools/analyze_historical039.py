"""Read-only bounded analysis of verified038 cohorts; retain every historical verdict."""
import hashlib
import json
from pathlib import Path

TASK = Path(__file__).resolve().parents[1]
OLD = TASK.parent / 'PHANTOM-M1-ACTION-LOOP-038' / 'evidence'

def main():
    result = {'contract': 'HISTORICAL038_READ_ONLY', 'scenes': {}}
    keys = ['materializedAtNanos', 'dead', 'moving', 'casting', 'attacking', 'nativeDamageSequence', 'nativeFarmCycleSequence', 'nativeRewardSequence', 'nativeFirstUnprovenReason', 'nativePhase', 'runtimeReason', 'continuationSession', 'liveResourceRecovery', 'liveAutoPlayTickReason', 'liveAutoUseTickReason', 'currentActionGuard', 'actionReason', 'targetObjectId', 'aiAttackTarget', 'mp', 'x', 'y', 'z']
    for scene in 'bc':
        root = OLD / f'FINAL_SCENE038{scene}'
        path = root / 'all-samples.json'
        samples = json.loads(path.read_text('utf-8-sig'))
        baseline = json.loads((root / 'baseline-cohort.json').read_text('utf-8-sig'))
        verdict = json.loads((root / 'continuity-v2.json').read_text('utf-8-sig'))
        rows = []
        for enrolled in baseline:
            identity = str(enrolled['profileId'])
            timeline = []
            previous = None
            for sample in samples:
                actor = next((actor for actor in sample['actors'] if str(actor['profileId']) == identity), None)
                if actor is None:
                    continue
                # Changes of actual phase/owner/session/guard are separate from periodic pose samples.
                signature = tuple(actor.get(key) for key in keys[:2] + keys[8:16])
                elapsed = float(sample['elapsedSeconds'])
                if signature != previous or not timeline or elapsed - timeline[-1]['elapsed'] >= 30:
                    timeline.append({'elapsed': round(elapsed, 3), **{key: actor.get(key, '') for key in keys}})
                    previous = signature
            rows.append({'profileId': identity, 'originalEpoch': enrolled['materializedAtNanos'], 'deadAtBaseline': enrolled.get('dead'), 'timeline': timeline})
        result['scenes'][scene] = {'samplesSha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'originalVerdict': verdict, 'actors': rows}
    output = TASK / 'evidence/HISTORICAL039.json'
    assert not output.exists()
    output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    for scene, value in result['scenes'].items():
        for actor in value['actors']:
            print(scene, actor['profileId'], 'baselineDead=' + str(actor['deadAtBaseline']), 'epochs=' + ','.join(sorted({row['materializedAtNanos'] for row in actor['timeline']})))
            if actor['profileId'] in ('437', '515', '576', '368', '181', '215', '415', '594', '615'):
                for row in actor['timeline']:
                    print(' ', row['elapsed'], row['nativePhase'], row['runtimeReason'], 'session=' + row['continuationSession'], 'damage=' + row['nativeDamageSequence'], 'cycles=' + row['nativeFarmCycleSequence'], 'target=' + row['targetObjectId'], row['nativeFirstUnprovenReason'], 'rest=' + row['liveResourceRecovery'])

if __name__ == '__main__':
    main()
