"""Read-only aggregate checker. Raw source/SQL/kill attribution must be reviewed separately.
Schema1: code_sha, primary_ids[2], cohort_ids[>=4], natural_scene=true, npc_ai=true,
samples[{elapsed:seconds, actors:[{profile,object,epoch,damage,kills,rewards,cycles,
targets,exp,sp,loot,overflow,incident}]}]. Numeric EXP/SP are actual live totals.
"""
from __future__ import annotations
import argparse
import json
import math
import re
from pathlib import Path

COUNTERS = ("damage", "kills", "rewards", "cycles", "targets", "exp", "sp", "loot")

def evaluate(doc: dict) -> dict:
    errors: list[str] = []
    def require(condition: bool, message: str) -> None:
        if not condition: errors.append(message)
    require(doc.get("schema") == 1, "schema must be 1")
    require(bool(re.fullmatch(r"[0-9a-f]{40}", str(doc.get("code_sha", "")))), "exact code_sha absent")
    require(doc.get("natural_scene") is True and doc.get("npc_ai") is True, "natural active-NPC scene not proved")
    ids = doc.get("cohort_ids", [])
    primaries = doc.get("primary_ids", [])
    if not isinstance(ids, list) or not all(type(i) is int and i > 0 for i in ids):
        return {"pass": False, "errors": errors + ["invalid cohort_ids"]}
    require(len(ids) >= 4 and len(set(ids)) == len(ids), "need >=4 distinct frozen cohort profiles")
    if not isinstance(primaries, list) or len(primaries) != 2 or not all(type(i) is int and i in ids for i in primaries) or len(set(primaries)) != 2:
        return {"pass": False, "errors": errors + ["need 2 distinct preselected primary_ids in cohort"]}
    samples = doc.get("samples", [])
    if not isinstance(samples, list) or len(samples) < 3:
        return {"pass": False, "errors": errors + ["samples missing"]}
    history = {i: [] for i in ids}
    times: list[float] = []
    for n, sample in enumerate(samples):
        if not isinstance(sample, dict): errors.append(f"sample {n} malformed"); continue
        stamp = sample.get("elapsed")
        if type(stamp) not in (int, float) or not math.isfinite(stamp) or stamp < 0:
            errors.append(f"sample {n} elapsed invalid"); continue
        times.append(float(stamp))
        actors = sample.get("actors", [])
        if not isinstance(actors, list): errors.append(f"sample {n} actors malformed"); continue
        found: dict[int, dict] = {}
        for actor in actors:
            if not isinstance(actor, dict) or type(actor.get("profile")) is not int:
                errors.append(f"sample {n} profile malformed"); continue
            pid = actor["profile"]
            if pid in found: errors.append(f"sample {n} duplicate profile {pid}")
            found[pid] = actor
        require(set(ids) == set(found), f"sample {n} frozen cohort incomplete/changed")
        for pid in ids:
            a = found.get(pid)
            if a is None: continue
            valid = all(type(a.get(k)) is int and a[k] >= 0 for k in COUNTERS)
            valid &= all(type(a.get(k)) is int and a[k] > 0 for k in ("object", "epoch"))
            require(valid, f"sample {n} profile {pid} invalid counters/identity")
            require(a.get("overflow") is False, f"sample {n} profile {pid} evidence unproven")
            require(a.get("incident") == "", f"sample {n} profile {pid} incident or missing state")
            if valid: history[pid].append((float(stamp), a))
    if len(times) == len(samples):
        require(all(b > a for a,b in zip(times,times[1:])), "time not strictly increasing")
        require(all(b-a <= 15 for a,b in zip(times,times[1:])), "sample gap exceeds15s")
        require(360 <= times[-1]-times[0] <= 420, "scene duration outside360..420s")
    totals = {}
    for pid, rows in history.items():
        if len(rows) != len(samples): continue
        first = rows[0][1]; last = rows[-1][1]
        require(all((a["object"],a["epoch"]) == (first["object"],first["epoch"]) for _,a in rows), f"profile {pid} lifetime changed")
        require(all(all(b[k]>=a[k] for k in COUNTERS) for (_,a),(_,b) in zip(rows,rows[1:])), f"profile {pid} counters regressed")
        d = {k:last[k]-first[k] for k in COUNTERS}; totals[str(pid)] = d
        # A good initial burst followed by minutes of idling is not continuous farming.
        tail = next((a for stamp,a in rows if stamp >= rows[-1][0]-120), first)
        require(last["rewards"] > tail["rewards"] and last["targets"] > tail["targets"],
                f"profile {pid} no rewarded continuation in final120s")
        if pid in primaries:
            require(d["cycles"]>=5 and d["kills"]>=5 and d["rewards"]>=5 and d["targets"]>=5, f"primary {pid} five cycles absent")
        else:
            require(d["rewards"]>=3 and d["targets"]>=3, f"cohort {pid} repeated rewarded target transitions absent")
        require(d["damage"]>0 and d["exp"]>0 and d["sp"]>0, f"profile {pid} actual progress absent")
    return {"pass":not errors,"errors":errors,"totals":totals,
            "note":"Checks aggregate necessary conditions only; not proof of native provenance/lifecycle/loot safety."}

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument("input",type=Path)
    args=parser.parse_args()
    try:
        data=json.loads(args.input.read_text(encoding="utf-8-sig"))
        if not isinstance(data,dict): raise ValueError("root must be object")
        result=evaluate(data)
    except (OSError, ValueError, TypeError) as exc:
        result={"pass":False,"errors":[str(exc)]}
    print(json.dumps(result,ensure_ascii=False,indent=2))
    return 0 if result["pass"] else 2

if __name__ == "__main__": raise SystemExit(main())
