"""Package exact selected TASK041 source/results/raw inputs; never infer gate verdicts."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--module', required=True)
    parser.add_argument('--plan', required=True)
    args = parser.parse_args()
    module = Path(args.module).resolve(strict=True)
    task = module / 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041'
    output = task / 'TASK041-review.zip'
    if output.exists():
        raise ValueError('Existing review is immutable; no overwrite.')
    plan = json.loads(Path(args.plan).read_text(encoding='utf-8-sig'))
    entries = {}
    for item in plan['items']:
        source = Path(item['source']).resolve(strict=True)
        if not source.is_relative_to(module):
            raise ValueError('Source outside exact HighFive module: ' + str(source))
        destination = PurePosixPath(item['destination'])
        if destination.is_absolute() or '..' in destination.parts or not destination.parts:
            raise ValueError('Unsafe archive path.')
        paths = sorted(source.rglob('*')) if source.is_dir() else [source]
        for path in paths:
            if not path.is_file() or '__pycache__' in path.parts:
                continue
            actual = path.resolve(strict=True)
            if not actual.is_relative_to(module) or path.is_symlink():
                raise ValueError('Linked source outside exact module.')
            name = str(destination / path.relative_to(source)) if source.is_dir() else str(destination)
            if name in entries:
                raise ValueError('Duplicate archive entry: ' + name)
            entries[name] = (actual, actual.stat().st_size, sha256(actual))
    # This is a transport manifest, not another acceptance checker.
    manifest = ''.join(f'{entry[2]}  {name}\n' for name, entry in sorted(entries.items()))
    with zipfile.ZipFile(output, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, (path, size, digest) in sorted(entries.items()):
            archive.write(path, 'TASK041-review/' + name)
        archive.writestr('TASK041-review/MANIFEST.sha256', manifest.encode('utf-8'))
    with zipfile.ZipFile(output) as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError('ZIP CRC failure: ' + bad)
        for name, (path, size, digest) in entries.items():
            with archive.open('TASK041-review/' + name) as stream:
                check = hashlib.sha256()
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    check.update(chunk)
            if check.hexdigest() != digest:
                raise ValueError('Archived source bytes differ: ' + name)
    print(json.dumps({'archive': str(output), 'entries': len(entries) + 1,
                      'bytes': output.stat().st_size, 'sha256': sha256(output),
                      'allEntryBytesVerified': True}, ensure_ascii=False))


if __name__ == '__main__':
    main()
