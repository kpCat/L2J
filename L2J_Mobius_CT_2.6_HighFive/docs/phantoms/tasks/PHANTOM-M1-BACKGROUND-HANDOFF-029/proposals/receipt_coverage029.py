"""Read-only normalized lifetime coverage check. Does not prove SQL equality itself.

Input must be produced from independent native hooks and existing strict SQL validator.
Never manufacture events to repair gaps; no counters or database rows are written here.
"""
from __future__ import annotations
import argparse
import json
import re
from pathlib import Path
from typing import Any, Iterable

HEX64=re.compile(r'[0-9a-f]{64}\Z')

def audit(events: Iterable[dict[str, Any]]) -> dict[str, Any]:
    scopes: dict[tuple[int,int,int],dict[str,Any]]={}
    receipt_owner: dict[str,tuple[int,int,int]]={}
    profiles: set[int]=set()
    for ordinal,event in enumerate(events,1):
        kind=event.get('kind')
        if kind=='SCENE_SELECT':
            # Only UI/telemetry view changes. Historical obligations stay enrolled.
            continue
        values=[event.get(k) for k in ('profile','object','epoch')]
        if any(type(v) is not int or v<=0 for v in values):
            raise ValueError(f'{ordinal}: exact positive lifetime required')
        key=tuple(values)
        if kind=='ENROLL':
            profiles.add(key[0])
            if key not in scopes: scopes[key]={'receipts':{},'latest':None,'terminal':None}
            if len(scopes)>128 or len(profiles)>32: raise ValueError('receipt enrollment capacity; no eviction')
            continue
        if key not in scopes: raise ValueError(f'{ordinal}: unregistered lifetime')
        row=scopes[key]
        digest=event.get('receipt')
        if not isinstance(digest,str) or not HEX64.fullmatch(digest):raise ValueError('exact receipt digest required')
        if digest in receipt_owner and receipt_owner[digest]!=key:raise ValueError('receipt reused for another lifetime')
        if kind=='PREPARED':
            if row['terminal'] is not None:raise ValueError('new receipt after terminal')
            if digest not in row['receipts']:
                row['receipts'][digest]={'finalized':False,'sql':False}
                row['latest']=digest
                receipt_owner[digest]=key
        elif kind=='FINALIZED':
            if digest not in row['receipts']:raise ValueError('finalize without native prepare')
            row['receipts'][digest]['finalized']=True
        elif kind=='SQL_VERIFIED':
            if digest not in row['receipts'] or not row['receipts'][digest]['finalized']:
                raise ValueError('SQL verification lacks finalized receipt')
            proof=event.get('proof')
            if not isinstance(proof,str) or not proof.strip():raise ValueError('independent comparison proof required')
            row['receipts'][digest]['sql']=True
        elif kind=='TERMINAL':
            if digest not in row['receipts']:raise ValueError('terminal has no prepared receipt')
            if row['terminal'] is not None and row['terminal']!=event:raise ValueError('conflicting terminal')
            row['terminal']=dict(event)
        else:raise ValueError(f'unknown event kind {kind!r}')
    outcomes=[]
    for (profile,obj,epoch),row in sorted(scopes.items()):
        terminal=row['terminal']
        complete=bool(row['receipts']) and all(r['finalized'] and r['sql'] for r in row['receipts'].values())
        complete=complete and terminal is not None and terminal.get('permanent_seal') is True
        complete=complete and terminal.get('owner_state')=='DETACHED' and terminal.get('receipt')==row['latest']
        outcomes.append({'profile':profile,'object':obj,'epoch':epoch,'receipts':len(row['receipts']),
                         'terminal':terminal is not None,'coverage':bool(complete)})
    return {'coverage_pass':bool(outcomes) and all(x['coverage'] for x in outcomes),
            'lifetimes':len(outcomes),'receipts':len(receipt_owner),'outcomes':outcomes,
            'scope':'NATIVE_RECEIPT_COVERAGE_ONLY; linked background SQL equality is a separate gate'}

def main() -> int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('events',type=Path);p.add_argument('report',type=Path);a=p.parse_args()
    if a.report.exists():p.error('report exists; immutable output required')
    try:
        with a.events.open(encoding='utf-8-sig') as f:
            rows=[json.loads(s) for s in f if s.strip()]
        result=audit(rows)
    except (OSError,ValueError,TypeError) as e:
        p.exit(2,f'Invalid evidence: {e}\n')
    a.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return 0 if result['coverage_pass'] else 1
if __name__=='__main__':raise SystemExit(main())
