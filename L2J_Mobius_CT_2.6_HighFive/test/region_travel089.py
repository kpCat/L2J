"""Exact inventory and byte fence for the private, DB-free TASK089 source shadow."""
import hashlib
import json
import os
import sys
import uuid
from pathlib import Path

FAMILIES = ('build.xml', 'java', 'test', 'tools', 'dist/game', 'dist/libs',
            'dist/db_installer/sql', 'docs/phantoms/live-world')
OWN = ('java/org/l2jmobius/gameserver/phantoms/navigation/PhantomWalkCorridorPlanner.java',
       'java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java',
       'java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java',
       'test/java/org/l2jmobius/tests/phantoms/PhantomTravel089Test.java',
       'test/java/org/l2jmobius/tests/phantoms/PhantomTravel089Geo.java',
       'tools/phantom-runtime/Task089.ps1', 'test/region_travel089.py')
HARNESS = OWN[-2:]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def plain(path):
    if not path.is_absolute() or path.resolve() != path or path.is_symlink():
        raise ValueError('TASK089_PHYSICAL_PATH')
    if path.stat().st_file_attributes & 0x400:
        raise ValueError('TASK089_REPARSE')


def inventory(root):
    found = set()
    for family in FAMILIES:
        start = root / family
        plain(start)
        if start.is_file():
            found.add(start)
            continue
        for directory, dirs, files in os.walk(start, followlinks=False):
            for name in dirs + files:
                plain(Path(directory) / name)
            found.update(Path(directory) / name for name in files)
    found.discard(root / 'dist/game/config/Database.ini')
    return found


def build_inputs(rows, module):
    lines = [f'{Path(source).relative_to(module).as_posix()}\t{sha}'
             for source, _, sha in rows if Path(source).is_relative_to(module)
             and Path(source).relative_to(module).as_posix() not in HARNESS]
    return hashlib.sha256('\n'.join(sorted(lines)).encode('utf-8')).hexdigest()


def build_outputs(stage):
    lines = []
    for directory in ('build/bin', 'build/phantom-test/bin', 'build/phantom-test/resources'):
        for path in sorted((stage / directory).rglob('*')):
            plain(path)
            if path.is_file():
                lines.append(f'{path.relative_to(stage).as_posix()}\t{digest(path)}')
    if not lines:
        raise ValueError('TASK089_NO_COMPILED_OUTPUT')
    return hashlib.sha256('\n'.join(lines).encode('utf-8')).hexdigest()


def compilation(stage, rows, module):
    if not (stage / 'compile.started').is_file() or 'BUILD SUCCESSFUL' not in (stage / 'compile.log').read_text(encoding='utf-8-sig'):
        raise ValueError('TASK089_NO_SUCCESSFUL_COMPILE')
    return dict(inputPin=digest(stage / 'input-pin.json'), inputs=build_inputs(rows, module),
                outputs=build_outputs(stage), owner=digest(stage / 'owner.txt'))


