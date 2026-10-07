"""Read-only publication checks for the exact TASK023 artifact family."""
import hashlib
import json
import re
from pathlib import Path

TASK = Path(__file__).resolve().parent
ROOT = TASK.parents[4]
EXCLUDED = {"PUBLISH_RECEIPT.txt", "PUBLISH_COMMANDS.json", "PUBLISH_WHITESPACE.txt"}
GENERATED = {"ARTIFACT_GUARD.json", "ARTIFACT_INVENTORY.tsv", "ARTIFACT_EXACT_ALLOWLIST.txt"}
MARKERS = "Рџ Рќ Рћ Р• РЎ Р› Р¤ Рњ РЈ Рљ Рґ Рµ Р° Р» РЅ Рѕ СЏ С€ СЂ С‹ СЊ С‚ Сѓ С‡ С… С† �".split()
ESCAPED = re.compile(r"\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};")
SECRET = re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|sk-[A-Za-z0-9_-]{30,}|AKIA[A-Z0-9]{16})\b|(?i:Bearer)\s+[A-Za-z0-9._-]{24,}|(?i:password|dbpassword|databasepassword|api[_-]?key|access[_-]?token)\s*[:=]\s*[\"']?([^\s\"';,<>]{1,})")

def path_key(path):
    return path.relative_to(ROOT).as_posix()

def included(path):
    return path.is_file() and "__pycache__" not in path.parts and path.suffix not in {".pyc", ".class"} and path.name not in EXCLUDED

def main():
    artifacts = sorted((p for p in TASK.rglob("*") if included(p)), key=path_key)
    source = [ROOT / s.strip() for s in (TASK / "SOURCE_EXACT_ALLOWLIST.txt").read_text(encoding="utf-8-sig").splitlines() if s.strip()]
    report = {"task_files": len(artifacts), "source_files": len(source), "utf8_errors": [], "large_files": [], "mojibake": [], "escaped_cyrillic": [], "secret_review": [], "immutable_mismatch": []}
    manifest = json.loads((TASK / "PACKAGE_MANIFEST.json").read_text(encoding="utf-8-sig"))
    for name, expected in manifest["files"].items():
        actual = hashlib.sha256((TASK / name).read_bytes()).hexdigest()
        if actual.lower() != expected.lower():
            report["immutable_mismatch"].append(name)
    inventory = ["path\tbytes\tsha256"]
    for path in source + artifacts:
        data = path.read_bytes()
        relative = path_key(path)
        if path.name not in GENERATED:
            inventory.append(f"{relative}\t{len(data)}\t{hashlib.sha256(data).hexdigest()}")
        if len(data) >= 95_000_000:
            report["large_files"].append(relative)
        try:
            contents = data.decode("utf-8-sig")
        except UnicodeDecodeError:
            report["utf8_errors"].append(relative)
            continue
        for number, line in enumerate(contents.splitlines(), 1):
            for category, found in (("mojibake", any(m in line for m in MARKERS)), ("escaped_cyrillic", bool(ESCAPED.search(line))), ("secret_review", bool(SECRET.search(line)))):
                if found:
                    report[category].append({"path": relative, "line": number})
    raw_dates = {"evidence/C_POST_CRASH_WALK/dry-path.txt", "evidence/DRY_PATH_PRECHECK.txt", "evidence/NATIVE_WALK_B_RESTART/dry-path.txt"}
    technical_markers = {("Guard-Artifacts023.py", 11), ("Scope-Guard023.ps1", 33)}
    technical_credentials = {("Prepare-Runtime023.ps1", 26), ("Prepare-Runtime023.ps1", 27), ("Read-Clone023.ps1", 14)}
    prefix = path_key(TASK) + "/"
    report["unexpected_mojibake"] = [m for m in report["mojibake"] if m["path"][len(prefix):] not in raw_dates and (m["path"][len(prefix):], m["line"]) not in technical_markers]
    report["unexpected_secret"] = [m for m in report["secret_review"] if (m["path"][len(prefix):], m["line"]) not in technical_credentials]
    report["checks_pass"] = not any(report[k] for k in ("utf8_errors", "large_files", "immutable_mismatch", "unexpected_mojibake", "escaped_cyrillic", "unexpected_secret"))
    (TASK / "ARTIFACT_GUARD.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (TASK / "ARTIFACT_INVENTORY.tsv").write_text("\n".join(inventory) + "\n", encoding="utf-8")
    artifacts = sorted((p for p in TASK.rglob("*") if included(p)), key=path_key)
    (TASK / "ARTIFACT_EXACT_ALLOWLIST.txt").write_text("\n".join(sorted(set(map(path_key, artifacts + [TASK / "ARTIFACT_EXACT_ALLOWLIST.txt"])))) + "\n", encoding="utf-8")
    print(json.dumps({k: len(v) if isinstance(v, list) else v for k, v in report.items()}, ensure_ascii=False))

if __name__ == "__main__":
    main()
