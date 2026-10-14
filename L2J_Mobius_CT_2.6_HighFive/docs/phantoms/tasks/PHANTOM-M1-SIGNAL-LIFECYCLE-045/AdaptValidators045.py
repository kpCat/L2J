"""Stage unchanged strict039 validators with an exact045a namespace adapter."""
from pathlib import Path
import hashlib
import json

task = Path(__file__).resolve().parent
module = task.parents[3]
source = task.parent / "PHANTOM-M1-G4-CAUSAL-044/proposals/ops044/validation"
target = task / "proposals/ops045/validation"
private = module / ".phantom-local/ops045/validation"
target.mkdir(parents=True, exist_ok=True)
private.mkdir(parents=True, exist_ok=True)
names = ["commit_chain039.py", "complete_chain044.py", "raw_archive044.py", "verify_commit_run039.py",
         "test_commit_chain039.py", "test_complete_chain044.py", "VerifyNativeCommit039.java", "VerifyStoppedCut039.java"]
pins = []
for name in names:
    raw = (source / name).read_bytes()
    text = raw.decode("utf-8-sig").replace("044", "045").replace("phantom_test_045[ab]", "phantom_test_045a")
    path = target / name.replace("044", "045")
    path.write_text(text, encoding="utf-8", newline="\n")
    (private / path.name).write_bytes(path.read_bytes())
    pins.append({"source": str((source / name).relative_to(module)), "sourceSha256": hashlib.sha256(raw).hexdigest(),
                 "target": str(path.relative_to(module)), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                 "change": "044 namespace/name only; DB mask narrowed to exact045a; all proof criteria unchanged"})
(task / "evidence/VALIDATOR_PINS045.json").write_text(json.dumps(pins, indent=2), encoding="utf-8")
print("Strict039 validators staged; exact045a only; no threshold changes.")
