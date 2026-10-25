"""One physical TASK091 shadow. No DB connection or native gameplay is implicit."""
import hashlib
import json
import os
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path
import shutil
import subprocess
import sys
import uuid

FAMILIES = ('build.xml', 'java', 'test', 'tools', 'dist/game', 'dist/libs',
            'dist/db_installer/sql', 'docs/phantoms/live-world')
EXCLUDE = {'dist/game/config/Database.ini', 'dist/game/config/Network.ini',
           'dist/game/config/Server.ini'}
OWN = {
    'java/org/l2jmobius/gameserver/phantoms/navigation/PhantomWalkCorridorPlanner.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomTravel091Test.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomTravel091Native.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomTask091Lane.java',
    'test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java',
    'tools/phantom-runtime/Task091.ps1', 'test/interregional_travel091.py',
}


def require(condition, key):
    if not condition:
        raise ValueError('TASK091_' + key)


def plain(path):
    require(path.is_absolute() and path == path.resolve(), 'PHYSICAL_PATH')
    require(not path.is_symlink() and not path.stat().st_file_attributes & 0x400, 'REPARSE')


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def inventory(root):
    result = {}
    for family in FAMILIES:
        start = root / family
        plain(start)
        paths = [start] if start.is_file() else walk(start)
        for path in paths:
            relative = path.relative_to(root).as_posix()
            if relative not in EXCLUDE:
                result[relative] = path
    return result


def walk(start):
    for directory, dirs, files in os.walk(start, followlinks=False):
        for name in dirs + files:
            plain(Path(directory) / name)
        yield from (Path(directory) / name for name in files)


def command(args, cwd, log):
    with log.open('wb') as output:
        process = subprocess.Popen(args, cwd=cwd, stdout=output, stderr=subprocess.STDOUT)
        process_receipt = dict(pid=process.pid, startedUtc=datetime.now(timezone.utc).isoformat(),
                               log=log.name, cwd=str(cwd))
        log.with_suffix('.process.json').write_text(json.dumps(process_receipt), encoding='utf-8')
        code = process.wait()
        process_receipt.update(exitCode=code, finishedUtc=datetime.now(timezone.utc).isoformat())
        log.with_suffix('.process.json').write_text(json.dumps(process_receipt), encoding='utf-8')
    print(f'TASK091_COMMAND {log.name} exit={code}', flush=True)
    return code


