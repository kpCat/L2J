"""Read immutable043 observations; write only one bounded044 causal summary."""
import hashlib
import json
from pathlib import Path
import re

MODULE = Path(__file__).resolve().parents[5]
TASK = Path(__file__).resolve().parents[1]
OLD = MODULE / 'docs/phantoms/tasks/PHANTOM-M1-SOFT-HANDOFF-043/evidence'


def read_rows(path):
    return [json.loads(line) for line in path.read_text(encoding='utf-8-sig').splitlines() if line]


def field(text, name):
    match = re.search(r'\b' + name + r'=([^,\]]+)', text)
    return match.group(1) if match else None


def selected(frame, profile):
    return next((a for a in frame.get('actors', []) if str(a.get('profileId')) == str(profile)), None)


def summarize(frame, profile):
    actor = selected(frame, profile)
    admission = actor.get('operatorAdmission', '')
    scheduler = admission.split('scheduler=', 1)[-1].split('materialization=', 1)[0]
    return {
        'sampleNanos': frame['sampleNanos'],
        'scheduler': {key: field(scheduler, key) for key in ('effectiveState', 'requestedState', 'activeSignalSources', 'tickSequence', 'nextDueNanos', 'lastTransitionNanos')},
        'worldPresent': actor.get('worldPresent'),
        'readiness': {key: field(admission, key) for key in ('committedCursorMinute', 'requestedHorizonMinute', 'reason')},
        'ecologyLiveOwner': actor.get('ecology.entry._liveOwner'),
        'ecologyStage': actor.get('ecology.entry._stage'),
        'nativeOutstanding': actor.get('current.nativeOwnerOutstanding'),
    }


def main():
    c1path = OLD / 'LIFE_C1_043/phase-markers.jsonl'
    c1 = read_rows(c1path)
    phases = {row['phase']: row for row in c1}
    start, end = phases['BACKGROUND_ABSENT']['sampleNanos'], phases['RETURN']['sampleNanos']
    samplepath = OLD / 'LIFE_C1_043/full-native/full-cohort-samples.jsonl'
    away = [row for row in read_rows(samplepath) if start <= row['sampleNanos'] <= end and selected(row, 776)]
    states = sorted({summarize(row, 776)['scheduler']['effectiveState'] for row in away})
    d1path = OLD / 'D1-LIFE043.json'
    d1 = json.loads(d1path.read_text(encoding='utf-8-sig'))
    files = [c1path, samplepath, d1path]
    output = {
        'contract': 'TASK044_OFFLINE_CAUSAL_INPUT',
        'inputSha256': {str(path.relative_to(MODULE)): hashlib.sha256(path.read_bytes()).hexdigest() for path in files},
        'D1': {'profileId': 1189, 'lastAwayNanos': d1['lastQuietSampleNanos'], 'signalKey': 'EVIDENCE_GAP',
               'reason': 'No signal-key/sequence/expiry fields or flight events in retained043 exporter; count cannot identify source.'},
        'C1': {'profileId': 776, 'actualAbsentSeconds': (end - start) / 1e9, 'sampleCount': len(away),
               'observedSchedulerStates': states, 'schedulerBackgroundDue': None,
               'phases': {name: summarize(row, 776) for name, row in phases.items()},
               'classification': 'NO_BACKGROUND_WORK_DUE_SLEEPING_ECOLOGY_FENCED',
               'ordinaryCadenceMillis': [300000, 900000],
               'alternativeProducer': 'Ecology native-release catch-up is separately queued; requested horizon advances, cursor remains29865613 through RETURN. No committed producer in the measured interval; no causal failure of that producer established.'},
    }
    target = TASK / 'evidence/OFFLINE044.json'
    if target.exists():
        raise RuntimeError('Immutable044 offline output already exists')
    target.write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('C1:', output['C1']['actualAbsentSeconds'], 'seconds;', len(away), 'samples;', states, '; actual BG work due: absent')
    print('D1: owner key/sequence/expiry EVIDENCE_GAP; targeted snapshot required')


if __name__ == '__main__':
    main()
