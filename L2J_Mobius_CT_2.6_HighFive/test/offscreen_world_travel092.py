"""Own DB-free TASK092 lane. No SQL, native bootstrap, Geo or server launch."""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import uuid
from pathlib import Path

OWN = ('test/offscreen_world_travel092.py',
       'test/java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomOffscreen092Checks.java')


def plain(path):
    path = Path(path)
    if not path.is_absolute() or path.resolve() != path:
        raise ValueError('TASK092_PHYSICAL_ABSOLUTE_PATH_REQUIRED')
    for current in (path, *path.parents):
        if current.exists() and current.stat().st_file_attributes & 0x400:
            raise ValueError('TASK092_REPARSE_REFUSED')
    return path


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def inputs(module):
    result = {'build.xml', 'tools/OwnerConfig.ps1', 'test/offscreen_world_travel092.py'}
    for family in ('java', 'test/java', 'test/resources'):
        for directory, dirs, files in os.walk(module / family, followlinks=False):
            for name in dirs + files:
                plain(Path(directory) / name)
            result.update((Path(directory) / name).relative_to(module).as_posix() for name in files)
    profile = json.loads((module / 'dist/game/config/OwnerConfig.json').read_text(encoding='utf-8-sig'))
    for name, kind in profile['files'].items():
        relative = Path(name)
        if kind == 'public':
            if relative.is_absolute() or '..' in relative.parts or relative.suffix != '.ini':
                raise ValueError('TASK092_PUBLIC_CONFIG_PATH')
            result.add('dist/game/config/' + relative.as_posix())
    result.update('dist/game/config/' + name for name in (
        'OwnerConfig.json', 'OwnerProfile38.tsv', 'OwnerProfilePreserved.properties', 'ClassMaster.xml'))
    result.update('dist/game/data/' + name for name in (
        'EnchantItemGroups.xml', 'EnchantItemData.xml', 'xsd/enchantItemGroups.xsd', 'xsd/enchantItemData.xsd',
        'phantoms/topology/high-five-core.xml', 'stats/npcs/22800-22899.xml', 'spawns/Others/22_21.xml'))
    result.update(path.relative_to(module).as_posix() for path in (module / 'dist/libs').glob('*.jar')
                  if path.name not in ('GameServer.jar', 'LoginServer.jar') and not path.name.endswith('-sources.jar'))
    for relative in result:
        plain(module / relative)
    return sorted(result)


def verify(module, stage, refresh=False):
    if stage.is_relative_to(module.parent) or not stage.name.startswith('phantom092-'):
        raise ValueError('TASK092_EXTERNAL_OWN_STAGE_REQUIRED')
    manifest = json.loads((stage / 'inputs.json').read_text(encoding='utf-8'))
    if manifest['module'] != str(module) or set(manifest['files']) != set(inputs(module)) or set(manifest['files']) != set(inputs(stage / 'source')):
        raise ValueError('TASK092_INPUT_INVENTORY_DRIFT')
    for relative, expected in manifest['files'].items():
        source = plain(module / relative)
        target = plain(stage / 'source' / relative)
        if (sha(source) != expected and not (refresh and relative in OWN)) or sha(target) != expected:
            raise ValueError('TASK092_INPUT_BYTES_DRIFT: ' + relative)
    for relative, expected in manifest['protected'].items():
        if sha(plain(module / relative)) != expected:
            raise ValueError('TASK092_SOURCE_JAR_DRIFT')
    print('TASK092_INPUTS_VERIFIED count=' + str(len(manifest['files']))
          + ('; unchanged-inputs=exact; own-test-refresh=pending' if refresh else '; source/copy=exact') + '; source-jars=unchanged')
    return manifest


def outputs(stage):
    result = {}
    for family in ('build/bin', 'build/phantom-test/bin', 'build/phantom-test/resources', 'build/dist/libs'):
        for directory, dirs, files in os.walk(stage / family, followlinks=False):
            for name in dirs + files:
                plain(Path(directory) / name)
            for name in files:
                path = Path(directory) / name
                result[path.relative_to(stage).as_posix()] = sha(path)
    if not result:
        raise ValueError('TASK092_NO_BUILD_OUTPUT')
    return result


