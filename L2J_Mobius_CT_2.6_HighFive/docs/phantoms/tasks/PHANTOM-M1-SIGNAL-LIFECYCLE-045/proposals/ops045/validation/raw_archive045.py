"""Read closed collector ZIP batches; unchanged bytes feed the existing Java codecs.

Only offline scratch decoding creates individual files. Gameplay capture keeps
closed compressed batches and the original ledger; neither is rewritten.
"""
from pathlib import Path
import hashlib
import zipfile
import functools
import json

OPS = Path(__file__).resolve().parents[1]

@functools.lru_cache(maxsize=16)
def archive_entries(path, expected_sha):
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_sha:
        raise ValueError('Closed archive SHA mismatch: ' + str(path))
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) > 512 or len(names) != len(set(names)):
            raise ValueError('Archive batch cardinality/conflict')
        if any(Path(name).name != name or '/' in name or '\\' in name for name in names):
            raise ValueError('Archive entry escaped its batch')
        if sum(item.file_size for item in archive.infolist()) > 16 * 1024 * 1024:
            raise ValueError('Existing ring-byte budget exceeded')
        if names == ['records.jsonl']:
            records = archive.read('records.jsonl').decode('utf-8').splitlines()
            if not 1 <= len(records) <= 512:
                raise ValueError('Batch record cardinality')
            result, total = {}, 0
            for line in records:
                record = json.loads(line)
                if set(record) != {'file', 'text'} or not all(isinstance(value, str) for value in record.values()):
                    raise ValueError('Exact batch record schema required')
                name = record['file']
                if Path(name).name != name or '/' in name or '\\' in name or 'record:' + name in result:
                    raise ValueError('Batch logical filename conflict/escape')
                payload = record['text'].encode('utf-8')
                total += len(payload)
                if len(payload) > 2 * 1024 * 1024 or total > 16 * 1024 * 1024:
                    raise ValueError('Existing record/ring-byte budget exceeded')
                result['record:' + name] = payload
            return result
        return {name: archive.read(name) for name in names}

def raw_path(root, row):
    root = root.resolve()
    name = row['raw_file']
    if Path(name).name != name or '/' in name or '\\' in name:
        raise ValueError('Raw filename escaped capture')
    if 'raw_archive' not in row:
        return root / name
    archive_name, entry = row['raw_archive'], row['raw_entry']
    if row.get('raw_format') == 'BATCH_PROPERTIES_JSONL045' and entry != 'record:' + name:
        raise ValueError('Exact logical record identity required')
    if Path(archive_name).name != archive_name or not archive_name.startswith('witnesses-') or not archive_name.endswith('.zip'):
        raise ValueError('Exact closed batch required')
    digest = row['raw_archive_sha256']
    if len(digest) != 64 or any(c not in '0123456789abcdef' for c in digest):
        raise ValueError('Archive SHA required')
    payload = archive_entries(root / archive_name, digest)[entry]
    if hashlib.sha256(payload).hexdigest() != row['raw_sha256']:
        raise ValueError('Original raw bytes changed')
    target = OPS / 'decoded045' / digest / name
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        if target.read_bytes() != payload: raise ValueError('Conflicting offline cache')
    else:
        with target.open('xb') as stream: stream.write(payload)
    # Existing rare async SQL companion remains an original, separately pinned capture.
    sql = root / (name + '.sql.tsv')
    if sql.exists():
        companion = target.with_name(target.name + '.sql.tsv')
        data = sql.read_bytes()
        if companion.exists():
            if companion.read_bytes() != data: raise ValueError('Conflicting original SQL companion')
        else:
            with companion.open('xb') as stream: stream.write(data)
    return target
