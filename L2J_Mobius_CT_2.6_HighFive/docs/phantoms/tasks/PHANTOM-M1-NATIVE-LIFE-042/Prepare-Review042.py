"""Exact bounded TASK042 inventory; no database/runtime mutation."""
from pathlib import Path
import hashlib
import json

task = Path(__file__).resolve().parent
repo = task.parents[4]
owner = repo / 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java'
paths = [owner]
paths += sorted(path for path in task.iterdir() if path.is_file() and path.suffix in ('.md', '.tsv', '.py', '.ps1'))
paths += sorted((task / 'proposals/ops042').iterdir())
names = (
    'CAUSE276042.json', 'C24_REUSE042.json', 'GROUPS042.json', 'LIFE042.json',
    'LIFE_B2_AI_FAILURE042.json', 'LIFE_B2_FAILURE042.json', 'CONSERVATION_INDEX042.json',
    'REGRESSIONS042.json', 'FINAL_OWNERSHIP042.json', 'GUARD_WrongManifest042.json',
    'GUARD_MixedOwners042.json', 'LIFE_INPUT_CONTRACT042.json', 'LIFE_PRIMARY_INPUT042.json',
    'CURRENT93_042-matrix.tsv', 'FRAME_SHARING042_V2/result.json', 'PREP_GIT042.jsonl',
    'A5_NATIVE_WORLD_INPUT_GROUP_A_042/continuity-v2.json',
    'A5_NATIVE_WORLD_INPUT_GROUP_A_042/baseline-cohort.json',
    'A5_NATIVE_WORLD_INPUT_GROUP_A_042/primary.json',
    'A5_NATIVE_WORLD_INPUT_GROUP_A_042/prospective-native-setup042.json',
    'A5_NATIVE_WORLD_INPUT_GROUP_A_042/pre-enrollment-calendar038.json',
    'A5_NATIVE_WORLD_INPUT_GROUP_A_042/capture-result.json',
    'A5_NATIVE_WORLD_INPUT_GROUP_A_042/stock-stop-verified.json',
    'A5_NATIVE_WORLD_INPUT_GROUP_A_042/stock-stop/typed-stop-done.properties',
    'A3_CALENDAR_WINDOW_GROUP_A_042_FAILURE_STOP/typed-stop-done.properties',
    'A4_READY_INPUT_GROUP_A_042_FAILURE_STOP/typed-stop-done.properties',
    'LIFE_B1_042-FALLBACK-STOP/typed-stop-done.properties',
    'LIFE_B2_RUNNER_FIXED042-FALLBACK-STOP/typed-stop-done.properties',
    'LIFE_B2_RUNNER_FIXED042/primary.json',
    'LIFE_B2_RUNNER_FIXED042/premeasurement-primary-rule042.json',
    'LIFE_B2_RUNNER_FIXED042/premeasurement-endpoint-eligibility042.json',
)
paths += [task / 'evidence' / name for name in names]
paths += sorted((task / 'evidence').glob('CURRENT93_042_*.provenance.json'))
paths += [task / 'evidence' / (name + '.provenance.json') for name in
          ('TESTLANE_RED042', 'TESTLANE_GREEN042', 'LIFECYCLE11_RUN042', 'SOFT_RETURN042',
           'DEBT_NEGATIVE042', 'DEBT90_RUN042', 'ACTION_LOOP042')]
paths += [task / name for name in ('REVIEW_ALLOWLIST042.txt', 'SOURCE_DIFF042.patch',
          'TASK042-review.zip', 'TASK042-review.sha256', 'ZIP_VERIFIED042.json',
          'PUBLICATION1_042.json', 'GIT_COMMANDS042.json')]
assert all(path.is_relative_to(repo) and (path == owner or path.is_relative_to(task)) for path in paths)
relative = sorted({path.relative_to(repo).as_posix() for path in paths})
(task / 'REVIEW_ALLOWLIST042.txt').write_text('\n'.join(relative) + '\n', encoding='utf-8', newline='\n')
print('Exact allowlist:', len(relative), 'paths; one TEST owner plus bounded TASK042 only.')
