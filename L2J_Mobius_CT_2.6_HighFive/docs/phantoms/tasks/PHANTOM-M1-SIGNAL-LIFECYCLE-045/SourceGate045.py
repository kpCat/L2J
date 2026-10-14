"""Exact045 source allowlist, encoding checks, and compact source gate receipt."""
from pathlib import Path
import hashlib
import json
import re
import subprocess

task = Path(__file__).resolve().parent
module = task.parents[3]
repo = module.parent
sources = ["java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationManager.java",
           "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java",
           "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java",
           "test/java/org/l2jmobius/tests/phantoms/PhantomPopulationSuite.java"]
packet = ["GOAL.md", "TASK.md", "CONTEXT.md", "DESIGN.md", "PLAN.md", "ACCEPTANCE.md", "SOURCE_MAP.tsv", "HANDOFF.md"]
helpers = [Path(row["target"]) for row in json.loads((task / "evidence/ADAPTER_PINS045.json").read_text())]
paths = [module / p for p in sources] + [task / p for p in packet]
paths += [module / p for p in helpers]
paths += [task / "Adapt045.py", task / "SourceGate045.py", task / "CHECKPOINT045.md", task / "proposals/ops045/Invoke-Git045.ps1"]
paths += [task / "evidence/ADAPTER_PINS045.json", task / "evidence/SOURCE_DIFF045.patch"]
logs = [module / ".phantom-local/ops045" / name for name in ["RED045_RUN.log", "GREEN045.log",
        "DBFREE_population-schedule.log", "DBFREE_population-catalog.log", "DBFREE_population-ecology-goal033.log",
        "DBFREE_topology-scheduler-signal-integration.log", "DBFREE_topology-signal-ledger.log"]]
logs += [task / "evidence" / name for name in ["TEST_OWNER_RED045.log", "TEST_OWNER_GREEN045.log",
         "POPULATION_BASE_CONTRACT045.log", "POPULATION_GREEN045.log", "POPULATION_GREEN2_045.log"]]
tests = []
for log in logs:
    data = log.read_bytes()
    encoding = "utf-16" if data.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig"
    facts = [line for line in data.decode(encoding, errors="replace").splitlines()
             if line.startswith(("[PASS]", "[FAIL]", "SUMMARY:"))]
    tests.append({"raw": str(log), "sha256": hashlib.sha256(data).hexdigest(), "facts": facts})
(task / "evidence/SOURCE_TESTS045.json").write_text(json.dumps(tests, indent=2, ensure_ascii=False), encoding="utf-8")
paths += [task / "evidence" / name for name in ["RED045_RUN.log", "GREEN045.log", "COMPILE_RED045.log", "SOURCE_TESTS045.json"]]
markers = "Рџ Рќ Рћ Р• РЎ Р› Р¤ Рњ РЈ Рљ Рґ Рµ Р° Р» РЅ Рѕ СЏ С€ СЂ С‹ СЊ С‚ Сѓ С‡ С… С† �".split()
escaped = re.compile(r"\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};")
checks = []
for path in paths:
    data = path.read_bytes()
    text = data.decode("utf-8-sig")
    # This checker contains the literal search patterns, not user-facing escaped text.
    if path.name == "SourceGate045.py":
        continue
    mojibake = [m for m in markers if m in text]
    escapes = escaped.findall(text)
    if mojibake or escapes:
        raise SystemExit(f"Encoding marker in {path.name}: {mojibake}, {escapes[:5]}")
    checks.append({"path": str(path.relative_to(repo)).replace("\\", "/"), "sha256": hashlib.sha256(data).hexdigest()})
relative = [str(p.relative_to(repo)).replace("\\", "/") for p in paths]
changed = subprocess.check_output(["git", "-C", str(repo), "diff", "--name-only"], text=True).splitlines()
if set(changed) - set(relative):
    raise SystemExit(f"Foreign tracked changes: {set(changed) - set(relative)}")
subprocess.run(["git", "-C", str(repo), "diff", "--check"], check=True)
(task / "evidence/SOURCE_ALLOWLIST045.txt").write_text("\n".join(relative) + "\n", encoding="utf-8")
(task / "evidence/SOURCE_GATE045.json").write_text(json.dumps({"scope": "SOURCE_PRE_NATIVE", "changed": changed,
    "mojibake": "PASS", "escapedCyrillic": "PASS", "diffCheck": "PASS", "files": checks}, indent=2), encoding="utf-8")
print(f"Exact source allowlist {len(paths)} paths; diff check PASS; both encoding checks PASS.")
