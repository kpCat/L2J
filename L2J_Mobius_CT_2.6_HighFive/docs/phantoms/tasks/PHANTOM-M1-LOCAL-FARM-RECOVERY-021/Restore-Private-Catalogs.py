"""Preserve pinned Git blob bytes in the private runtime; checkout CRLF is not source change."""
import hashlib
import subprocess
import sys
from pathlib import Path

TASK = Path(__file__).resolve().parent
ROOT = TASK.parents[4]
runtime = Path(sys.argv[1]).resolve()
expected = ROOT / 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/observe021/runtime'
if runtime != expected:
    raise SystemExit('Unexpected private runtime destination')
for name in ('high-five-population-v1.xml', 'high-five-population-v2.xml', 'high-five-ecology-v1.xml'):
    relative = f'game/data/phantoms/population/{name}'
    target = runtime / relative
    blob = subprocess.check_output(['git', 'show', 'HEAD:L2J_Mobius_CT_2.6_HighFive/dist/' + relative], cwd=ROOT)
    if target.read_bytes().replace(b'\r\n', b'\n') != blob.replace(b'\r\n', b'\n'):
        raise SystemExit('Catalog has a semantic difference: ' + relative)
    target.write_bytes(blob)
    print('PINNED_PRIVATE_BLOB', relative, hashlib.sha256(blob).hexdigest())
