#!/usr/bin/env python3
"""Read one existing Pilot mailbox outcome. Never dispatches, replays, or edits.

Run against an immutable copy whenever possible. A live filesystem read is not an
atomic runtime snapshot and cannot prove native movement, lifecycle or consent.
The caller supplies the expected identity from its previously captured command.
Only the stdlib is used. Python 3.10+.
"""
from __future__ import annotations
import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import stat
import sys
import uuid
import xml.etree.ElementTree as ET

LIMIT=65536
READ_OPERATIONS=frozenset({'STATUS','CAPABILITIES','SNAPSHOT_PHANTOMS',
                          'SNAPSHOT_M1_ENVELOPE','SNAPSHOT_SELECTED_PHANTOM_TRACE',
                          'SNAPSHOT_TARGETS','SNAPSHOT_PHANTOM_CAUSAL_TRACE'})

class AuditError(ValueError):
    """Untrusted, unbounded, ambiguous or identity-incompatible input."""

def canonical_uuid(value: str) -> str:
    try: normalized=str(uuid.UUID(value))
    except (ValueError, AttributeError, TypeError) as exc: raise AuditError('INVALID_UUID') from exc
    if normalized != value.lower(): raise AuditError('NONCANONICAL_UUID')
    return normalized

@dataclass(frozen=True)
class Expected:
    request_id: str
    session_id: str
    run_id: str
    sequence: int
    operation: str
    actor_id: int
    def __post_init__(self) -> None:
        for value in (self.request_id,self.session_id,self.run_id): canonical_uuid(value)
        if (type(self.sequence) is not int or self.sequence < 1 or
            type(self.actor_id) is not int or self.actor_id < 1 or
            re.fullmatch(r'[A-Z][A-Z0-9_]{0,63}',self.operation) is None):
            raise AuditError('INVALID_EXPECTED_IDENTITY')

def safe_path(path: Path) -> Path:
    """Reject symlinks/reparse points instead of silently resolving through them."""
    absolute=Path(path).absolute()
    if '..' in absolute.parts: raise AuditError('PARENT_PATH_NOT_ALLOWED')
    for p in [*reversed(absolute.parents),absolute]:
        try: info=p.lstat()
        except FileNotFoundError: continue
        if stat.S_ISLNK(info.st_mode) or (getattr(info,'st_file_attributes',0)&0x400):
            raise AuditError('REPARSE_OR_SYMLINK')
    return absolute

def read_bounded(path: Path) -> bytes | None:
    path=safe_path(path)
    try:
        info=path.stat()
        if not stat.S_ISREG(info.st_mode):raise AuditError('NOT_REGULAR_FILE')
        if info.st_size>LIMIT:raise AuditError('RECORD_EXCEEDS_64K')
        with path.open('rb') as stream:data=stream.read(LIMIT+1)
    except FileNotFoundError:return None
    if len(data)>LIMIT:raise AuditError('RECORD_EXCEEDS_64K')
    return data

def xml_root(data: bytes, tag: str) -> ET.Element:
    try:text=data.decode('utf-8-sig',errors='strict')
    except UnicodeDecodeError as exc:raise AuditError('INVALID_UTF8') from exc
    if re.search(r'<!\s*(DOCTYPE|ENTITY)',text,re.IGNORECASE):raise AuditError('DTD_OR_ENTITY_REJECTED')
    try:root=ET.fromstring(text)
    except ET.ParseError as exc:raise AuditError('INVALID_XML') from exc
    if root.tag!=tag or root.get('version')!='1':raise AuditError('INVALID_ROOT_OR_VERSION')
    return root

def verify_identity(root: ET.Element, expected: Expected, *, actor: bool) -> None:
    attrs={'requestId':expected.request_id,'sessionId':expected.session_id,'runId':expected.run_id,
           'sequence':str(expected.sequence),'operation':expected.operation}
    if actor:attrs['actorObjectId']=str(expected.actor_id)
    for key,value in attrs.items():
        if root.get(key)!=value:raise AuditError('IDENTITY_MISMATCH:'+key)

