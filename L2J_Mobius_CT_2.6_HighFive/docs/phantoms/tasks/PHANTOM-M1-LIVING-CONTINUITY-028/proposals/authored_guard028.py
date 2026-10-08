"""Strict UTF-8 and separate mojibake / escaped Cyrillic checks of authored scope."""
from pathlib import Path
import re

TASK = Path(__file__).resolve().parents[1]
MODULE = TASK.parents[3]
MARKERS = ['Рџ','Рќ','Рћ','Р•','РЎ','Р›','Р¤','Рњ','РЈ','Рљ','Рґ','Рµ','Р°','Р»',
           'РЅ','Рѕ','СЏ','С€','СЂ','С‹','СЊ','С‚','Сѓ','С‡','С…','С†','�']
escaped = re.compile(r'\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|'
                     r'&#x04[0-9A-Fa-f]{2};|&#x05[0-9A-Fa-f]{2};|'
                     r'&#X04[0-9A-Fa-f]{2};|&#X05[0-9A-Fa-f]{2};')
files = [p for p in TASK.iterdir() if p.is_file()]
files += [p for p in (TASK / 'proposals').iterdir() if p.suffix == '.py']
files += [MODULE / p for p in (
    'java/org/l2jmobius/gameserver/localplay/LocalPlaySyntheticHumanService.java',
    'java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationRetentionPolicy.java',
    'java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java',
    'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java',
    'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
    'tools/phantom-local-play/Invoke-LocalPlayPilot.ps1',
    'tools/phantom-local-play/LocalPlay-Pilot.ps1',
    'test/java/org/l2jmobius/tests/phantoms/LocalPlayContinuity028Suite.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomSoftReturn028Suite.java')]
bad_mojibake, bad_escaped = [], []
for path in files:
    text = path.read_text(encoding='utf-8-sig', errors='strict')
    # This guard's marker/regex definitions and pre-approved package definitions are technical test input.
    technical = path == Path(__file__).resolve() or path.name == 'PACKAGE_CHECKS.md'
    if not technical and any(marker in text for marker in MARKERS):
        bad_mojibake.append(str(path))
    if not technical and escaped.search(text):
        bad_escaped.append(str(path))
print(f'UTF8: {len(files)} authored files decoded strictly')
print('mojibake-маркеры в изменённых файлах проверены:', bad_mojibake or 'PASS')
print('escaped Cyrillic в изменённых файлах проверены:', bad_escaped or 'PASS')
if bad_mojibake or bad_escaped:
    raise SystemExit(1)
