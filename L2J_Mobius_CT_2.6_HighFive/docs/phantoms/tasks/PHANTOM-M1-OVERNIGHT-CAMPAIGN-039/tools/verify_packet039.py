"""Проверка неизменности task-пакета; только чтение файлов."""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path, PurePosixPath
import re


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--strict', action='store_true', help='Отказать при лишнем файле в исходном пакете')
    args=parser.parse_args()
    root=args.root.resolve(strict=True)
    manifest=root/'MANIFEST.sha256'
    listed=set()
    for line in manifest.read_text(encoding='utf-8').splitlines():
        digest, name=line.split('  ', 1)
        relative=PurePosixPath(name)
        if (not re.fullmatch(r'[0-9a-f]{64}',digest) or '\\' in name
                or relative.is_absolute() or any(p in ('..','.') for p in relative.parts)
                or name in listed or name=='MANIFEST.sha256'):
            raise SystemExit(f'INVALID_MANIFEST: {name}')
        path=root.joinpath(*relative.parts)
        if not path.is_file() or path.is_symlink() or not path.resolve().is_relative_to(root):
            raise SystemExit(f'UNSAFE_OR_MISSING: {name}')
        for parent in path.parents:
            if parent==root: break
            if parent.is_symlink(): raise SystemExit(f'SYMLINK: {name}')
        if hashlib.sha256(path.read_bytes()).hexdigest()!=digest:
            raise SystemExit(f'HASH_MISMATCH: {name}')
        listed.add(name)
    if args.strict:
        actual={p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file()
                and '__pycache__' not in p.parts and p.name!='MANIFEST.sha256'}
        if actual!=listed:
            raise SystemExit(f'EXTRA_OR_MISSING: {sorted(actual ^ listed)}')
    print(f'PACKET039 PASS: {len(listed)} SHA-256 entries')
    return 0


if __name__=='__main__':
    raise SystemExit(main())