def run(mode, module, stage):
    if mode not in ('verify', 'verify_execution', 'refresh', 'refresh_harness', 'seal_build'):
        raise ValueError('TASK089_ACTION')
    module, stage = Path(module), Path(stage)
    plain(module)
    plain(stage)
    copy = stage / 'source'
    plain(copy)
    if stage.is_relative_to(module.parent) or not stage.name.startswith('phantom089-'):
        raise ValueError('TASK089_EXTERNAL_STAGE')
    manifest = stage / 'inputs.tsv'
    rows = [line.split('\t') for line in manifest.read_text(encoding='utf-8').splitlines()]
    if (stage / 'input-pin.json').exists():
        old_pin = json.loads((stage / 'input-pin.json').read_text(encoding='utf-8'))
        if digest(manifest) != old_pin['inputs']:
            raise ValueError('TASK089_PIN_DRIFT')
    geo = module.anchor and Path(module.anchor) / 'PhantomWorld/Living-Economy-068-candidate/game/data/geodata'
    plain(geo)
    known = {}
    for source, target, expected in rows:
        source, target = Path(source), Path(target)
        plain(source)
        plain(target)
        allowed = source.is_relative_to(module) or source.parent == geo and source.suffix == '.l2j'
        relative = source.relative_to(module) if source.is_relative_to(module) else Path('dist/game/data/geodata') / source.name
        if not allowed or target != copy / relative or target in known or digest(target) != expected:
            raise ValueError('TASK089_INPUT_ESCAPE_OR_COPY_DRIFT')
        allowed_refresh = OWN if mode == 'refresh' else HARNESS if mode == 'refresh_harness' else ()
        if relative.as_posix() not in allowed_refresh:
            if digest(source) != expected:
                raise ValueError('TASK089_SOURCE_DRIFT: ' + relative.as_posix())
        known[target] = (source, expected)
    if mode in ('refresh', 'refresh_harness'):
        compiled = (stage / 'compile.started').exists()
        if mode == 'refresh' and compiled and not any(digest(module / name) != known[copy / name][1] for name in OWN[:3]):
            raise ValueError('TASK089_REPEAT_COMPILE_WITHOUT_PRODUCTION_CHANGE')
        previous_build = None
        if mode == 'refresh_harness':
            # Guard-only upgrade of an already successful build. Every other
            # input, including all Java/tests/data/libs, was checked above.
            previous_build = compilation(stage, rows, module)
            receipt = stage / 'compile.success.json'
            if receipt.exists() and json.loads(receipt.read_text(encoding='utf-8')) != previous_build:
                raise ValueError('TASK089_STALE_COMPILED_OUTPUT')
        backup = stage / ('pre-review-backup-' + uuid.uuid4().hex)
        backup.mkdir(exist_ok=False)
        if compiled and mode == 'refresh':
            for name in ('compile.started', 'compile.log', 'inputs.tsv', 'input-pin.json'):
                (backup / name).write_bytes((stage / name).read_bytes())
                if digest(backup / name) != digest(stage / name):
                    raise ValueError('TASK089_COMPILE_BACKUP_MISMATCH')
            (stage / 'compile.started').unlink()
            if (stage / 'compile.success.json').exists():
                (backup / 'compile.success.json').write_bytes((stage / 'compile.success.json').read_bytes())
                (stage / 'compile.success.json').unlink()
        for relative in OWN if mode == 'refresh' else HARNESS:
            source, target = module / relative, copy / relative
            plain(source)
            if target.exists():
                old = backup / relative
                old.parent.mkdir(parents=True, exist_ok=True)
                old.write_bytes(target.read_bytes())
                if digest(old) != digest(target):
                    raise ValueError('TASK089_BACKUP_MISMATCH')
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read_bytes())
            known[target] = (source, digest(source))
        (backup / 'inputs.tsv').write_bytes(manifest.read_bytes())
        manifest.write_text(''.join(f'{source}\t{target}\t{sha}\n' for target, (source, sha) in sorted(known.items())), encoding='utf-8')
        sys.path.insert(0, str(module / 'test'))
        import region_travel087_source_audit
        regenerated = backup / 'source-audit-recomputed'
        region_travel087_source_audit.run(module, regenerated)
        for name in ('source-provenance.json', 'ingress-gk-candidates.tsv'):
            if digest(regenerated / name) != digest(stage / 'source-audit' / name):
                raise ValueError('TASK089_SOURCE_AUDIT_NOT_REPRODUCIBLE')
        provenance = stage / 'source-audit/source-provenance.json'
        data = json.loads(provenance.read_text(encoding='utf-8'))
        if digest(stage / 'source-audit/ingress-gk-candidates.tsv') != data['candidates_sha256']:
            raise ValueError('TASK089_CANDIDATE_DRIFT')
        pin = {'inputs': digest(manifest), 'provenance': digest(provenance), 'candidates': data['candidates_sha256']}
        (stage / 'input-pin.json').write_text(json.dumps(pin, sort_keys=True), encoding='utf-8')
        if previous_build is not None:
            new_rows = [line.split('\t') for line in manifest.read_text(encoding='utf-8').splitlines()]
            if build_inputs(new_rows, module) != previous_build['inputs'] or build_outputs(stage) != previous_build['outputs']:
                raise ValueError('TASK089_HARNESS_CHANGED_BUILD')
            previous_build['inputPin'] = digest(stage / 'input-pin.json')
            (stage / 'compile.success.json').write_text(json.dumps(previous_build, sort_keys=True), encoding='utf-8')
    expected_copy = set(known)
    if inventory(copy) != expected_copy:
        raise ValueError('TASK089_COPY_INVENTORY')
    expected_source = {source for source, _ in known.values() if source.is_relative_to(module)}
    if inventory(module) != expected_source:
        raise ValueError('TASK089_SOURCE_INVENTORY')
    if len([source for source, _ in known.values() if source.parent == geo]) != 203:
        raise ValueError('TASK089_GEO203')
    pin = json.loads((stage / 'input-pin.json').read_text(encoding='utf-8'))
    for key, file in (('inputs', manifest), ('provenance', stage / 'source-audit/source-provenance.json'),
                      ('candidates', stage / 'source-audit/ingress-gk-candidates.tsv')):
        if digest(file) != pin[key]:
            raise ValueError('TASK089_PIN_DRIFT')
    if mode in ('seal_build', 'verify_execution'):
        expected = compilation(stage, rows, module)
        receipt = stage / 'compile.success.json'
        if mode == 'seal_build':
            receipt.write_text(json.dumps(expected, sort_keys=True), encoding='utf-8')
        elif not receipt.exists() or json.loads(receipt.read_text(encoding='utf-8')) != expected:
            raise ValueError('TASK089_COMPILE_PIN_MISMATCH')
    print(f'TASK089_EXACT_INPUTS={len(known)}; bytes/inventory/provenance=PASS; SQL=0')


if __name__ == '__main__':
    run(*sys.argv[1:])
