#!/usr/bin/env python3
"""Validate collected native snapshots against post-FINALIZE SQL. Never connects to DB.

Input is a task-owned JSON export; this verifier does not authenticate its collector.
The collector must keep raw hashes and freeze expected values at the sealed native
store boundary, not at an arbitrary census or before additional combat.
A PASS here proves only this supplied persistence comparison, never all of M1.
"""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
import re
from typing import Any

INT_FIELDS = ("level", "exp", "sp", "expBeforeDeath", "x", "y", "z", "heading", "classIndex", "classId", "race")
FLOAT_FIELDS = ("hp", "maxHp", "mp", "maxMp", "cp", "maxCp")
HASH_FIELDS = ("inventoryHash", "skillsHash")
SHA256 = re.compile(r"[0-9a-fA-F]{64}\Z")
SHA1 = re.compile(r"[0-9a-fA-F]{40}\Z")
DB = re.compile(r"l2jmobiush5_localplay_contract024[a-h]\Z")


def verify(doc: Any) -> dict[str, Any]:
    errors: list[str] = []
    if not isinstance(doc, dict):
        return {"pass": False, "errors": ["root must be an object"]}
    def require(ok: bool, message: str) -> None:
        if not ok:
            errors.append(message)
    require(type(doc.get("version")) is int and doc["version"] == 1, "version must be 1")
    require(isinstance(doc.get("sourceSha"), str) and bool(SHA1.fullmatch(doc["sourceSha"])), "sourceSha missing/invalid")
    require(isinstance(doc.get("database"), str) and bool(DB.fullmatch(doc["database"])), "database is not a TASK024 clone")
    require(doc.get("barrier") == "QUIESCENT_NATIVE_PREPARE", "expected snapshot is not a quiescent native store barrier")
    for key in ("rawSnapshotSha256", "rawSqlSha256"):
        require(isinstance(doc.get(key), str) and bool(SHA256.fullmatch(doc[key])), key + " missing/invalid")
    for key in ("retainedEntries", "pendingOwnedStores"):
        require(type(doc.get(key)) is int and doc[key] == 0, key + " must be exact zero")
    require(doc.get("forcedStop") is False, "forced stop is not a graceful persistence PASS")
    ids = doc.get("requiredProfiles")
    valid_ids = isinstance(ids, list) and 1 <= len(ids) <= 8 and all(type(x) is int and x > 0 for x in ids)
    require(valid_ids, "requiredProfiles must contain 1..8 positive integers")
    if valid_ids:
        require(len(ids) == len(set(ids)), "requiredProfiles contains duplicates")
    rows = doc.get("rows")
    if not isinstance(rows, list) or not rows or len(rows) > 8:
        errors.append("rows must contain 1..8 actors")
        return {"pass": False, "errors": errors}
    seen: set[int] = set()
    for index, row in enumerate(rows):
        prefix = f"row[{index}]"
        if not isinstance(row, dict):
            errors.append(prefix + " must be an object"); continue
        pid = row.get("profileId")
        if type(pid) is not int or pid <= 0:
            errors.append(prefix + " invalid profileId"); continue
        prefix = f"profile {pid}"
        require(pid not in seen, prefix + " duplicate")
        seen.add(pid)
        for key in ("objectId", "epoch"):
            require(type(row.get(key)) is int and row[key] > 0, prefix + " invalid " + key)
        require(row.get("source") == "native-sealed-snapshot", prefix + " wrong expected snapshot source")
        require(row.get("nativeFinalized") is True, prefix + " native store not finalized")
        require(type(row.get("projectionReady")) is bool, prefix + " projection status missing")
        expected, saved = row.get("expected"), row.get("saved")
        if not isinstance(expected, dict) or not isinstance(saved, dict):
            errors.append(prefix + " expected/saved missing"); continue
        for key in INT_FIELDS:
            a, b = expected.get(key), saved.get(key)
            require(type(a) is int and type(b) is int, prefix + " invalid integer " + key)
            if type(a) is int and type(b) is int:
                require(a == b, f"{prefix} {key}: expected={a}, saved={b}")
        for key in FLOAT_FIELDS:
            a, b = expected.get(key), saved.get(key)
            numeric = all(type(x) in (int, float) and math.isfinite(x) and x >= 0 for x in (a, b))
            require(numeric, prefix + " invalid finite native scalar " + key)
            if numeric:
                require(abs(a - b) <= 1e-6, f"{prefix} {key}: expected={a}, saved={b}")
        for key in HASH_FIELDS:
            a, b = expected.get(key), saved.get(key)
            legal = isinstance(a, str) and isinstance(b, str) and bool(SHA256.fullmatch(a)) and bool(SHA256.fullmatch(b))
            require(legal, prefix + " invalid hash " + key)
            if legal:
                require(a.lower() == b.lower(), prefix + " " + key + " differs")
    if valid_ids:
        require(seen == set(ids), f"cohort differs: expected={sorted(ids)}, actual={sorted(seen)}")
    return {"pass": not errors, "checkedProfiles": sorted(seen), "errors": errors}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if args.input.stat().st_size > 2_000_000:
            raise ValueError("input exceeds 2 MB")
        doc = json.loads(args.input.read_text(encoding="utf-8-sig"))
        result = verify(doc)
    except (OSError, ValueError, TypeError) as error:
        result = {"pass": False, "errors": [str(error)]}
    output = json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False)
    if args.output:
        args.output.write_text(output + "\n", encoding="utf-8")
    print(output)
    return 0 if result["pass"] else 2

if __name__ == "__main__":
    raise SystemExit(main())
