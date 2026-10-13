"""Check exact source pins, bounded scope, Python syntax and separate text encodings."""
from pathlib import Path
import ast
import hashlib
import json
import re

task = Path(__file__).resolve().parent
repo = task.parents[4]
prefix = task.relative_to(repo).as_posix() + '/'
owner = 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java'
paths = (task / 'REVIEW_ALLOWLIST042.txt').read_text(encoding='utf-8').splitlines()
assert len(paths) == len(set(paths))
assert all(path == owner or path.startswith(prefix) for path in paths)
assert all(not re.search(r'(^|/)(secrets|runtime|stopped-sql|full-native)(/|$)', path) for path in paths)
assert all(not path.endswith(('.sql', '.jar')) for path in paths)
markers = ['Р' + char for char in 'џќћ•Ў›¤њЈљґµ°»Ѕѕ']
markers += ['С' + char for char in 'Џ€Ђ‹Њ‚ѓ‡…†'] + [chr(65533)]
mojibake = re.compile('|'.join(re.escape(marker) for marker in markers), re.IGNORECASE)
escaped = re.compile(r'\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};')
mojibake_hits, escape_hits = [], []
count = 0
for relative in paths:
    path = repo / relative
    if not path.exists() or path.suffix == '.zip':
        continue
    assert path.stat().st_size < 350000, ('Oversized review member', relative)
    text = path.read_text(encoding='utf-8-sig')
    if path.suffix == '.py':
        ast.parse(text, filename=str(path))
    if path.suffix == '.json':
        json.loads(text)
    for number, line in enumerate(text.splitlines(), 1):
        if mojibake.search(line):
            mojibake_hits.append((relative, number))
        if escaped.search(line):
            escape_hits.append((relative, number))
    count += 1
assert not mojibake_hits, ('mojibake', mojibake_hits)
assert not escape_hits, ('escaped Cyrillic', escape_hits)
for row in json.loads((task / 'proposals/ops042/SOURCE_PINS042.json').read_text(encoding='utf-8')):
    actual = hashlib.sha256((task / 'proposals/ops042' / row['name']).read_bytes()).hexdigest()
    assert actual == row['sha256'], row['name']
result = dict(scope='exact TASK042 allowlist plus single TEST owner', checkedFiles=count,
              mojibakeMarkers='PASS', escapedCyrillic='PASS', sourcePins18='PASS', pythonSyntax='PASS')
print(json.dumps(result, ensure_ascii=False))
