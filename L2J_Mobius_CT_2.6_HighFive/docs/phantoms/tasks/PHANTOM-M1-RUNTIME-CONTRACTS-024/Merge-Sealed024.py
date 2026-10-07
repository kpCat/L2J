from pathlib import Path
import argparse,hashlib,json
p=argparse.ArgumentParser();p.add_argument('scene',type=Path);a=p.parse_args()
dest=a.scene/'all-sealed'
if dest.exists():raise ValueError('No overwrite')
dest.mkdir();manifest=[]
for parent in ('sealed','final-sealed'):
    for source in sorted((a.scene/parent).glob('*.properties')):
        data=source.read_bytes();target=dest/source.name
        if target.exists() and target.read_bytes()!=data:raise ValueError('Conflicting immutable receipt identity')
        if not target.exists():target.write_bytes(data)
        manifest.append(dict(source=parent+'/'+source.name,sha256=hashlib.sha256(data).hexdigest()))
(dest/'sources.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print('Immutable receipt union: '+str(len(manifest)))
