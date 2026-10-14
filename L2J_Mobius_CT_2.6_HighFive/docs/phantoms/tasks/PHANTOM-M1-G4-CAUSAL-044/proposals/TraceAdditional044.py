"""Summarize immutable one-profile source reads without retrospective attribution."""
import hashlib
import json
from pathlib import Path
import re

TASK = Path(__file__).resolve().parents[1]
SCENE = TASK / 'evidence/DIAG_A2_SOURCE044'


def pin(path):
    return {'path': str(path), 'bytes': path.stat().st_size,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def rows(path):
    return [json.loads(line) for line in path.read_text(encoding='utf-8-sig').splitlines() if line]


def main():
    target = TASK / 'evidence/ADDITIONAL_SOURCE_RESULT044.json'
    if target.exists():
        raise ValueError('Immutable additional trace result exists')
    raw = SCENE / 'full-native/full-cohort-samples.jsonl'
    phases_path = SCENE / 'phase-markers.jsonl'
    phases = rows(phases_path) if phases_path.exists() else []
    away = next((int(row['sampleNanos']) for row in phases if row['phase'] == 'AWAY_ARRIVED'), None)
    frames = rows(raw) if raw.exists() else []
    captures, sources = [], {}
    for frame in frames:
        if len(frame['actors']) != 1:
            raise ValueError('Exact one-profile signal view required')
        actor = frame['actors'][0]
        view = actor.get('schedulerSignalSources044')
        if view is None:
            raise ValueError('Actual Scheduler signal view missing')
        capture = {'sampleNanos': frame['sampleNanos'], 'profileId': actor['profileId'],
                   'away': away is not None and int(frame['sampleNanos']) >= away,
                   'sources': view, 'operatorAdmission': actor.get('operatorAdmission'),
                   'worldPresent': actor.get('worldPresent'), 'dead': actor.get('dead'),
                   'outstanding': actor.get('current.nativeOwnerOutstanding'),
                   'observerPrewarm': actor.get('observerPrewarm'),
                   'observerNativeVisible': actor.get('observerNativeVisible')}
        captures.append(capture)
        for body in re.findall(r'SignalSourceSnapshot\[([^\]]+)\]', view):
            fields = dict(part.split('=', 1) for part in body.split(', '))
            key = (actor['profileId'], fields['sourceKey'], fields['sequence'], fields['expiresAtNanos'])
            item = sources.setdefault(key, {'profileId': actor['profileId'], 'sourceKey': fields['sourceKey'],
                                           'sequence': fields['sequence'], 'requiredState': fields['requiredState'],
                                           'originalTtlMillis': fields['originalTtlMillis'],
                                           'expiresAtNanos': fields['expiresAtNanos'], 'frames': 0, 'awayFrames': 0,
                                           'first': fields, 'last': fields})
            item['frames'] += 1
            item['awayFrames'] += int(capture['away'])
            item['last'] = fields
    away_captures = [row for row in captures if row['away']]
    endpoints = {name: (group[0], group[-1]) if group else []
                 for name, group in [('all', captures), ('away', away_captures)]}
    value = {'contract': 'ADDITIONAL_SOURCE_ONLY044_RESULT', 'fullG4': False,
             'retrospectiveD1Owner': 'UNKNOWN; new profile cannot attribute historical1189',
             'nativeReads': 'NONATOMIC_VOLATILE; source list internally monitor-locked',
             'frameCount': len(frames), 'awayFrameCount': len(away_captures),
             'sources': list(sources.values()), 'phaseNames': [row['phase'] for row in phases],
             'endpoints': endpoints,
             'pins': [pin(path) for path in [raw, phases_path, SCENE / 'primary.json',
                      SCENE / 'episode-failure.txt', SCENE / 'stock-stop-verified.json'] if path.exists()]}
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: value[key] for key in ('frameCount', 'awayFrameCount', 'phaseNames')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