def main():
    if len(sys.argv) not in (3, 4):
        raise ValueError('Usage: prepare|verify|contracts|required-product|regression <module> [stage]')
    mode, module = sys.argv[1], plain(Path(sys.argv[2]))
    if mode == 'prepare':
        if len(sys.argv) != 3:
            raise ValueError('TASK092_PREPARE_CREATES_FRESH_STAGE_ONLY')
        stage = plain(Path(tempfile.gettempdir()) / ('phantom092-' + uuid.uuid4().hex))
        if stage.is_relative_to(module.parent):
            raise ValueError('TASK092_EXTERNAL_STAGE_REQUIRED')
        stage.mkdir(exist_ok=False)
        files = {}
        for relative in inputs(module):
            source, target = module / relative, stage / 'source' / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            files[relative] = sha(source)
            if sha(target) != files[relative]:
                raise ValueError('TASK092_COPY_MISMATCH')
        protected = {relative: sha(plain(module / relative)) for relative in (
            'dist/libs/GameServer.jar', 'dist/libs/LoginServer.jar')}
        (stage / 'inputs.json').write_text(json.dumps(dict(module=str(module), files=files, protected=protected),
                                                  indent=2), encoding='utf-8')
        verify(module, stage)
        print('STAGE092=' + str(stage))
        return
    if len(sys.argv) != 4:
        raise ValueError('TASK092_EXISTING_STAGE_REQUIRED')
    stage = plain(Path(sys.argv[3]))
    if mode == 'refresh-tests':
        manifest = verify(module, stage, refresh=True)
        sealed = stage / 'outputs.json'
        if sealed.exists():
            previous = json.loads(sealed.read_text(encoding='utf-8'))
            if previous['inputs'] != sha(stage / 'inputs.json') or previous['buildLog'] != sha(stage / 'build.log') or previous['files'] != outputs(stage):
                raise ValueError('TASK092_PRE_CORRECTION_BUILD_DRIFT')
        backup = stage / ('test-correction-' + uuid.uuid4().hex)
        backup.mkdir(exist_ok=False)
        shutil.copyfile(stage / 'inputs.json', backup / 'inputs.json')
        for relative in OWN:
            source, target = module / relative, stage / 'source' / relative
            if sha(source) != manifest['files'][relative]:
                saved = backup / Path(relative).name
                shutil.copyfile(target, saved)
                if sha(saved) != manifest['files'][relative]:
                    raise ValueError('TASK092_BACKUP_MISMATCH')
                shutil.copyfile(source, target)
                manifest['files'][relative] = sha(source)
                if sha(target) != manifest['files'][relative]:
                    raise ValueError('TASK092_REFRESH_MISMATCH')
        (stage / 'inputs.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
        if sealed.exists():
            sealed.rename(backup / 'outputs-before-correction.json')
        elif (stage / 'build.log').exists():
            (stage / 'build.log').rename(backup / 'build-failed.log')
        verify(module, stage)
        return
    manifest = verify(module, stage)
    if mode == 'verify':
        return
    if mode not in ('contracts', 'required-product', 'regression', 'seal-build'):
        raise ValueError('TASK092_DB_FREE_MODE_ONLY')
    if 'BUILD SUCCESSFUL' not in (stage / 'build.log').read_text(encoding='utf-8-sig'):
        raise ValueError('TASK092_SUCCESSFUL_BUILD_REQUIRED')
    if mode == 'seal-build':
        with (stage / 'outputs.json').open('x', encoding='utf-8') as output:
            json.dump(dict(inputs=sha(stage / 'inputs.json'), buildLog=sha(stage / 'build.log'), files=outputs(stage)), output, indent=2)
        print('TASK092_BUILD_SEALED')
        return
    sealed = json.loads((stage / 'outputs.json').read_text(encoding='utf-8'))
    if sealed['inputs'] != sha(stage / 'inputs.json') or sealed['buildLog'] != sha(stage / 'build.log') or sealed['files'] != outputs(stage):
        raise ValueError('TASK092_BUILD_RECEIPT_DRIFT')
    classpath = [stage / 'build/bin', stage / 'build/phantom-test/bin', stage / 'build/phantom-test/resources']
    classpath += [stage / 'source' / relative for relative in sorted(manifest['files'])
                  if relative.startswith('dist/libs/') and relative.endswith('.jar')]
    classpath = [path for path in classpath if path.name not in ('GameServer.jar', 'LoginServer.jar')]
    classes = ['org.l2jmobius.gameserver.phantoms.knowledge.PhantomOffscreen092Checks'] if mode != 'regression' else [
        'org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks']
    java = shutil.which('java')
    if not java:
        raise ValueError('TASK092_JAVA_REQUIRED')
    for classname in classes:
        arguments = [java, '-Xms64m', '-Xmx256m', '-Dfile.encoding=UTF-8', '-cp', os.pathsep.join(map(str, classpath)), classname]
        if mode != 'regression':
            arguments += [mode, str(stage / 'source')]
        log = stage / (mode + '-' + classname.rsplit('.', 1)[-1] + '.log')
        with log.open('w', encoding='utf-8') as output:
            completed = subprocess.run(arguments, cwd=stage / 'source/dist/game', stdout=output, stderr=subprocess.STDOUT,
                                       encoding='utf-8', timeout=60, check=False)
        print(log.read_text(encoding='utf-8'))
        print('TASK092_EXIT=' + str(completed.returncode))
        if completed.returncode:
            sys.exit(completed.returncode)


if __name__ == '__main__':
    main()
