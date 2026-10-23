"""Exact allowlist and two independent source-text checks; no runtime writes."""
import argparse
import hashlib
import pathlib
import re

parser = argparse.ArgumentParser()
parser.add_argument("--repo", type=pathlib.Path, required=True)
parser.add_argument("--paths", type=pathlib.Path, required=True)
args = parser.parse_args()
repo = args.repo.resolve(strict=True)
paths = args.paths.read_text(encoding="utf-8-sig").splitlines()
markers = "Рџ Рќ Рћ Р• РЎ Р› Р¤ Рњ РЈ Рљ Рґ Рµ Р° Р» РЅ Рѕ СЏ С€ СЂ С‹ СЊ С‚ Сѓ С‡ С… С† �".split()
escaped = re.compile(r"\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};")
failures = []
for relative in paths:
    path = repo.joinpath(relative).resolve(strict=True)
    if not path.is_relative_to(repo) or ".phantom-local" in path.parts:
        raise SystemExit("SCOPE_FAIL: private/outside path")
    text = path.read_text(encoding="utf-8-sig")
    # This validator contains the reviewed detector literals, never user-facing text.
    if path == pathlib.Path(__file__).resolve():
        text = "\n".join(line for line in text.splitlines() if not line.startswith(("markers =", "escaped =")))
    for label, found in (("MOJIBAKE", any(marker in text for marker in markers)), ("ESCAPED_CYRILLIC", bool(escaped.search(text)))):
        if found:
            failures.append(f"{label}_MATCH {relative}")
    print(f"SHA256\t{relative}\t{hashlib.sha256(path.read_bytes()).hexdigest()}")
print("MOJIBAKE_MARKERS_CHECKED=" + ("FAIL" if any(x.startswith("MOJIBAKE") for x in failures) else "PASS"))
print("ESCAPED_CYRILLIC_CHECKED=" + ("FAIL" if any(x.startswith("ESCAPED") for x in failures) else "PASS"))
if failures:
    raise SystemExit("\n".join(failures))
