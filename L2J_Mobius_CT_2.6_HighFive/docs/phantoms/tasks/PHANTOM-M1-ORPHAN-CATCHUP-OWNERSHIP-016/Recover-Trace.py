"""Export frozen causal events from raw mailbox XML, preserving literal attribute TABs."""
import html
import json
import re
from pathlib import Path

TASK = Path(__file__).resolve().parent
MODULE = TASK.parents[3]
PRIVATE = MODULE / '.phantom-local/observe016'
pages = sorted((PRIVATE / 'causal').glob('*.json'), key=lambda p: int(p.name.split('-')[0]))
results = [(p, json.loads(p.read_text(encoding='utf-8-sig'))) for p in pages]
end = next(r for _, r in results if r['operation'] == 'END_PHANTOM_CAUSAL_TRACE')
begin = next(r for _, r in results if r['operation'] == 'BEGIN_PHANTOM_CAUSAL_TRACE')
status = next(r for _, r in results if r['operation'] == 'STATUS')
events = {}
for path, result in results:
    if int(result['sequence']) < int(end['sequence']):
        continue
    raw = (PRIVATE / 'runtime/playtest-pilot/results' / (result['requestId'] + '.xml')).read_text(encoding='utf-8-sig')
    for seq, value in re.findall(r'\b(event\.\d+)="([^"]*)"', raw):
        value = html.unescape(value)
        fields = value.split('\t')
        if len(fields) != 11:
            raise SystemExit(f'Raw mailbox separator unavailable: fields={len(fields)} tabs={raw.count(chr(9))}')
        if int(fields[0]) != int(seq.split('.')[1]):
            raise SystemExit('Raw sequence mismatch')
        events[int(fields[0])] = value
meta = end['candidate']
if len(events) != int(meta['retained']) or meta['active'] != 'false':
    raise SystemExit('Frozen retained/export count mismatch')
header = 'seq\tnanoTime\tthreadName\tprofileId\tevent\tstateA\tstateB\treason\tvalue1\tvalue2\tvalue3'
(TASK / 'CAUSAL_TRACE.tsv').write_text(header + '\n' + '\n'.join(events[k] for k in sorted(events)) + '\n', encoding='utf-8')
proof = {key: meta[key] for key in ('watched', 'attempts', 'dropped', 'retained', 'active', 'startedNanos', 'snapshotNanos')}
proof.update(preState='ARMED_IDLE', clientState='IN_GAME', clientStateProof='successful STATUS via sessionValid/realClient admission', identityOwner=status['before']['identityOwner'], objectId=268492939, beginUtc=begin['endUtc'], snapshotUtc=end['endUtc'], endUtc=end['endUtc'], exported=len(events), exportRecovery='raw XML attributes; literal tabs preserved; recorder not repeated')
for key in ('x', 'y', 'z'):
    proof[key] = status['before'][key]
(TASK / 'CAUSAL_META.json').write_text(json.dumps(proof, indent=2) + '\n', encoding='utf-8')
print(json.dumps(proof))
