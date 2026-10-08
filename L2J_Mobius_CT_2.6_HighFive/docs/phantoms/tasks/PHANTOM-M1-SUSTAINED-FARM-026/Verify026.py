"""Read-only TASK026 adapter of exact024 canonical verification, plus early SQL conversion."""
import argparse
import csv
import hashlib
import json
import subprocess
import sys
from decimal import Decimal
from pathlib import Path

TASK = Path(__file__).resolve().parent
MODULE = TASK.parents[3]
OLD = TASK.parent / "PHANTOM-M1-RUNTIME-CONTRACTS-024"

def properties(path):
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8-sig").splitlines() if "=" in line)

def blocks(path):
    result = []
    for chunk in path.read_text(encoding="utf-8-sig").split("QUERY\t")[1:]:
        lines = chunk.splitlines()
        result.append(list(csv.DictReader(lines[1:], delimiter="\t")))
    if len(result) != 5:
        raise ValueError("Exact five captured SQL blocks required")
    return result

def write_rows(directory, name, keys, rows):
    with (directory / name).open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=keys, delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)

if len(sys.argv) > 1 and sys.argv[1] == "restart":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode")
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    def rows(directory, name):
        with (directory / name).open(encoding="utf-8-sig", newline="") as stream:
            return list(csv.DictReader(stream, delimiter="\t"))
    checks = {}
    checks["characters_exact_except_online"] = [{key: value for key, value in row.items() if key != "online"} for row in rows(args.before, "characters.tsv")] == [{key: value for key, value in row.items() if key != "online"} for row in rows(args.after, "characters.tsv")]
    for name in ("items.tsv", "skills.tsv"):
        checks[name] = rows(args.before, name) == rows(args.after, name)
    prior_manifest = json.loads((args.before / "runtime-manifest.json").read_text(encoding="utf-8-sig"))
    later_manifest = json.loads((args.after / "runtime-manifest.json").read_text(encoding="utf-8-sig"))
    checks["same_owned_database"] = prior_manifest["databaseName"] == later_manifest["databaseName"] and prior_manifest["databaseName"].startswith("l2jmobiush5_localplay_contract026")
    checks["same_source_sha"] = prior_manifest["codeSha"] == later_manifest["codeSha"]
    states = []
    metadata = []
    for directory in (args.before, args.after):
        checks[str(directory.name) + "_pending_zero"] = int(rows(directory, "counts.tsv")[0]["pendingOwnedStores"]) == 0
        decoded = [line.split("\t") for line in (directory / "durable-decoded.tsv").read_text(encoding="utf-8-sig").splitlines()]
        state = {row[0]: row for row in decoded if row[2] == "state"}
        context = {row[0]: row for row in decoded if row[2] == "context"}
        checks[str(directory.name) + "_context_completed_linked"] = bool(state) and all(pid in context and context[pid][3] == "COMPLETED" and context[pid][4] == row[1] and context[pid][5] == row[4] for pid, row in state.items())
        states.append({pid: [row[3], *row[5:]] for pid, row in state.items()})
        metadata.append({pid: dict(version=row[1], digest=row[4]) for pid, row in state.items()})
    checks["durable_native_state_progress_position"] = states[0] == states[1]
    doc = dict(kind="ACTUAL_RESTART_PRE_ORDINARY_CANONICAL", checks=checks, stateMetadata=metadata, pass_=all(checks.values()), note="Plan/metadata CAS may advance versions; exact native fields, full items/skills and linked completed context are mandatory.")
    args.output.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(doc))
    raise SystemExit(0 if doc["pass_"] else 2)