def properties(data: bytes) -> dict[str,str]:
    try:text=data.decode('utf-8-sig',errors='strict')
    except UnicodeDecodeError as exc:raise AuditError('INVALID_PROPERTIES_UTF8') from exc
    result={}
    # Actual writer uses plain scalar names. Leave Java value escapes as evidence.
    for line in text.splitlines():
        if not line or line.startswith(('#','!')):continue
        key,sep,value=line.partition('=')
        if not sep or not re.fullmatch(r'[A-Za-z][A-Za-z0-9]*',key) or key in result:
            raise AuditError('INVALID_OR_DUPLICATE_PROPERTIES')
        result[key]=value
    return result

def inspect_mailbox(mailbox: Path, expected: Expected) -> dict:
    root=safe_path(mailbox)
    if not root.is_dir():raise AuditError('MAILBOX_NOT_DIRECTORY')
    rid=canonical_uuid(expected.request_id)
    records={}
    for directory,suffix in [('inbox','.xml'),('processing','.xml'),('results','.xml'),('journal','.properties')]:
        records[directory]=read_bounded(root/directory/(rid+suffix))
    request_states=[]
    for name in ('inbox','processing'):
        if records[name] is not None:
            request=xml_root(records[name],'pilotRequest')
            verify_identity(request,expected,actor=False);request_states.append(name)
    claim=properties(records['journal']) if records['journal'] is not None else {}
    if claim.get('requestId',expected.request_id)!=expected.request_id:
        raise AuditError('CLAIM_IDENTITY_MISMATCH')
    # Other claim fields vary between the real/synthetic paths; verify if present.
    for key,wanted in [('sessionId',expected.session_id),('runId',expected.run_id),('sequence',str(expected.sequence))]:
        if key in claim and claim[key]!=wanted:raise AuditError('CLAIM_IDENTITY_MISMATCH:'+key)
    result={'request_id':rid,'session_id':expected.session_id,'run_id':expected.run_id,
            'sequence':expected.sequence,'operation':expected.operation,'actor_id':expected.actor_id,
            'replay_allowed':False,'gameplay_proven':False,'snapshot_consistency':'FILESYSTEM_NONATOMIC',
            'present':{k:v is not None for k,v in records.items()},
            'sha256':{k:hashlib.sha256(v).hexdigest() for k,v in records.items() if v is not None},
            'temporary_result_present':safe_path(root/'results'/(rid+'.xml.tmp')).exists(),
            'claim_state':claim.get('state','ABSENT')}
    data=records['results']
    if data is not None:
        response=xml_root(data,'pilotResult');verify_identity(response,expected,actor=True)
        status=response.get('status','');reason=response.get('reason','')
        result['status']=status;result['reason']=reason[:512]
        if status=='UNCERTAIN':classification='SERVER_UNCERTAIN'
        elif status=='ACCEPTED':classification='ACTION_ACCEPTED_NOT_ARRIVED'
        elif status=='SUCCEEDED':classification='READ_RESULT_AVAILABLE' if expected.operation in READ_OPERATIONS else 'ACTION_RESULT_AVAILABLE'
        elif status in {'REJECTED','CANCELLED','FAILED'}:classification='SERVER_'+status
        else:classification='UNRECOGNIZED_SERVER_STATUS'
    elif claim:classification='CLAIMED_WITHOUT_RESULT'
    elif 'processing' in request_states:classification='PROCESSING_OUTCOME_UNKNOWN'
    elif 'inbox' in request_states:classification='INBOX_OBSERVED_NOT_PROVEN_EXECUTED'
    else:classification='RESULT_NOT_FOUND'
    result['classification']=classification
    return result

def main() -> int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--mailbox',type=Path,required=True)
    p.add_argument('--request-id',required=True);p.add_argument('--session-id',required=True)
    p.add_argument('--run-id',required=True);p.add_argument('--sequence',type=int,required=True)
    p.add_argument('--operation',required=True);p.add_argument('--actor-id',type=int,required=True)
    a=p.parse_args()
    try:r=inspect_mailbox(a.mailbox,Expected(a.request_id,a.session_id,a.run_id,a.sequence,a.operation,a.actor_id))
    except (AuditError,OSError) as exc:
        print(json.dumps({'audit_error':str(exc),'replay_allowed':False},ensure_ascii=False));return 2
    print(json.dumps(r,ensure_ascii=False,indent=2));return 0

if __name__=='__main__':sys.exit(main())
