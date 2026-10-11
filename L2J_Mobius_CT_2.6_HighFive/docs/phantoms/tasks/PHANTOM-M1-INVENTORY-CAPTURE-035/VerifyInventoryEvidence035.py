"""Read-only causal035 validator: all objects, stock cap, receipt identity and release."""
import base64
import csv
import hashlib
import json
import pathlib
import sys


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8-sig").splitlines() if "=" in line)


def rows(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def canonical(items):
    return sorted((int(x[0]), int(x[1]), int(x[2]), x[3]) for x in items)


def inventory_hash(items):
    digest = hashlib.sha256()
    for item in canonical(items):
        for value in item:
            digest.update(str(value).encode("ascii") + b"\0")
    return digest.hexdigest()


def native_items(values):
    return canonical(x.split(":") for x in values["fullNativeItems"].split("|") if x)


def sql_sections(path):
    result = []
    current = None
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("QUERY\t"):
            current = []
            result.append((line[6:], current))
        elif current is not None:
            current.append(line.split("\t"))
    return result


def verify(root):
    starts = list(root.glob("STARTUP032b-*"))
    assert len(starts) == 1, "Exact final035b episode required"
    start = starts[0]
    pre = rows(root / "R2_PRE_SQL035/items.tsv")
    post = rows(root / "R2_POST_SQL035/items.tsv")
    assert pre == post, "Any item row/object/count/location/enchant changed"
    for before, after in zip(rows(root / "R2_PRE_SQL035/characters.tsv"), rows(root / "R2_POST_SQL035/characters.tsv"), strict=True):
        assert before["online"] == "2" and after["online"] == "0", "Stock logout online transition missing"
        assert {k: v for k, v in before.items() if k != "online"} == {k: v for k, v in after.items() if k != "online"}, "Canonical progress/vitals/position or earned values changed"
    assert rows(root / "R2_PRE_SQL035/skills.tsv") == rows(root / "R2_POST_SQL035/skills.tsv"), "Skill rows changed"
    counts = rows(root / "R2_POST_SQL035/counts.tsv")
    assert counts[0] == {"database_name": "l2jmobiush5_localplay_contract035b", "pendingOwnedStores": "0", "onlineCharacters": "0"}
    results = []
    for profile, object_id in [(36, 268484533), (45, 268484616)]:
        initial = [x for x in pre if int(x["profileId"]) == profile]
        expected = canonical((x["object_id"], x["item_id"], x["count"], x["loc"]) for x in initial)
        assert len(expected) == 252 and sum(x[3] == "INVENTORY" for x in expected) == 249 and sum(x[3] == "PAPERDOLL" for x in expected) == 3
        digest = inventory_hash(expected)
        finalized = list(start.glob(f"{profile}-*-finalized.properties"))
        assert len(finalized) == 1, "Exactly one causal finalized lifetime required"
        final = properties(finalized[0])
        prepared = properties(finalized[0].with_name(finalized[0].name.replace("-finalized", "")))
        registered = properties(start / f"{profile}-{final['epoch']}-register.properties")
        terminal = properties(start / f"{profile}-{final['epoch']}-terminal.properties")
        for value in (registered, prepared, final):
            assert native_items(value) == expected, "Full native multiset differs"
            assert value["fullNativeInventoryHash"] == digest
            for name, amount in [("fullItemCount", 252), ("fullInventoryCount", 249), ("fullPaperdollCount", 3), ("questCount", 0), ("nonQuestCount", 252), ("stockCap", 250)]:
                assert int(value[name]) == amount, f"Wrong {name}"
        for value in (prepared, final, terminal):
            assert int(value["profileId"]) == profile and int(value["objectId"]) == object_id
            assert value["epoch"] == final["epoch"] and value["receiptSha256"] == final["receiptSha256"]
        assert prepared["checkpointStage"] == "PREPARED" and final["checkpointStage"] == "FINALIZED"
        assert prepared["fullNativeItems"] == final["fullNativeItems"]
        assert prepared["inventoryHash"] == final["inventoryHash"] == digest
        assert hashlib.sha256(base64.b64decode(final["fullStateBase64"])).hexdigest() == final["afterPayloadSha256"]
        assert final["exactArgument"] == "true" and final["nativeOwnerSealed"] == "true"
        assert terminal["nativeOwnerState"] == "DETACHED" and terminal["nativeOwnerCurrent"] == "false"
        assert all(terminal[k] == "true" for k in ("worldAbsent", "autoSaveAbsent", "leaseAbsent"))
        assert terminal["terminalOutstanding"] == terminal["terminalPendingTimers"] == "0"
        assert int(final["hookNanos"]) < int(terminal["terminalNanos"])
        assert not terminal["nativeFirstIncident"] and not terminal["nativeCheckpointFirstClass"]
        # Native Player disposal clears its in-memory container AFTER successful finalized release.
        assert terminal["fullItemCount"] == "0", "Unexpected disposed-container evidence"
        sections = sql_sections(finalized[0].with_name(finalized[0].name + ".sql.tsv"))
        item_section = next(v for q, v in sections if q.startswith("SELECT object_id,item_id,count,loc,"))
        assert canonical(x[:4] for x in item_section[1:]) == expected
        assert [x[4:] for x in item_section[1:]] == [[x["loc_data"], x["enchant_level"]] for x in initial]
        character = next(v for q, v in sections if q.startswith("SELECT c.charId,"))
        character = dict(zip(character[0], character[1]))
        for sql_key, native_key in [("charId", "objectId"), ("level", "level"), ("exp", "exp"), ("sp", "sp"), ("expBeforeDeath", "expBeforeDeath"), ("x", "x"), ("y", "y"), ("z", "z"), ("vitality_points", "vitality")]:
            assert character[sql_key] == final[native_key], f"Canonical native/SQL mismatch {sql_key}"
        results.append({"profileId": profile, "objectId": object_id, "epoch": int(final["epoch"]), "INVENTORY": 249, "PAPERDOLL": 3, "quest": 0, "nonquest": 252, "all": 252, "cap": 250, "hash": digest, "preparedRowVersion": int(final["preparedRowVersion"]), "receiptSha256": final["receiptSha256"], "native": "FINALIZED", "worldAutoSaveLease": "ABSENT_AFTER_FINALIZED", "SQLRows": "EXACT", "disposedNativeContainer": 0})
    status_files = list(start.glob("observer-status-*.properties"))
    assert len(status_files) == 1
    status = properties(status_files[0])
    assert all(status[k] == "0" for k in ("pending", "overflow", "activeReferences", "bufferBytes"))
    assert not status["exporterFailure"] and status["captured"] == status["exported"]
    ledger = [json.loads(x) for x in (start / "lifetimes.jsonl").read_text(encoding="utf-8-sig").splitlines()]
    assert ledger[0]["kind"] == "RUN_START" and ledger[-1]["kind"] == "RUN_END"
    assert not ledger[-1]["failure"] and ledger[-1]["dropped"] == 0 and ledger[-1]["exporter_drained"]
    for sequence, entry in enumerate(ledger, 1):
        assert entry["seq"] == sequence and entry["run_id"] == ledger[0]["run_id"]
        if entry["kind"] in ("RUN_START", "RUN_END"):
            assert sequence in (1, len(ledger)), "Unexpected metadata frame"
        else:
            assert hashlib.sha256((start / entry["raw_file"]).read_bytes()).hexdigest() == entry["raw_sha256"], "Ledger SHA differs"
    stop = properties(root / "R2_STOP035/typed-stop-done.properties")
    assert "phase=DONE, outcome=COMPLETE" in stop["progress"] and "retainedEntries=0" in stop["materialization"]
    assert not stop["proofFailure"]
    reload_text = (root / "R2_SAMEDB_RELOAD035/reload.log").read_text(encoding="utf-8-sig")
    assert "SUMMARY_RELOAD035 total=2 passed=2 failed=0 database=l2jmobiush5_localplay_contract035b" in reload_text
    for value in results:
        assert f"hash={value['hash']}" in reload_text
    return {"contract": "TASK035_FULL_INVENTORY", "pass": True, "rows": results, "observerNativeBirths": int(status["nativeBirths"]), "ledgerSHAEntries": len(ledger), "globalDrain": "DONE_COMPLETE_RETAINED0", "sameDBReload": "2/2", "M1": "OPEN", "REAL_FINAL": "NOT_RUN"}


if __name__ == "__main__":
    evidence = pathlib.Path(sys.argv[1]).resolve()
    result = verify(evidence)
    output = evidence / "R2_FULL_INVENTORY_PROOF035.json"
    if len(sys.argv) > 2 and sys.argv[2] == "--verify-existing":
        assert json.loads(output.read_text(encoding="utf-8")) == result, "Immutable proof differs from fresh verification"
    else:
        assert not output.exists(), "Immutable proof already exists"
        output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))