if len(sys.argv) > 1 and sys.argv[1] == "early":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode")
    parser.add_argument("--crash", type=Path, required=True)
    parser.add_argument("--recovery", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--before-counts", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(exist_ok=False)
    crash = properties(args.crash / "planned-crash.properties")
    native = properties(args.crash / crash["snapshotFile"])
    recovered = properties(args.recovery / "recovery-commit.properties")
    before, after = blocks(args.crash / "window-sql.tsv"), blocks(args.recovery / "recovery-sql.tsv")
    profile, object_id = int(native["profileId"]), int(native["objectId"])
    checks = {}
    for key in ("profileId", "objectId", "x", "y", "z", "heading", "exp", "sp", "inventoryHash"):
        checks[key] = bool(native[key] and native[key] == recovered[key])
    checks["full_native_characters"] = before[1] == after[1]
    checks["full_native_items_six_columns"] = before[2] == after[2]
    checks["full_native_skills"] = before[3] == after[3]
    aliases = {"objectId": "charId", "hp": "curHp", "mp": "curMp", "cp": "curCp", "classId": "classid", "vitality": "vitality_points"}
    character = after[1][0]
    for key in ("level", "exp", "sp", "expBeforeDeath", "hp", "maxHp", "mp", "maxMp", "cp", "maxCp", "x", "y", "z", "heading", "classId", "race", "vitality"):
        checks["native_" + key] = Decimal(native[key]) == Decimal(character[aliases.get(key, key)])
    components = after[4]
    checks["receipt_absent"] = not any(row["component_type"] == "background.owned-store" for row in components)
    with args.before_counts.open(encoding="utf-8-sig", newline="") as stream:
        count = next(csv.DictReader(stream, delimiter="\t"))
    checks["only_original_pending_resolved"] = int(count["pendingOwnedStores"]) == 1 and checks["receipt_absent"]
    state = next(row for row in components if row["component_type"] == "background.state")
    checks["exact_committed_payload"] = hashlib.sha256(bytes.fromhex(state["payload"])).hexdigest() == recovered["fullStateSha256"]
    checks["early_boundary"] = recovered["source"] == "RECOVERY_COMMIT_BEFORE_ADMISSION" and recovered["transactionReturned"] == "true"
    doc = dict(kind="RECOVERY_COMMIT_EXACT", checks=checks, pass_=all(checks.values()))
    (args.output / "early-comparison.json").write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    keys = list(native_key for native_key in ("profileId", "objectId", "level", "exp", "sp", "expBeforeDeath", "hp", "maxHp", "mp", "maxMp", "cp", "maxCp", "x", "y", "z", "heading", "classIndex", "classId", "race", "vitality", "online"))
    row = {key: character[aliases.get(key, key)] for key in keys if key not in ("profileId", "classIndex", "online")}
    row.update(profileId=profile, classIndex=native["classIndex"], online=0)
    write_rows(args.output, "characters.tsv", keys, [row])
    write_rows(args.output, "items.tsv", ["profileId", "owner_id", "object_id", "item_id", "count", "loc", "loc_data", "enchant_level"], [dict(profileId=profile, owner_id=object_id, **row) for row in after[2]])
    write_rows(args.output, "skills.tsv", ["profileId", "charId", "skill_id", "skill_level", "class_index"], [dict(profileId=profile, charId=object_id, **row) for row in after[3]])
    write_rows(args.output, "counts.tsv", ["database_name", "pendingOwnedStores", "onlineCharacters"], [dict(database_name=after[0][0]["database_name"], pendingOwnedStores=0, onlineCharacters=0)])
    (args.output / "durable-hex.tsv").write_text("".join(f"{row['profile_id']}\t{row['row_version']}\t{row['component_type']}\t{row['payload']}\n" for row in components), encoding="utf-8")
    (args.output / "runtime-manifest.json").write_bytes(args.manifest.read_bytes())
    java = "C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe"
    command = [java, "-cp", str(MODULE / "dist/libs/*"), str(OLD / "ReadDurable024.java"), str(args.output / "durable-hex.tsv")]
    decoded = subprocess.run(command, capture_output=True, text=True, check=True)
    (args.output / "durable-decoded.tsv").write_text(decoded.stdout, encoding="utf-8")
    print(json.dumps(doc))
    raise SystemExit(0 if doc["pass_"] else 2)

body = (OLD / "Verify-Restart024.py").read_text(encoding="utf-8-sig")
body = body.replace(".startswith('l2jmobiush5_localplay_contract024')", ".startswith('l2jmobiush5_localplay_contract026')")
body = body.replace("r['owner']=='TASK024_CONTRACT'", "r['owner']=='TASK026_CONTRACT'")
exec(compile(body, str(OLD / "Verify-Restart024.py"), "exec"))
