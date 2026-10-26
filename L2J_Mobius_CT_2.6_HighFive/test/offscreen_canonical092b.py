"""One TASK092B campaign; reuse the 091 physical inventory, process receipts and SQL guard."""
import json
import os
from pathlib import Path
import shutil
import sys
import uuid
from datetime import datetime, timedelta, timezone
from interregional_travel091 import inventory, plain, digest, command, require


def main(mode, module, stage, compiled=None):
    module, stage = Path(module), Path(stage)
    plain(module)
    plain(stage)
    require(stage.name.startswith('phantom092b-') and not stage.is_relative_to(module.parent), '092B_EXTERNAL_STAGE')
    shadow = stage / 'source'
    manifest = stage / 'inputs092b.json'
    if mode == 'prepare':
        require(not shadow.exists() and not manifest.exists(), '092B_PREPARE_ONCE')
        files = inventory(module)
        for generated in ('dist/libs/GameServer.jar', 'dist/libs/LoginServer.jar'):
            files.pop(generated, None)
        files['dist/game/config/Server.ini'] = module / 'dist/game/config/Server.ini'
        rows = {}
        for name, source in files.items():
            target = shadow / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            rows[name] = digest(source)
            require(digest(target) == rows[name], '092B_COPY_SHA')
        geo = Path(module.anchor) / 'PhantomWorld/Living-Economy-068-candidate/game/data/geodata'
        plain(geo)
        # The native environment needs the same lawful Geo inputs, with no change to their owner.
        tiles = sorted(geo.glob('*.l2j'))
        require(len(tiles) == 203, '092B_GEO203')
        geohashes = {}
        for source in tiles:
            plain(source)
            target = shadow / 'dist/game/data/geodata' / source.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            geohashes[source.name] = digest(source)
            require(digest(target) == geohashes[source.name], '092B_GEO_SHA')
        owner_id = str(uuid.uuid4())
        data = dict(module=str(module), stage=str(stage), owner=owner_id, files=rows, geoSource=str(geo), geo=geohashes)
        manifest.write_text(json.dumps(data, indent=2), encoding='utf-8')
        lines = [f'{module / name}\t{shadow / name}\t{sha}' for name, sha in sorted(rows.items())]
        lines += [f'{geo / name}\t{shadow / "dist/game/data/geodata" / name}\t{sha}' for name, sha in sorted(geohashes.items())]
        (stage / 'inputs.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')
        private = stage / 'private'
        private.mkdir(exist_ok=False)
        owner = dict(owner='TASK092B_NATIVE_TRAVEL', schema='l2jmobiush5_phantom_test_092b',
                     baseline='ac5931941f36fbdbb1a64e8d848b5fb57853b74c', installationId=owner_id,
                     sourceModule=module.as_posix(), shadowModule=shadow.as_posix(), stage=stage.as_posix(), geoSource=geo.as_posix(),
                     expiresUtc=(datetime.now(timezone.utc) + timedelta(hours=2)).strftime('%Y-%m-%dT%H:%M:%SZ'),
                     inputsSha256=digest(stage / 'inputs.tsv'), sourceDatabaseSha256=digest(module / 'dist/game/config/Database.ini'))
        (private / 'owner.properties').write_text(''.join(f'{key}={value}\n' for key, value in owner.items()), encoding='utf-8')
        print(f'092B_PREPARED files={len(rows)} Geo=203 DDL=0 DML=0', flush=True)
        return 0
    data = json.loads(manifest.read_text(encoding='utf-8'))
    require(data['module'] == str(module) and data['stage'] == str(stage), '092B_OWNER_ROOT')
    second_resume = mode in ('resume2-preflight', 'verified2', 'restart2', 'connections2')
    final_closure = mode in ('final-preflight', 'final-travel', 'final-restart', 'final-connections')
    if compiled is not None:
        compiled = Path(compiled)
        plain(compiled)
        require(compiled.name.startswith('phantom092b-recovery-') and not compiled.is_relative_to(module.parent)
                and mode in ('focused', 'resume-preflight', 'verified', 'restart', 'connections',
                             'resume2-preflight', 'verified2', 'restart2', 'connections2',
                             'final-preflight', 'final-travel', 'final-restart', 'final-connections'), '092B_RECOVERY_OUTPUT')
        recovered = json.loads((compiled / ('final-outputs092b.json' if final_closure else 'resume2-outputs092b.json' if second_resume else 'resume-outputs092b.json')).read_text(encoding='utf-8'))
        require(recovered['owner'] == data['owner'] and recovered['stage'] == str(stage)
                and recovered['module'] == str(module), '092B_RECOVERY_OWNER')
        for name, sha in recovered['files'].items():
            file = compiled / name
            plain(file)
            require(file.is_relative_to(compiled) and digest(file) == sha, '092B_RECOVERY_BUILD_DRIFT')
        for name, sha in recovered['sources'].items():
            require(digest(module / name) == sha, '092B_RECOVERY_SOURCE_DRIFT_' + name)
    # Every launch rechecks source and physical shadow; build outputs and private config are separate.
    for name, sha in data['files'].items():
        require(digest(module / name) == sha and digest(shadow / name) == sha, '092B_INPUT_DRIFT_' + name)
    seal = stage / 'outputs092b.json'
    if mode != 'build':
        for name, sha in json.loads(seal.read_text(encoding='utf-8')).items():
            require(digest(stage / name) == sha, '092B_BUILD_DRIFT')
    if mode == 'build':
        ant = shutil.which('ant')
        require(ant is not None and not (stage / 'build.log').exists(), '092B_BUILD_ONCE')
        code = command(['cmd.exe', '/d', '/c', ant, '-f', str(shadow / 'build.xml'), '-Dbuild=' + str(stage / 'build'), 'jar'], shadow, stage / 'build.log')
        if code:
            return code
        sources = sorted((shadow / 'test/java').rglob('*.java'))
        argsfile = stage / 'test-sources.args'
        argsfile.write_text('\n'.join('"' + path.as_posix() + '"' for path in sources), encoding='utf-8')
        code = command(['javac', '-encoding', 'UTF-8', '-source', '25', '-target', '25', '-cp', classpath(stage, shadow),
                        '-d', str(stage / 'tests'), '@' + str(argsfile)], shadow, stage / 'test-compile.log')
        if code == 0:
            outputs = [*sorted((stage / 'build/bin').rglob('*.class')), *sorted((stage / 'tests').rglob('*.class')),
                       *sorted((stage / 'build/dist/libs').glob('*.jar'))]
            seal.write_text(json.dumps({str(path.relative_to(stage)): digest(path) for path in outputs}), encoding='utf-8')
        return code
    cp = classpath(stage, shadow, compiled, second_resume, final_closure)
    args = ['java', '-Xms128m', '-Xmx4g', '-Dfile.encoding=UTF-8', '-cp', cp]
    if mode == 'focused':
        return command(args + ['org.l2jmobius.tests.phantoms.PhantomOffscreen092BChecks', str(shadow)], shadow / 'dist/game', stage / 'focused.log')
    require(mode in ('configure', 'bootstrap', 'native', 'continue', 'verified', 'restart', 'connections', 'resume-preflight',
                     'resume2-preflight', 'verified2', 'restart2', 'connections2',
                     'final-preflight', 'final-travel', 'final-restart', 'final-connections'), '092B_MODE')
    require(mode not in ('resume-preflight', 'resume2-preflight', 'verified2', 'restart2', 'connections2') and not final_closure or compiled is not None, '092B_RESUME_COMPILED_REQUIRED')
    args[1:1] = ['-Dphantom.task092b.native=true', '-Dphantom.task092b.root=' + str(stage),
                  '-Dphantom.task092b.uuid=' + data['owner'], '-Dphantom.module.root=' + str(shadow),
                  '-Dphantom.test.config=' + str(shadow / 'dist/game/config/Database.ini')]
    return command(args + ['org.l2jmobius.tests.phantoms.PhantomOffscreen092BChecks', mode, str(module)], shadow / 'dist/game', stage / (mode + '.log'))


def classpath(stage, shadow, compiled=None, second_resume=False, final_closure=False):
    jars = [path for path in (shadow / 'dist/libs').glob('*.jar')
            if path.name not in ('GameServer.jar', 'LoginServer.jar') and not path.name.endswith('-sources.jar')]
    classes = [stage / 'build/bin', stage / 'tests'] if compiled is None else [compiled / 'build/bin', compiled / 'tests', stage / 'tests']
    if second_resume:
        classes.insert(1, compiled / 'resume2-tests')
    if final_closure:
        classes = [compiled / 'closure-build/bin', compiled / 'closure-tests', stage / 'tests']
    return os.pathsep.join(map(str, [*classes, shadow / 'test/resources', *jars]))


if __name__ == '__main__':
    sys.exit(main(*sys.argv[1:]))
