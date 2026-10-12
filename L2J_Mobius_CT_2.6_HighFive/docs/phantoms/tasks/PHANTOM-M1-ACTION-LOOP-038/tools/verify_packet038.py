#!/usr/bin/env python3
"""Check this task packet; optionally compile/run the isolated contract proposal."""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--test-proposal', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    manifest = root / 'MANIFEST.sha256'
    seen = set()
    for line in manifest.read_text(encoding='utf-8').splitlines():
        digest, relative = line.split('  ', 1)
        file = root / relative
        if len(digest) != 64 or relative in seen or Path(relative).is_absolute() or '..' in Path(relative).parts:
            raise ValueError(f'Invalid manifest entry: {relative}')
        seen.add(relative)
        if not file.is_file() or file.is_symlink() or not file.resolve().is_relative_to(root):
            raise ValueError(f'Missing or unsafe file: {relative}')
        actual = hashlib.sha256(file.read_bytes()).hexdigest()
        if actual != digest:
            raise ValueError(f'Hash mismatch: {relative}')
    print(f'MANIFEST: {len(seen)}/{len(seen)} PASS')
    if args.test_proposal:
        javac, java = shutil.which('javac'), shutil.which('java')
        if not javac or not java:
            raise RuntimeError('JDK not available; proposal test NOT_RUN')
        with tempfile.TemporaryDirectory(prefix='task038-contract-') as output:
            sources = [root / 'proposals' / name for name in ('ActionContinuation038.java', 'ActionContinuation038Checks.java')]
            subprocess.run([javac, '-encoding', 'UTF-8', '-d', output, *map(str, sources)], check=True, timeout=40)
            subprocess.run([java, '-cp', output, 'ActionContinuation038Checks'], check=True, timeout=15)
    print('GAME_SERVER_VERIFICATION=NOT_RUN_BY_PACKET_TOOL')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
