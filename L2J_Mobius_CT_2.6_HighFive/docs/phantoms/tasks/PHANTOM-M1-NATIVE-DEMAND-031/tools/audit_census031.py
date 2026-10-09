#!/usr/bin/env python3
"""Read-only audit of TASK030 global-admission.tsv. Counts are NOT a native admission decision."""
import argparse
import csv
import json
from collections import Counter
from pathlib import Path

REQUIRED = ('profileId','objectId','state','calendarOnline','admitted','farmAllowed',
            'readinessComplete','firstGuard','sampleNanos')
BOOL = ('calendarOnline','admitted','farmAllowed','readinessComplete')

def audit(rows):
    rows = list(rows)
    seen = set()
    for row in rows:
        missing = [key for key in REQUIRED if key not in row]
        if missing:
            raise ValueError('Missing columns: ' + ','.join(missing))
        profile = int(row['profileId'])
        if profile <= 0 or profile in seen:
            raise ValueError('Invalid/duplicate profileId: ' + str(profile))
        seen.add(profile)
        for key in BOOL:
            if row[key] not in ('true', 'false'):
                raise ValueError('Invalid boolean ' + key + ' for ' + str(profile))
        if int(row['sampleNanos']) < 0:
            raise ValueError('Negative sample time')
    ready_online = [r for r in rows if r['state']=='READY' and r['calendarOnline']=='true']
    old = [r for r in ready_online if r['farmAllowed']=='true' and r['readinessComplete']=='true']
    times = [int(r['sampleNanos']) for r in rows]
    return {
        'kind':'READ_ONLY_FILTER_AUDIT_NOT_NATIVE_ADMISSION',
        'rows':len(rows), 'states':dict(sorted(Counter(r['state'] for r in rows).items())),
        'ready_online':len(ready_online), 'old_setup_matches':len(old),
        'ready_online_background_farm_denied':sum(r['farmAllowed']=='false' for r in ready_online),
        'ready_online_due_snapshot_incomplete':sum(r['readinessComplete']=='false' for r in ready_online),
        'reported_admitted':sum(r['admitted']=='true' for r in rows),
        'first_guard_counts':dict(Counter(r['firstGuard'] for r in rows).most_common()),
        'sample_span_seconds':(max(times)-min(times))/1e9 if times else None,
        'atomic_global_snapshot':False,
        'diagnosis':'Not established by this file. No guards bypassed; no rows repaired.',
    }

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('input',type=Path)
    p.add_argument('--output',type=Path)
    args=p.parse_args()
    try:
        with args.input.open(encoding='utf-8-sig',newline='') as f:
            result=audit(csv.DictReader(f,delimiter='\t'))
        text=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
        if args.output:
            with args.output.open('x',encoding='utf-8') as f: f.write(text)
        else: print(text,end='')
    except (OSError,ValueError,KeyError) as e:
        p.exit(2,str(e)+'\n')
if __name__=='__main__': main()
