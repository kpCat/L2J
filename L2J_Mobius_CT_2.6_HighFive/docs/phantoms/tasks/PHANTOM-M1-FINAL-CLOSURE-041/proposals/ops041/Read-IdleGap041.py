"""Read existing complete native samples; identify real no-cycle intervals."""
import argparse
import json
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('scene', type=Path)
p.add_argument('profile')
a = p.parse_args()
frames = json.loads((a.scene / 'all-samples.json').read_text(encoding='utf-8-sig'))
rows = [(float(f['elapsedSeconds']), next(x for x in f['actors'] if str(x['profileId']) == a.profile)) for f in frames]
print('AVAILABLE_FIELDS', ','.join(rows[0][1]))
start = 0
gaps = []
for i in range(1, len(rows)):
    if rows[i][1]['nativeFarmCycleSequence'] != rows[start][1]['nativeFarmCycleSequence'] or rows[i][1]['materializedAtNanos'] != rows[start][1]['materializedAtNanos']:
        gaps.append((rows[i][0] - rows[start][0], start, i))
        start = i
gaps.append((rows[-1][0] - rows[start][0], start, len(rows) - 1))
fields = ('nativeFarmCycleSequence', 'nativePhase', 'nativeFirstUnprovenReason', 'actionReason', 'actionOutcome', 'liveAutoPlayTickReason', 'liveAutoUseTickReason', 'liveResourceRecovery', 'liveResourceReason', 'liveResourceSinceNanos', 'liveResourceExpired', 'mp', 'maxMp', 'hp', 'maxHp', 'intention', 'casting', 'dead', 'targetObjectId', 'targetHp', 'nativeTargetNpcId', 'x', 'y', 'z', 'nativeLastCompletedEvent')
for seconds, lo, hi in sorted(gaps, reverse=True)[:2]:
    stride = max(1, (hi - lo) // 8)
    print(json.dumps({'profile': a.profile, 'gap': seconds, 'from': rows[lo][0], 'to': rows[hi][0], 'samples': [{'elapsed': rows[i][0], **{k: rows[i][1].get(k) for k in fields if k in rows[i][1]}} for i in sorted(set(range(lo, hi + 1, stride)) | {hi})]}, ensure_ascii=False))
