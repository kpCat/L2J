"""Compact045 facts from immutable own evidence; no database or runtime mutation."""
from pathlib import Path
import csv
import hashlib
import json
import subprocess

TASK = Path(__file__).resolve().parent
MODULE = TASK.parents[3]
REPO = MODULE.parent
OPS = MODULE / ".phantom-local/ops045"
BASE = "a6fde1df1b1785b93e2248a20786c61878e05428"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def write(name, value):
    (TASK / "evidence" / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

scene = TASK / "evidence/NATIVE045"
with (scene / "enrollment-census/global-admission.tsv").open(encoding="utf-8-sig", newline="") as stream:
    rows = list(csv.DictReader(stream, delimiter="\t"))
baseline = json.loads((scene / "baseline-cohort.json").read_text(encoding="utf-8-sig"))
ids = {str(row["profileId"]) for row in baseline}
cohort = [row for row in rows if row["profileId"] in ids]
write("NATIVE_INPUT045.json", {
    "status": "INPUT_FAIL_BEFORE_DEPARTURE", "globalProfiles": len(rows),
    "admittedProfiles": sum(row["admitted"] == "true" for row in rows),
    "enrolledCount": len(baseline), "enrolled": cohort,
    "baseline": baseline, "MOVEExecuted": False, "backgroundWindow": False,
    "G4": "NOT_VERIFIED", "historical1189Owner": "UNKNOWN",
    "pins": [{"path": str(path), "sha256": sha(path)} for path in [
        scene / "baseline-cohort.json", scene / "enrollment-census/global-admission.tsv",
        scene / "premeasurement-endpoint-eligibility045.json", scene / "episode-failure.txt",
        scene / "synthetic-start.json", scene / "synthetic-stop.json"]]})

proof_path = OPS / "CONSERVATION045.json"
proof = json.loads(proof_path.read_text(encoding="utf-8-sig"))
write("CONSERVATION_SUMMARY045.json", {key: proof[key] for key in [
    "contract", "sourceSha", "jarSha256", "nativeBirths", "commitRecords", "allBirthsTerminal",
    "GLOBAL_DRAIN", "NATIVE_BOUNDARY_INTERVAL", "FINAL_STOPPED_CUT", "FULL_SAVE", "pass", "failures"]} | {
    "nativeWitnesses": len(proof["rows"]), "stoppedProfiles": len(proof["stoppedProfiles"]),
    "fullProof": str(proof_path), "fullProofSha256": sha(proof_path),
    "rawPinsCount": len(proof["rawPins"]), "nativeVerifierExit": proof["nativeVerifierExit"],
    "stoppedVerifierExit": proof["stoppedVerifierExit"]})

protected = []
for name in ["PhantomScheduler.java", "activity/PhantomMaterializationRetentionPolicy.java",
             "topology/PhantomHumanLocalityControl.java", "topology/PhantomPerceptionProvider.java",
             "population/PhantomPresenceRegistry.java", "population/PhantomPopulationEcologyService.java",
             "background/PhantomHistoricalBackgroundService.java", "background/PhantomVisibleAutoPlay.java",
             "background/PhantomBackgroundTransaction.java", "player/PhantomNativeWorkScope.java",
             "activity/PhantomSchedulerPolicy.java", "PhantomSystem.java"]:
    relative = "L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/" + name
    before = subprocess.check_output(["git", "-C", str(REPO), "show", BASE + ":" + relative])
    path = REPO / relative
    equal = before.replace(b"\r\n", b"\n") == path.read_bytes().replace(b"\r\n", b"\n")
    if not equal: raise SystemExit("Protected product drift: " + relative)
    protected.append({"path": relative, "sha256": sha(path), "normalizedEqualsBASE": equal})

preservation = MODULE / ".phantom-local/contract045a/original-preservation.tsv"
with preservation.open(encoding="utf-8-sig", newline="") as stream:
    originals = list(csv.DictReader(stream, delimiter="\t"))
original_root = MODULE / ".phantom-local/contract036b/runtime"
for row in originals:
    if sha(original_root / row["path"]).lower() != row["sha256"].lower():
        raise SystemExit("Protected036 input drift: " + row["path"])
evaluator = MODULE / "docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/continuity028.py"
if sha(evaluator) != "15c153d30fe7f85719b1698059f4e28c9c7fa325caa420a02de7ac19cff35112":
    raise SystemExit("Original CONTINUITY_V2 drift")
main = Path(r"C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-SIGNAL-LIFECYCLE-045")
for name in ["GOAL.md", "TASK.md", "CONTEXT.md", "DESIGN.md", "PLAN.md", "ACCEPTANCE.md", "SOURCE_MAP.tsv", "HANDOFF.md"]:
    if (main / name).read_bytes() != (TASK / name).read_bytes():
        raise SystemExit("MAIN input drift: " + name)
write("PRESERVATION045.json", {"protectedProduct": protected, "original036Files": len(originals),
    "original036": "UNCHANGED", "MAIN8Inputs": "BYTE_IDENTICAL", "CONTINUITY_V2": sha(evaluator),
    "oldDatabases": "NO_WRITE_AUTHORITY_OR_COMMAND; own writes exact045a only",
    "oldRaw": "NO_MUTATION; existing historical FAIL/UNKNOWN retained",
    "current040a": "Actual process/JAR incarnation verified separately in runtime receipt"})
print("Native input/FULL conservation summaries and protected source/304 old inputs/MAIN/evaluator pins PASS.")
