"""Transport the complete immutable C8 inputs in one review member, without truncation."""
import argparse
import hashlib
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--module', required=True)
args = parser.parse_args()
module = Path(args.module).resolve(strict=True)
evidence = module / 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/evidence'
destination = module / '.phantom-local/ops041/input-bundles041/C8-complete-inputs.zip'
destination.parent.mkdir(exist_ok=True)
roots = [(evidence / 'STARTUP032a-4380ed4d269f437eb2678e75d171d334', 'startup'),
         (evidence / 'C8_GROUP_A2_041', 'scene'),
         (evidence / 'C8_ALL_BIRTH_SQL041', 'stopped-sql'),
         (module / '.phantom-local/contract041a/runtime/libs', 'candidate924-libs')]
pins = {}
with zipfile.ZipFile(destination, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
    for root, prefix in roots:
        root.resolve(strict=True)
        for path in sorted(root.rglob('*')):
            if not path.is_file():
                continue
            if not path.resolve(strict=True).is_relative_to(module) or path.is_symlink():
                raise ValueError('Linked input outside own HighFive.')
            if prefix == 'candidate924-libs' and path.suffix != '.jar':
                continue
            name = prefix + '/' + path.relative_to(root).as_posix()
            pins[name] = hashlib.sha256(path.read_bytes()).hexdigest()
            archive.write(path, name)
    archive.writestr('MANIFEST.sha256', ''.join(f'{digest}  {name}\n' for name, digest in pins.items()))
with zipfile.ZipFile(destination) as archive:
    if archive.testzip():
        raise ValueError('Transport ZIP CRC failure.')
    for name, digest in pins.items():
        if hashlib.sha256(archive.read(name)).hexdigest() != digest:
            raise ValueError('Input bytes differ: ' + name)
print(f'C8_INPUT_BUNDLE entries={len(pins)+1} bytes={destination.stat().st_size} allOriginalBytesVerified=true')
