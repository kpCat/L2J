"""Summarize final TASK038 raw evidence without changing verdicts or counters."""
import csv
import hashlib
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

TASK = Path(__file__).resolve().parents[1]
EVIDENCE = TASK / 'evidence'
MODULE = TASK.parents[3]
def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def props(path):
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line)
def main():
    output = EVIDENCE / 'RESULT_DATA038.json'
    assert not output.exists(), 'Immutable final facts already exist'
    frozen = read(EVIDENCE / 'J2_FREEZE.json')
    tests = []
    for name in ('J2_ACTION038', 'FINAL_J2_NATIVE038', 'FINAL_J2_LIFECYCLE038',
                 'FINAL_J2_Q15_038', 'FINAL_J2_CAPTURE038', 'FINAL_J2_PROJECTION038',
                 'FINAL_J2_CAPACITY038', 'FINAL_J2_MODEL038', 'FINAL_J2_SQL038',
                 'FINAL_J2_COMPACT038', 'FINAL_J2_VISIBLE038', 'FINAL_J2_DEBT038',
                 'FINAL_J2_TRAVEL038',
                 *(f'FINAL_J2_MATRIX038_{i:02}' for i in range(1, 19))):
        text = (EVIDENCE / f'{name}.log').read_text(encoding='utf-8-sig')
        summary = re.findall(r'^SUMMARY:.*total=(\d+) passed=(\d+) failed=(\d+)', text, re.M)[-1]
        provenance = read(EVIDENCE / f'{name}.provenance.json')
        assert provenance['jarSha256'] == frozen['gameJarSha256'] and not provenance['developmentBinFirst']
        tests.append({'label': name, 'total': int(summary[0]), 'passed': int(summary[1]),
                      'failed': int(summary[2]), 'logSha256': sha(EVIDENCE / f'{name}.log'),
                      'provenanceSha256': sha(EVIDENCE / f'{name}.provenance.json')})
    scenes = []
    for episode in 'bc':
        scene = EVIDENCE / f'FINAL_SCENE038{episode}'
        evaluation = read(scene / 'continuity-v2.json')
        baseline = read(scene / 'baseline-cohort.json')
        primary = read(scene / 'primary.json')
        samples = read(scene / 'all-samples.json')
        lifetime = read(EVIDENCE / f'FINAL_ALL_LIFETIMES038{episode}.json')
        stop = read(scene / 'stock-stop-verified.json')
        typed = props(scene / 'stock-stop/typed-stop-done.properties')
        assert stop['sourceSha'] == frozen['sourceSha'] and stop['healthyStockStop']
        typed_pass = ('phase=DONE, outcome=COMPLETE' in typed['progress']
                      and 'retainedEntries=0' in typed['materialization']
                      and all(typed[key] == 'false' for key in ('SCHEDULED_POOLShutdown',
                              'INSTANT_POOLShutdown', 'HIGH_PRIORITY_SCHEDULED_POOLShutdown'))
                      and not typed['proofFailure'])
        with (scene / 'stopped-sql/characters.tsv').open(encoding='utf-8-sig') as stream:
            classes = {row['profileId']: row['classId'] for row in csv.DictReader(stream, delimiter='\t')}
        rows = []
        for row in evaluation['rows']:
            actor = next(actor for actor in baseline if actor['profileId'] == row['profileId'])
            row = dict(row)
            row.update(originalEpoch=actor['materializedAtNanos'], originalDead=actor['dead'],
                       nativeClassId=classes[row['profileId']])
            rows.append(row)
        scenes.append({'episode': episode, 'seconds': evaluation['seconds'],
                       'sampleCount': len(samples), 'primaryIds': [p['profileId'] for p in primary],
                       'continuityPass': evaluation['pass'], 'passingRows': sum(row['pass'] for row in rows),
                       'rows': rows, 'legacy': read(scene / 'legacy-result.json'),
                       'allLifetimes': {'births': lifetime['births'],
                          'allBirthsTerminal': lifetime['allBirthsTerminal'],
                          'verified': sum(row['pass'] for row in lifetime['rows']),
                          'notVerified': sum(not row['pass'] for row in lifetime['rows']),
                          'reasons': dict(Counter(row.get('reason', '') for row in lifetime['rows'] if not row['pass']))},
                       'typedGlobalDrainPassBeforePools': typed_pass, 'stockStop': stop,
                       'evaluationSha256': sha(scene / 'continuity-v2.json'),
                       'samplesSha256': sha(scene / 'all-samples.json')})
    matrix = [row for row in tests if row['label'].startswith('FINAL_J2_MATRIX038_')]
    assert sum(row['total'] for row in matrix) == 93
    value = {'taskResult': 'FAILED_ACTION_LOOP', 'M1': 'OPEN', 'REAL_FINAL': 'NOT_RUN',
             'stopAuthority': 'TASK038_CONTRACT', 'frozen': frozen,
             'capturedUtc': datetime.now(timezone.utc).isoformat(), 'tests': tests,
             'matrix': {'total': 93, 'passed': sum(row['passed'] for row in matrix),
                        'failed': sum(row['failed'] for row in matrix)}, 'scenes': scenes,
             'budget': {'fullStarts': 5, 'productionBuilds': 2, 'fullScenes': 2,
                        'limits': [8, 4, 4], 'actualStartUtc': '2026-10-11T23:09:28Z',
                        'hardStopUtc': '2026-10-12T07:09:28Z'}}
    assert any(not scene['continuityPass'] for scene in scenes)
    output.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'matrix': value['matrix'], 'scenes': [(s['episode'], s['passingRows']) for s in scenes],
                      'result': value['taskResult']}))
if __name__ == '__main__':
    main()
