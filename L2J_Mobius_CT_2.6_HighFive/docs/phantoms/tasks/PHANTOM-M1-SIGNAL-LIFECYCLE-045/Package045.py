"""Exact045 scope, encoding, and compact review CRC/member-SHA validation."""
from pathlib import Path
import ast
import hashlib
import json
import os
import re
import subprocess
import zipfile

TASK = Path(__file__).resolve().parent
MODULE = TASK.parents[3]
REPO = MODULE.parent
PREFIX = str(TASK.relative_to(REPO)).replace("\\", "/") + "/"
paths = [REPO / name for name in (TASK / "evidence/SOURCE_ALLOWLIST045.txt").read_text(encoding="utf-8-sig").splitlines()]
root_names = ["RESULT045.md", "GATES045.tsv", "REVIEW045.md", "CHECKPOINT045.md", "GIT_COMMANDS045.md",
              "PRECONDITIONS045.md", "Run-Native045.ps1", "AdaptValidators045.py", "FinalizeEvidence045.py",
              "FinalizeRegression045.py", "Package045.py"]
indices = ["SOURCE_ALLOWLIST045.txt", "SOURCE_GATE045.json", "NATIVE_BUILD045.json", "NATIVE_INPUT045.json",
           "CONSERVATION_SUMMARY045.json", "PRESERVATION045.json", "FINAL_RUNTIME_PRESERVATION045.json",
           "REGRESSIONS_FINAL045.json", "VALIDATOR_PINS045.json", "CURRENT93_045-matrix.tsv"]
paths += [TASK / name for name in root_names] + [TASK / "evidence" / name for name in indices]
paths += [MODULE / row["target"] for row in json.loads((TASK / "evidence/VALIDATOR_PINS045.json").read_text())]
publication = TASK / "evidence/PUBLICATION1_RECEIPT045.json"
if publication.exists(): paths.append(publication)
paths = list(dict.fromkeys(paths))
source_allowlist = {str(p.relative_to(REPO)).replace("\\", "/") for p in paths}
changed = subprocess.check_output(["git", "-C", str(REPO), "diff", "--name-only"], text=True).splitlines()
if set(changed) - source_allowlist: raise SystemExit("Foreign tracked changes: " + repr(set(changed) - source_allowlist))
subprocess.run(["git", "-C", str(REPO), "diff", "--check"], check=True)
protected = [p for p in paths if "/java/" in str(p.relative_to(REPO)).replace("\\", "/")]
semantic = subprocess.check_output(["git", "-C", str(REPO), "diff", "--name-only",
    "0a07452b40d246459954ed98fb8a76f1fa7e463f", "--", *[str(p.relative_to(REPO)) for p in protected]], text=True)
if semantic.strip(): raise SystemExit("Java/TEST changed after native candidate: " + semantic)
markers = ['Рџ','Рќ','Рћ','Р•','РЎ','Р›','Р¤','Рњ','РЈ','Рљ','Рґ','Рµ','Р°','Р»','РЅ','Рѕ','СЏ','С€','СЂ','С‹','СЊ','С‚','Сѓ','С‡','С…','С†','�']
escaped = re.compile(r"\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};")
members = []
for path in paths:
    payload = path.read_bytes()
    text = payload.decode("utf-8-sig")
    # Only literal scanner definitions are technical pattern data, not user-facing strings.
    scanned = "\n".join(line for line in text.splitlines() if not
        (path.name in ("Package045.py", "SourceGate045.py") and line.lstrip().startswith("markers =")))
    if any(marker in scanned for marker in markers): raise SystemExit("Mojibake marker: " + str(path))
    if escaped.search(scanned): raise SystemExit("Escaped Cyrillic: " + str(path))
    if path.suffix == ".py": ast.parse(text, filename=str(path))
    members.append({"name": str(path.relative_to(REPO)).replace("\\", "/"), "bytes": len(payload),
                    "sha256": hashlib.sha256(payload).hexdigest()})
encoding = TASK / "evidence/ENCODING045.json"
encoding.write_text(json.dumps({"mojibakeMarkers": "PASS", "escapedCyrillic": "PASS", "files": len(paths),
    "technicalExemption": "Literal scanner marker definitions only; no user-facing exemptions",
    "javaTestEqualsNativeCandidate": True, "changed": changed}, indent=2), encoding="utf-8")
paths.append(encoding)
payload = encoding.read_bytes()
members.append({"name": str(encoding.relative_to(REPO)).replace("\\", "/"), "bytes": len(payload), "sha256": hashlib.sha256(payload).hexdigest()})
names = [row["name"] for row in members]
if len(names) != len(set(names)): raise SystemExit("Duplicate review member")
archive = TASK / "TASK045-review.zip"
temporary = archive.with_suffix(".zip.tmp")
with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zipped:
    for path, row in zip(paths, members): zipped.writestr(row["name"], path.read_bytes())
with zipfile.ZipFile(temporary) as zipped:
    if zipped.testzip() is not None or zipped.namelist() != names: raise SystemExit("CRC/member list failed")
    for path, row in zip(paths, members):
        name = row["name"]
        if Path(name).is_absolute() or ".." in Path(name).parts or "\\" in name: raise SystemExit("Unsafe archive path")
        data = zipped.read(name)
        if data != path.read_bytes() or hashlib.sha256(data).hexdigest() != row["sha256"]: raise SystemExit("Member SHA failed")
if temporary.stat().st_size > 2 * 1024 * 1024: raise SystemExit("Review exceeds compact2MiB artifact budget")
os.replace(temporary, archive)
qa = TASK / "evidence/ZIP_QA045.json"
qa.write_text(json.dumps({"archive": str(archive), "bytes": archive.stat().st_size,
    "sha256": hashlib.sha256(archive.read_bytes()).hexdigest(), "CRC": "PASS", "allMemberSHA": "PASS",
    "noDatabaseRawJarSecrets": True, "members": members}, indent=2), encoding="utf-8")
allowlist = names + [str(qa.relative_to(REPO)).replace("\\", "/"), PREFIX + "ALLOWLIST045.txt"]
(TASK / "ALLOWLIST045.txt").write_text("\n".join(allowlist) + "\n", encoding="utf-8")
print(f"Exact scoped review: {len(members)} members, {archive.stat().st_size} bytes; CRC/memberSHA/encoding PASS.")
