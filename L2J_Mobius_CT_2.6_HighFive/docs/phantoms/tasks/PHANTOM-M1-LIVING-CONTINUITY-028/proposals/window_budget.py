#!/usr/bin/env python3
"""Preflight only: budget a Synthetic session without changing server deadlines."""
from __future__ import annotations
import argparse
import json
import math

TTL_SECONDS=525.0
SERVER_COMMAND_LIMIT=400
COMMAND_RESERVE=50

def budget(elapsed: float, phases: dict[str,float], *, commands: int,
           cleanup_seconds: float=45, reserve_seconds: float=40) -> dict:
    nums=[elapsed,cleanup_seconds,reserve_seconds,*phases.values()]
    if any(isinstance(x,bool) or not isinstance(x,(int,float)) or not math.isfinite(x) or x<0 for x in nums):
        raise ValueError('Durations must be finite non-negative seconds.')
    if type(commands) is not int or commands<0:raise ValueError('Commands must be a non-negative integer.')
    if not phases or any(not name for name in phases):raise ValueError('Named phases required.')
    total=elapsed+sum(phases.values())+cleanup_seconds+reserve_seconds
    remaining=TTL_SECONDS-total
    commands_ok=commands<=SERVER_COMMAND_LIMIT-COMMAND_RESERVE
    return {'ttl_seconds':TTL_SECONDS,'elapsed_since_native_start':elapsed,
            'phase_seconds':phases,'cleanup_seconds':cleanup_seconds,'reserve_seconds':reserve_seconds,
            'headroom_seconds':remaining,'planned_commands':commands,
            'server_command_limit':SERVER_COMMAND_LIMIT,'command_reserve':COMMAND_RESERVE,
            'fits':remaining>=0 and commands_ok,'extends_server_ttl':False}

def main() -> int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--elapsed',type=float,required=True);p.add_argument('--commands',type=int,required=True)
    p.add_argument('--phase',action='append',default=[],help='name=seconds (repeatable)')
    a=p.parse_args();phases={}
    try:
        for item in a.phase:
            name,sep,value=item.partition('=')
            if not sep or name in phases:raise ValueError('Duplicate or invalid phase.')
            phases[name]=float(value)
        result=budget(a.elapsed,phases,commands=a.commands)
    except ValueError as exc:p.error(str(exc))
    print(json.dumps(result,ensure_ascii=False,indent=2));return 0 if result['fits'] else 1
if __name__=='__main__':raise SystemExit(main())
