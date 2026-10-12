"""Verify and select immutable TASK037 evidence without touching old runtimes."""
import hashlib
import json
from pathlib import Path
import zipfile

ARCHIVE = Path(r'C:\Users\ZBook\.codex\visualizations\2026\10\11\01a12bce-134e-7953-99d3-d5e3c3858467\TASK037-review.zip')
OUTPUT = Path(__file__).resolve().parents[1] / 'evidence' / 'INPUTS037'
SELECTED = ('receipts/final-report-data037.json', 'receipts/scene037c-tail-digest.json',
            'receipts/tail-native-geometry037.json', 'receipts/tailtravel037.json',
            'receipts/third-parity-failure037.json', 'tools/ops037/reuse-manifest037.json')

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def main():
    assert not OUTPUT.exists(), 'Immutable selected inputs already exist'
    assert ARCHIVE.stat().st_size == 113467888
    digest = sha(ARCHIVE)
    assert digest == '502aa769ddebc410e33003f14e825f0ef7a9561838188938af50509e26bb0509'
    rows = []
    with zipfile.ZipFile(ARCHIVE) as archive:
        assert len(archive.infolist()) == 24713
        for name in SELECTED:
            data = archive.read(name)
            target = OUTPUT / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            assert sha(target) == hashlib.sha256(data).hexdigest()
            json.loads(data.decode('utf-8-sig'))
            rows.append({'entry': name, 'bytes': len(data), 'sha256': sha(target)})
    receipt = {'archive': str(ARCHIVE), 'archiveSha256': digest, 'entries': 24713,
               'selected': rows, 'historical357': 'UNKNOWN', 'historical253': 'UNKNOWN',
               'historical566': 'NOT_VERIFIED_UNKNOWN_PRODUCER'}
    (OUTPUT / 'receipt038.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(receipt, indent=2))

if __name__ == '__main__':
    main()
