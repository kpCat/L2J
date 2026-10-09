"""Publish bounded evidence with explicit privacy derivatives, preserving local raw inputs."""
import hashlib
import json
from pathlib import Path
import re
import zipfile

TASK = Path(__file__).resolve().parents[1]
RAW = TASK / 'evidence'
ARCHIVES = TASK / 'archives'
ALLOWED = {'.json', '.jsonl', '.tsv', '.csv', '.properties', '.request', '.xml', '.log', '.txt', '.md', '.py', '.java', '.ps1'}
PRIVATE_ACCOUNT = re.compile(rb'kpcat|jvtygr', re.IGNORECASE)
SECRET = re.compile(rb'(?im)^\s*(?:password|passwd|jdbcpassword)\s*[=:]\s*\S+')


def main():
    ARCHIVES.mkdir(exist_ok=True)
    output = ARCHIVES / 'RAW_EVIDENCE_028.zip'
    if output.exists():
        raise ValueError('Immutable evidence archive exists')
    sources = []
    for path in sorted(RAW.rglob('*')):
        if not path.is_file():
            continue
        if path.suffix.lower() not in ALLOWED or 'secrets' in path.parts or path.is_symlink():
            raise ValueError(f'Unapproved evidence artifact: {path.relative_to(TASK)}')
        original = path.read_bytes()
        published, derivative = original, None
        if path.name == 'runtime-manifest.json':
            document = json.loads(original.decode('utf-8-sig'))
            if 'account' in document:
                del document['account']
                published = (json.dumps(document, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
                derivative = 'Removed account field only; immutable original remains local/private.'
        if PRIVATE_ACCOUNT.search(published) or SECRET.search(published):
            raise ValueError(f'Privacy review required: {path.relative_to(TASK)}')
        sources.append((path, published, {'path': path.relative_to(TASK).as_posix(),
                                        'originalBytes': len(original), 'publishedBytes': len(published),
                                        'originalSha256': hashlib.sha256(original).hexdigest(),
                                        'publishedSha256': hashlib.sha256(published).hexdigest(),
                                        'privacyDerivative': derivative}))
    temporary = output.with_suffix('.zip.tmp')
    with zipfile.ZipFile(temporary, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path, published, row in sources:
            archive.writestr('PHANTOM-M1-LIVING-CONTINUITY-028/' + row['path'], published)
    if temporary.stat().st_size >= 95_000_000:
        raise ValueError('Archive exceeds bounded publication size; original temp retained')
    with zipfile.ZipFile(temporary) as archive:
        if archive.testzip() is not None:
            raise ValueError('Archive integrity failure')
        for path, published, row in sources:
            restored = archive.read('PHANTOM-M1-LIVING-CONTINUITY-028/' + row['path'])
            if hashlib.sha256(restored).hexdigest() != row['publishedSha256']:
                raise ValueError('Archive content hash differs')
    temporary.rename(output)
    manifest = {'kind': 'TASK028_IMMUTABLE_EVIDENCE_PUBLICATION', 'files': [r for _, _, r in sources],
                'archive': output.name, 'bytes': output.stat().st_size,
                'sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
                'rawLocalFilesModified': False, 'privacyAccountValuesPublished': False,
                'credentialsJarsGeodataFullDatabaseDumpsJfrPublished': False}
    with (ARCHIVES / 'ARCHIVE_MANIFEST.json').open('x', encoding='utf-8') as stream:
        json.dump(manifest, stream, ensure_ascii=False, indent=2); stream.write('\n')
    print(json.dumps({'files': len(sources), 'archiveBytes': manifest['bytes'], 'sha256': manifest['sha256'],
                      'privacyDerivatives': sum(r['privacyDerivative'] is not None for _, _, r in sources)}))


if __name__ == '__main__':
    main()
