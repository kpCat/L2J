"""Exact TASK018 scope and separate source-text encoding checks."""
import csv
import re
import subprocess
from pathlib import Path

TASK = Path(__file__).resolve().parent
ROOT = TASK.parents[4]
rows = list(csv.DictReader((TASK / 'SOURCE_MAP.tsv').open(encoding='utf-8-sig'), delimiter='\t'))
allowed = {row['path'] for row in rows if row['mode'] in ('MODIFY', 'NEW_OR_MODIFY_TEST', 'OPTIONAL_MODIFY_TEST')}
family = TASK.relative_to(ROOT).as_posix() + '/'
status = subprocess.check_output(['git', 'status', '--porcelain', '-uall'], cwd=ROOT, text=True)
paths = [line[3:] for line in status.splitlines()]
outside = [path for path in paths if path not in allowed and not path.startswith(family)]
if outside:
    raise SystemExit('OUTSIDE_ALLOWLIST: ' + repr(outside))
markers = [''.join(map(chr, pair)) for pair in ((1056,1119),(1056,1037),(1056,1038),(1056,8226),(1056,1025),(1056,8250),(1056,164),(1056,1114),(1056,1032),(1056,1113),(1056,1169),(1056,181),(1056,176),(1056,187),(1056,1029),(1056,1109),(1057,1039),(1057,8364),(1057,1026),(1057,8249),(1057,1034),(1057,8218),(1057,1107),(1057,8225),(1057,8230),(1057,8224))] + [chr(65533)]
escaped = re.compile(r'\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};')
bad_markers, bad_escapes, generated_markers = [], [], []
for path in paths:
    text = (ROOT / path).read_text(encoding='utf-8-sig')
    if any(marker in text for marker in markers):
        (generated_markers if path.endswith('.log') else bad_markers).append(path)
    if escaped.search(text):
        bad_escapes.append(path)
print('EXACT_SCOPE_PASS paths=' + str(len(paths)))
print('MOJIBAKE_SOURCE_CHECK: ' + repr(bad_markers))
print('ESCAPED_CYRILLIC_CHECK: ' + repr(bad_escapes))
print('GENERATED_LOG_ENCODING_MATCHES: ' + repr(generated_markers))
if bad_markers or bad_escapes:
    raise SystemExit('Source encoding verification failed.')
print('Generated logs are retained verbatim as evidence; no authored user-facing text is escaped or garbled.')
