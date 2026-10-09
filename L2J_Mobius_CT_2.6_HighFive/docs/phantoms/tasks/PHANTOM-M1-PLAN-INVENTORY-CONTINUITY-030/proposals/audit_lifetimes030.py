"""Offline structural receipt audit. Reads JSONL, stores history on disk, never repairs data.

Not a native proof: input producers must attest their identity, snapshots and birth count.
This checks lifecycle/receipt coverage only; SQL equality and background rewards are separate.
The CLI refuses existing output files. Python standard library only.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import sqlite3
from pathlib import Path
from typing import Iterable, Mapping, Any

EDGE_KINDS = {'BACKGROUND_COMMIT','BACKGROUND_REPLAY','PROJECTION_COMMIT'}
KINDS = {'RUN_START','RUN_END','REGISTER','PREPARED','FINALIZED','TERMINAL'} | EDGE_KINDS
DIGEST = re.compile(r'^[0-9a-f]{64}$')
MAX_RECORD = 65536
MAX_INPUT = 512 * 1024 * 1024


def positive_int(value: Any) -> bool:
    return type(value) is int and value > 0


def audit(events: Iterable[Mapping[str, Any]], db_path: str = ':memory:') -> dict[str, Any]:
    """Data errors produce coverage_pass=False. Infrastructure failures raise, never PASS."""
    count_faults = 0
    first_faults: list[str] = []
    def fault(code: str) -> None:
        nonlocal count_faults
        count_faults += 1
        if len(first_faults) < 32:
            first_faults.append(code)
    db = sqlite3.connect(db_path)
    try:
        db.execute('CREATE TABLE events(run TEXT, seq INTEGER, kind TEXT, p INTEGER, o INTEGER, e INTEGER, payload TEXT, PRIMARY KEY(run,seq))')
        db.execute('CREATE INDEX scopes ON events(run,p,o,e,seq)')
        raw_count = 0
        for original in events:
            raw_count += 1
            item = dict(original)
            payload = json.dumps(item, ensure_ascii=False, sort_keys=True, separators=(',',':'), allow_nan=False)
            run, seq, kind = item.get('run_id'), item.get('seq'), item.get('kind')
            if not isinstance(run,str) or not 1 <= len(run) <= 128 or not positive_int(seq) or kind not in KINDS or len(payload.encode('utf-8')) > MAX_RECORD:
                fault('RECORD_IDENTITY_OR_SIZE'); continue
            p=o=e=0
            if kind in EDGE_KINDS:
                p,o = (item.get(x) for x in ('profile_id','object_id'))
                if not all(positive_int(x) for x in (p,o)) or not DIGEST.fullmatch(str(item.get('raw_sha256',''))):
                    fault('BACKGROUND_EDGE_IDENTITY'); continue
            elif kind not in {'RUN_START','RUN_END'}:
                p,o,e = (item.get(x) for x in ('profile_id','object_id','epoch'))
                if not all(positive_int(x) for x in (p,o,e)):
                    fault('LIFETIME_IDENTITY'); continue
            if kind in {'PREPARED','FINALIZED','TERMINAL'} and not DIGEST.fullmatch(str(item.get('receipt',''))):
                fault('RECEIPT_DIGEST'); continue
            old = db.execute('SELECT payload FROM events WHERE run=? AND seq=?',(run,seq)).fetchone()
            if old is not None:
                if old[0] != payload: fault('CONFLICTING_DUPLICATE')
                continue
            db.execute('INSERT INTO events VALUES(?,?,?,?,?,?,?)',(run,seq,kind,p,o,e,payload))
        db.commit()
        total_registered=total_terminal=run_count=0
        run_cursor=db.execute('SELECT DISTINCT run FROM events ORDER BY run')
        for (run,) in run_cursor:
            run_count+=1
            starts = db.execute("SELECT seq,payload FROM events WHERE run=? AND kind='RUN_START'",(run,)).fetchall()
            ends = db.execute("SELECT seq,payload FROM events WHERE run=? AND kind='RUN_END'",(run,)).fetchall()
            if len(starts)!=1 or len(ends)!=1:
                fault('RUN_BOUNDARY_MISSING_OR_DUPLICATE')
            if len(starts)==1:
                start_data=json.loads(starts[0][1])
                if not re.fullmatch(r'[0-9a-f]{40}',str(start_data.get('code_sha',''))) or not start_data.get('incarnation'):
                    fault('RUN_PROVENANCE')
            if len(starts)==1 and len(ends)==1:
                lo,hi=db.execute('SELECT MIN(seq),MAX(seq) FROM events WHERE run=?',(run,)).fetchone()
                if starts[0][0]!=lo or ends[0][0]!=hi or lo>=hi: fault('RUN_ORDER')
            reg_count=db.execute("SELECT COUNT(*) FROM events WHERE run=? AND kind='REGISTER'",(run,)).fetchone()[0]
            total_registered += reg_count
            if len(ends)==1:
                end=json.loads(ends[0][1])
                if type(end.get('native_births')) is not int or end['native_births'] != reg_count: fault('NATIVE_BIRTH_COVERAGE')
                if end.get('dropped') != 0 or end.get('failure') != '' or end.get('exporter_drained') is not True: fault('CAPTURE_OR_EXPORT_FAILURE')
            scope_cursor=db.execute('SELECT DISTINCT p,o,e FROM events WHERE run=? AND p>0 AND e>0 ORDER BY p,o,e',(run,))
            for p,o,e in scope_cursor:
                registered=False
                terminal=False
                prepared: dict[str,int]={}
                finalized:set[str]=set()
                last_final=''
                for seq,kind,payload in db.execute('SELECT seq,kind,payload FROM events WHERE run=? AND p=? AND o=? AND e=? ORDER BY seq',(run,p,o,e)):
                    row=json.loads(payload)
                    if terminal:
                        fault('EVENT_AFTER_TERMINAL'); continue
                    if kind=='REGISTER':
                        if registered: fault('DUPLICATE_LIFETIME_REGISTER')
                        registered=True
                    elif not registered:
                        fault('EVENT_WITHOUT_REGISTER')
                    elif kind=='PREPARED':
                        receipt=row['receipt']
                        if receipt in prepared: fault('DUPLICATE_RECEIPT_PREPARE')
                        prepared[receipt]=seq
                    elif kind=='FINALIZED':
                        receipt=row['receipt']
                        if receipt not in prepared or receipt in finalized: fault('FINALIZE_WITHOUT_UNIQUE_PREPARE')
                        else: finalized.add(receipt); last_final=receipt
                    elif kind=='TERMINAL':
                        terminal=True; total_terminal+=1
                        if row.get('owner_state')!='DETACHED' or row.get('permanent') is not True or row.get('outstanding')!=0 or row.get('timers')!=0:
                            fault('TERMINAL_NOT_QUIESCENT_PERMANENT')
                        if not last_final or row['receipt']!=last_final or set(prepared)!=finalized:
                            fault('TERMINAL_RECEIPT_GAP')
                if not terminal: fault('LIFETIME_NOT_TERMINAL')
        if run_count==0 or total_registered==0:
            fault('EMPTY_INPUT_NOT_PROOF')
        return dict(contract='STRUCTURAL_LIFETIME_COVERAGE030', coverage_pass=count_faults==0,
                    raw_records=raw_count, runs=run_count, registered=total_registered,
                    terminals=total_terminal, fault_count=count_faults, first_faults=first_faults,
                    native_correctness='NOT_EVALUATED', sql_equality='NOT_EVALUATED')
    finally:
        db.close()


def iter_jsonl(path: Path):
    total=0
    with path.open('rb') as source:
        while True:
            raw=source.readline(MAX_RECORD+1)
            if not raw: break
            total+=len(raw)
            if len(raw)>MAX_RECORD or total>MAX_INPUT: raise ValueError('Input size bound exceeded')
            if not raw.strip(): continue
            value=json.loads(raw.decode('utf-8-sig'))
            if not isinstance(value,dict): raise ValueError('Each event must be an object')
            yield value


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input',type=Path)
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    dbfile=args.output.with_suffix(args.output.suffix+'.sqlite')
    if args.output.exists() or dbfile.exists(): raise FileExistsError('Output artifacts already exist')
    # Exclusive creation also prevents overwriting another concurrent audit.
    with dbfile.open('xb'): pass
    result=audit(iter_jsonl(args.input),str(dbfile))
    digest=hashlib.sha256()
    with args.input.open('rb') as src:
        for block in iter(lambda:src.read(65536),b''): digest.update(block)
    result['input_sha256']=digest.hexdigest()
    with args.output.open('x',encoding='utf-8') as out: json.dump(result,out,ensure_ascii=False,indent=2)
    print(json.dumps(result,ensure_ascii=False))
    return 0 if result['coverage_pass'] else 2

if __name__=='__main__':
    raise SystemExit(main())
