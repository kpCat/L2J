"""Split one read-only consistent MariaDB batch into the existing export artifacts."""
import sys
from pathlib import Path
root = Path(sys.argv[1]).resolve()
allowed = {'characters','items','skills','counts','durable-hex'}
chunks = {}
current = None
for line in (root/'consistent-sql039.tsv').read_text(encoding='utf-8-sig').splitlines():
    if line == 'section039':
        current = None
    elif line.startswith('SECTION039_'):
        current = line.removeprefix('SECTION039_')
        if current not in allowed or current in chunks: raise ValueError('Unexpected SQL section')
        chunks[current] = []
    elif current:
        chunks[current].append(line)
if set(chunks) != allowed: raise ValueError('Incomplete consistent snapshot')
for name, lines in chunks.items():
    if name == 'durable-hex': lines = lines[1:]
    with (root/(name+'.tsv')).open('x',encoding='utf-8',newline='\n') as out: out.write('\n'.join(lines)+'\n')
print('CONSISTENT_READ_ONLY_SNAPSHOT039 sections',len(chunks))
