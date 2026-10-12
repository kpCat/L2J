"""Exact TASK038 scope, immutable packet and two independent encoding checks."""
import hashlib
import json
import re
import subprocess
from pathlib import Path

BASE = 'aa63dafb79e19a531b5620af195764c823bd9539'
TASK = Path(__file__).resolve().parents[1]
MODULE = TASK.parents[3]
OWN = MODULE.parent
PREFIX = MODULE.name + '/'
TASK_PREFIX = PREFIX + 'docs/phantoms/tasks/PHANTOM-M1-ACTION-LOOP-038/'
PRODUCTION = {
    'java/org/l2jmobius/gameserver/phantoms/actions/PhantomEngagementPlanner.java',
    'java/org/l2jmobius/gameserver/phantoms/actions/PhantomEngagementController.java',
    'java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java',
    'java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java',
    'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java',
    'java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java',
    'java/org/l2jmobius/gameserver/taskmanagers/AutoPlayTaskManager.java',
    'java/org/l2jmobius/gameserver/taskmanagers/AutoUseTaskManager.java',
}
TESTS = {
    'test/java/org/l2jmobius/tests/phantoms/PhantomActionLoop038Suite.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java',
}
COMMANDS = []
def git(*args):
    command = ['git', '-C', str(OWN), *args]
    COMMANDS.append(command)
    return subprocess.check_output(command, text=True, encoding='utf-8').splitlines()

def main():
    changed = git('diff', BASE, '--name-only')
    # Old untracked evidence035/036/037 is retained; inspect new038 and source scope.
    untracked = git('ls-files', '--others', '--exclude-standard', '--', TASK_PREFIX,
                    *sorted(PREFIX + path for path in PRODUCTION | TESTS))
    untracked = [path for path in untracked if not path.startswith(TASK_PREFIX + 'evidence/')
                 and '/__pycache__/' not in path]
    paths = sorted(set(changed + untracked))
    allowed = {PREFIX + path for path in PRODUCTION | TESTS}
    unexpected = [path for path in paths if path not in allowed and not path.startswith(TASK_PREFIX)]
    assert not unexpected, unexpected[:10]
    source_map = []
    for row in (TASK / 'SOURCE_MAP.tsv').read_text(encoding='utf-8-sig').splitlines()[1:]:
        if not row.strip():
            continue
        fields = row.split('\t')
        if len(fields) == 3:
            fields.append('')
        path, permission, boundary, pinned = fields
        if not pinned:
            continue
        base_blob = git('rev-parse', BASE + ':' + PREFIX + path)[0]
        assert base_blob == pinned, path
        if permission.startswith('READ_ONLY'):
            assert git('rev-parse', 'HEAD:' + PREFIX + path)[0] == pinned, path
            assert PREFIX + path not in changed, path
        source_map.append({'path': path, 'permission': permission, 'baseBlob': pinned})
    packet = []
    for line in (TASK / 'MANIFEST.sha256').read_text(encoding='utf-8-sig').splitlines():
        digest, name = line.split('  ', 1)
        actual = hashlib.sha256((TASK / name).read_bytes()).hexdigest()
        assert actual == digest, name
        packet.append({'path': name, 'sha256': actual})
    technical_codepoints = [[1056,1119],[1056,1116],[1056,1115],[1056,8226],[1056,1038],[1056,8250],[1056,164],[1056,1114],[1056,1032],[1056,1113],[1056,1169],[1056,181],[1056,176],[1056,187],[1056,1029],[1056,1109],[1057,1039],[1057,8364],[1057,1026],[1057,8249],[1057,1034],[1057,8218],[1057,1107],[1057,8225],[1057,8230],[1057,8224],[65533]]
    markers = [''.join(map(chr, points)) for points in technical_codepoints]
    escaped = re.compile(r'\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};')
    mojibake_hits, escaped_hits = [], []
    for path in paths:
        text = (OWN / path).read_text(encoding='utf-8-sig')
        for number, line in enumerate(text.splitlines(), 1):
            if any(marker in line for marker in markers):
                mojibake_hits.append({'path': path, 'line': number})
            if escaped.search(line):
                escaped_hits.append({'path': path, 'line': number})
    assert not mojibake_hits, mojibake_hits
    assert not escaped_hits, escaped_hits
    receipt = {'base': BASE, 'head': git('rev-parse', 'HEAD')[0], 'paths': paths,
               'productionPaths': sorted(path for path in paths if path in {PREFIX+p for p in PRODUCTION}),
               'packetFilesVerified': packet, 'sourceMapVerified': source_map, 'mojibakeCheck': 'PASS',
               'escapedCyrillicCheck': 'PASS', 'commands': COMMANDS}
    (TASK / 'evidence/SCOPE_ENCODING038.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'paths': len(paths), 'production': len(receipt['productionPaths']), 'packet': len(packet), 'mojibake': 'PASS', 'escapedCyrillic': 'PASS'}))

if __name__ == '__main__':
    main()
