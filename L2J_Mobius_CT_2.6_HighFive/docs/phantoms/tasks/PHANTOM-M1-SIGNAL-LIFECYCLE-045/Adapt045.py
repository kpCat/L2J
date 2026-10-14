"""Reproduce the bounded ordinary045 tools from committed044 sources."""
from pathlib import Path
import hashlib
import json

task = Path(__file__).resolve().parent
module = task.parents[3]
previous = task.parent / "PHANTOM-M1-G4-CAUSAL-044"
ops = module / ".phantom-local/ops045"
proposals = task / "proposals/ops045"
if (proposals / "Control032.ps1").exists():
    raise SystemExit("045 adapters already exist; stage the reviewed tracked sources without regeneration.")
ops.mkdir(parents=True, exist_ok=True)
proposals.mkdir(parents=True, exist_ok=True)
names = ["Prepare032.ps1", "Control032.ps1", "Observe032.ps1",
         "Read-SharedJson032.ps1", "Contract032Observer.java",
         "ReadDryRoute032.java", "ReadDryPath044.java", "Select-Primary044.ps1",
         "Stop-ExactOwnedGracefully.ps1", "Build-StockShutdown044.ps1",
         "RequestShutdown.java", "RuntimeShutdownAgent044.java",
         "All-Birth-Cut044.ps1", "SplitSql039.py",
         "Invoke-SignalSnapshot044.ps1", "SignalSnapshot044.java"]
pins = []
for name in names + ["Verify044.ps1"]:
    source = previous / name if name == "Verify044.ps1" else previous / "proposals/ops044" / name
    original = source.read_bytes()
    text = original.decode("utf-8-sig").replace("044", "045")
    text = text.replace("PHANTOM-M1-G4-CAUSAL-045", "PHANTOM-M1-SIGNAL-LIFECYCLE-045")
    text = text.replace("ValidateSet('a','b')", "ValidateSet('a')")
    text = text.replace("contract045[abct]", "contract045a").replace("contract045[ab]", "contract045a")
    text = text.replace("phantom_test_045[ab]", "phantom_test_045a")
    if name == "Prepare032.ps1":
        text = text.replace("$gamePort=7796+", "$gamePort=7797+")
        text = text.replace("$loginPort=2126+", "$loginPort=2127+")
        text = text.replace("$controlPort=9036+", "$controlPort=9037+")
    target_name = name.replace("044", "045")
    target = task / target_name if name == "Verify044.ps1" else proposals / target_name
    target.write_text(text, encoding="utf-8", newline="\n")
    if name != "Verify044.ps1":
        (ops / target_name).write_bytes(target.read_bytes())
    pins.append({"source": str(source.relative_to(module)), "sourceSha256": hashlib.sha256(original).hexdigest(),
                 "target": str(target.relative_to(module)), "targetSha256": hashlib.sha256(target.read_bytes()).hexdigest()})
evidence = task / "evidence"
evidence.mkdir(exist_ok=True)
(evidence / "ADAPTER_PINS045.json").write_text(json.dumps(pins, indent=2), encoding="utf-8")
print(f"Reproduced {len(pins)} bounded tools; ordinary-only control guard preserved.")
