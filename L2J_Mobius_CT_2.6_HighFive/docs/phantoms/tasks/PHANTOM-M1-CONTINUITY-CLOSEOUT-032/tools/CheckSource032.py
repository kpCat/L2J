"""Read-only publication inventory and two separate UTF-8 source guards."""
import json
from pathlib import Path
import re

task = Path(__file__).resolve().parent.parent
module = task.parents[3]
root = module.parent
assert root.name == "L2J_Mobius" and root.parent.name == "m1-continuity-032"
paths = sorted(p for p in task.rglob("*") if p.is_file()
               and "evidence" not in p.relative_to(task).parts
               and "__pycache__" not in p.relative_to(task).parts)
paths += [module / p for p in (
    "java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java",
    "java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java",
    "java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java",
    "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java",
    "test/java/org/l2jmobius/tests/phantoms/PhantomVisibleContinuity032Suite.java")]
markers = ["Р" + c for c in "џќћ•Ў›¤њЈљґµ°»Ѕѕ"]
markers += ["С" + c for c in "Џ€Ђ‹Њ‚ѓ‡…†"] + [chr(0xfffd)]
mojibake = re.compile("|".join(re.escape(m) for m in markers))
escaped = re.compile(r"\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};")
failures = {}
for name, pattern in (("mojibake", mojibake), ("escapedCyrillic", escaped)):
    failures[name] = [f"{p.relative_to(root).as_posix()}:{i}"
                      for p in paths
                      for i, line in enumerate(p.read_text(encoding="utf-8-sig").splitlines(), 1)
                      if pattern.search(line)]
print(json.dumps({"files": len(paths), "checks": failures}, ensure_ascii=False))
if any(failures.values()):
    raise SystemExit(1)
print("EXACT_PATHS_BEGIN")
for p in paths:
    print(p.relative_to(root).as_posix())
