# Проверки RED → GREEN

## DB-free canonical semantics
R01: `groupViable` baseline live Party passes/fails exactly as before for representative ordinary target/Party/loadouts, including line `WAIT_GROUP_MP` and `.8` threshold.
R02: historical exact group after ordinary full natural recovery `required3060 / MP2806` — `PROVEN_IMPOSSIBLE` **только** если requiredLowerBound/maxPossibleMpUpperBound proven exact; else `UNKNOWN`, never fabricated proof.
R03: native initially low MP but can recover to sufficient MP -> `UNKNOWN`/`MAYBE`, no early deny.
R04: optional legal future buff/MP increase; if no trusted upper bound -> `UNKNOWN`.
R05: different learned level/enchant route, changed classIndex/weapon, alternate cheaper learned skill, Party member replacement, target HP/champion/regen/risk/config -> invalidate; no false impossible.
R06: strict > .8 threshold with equals, underflow/overflow, nonfinite values, unknown critical fields.
R07: no mutation of Player/Monster/currentMp, persistent SQL, source snapshots, RNG and global farm; no new worker/scheduler.
R08: safety/current owner and acquired skill target family remain exact; HEAL/BUFF/CONTROL not relabeled DAMAGE; stock priority intact.
R09: same deterministic candidate order and denied proposal denominator; no hardcoded classID, skillID or NPC ID.
R10: materialization/recovery skipped only after proven necessary impossibility, and current native admission rechecks all facts before CUT.
R11: previous historical stress cases 011/012/013/014 and `post002-support` (buffs) still PASS.
R12: historical selected14/terminal Adam903/223 and 183row checksum frozen before native; SQL credits exactly once if collection occurs; EVAL012 excluded.
R13: STOP/PAUSE/RESUME fail closed under owner identity and terminal receipts, no budget resets/double credit, no orphan processes.
R14: mandatory canceled/incomplete cohort never produces false seal. Two paired arms require exact physical hash and distinct incarnation/generation.
R15: new no-data/timeout must not increment stagnation; no model promotion after MSE alone.
R16: bounded CPU/wall/allocations, no repeated DB queries in hot combat loop, no high-rate logging.

## Native readiness (only after offline gates)
N01. Successful sealed TRAIN admission: 3distinct cells, >=2classes, both SOLO/PARTY, 2+ actually learned target-ready skill alternatives per supported family. 2 fresh matched A/B actors each.
N02. Earlier Party MP refusal must be avoided or rendered rigorous UNKNOWN; late refusal recorded if proof insufficient, never hidden.
N03. At least one actual native DAMAGE and legitimate Party action (where supported) with correct actual skill/recipient/MP paid; negative outcomes retained.
N04. One frozen train/tuning/audit and optimizer update if fresh sufficient data; otherwise `INSUFFICIENT_EVIDENCE`, no fake fit.
N05. Cold restore selected vs terminal optimizer, no double application; accurate GameServer boot count and owned process exits.

## Safety/gates
- Maximum2 readiness sessions <=20min total (including startup/cleanup), ≤3GS/60native-min; one FIT at most.
- Tests in original existing test suites, bounded Ant/JAR; no `ant verify` or production SQL.
- Two distinct UTF-8 checks, fixed mojibake/escaped Cyrillic, `git diff --check`, source scoping and GitHub independent readback.
