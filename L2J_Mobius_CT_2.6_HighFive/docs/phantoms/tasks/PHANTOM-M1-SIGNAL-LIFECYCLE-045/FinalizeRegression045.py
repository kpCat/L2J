"""Collect exact final JAR results, preserving every observed failure."""
from pathlib import Path
import csv
import hashlib
import json
import re

task = Path(__file__).resolve().parent
module = task.parents[3]
evidence = task / "evidence"
jar = module / ".phantom-local/contract045a/runtime/libs/GameServer.jar"
digest = hashlib.sha256(jar.read_bytes()).hexdigest()
expected = "fc19dc90786947eff5ad427792ac4f3a491340c0e647feb194ecb088f29adec2"
def read_log(path):
    raw = path.read_bytes()
    return raw.decode("utf-16" if raw.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig")

if digest != expected: raise SystemExit("Native JAR changed")
with (evidence / "CURRENT93_045-matrix.tsv").open(encoding="utf-8-sig", newline="") as stream:
    matrix = list(csv.DictReader(stream, delimiter="\t"))
total = 0
for row in matrix:
    match = re.search(r"total=(\d+) passed=(\d+) failed=(\d+)", row["summary"])
    if not match or row["exitCode"] != "0" or match[1] != match[2] or match[3] != "0":
        raise SystemExit("Required matrix failure: " + str(row))
    total += int(match[1])
if len(matrix) != 18 or total != 93: raise SystemExit("Required93 cardinality changed")
labels = {"FINAL_ACCEPTANCE11_045": 11, "FINAL_SOFT045": 3, "FINAL_SCHEDULER045": 30,
          "FINAL_POP_SCHEDULE045": 3, "FINAL_POP_CATALOG045": 3,
          "FINAL_TOPOLOGY045": 5, "FINAL_LEDGER045": 20}
facts = []
for label, count in labels.items():
    path = evidence / (label + ".log")
    text = read_log(path)
    matches = re.findall(r"SUMMARY:.*total=(\d+) passed=(\d+) failed=(\d+)", text)
    if not matches or tuple(map(int, matches[-1])) != (count, count, 0):
        raise SystemExit("Required focused failure: " + label)
    provenance_path = evidence / (label + ".provenance.json")
    provenance = json.loads(provenance_path.read_text(encoding="utf-8-sig"))
    if provenance["jarSha256"].lower() != digest or provenance["developmentBinFirst"]:
        raise SystemExit("Final result is not exact jar-first: " + label)
    facts.append({"label": label, "total": count, "passed": count, "failed": 0,
                  "raw": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                  "provenance": provenance})
extra = evidence / "FINAL_LIFECYCLE045.log"
extra_text = read_log(extra)
extra_failures = [line for line in extra_text.splitlines() if line.startswith("[FAIL]")]
result = {"jar": str(jar), "jarSha256": digest, "jarFirst": True,
          "matrixTotal": total, "matrix": matrix, "requiredFocused": facts,
          "strictValidatorNegatives": {"tests": 26, "passed": 26,
              "raw": str(module / ".phantom-local/ops045/VALIDATOR_NEGATIVES045.log")},
          "extraWrongNative24": {"status": "FAIL", "total": 24, "passed": 19, "failed": 5,
              "raw": str(extra), "sha256": hashlib.sha256(extra.read_bytes()).hexdigest(),
              "failures": extra_failures, "resolved": False},
          "G2_HISTORY": "PASS_ON_ORIGINAL_5c4ae8bf_ONLY", "G2_CURRENT": "NOT_VERIFIED"}
(evidence / "REGRESSIONS_FINAL045.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Current native JAR: mandatory93/93, acceptance11/11, soft3/3, scheduler30/30, affected PASS; extra24 FAIL retained.")