def main(mode, module, stage):
    module, stage = Path(module), Path(stage)
    plain(module)
    plain(stage)
    require(stage.name.startswith('phantom091-') and not stage.is_relative_to(module.parent), 'EXTERNAL_STAGE')
    shadow = stage / 'source'
    receipt = stage / 'inputs.json'
    if mode == 'prepare':
        require(not receipt.exists() and not shadow.exists(), 'PREPARE_ONCE')
        inputs = inventory(module)
        rows = {}
        for relative, source in inputs.items():
            target = shadow / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            rows[relative] = digest(source)
            require(digest(target) == rows[relative], 'COPY_SHA')
        geo = Path(module.anchor) / 'PhantomWorld/Living-Economy-068-candidate/game/data/geodata'
        plain(geo)
        tiles = sorted(geo.glob('*.l2j'))
        require(len(tiles) == 203, 'GEO203')
        geohashes = {}
        for source in tiles:
            plain(source)
            target = shadow / 'dist/game/data/geodata' / source.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            geohashes[source.name] = digest(source)
            require(digest(target) == geohashes[source.name], 'GEO_COPY_SHA')
        data = dict(module=str(module), stage=str(stage), owner=uuid.uuid4().hex,
                    files=rows, geoSource=str(geo), geo=geohashes)
        receipt.write_text(json.dumps(data, sort_keys=True), encoding='utf-8')
        print(f'TASK091_PREPARED files={len(rows)} Geo=203 SQL=0', flush=True)
        return 0
    data = json.loads(receipt.read_text(encoding='utf-8'))
    require(data['module'] == str(module) and data['stage'] == str(stage), 'OWNER_ROOT')
    inputs = inventory(module)
    require(not (set(data['files']) - set(inputs)), 'SOURCE_REMOVED')
    added = set(inputs) - set(data['files'])
    require(not added or mode == 'refresh' and added <= OWN, 'SOURCE_INVENTORY')
    changed = set()
    for relative, source in inputs.items():
        if relative in added:
            changed.add(relative)
            continue
        require(digest(shadow / relative) == data['files'][relative], 'COPY_DRIFT:' + relative)
        if digest(source) != data['files'][relative]:
            require(mode == 'refresh' and relative in OWN, 'SOURCE_DRIFT:' + relative)
            changed.add(relative)
    for name, expected in data['geo'].items():
        require(digest(shadow / 'dist/game/data/geodata' / name) == expected, 'GEO_DRIFT')
    native_owned = (stage / 'private/owner.properties').exists()
    require(native_owned or not any((shadow / relative).exists() for relative in EXCLUDE), 'PRIVATE_CONFIG')
    require(not (shadow / 'dist/game/config/Network.ini').exists(), 'UNEXPECTED_NETWORK_CONFIG')
    if mode == 'refresh':
        require(bool(changed), 'REFRESH_WITHOUT_CHANGE')
        backup = stage / ('before-refresh-' + uuid.uuid4().hex)
        backup.mkdir()
        shutil.copyfile(receipt, backup / 'inputs.json')
        if native_owned:
            for name in ('inputs.tsv', 'private/owner.properties', 'build/phantom-test/bin/org/l2jmobius/tests/phantoms/PhantomTravel091Native.class'):
                saved = backup / name
                saved.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(stage / name, saved)
                require(digest(saved) == digest(stage / name), 'NATIVE_BACKUP_SHA')
            server_row = next(row.split('\t') for row in (stage / 'inputs.tsv').read_text(encoding='utf-8').splitlines()
                              if row.split('\t')[1] == str(shadow / 'dist/game/config/Server.ini'))
            require(digest(Path(server_row[0])) == server_row[2] == digest(Path(server_row[1])), 'SERVER_INPUT_DRIFT')
        for relative in changed:
            if relative not in added:
                saved = backup / relative
                saved.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(shadow / relative, saved)
                require(digest(saved) == data['files'][relative], 'BACKUP_SHA')
            shutil.copyfile(module / relative, shadow / relative)
            data['files'][relative] = digest(module / relative)
            require(digest(shadow / relative) == data['files'][relative], 'REFRESH_SHA')
        receipt.write_text(json.dumps(data, sort_keys=True), encoding='utf-8')
        if native_owned:
            rows = [f'{module / name}\t{shadow / name}\t{sha}' for name, sha in sorted(data['files'].items())]
            rows += [f'{Path(data["geoSource"]) / name}\t{shadow / "dist/game/data/geodata" / name}\t{sha}' for name, sha in sorted(data['geo'].items())]
            rows.append('\t'.join(server_row))
            (stage / 'inputs.tsv').write_text('\n'.join(rows) + '\n', encoding='utf-8')
            owner_path = stage / 'private/owner.properties'
            owner_text, count = re.subn(r'(?m)^inputsSha256=.*$', 'inputsSha256=' + digest(stage / 'inputs.tsv'), owner_path.read_text(encoding='utf-8'))
            require(count == 1, 'OWNER_INPUT_PIN')
            pending_owner = owner_path.with_suffix('.pending')
            require(not pending_owner.exists(), 'OWNER_PENDING_EXISTS')
            pending_owner.write_text(owner_text, encoding='utf-8')
            pending_owner.replace(owner_path)
        print(f'TASK091_REFRESHED exactOwnFiles={len(changed)}', flush=True)
        return 0
    if mode == 'verify':
        print('TASK091_INPUTS_PASS SQL=0', flush=True)
        return 0
    if mode == 'compile':
        ant = shutil.which('ant.bat')
        require(ant is not None, 'ANT_INPUT')
        return command([ant, '-f', str(shadow / 'build.xml'), '-Dbuild=' + str(stage / 'build'),
                        'compile-tests'], shadow, stage / 'compile.log')
    require(mode in ('focused', 'preflight', 'configure', 'guards', 'bootstrap', 'native', 'continuenative', 'connections', 'regression'), 'MODE')
    libraries = [str(path) for path in sorted((shadow / 'dist/libs').glob('*.jar'))
                 if path.name not in ('GameServer.jar', 'LoginServer.jar') and not path.name.endswith('-sources.jar')]
    cp = ';'.join(str(stage / path) for path in ('build/bin', 'build/phantom-test/bin', 'build/phantom-test/resources')) + ';' + ';'.join(libraries)
    if mode == 'configure':
        require(not native_owned and not (stage / 'native.started').exists(), 'CONFIGURE_ONCE')
        private = stage / 'private'
        private.mkdir(exist_ok=False)
        source_server = module / 'dist/game/config/Server.ini'
        plain(source_server)
        shutil.copyfile(source_server, shadow / 'dist/game/config/Server.ini')
        rows = [f'{module / name}\t{shadow / name}\t{sha}' for name, sha in sorted(data['files'].items())]
        rows += [f'{Path(data["geoSource"]) / name}\t{shadow / "dist/game/data/geodata" / name}\t{sha}' for name, sha in sorted(data['geo'].items())]
        rows.append(f'{source_server}\t{shadow / "dist/game/config/Server.ini"}\t{digest(source_server)}')
        (stage / 'inputs.tsv').write_text('\n'.join(rows) + '\n', encoding='utf-8')
        owner = dict(owner='TASK091_NATIVE_FARM', schema='l2jmobiush5_phantom_test_091',
                     baseline='627bca947fd565ce18aa5cc85c7e1aa1288ccaef',
                     installationId=str(uuid.UUID(data['owner'])), sourceModule=module.as_posix(),
                     shadowModule=shadow.as_posix(), stage=stage.as_posix(), geoSource=Path(data['geoSource']).as_posix(),
                     expiresUtc=(datetime.now(timezone.utc) + timedelta(hours=2)).strftime('%Y-%m-%dT%H:%M:%SZ'),
                     inputsSha256=digest(stage / 'inputs.tsv'), sourceDatabaseSha256=digest(module / 'dist/game/config/Database.ini'))
        (private / 'owner.properties').write_text(''.join(f'{key}={value}\n' for key, value in owner.items()), encoding='utf-8')
    if mode in ('configure', 'guards', 'bootstrap', 'native', 'continuenative', 'connections'):
        args = ['java', '-Xms256m', '-Xmx4g', '-Dfile.encoding=UTF-8', '-Dphantom.task091.native=true',
                '-Dphantom.task091.root=' + str(stage), '-Dphantom.task091.uuid=' + str(uuid.UUID(data['owner'])),
                '-Dphantom.module.root=' + str(shadow), '-Dphantom.test.config=' + str(shadow / 'dist/game/config/Database.ini'),
                '-cp', cp, 'org.l2jmobius.tests.phantoms.PhantomTravel091Native', mode, str(module)]
        return command(args, shadow / 'dist/game', stage / (mode + '.log'))
    if mode == 'regression':
        for test in ('PhantomTravel089Test', 'PhantomWalkCorridor064Test', 'PhantomNormalGatekeeperPureTest'):
            code = command(['java', '-Xmx256m', '-cp', cp, 'org.l2jmobius.tests.phantoms.' + test],
                           shadow / 'dist/game', stage / (test + '.log'))
            if code:
                return code
        return 0
    args = ['java', '-Xmx256m', '-cp', cp]
    args += (['-Dphantom.task091.native=true', 'org.l2jmobius.tests.phantoms.PhantomTravel091Native', 'preflight', str(module)]
             if mode == 'preflight' else ['org.l2jmobius.tests.phantoms.PhantomTravel091Test'])
    return command(args, shadow / 'dist/game', stage / (mode + '.log'))


if __name__ == '__main__':
    sys.exit(main(*sys.argv[1:]))
