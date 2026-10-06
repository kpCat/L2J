"""Compile only the six exact permitted base blobs into a private precedence classpath."""
from pathlib import Path
import subprocess
import sys

module = Path(sys.argv[1]).resolve()
root = module.parent
base = '0205d04bc7763fafcbb776e6da8887c1f2912d8b'
paths = [
    'java/org/l2jmobius/gameserver/model/actor/PlayerNativeEvidence.java',
    'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
    'java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java',
    'java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java',
    'java/org/l2jmobius/gameserver/taskmanagers/AutoPlayTaskManager.java',
    'java/org/l2jmobius/gameserver/taskmanagers/AutoUseTaskManager.java',
]
private = module / '.phantom-local/base-probe022'
sources = []
for relative in paths:
    target = private / 'source' / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(subprocess.check_output(['git', '-C', str(root), 'show', base + ':L2J_Mobius_CT_2.6_HighFive/' + relative]))
    sources.append(str(target))
output = private / 'bin'
output.mkdir(parents=True, exist_ok=True)
classpath = str(root / 'build/bin') + ';' + str(module / 'dist/libs/*')
subprocess.run(['C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/javac.exe', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(output), *sources], check=True)
print('EXACT_BASE_PROBE', base, 'six existing source blobs; no source restore or live patch')
