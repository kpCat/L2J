"""Exact reviewed paths: source syntax, two separate encoding scans, and source pins."""
from pathlib import Path
import ast
import hashlib
import json
import re

task = Path(__file__).resolve().parent
repo = task.parents[4]
allowlist = (task/'REVIEW_ALLOWLIST043.txt').read_text(encoding='utf-8').splitlines()
markers = ('Рџ','Рќ','Рћ','Р•','РЎ','Р›','Р¤','Рњ','РЈ','Рљ','Рґ','Рµ','Р°','Р»','РЅ','Рѕ',
    'СЏ','С€','СЂ','С‹','СЊ','С‚','Сѓ','С‡','С…','С†','�')
escaped = re.compile(r'\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};')
mojibake_hits, escaped_hits, technical_hits, syntax = [], [], [], []
scan_paths = []
for name in allowlist:
    path = repo/name
    if not path.is_file() or path.suffix not in ('.md','.tsv','.py','.ps1','.java','.txt','.json','.patch'):
        continue
    if path.name in ('ENCODING043.json','GIT_COMMANDS043.json','PUBLICATION1_043.json','ZIP_VERIFIED043.json'):
        continue
    content = path.read_text(encoding='utf-8-sig')
    scan_paths.append(name)
    if path.suffix == '.py':
        ast.parse(content,filename=name)
        syntax.append(name)
    for index,line in enumerate(content.splitlines(),1):
        found = [value for value in markers if value in line]
        escapes = escaped.findall(line)
        if path.resolve() == Path(__file__).resolve() and found:
            technical_hits.append(dict(path=name,line=index,reason='Detector marker literals, not user-facing mojibake'))
        elif found:
            mojibake_hits.append(dict(path=name,line=index,markers=found))
        if escapes:
            escaped_hits.append(dict(path=name,line=index,matches=escapes))

proposal = task/'proposals/ops043'
for row in json.loads((proposal/'SOURCE_PINS043.json').read_text(encoding='utf-8')):
    actual = hashlib.sha256((proposal/row['name']).read_bytes()).hexdigest()
    assert actual == row['sha256'], 'Frozen private helper differs: '+row['name']
value = dict(contract='EXACT043_TEXT_ENCODING_AND_SYNTAX', files=len(scan_paths), pythonSyntaxFiles=len(syntax),
    mojibakeMarkers=dict(checked=True,verdict='PASS' if not mojibake_hits else 'FAIL',hits=mojibake_hits),
    escapedCyrillic=dict(checked=True,verdict='PASS' if not escaped_hits else 'FAIL',hits=escaped_hits),
    technicalDetectorLiterals=technical_hits, scannedPaths=scan_paths, frozenHelperPins='PASS')
(task/'evidence/ENCODING043.json').write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({key:value[key] for key in ('files','pythonSyntaxFiles','mojibakeMarkers','escapedCyrillic','frozenHelperPins')},ensure_ascii=False))
assert not mojibake_hits and not escaped_hits, 'Encoding defects must be fixed or explicitly classified before publication.'
