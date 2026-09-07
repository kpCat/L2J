# Goal034 closure 3 — canonical runtime layout + final real-stack acceptance

## Baseline
Это bounded closure Goal034.
- Branch: `feature/phantom-world`
- Required parent/HEAD/origin: `82f6c562bc14bd18eeffead03172d71124fbedee`
- Module: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Previous report: `docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-final.md`
- Goal035 НЕ начинать.

Parent уже имеет green Goal034 contract 5/5, DB negative guard, `ant verify`, standalone `ant jar`, real LoginServer READY и реальный GameServer process start.
Текущий blocker: GameServer sandbox не содержит полный runtime datapack/script layout.

## Root cause
Обычный GameServer запускается из `dist/game`.

Не считать `DatapackRoot` универсальным redirect:
- `IXmlReader.parseDatapackFile/Directory()` использует `new File(".", path)` и зависит от process working directory;
- `ScriptExecutor` hardcode'ит `-sourcepath data/scripts`;
- absolute `ScriptRoot` не заменяет cwd-relative sourcepath зависимостей.

Поэтому прежняя схема "sandbox config + absolute DatapackRoot/ScriptRoot на workingGame" неполна.

## Цель
До full verify исправить sandbox composition:
`focused runtime-layout contract -> fresh verify -> final jar -> real LS/GS gen1 -> LIVING 10/5 -> native restart -> gen2 continuity -> exact cleanup`
и перевести Goal034 в `SUCCESS`.

## Safety
Разрешена только test DB `l2jmobiush5_phantom_test`, host `127.0.0.1`/`localhost`, port `3308`, user `l2j_phantom_test`, mysql или mariadb transport при PASS canonical guard.
Production `l2jmobiush5` запрещена даже для probe/read/cleanup.
`ant prepare-phantom-test-db` НЕ выполнять.
Никаких wildcard/global process kills.
Working `dist/game` и его `data` должны остаться unchanged.

## READ_SET / root audit
До patch:
1. previous Goal034 final report;
2. `PhantomBlackBoxLocalStackGoal034.java` — prepareSandbox/startServer/integrity;
3. `IXmlReader.java` — datapack helpers;
4. `ScriptEngine.java`;
5. `ScriptExecutor.java`;
6. `Server.ini` DatapackRoot/ScriptRoot defaults.

Разрешено <=6 repository searches только для startup-critical cwd-relative resource roots (`new File(".",`, `new File("data`, `Paths.get("data`, `Path.of("data`, `data/scripts`, root cfg lookups).
Цель: перечислить resource paths, которые bootstrap читает относительно cwd. Не делать production path refactor.

## Required architecture
Sandbox GameServer:
`.phantom-local/blackbox/goal034/<run-id>/game/`

В нём:
- mutable sandbox `config/`;
- isolated `log/`;
- нужные root cfg;
- **полный canonical `dist/game/data/` snapshot** под `game/data/...`.

Не selective copy только mapregion/scripts.

Предпочтительно обычный file copy, чтобы child не мог изменить canonical working data.
Не использовать hard links/junction/symlink на canonical data, если запись через них способна изменить working tree.
Если copy объективно неприемлем — сначала measurement/evidence, потом только безопасная read-only alternative.

Sandbox config:
- `DatapackRoot = .`
- `ScriptRoot = ./data/scripts`

Это должно повторять normal `dist/game` runtime layout.

## Pre-spawn runtime-layout gate
После prepareSandbox и ДО DB fixture mutation/child spawn fail closed, если отсутствует:
- `game/data/`
- `game/data/mapregion/`
- `game/data/CategoryData.xml`
- `game/data/scripts/`
- `game/data/scripts/handlers/MasterHandler.java`
- `game/data/scripts/handlers/EffectMasterHandler.java`
- `game/data/scripts/handlers/skill/effects/` + хотя бы один `.java`
- `game/config/Scripts.xml`
- дополнительные cwd-relative startup resources, доказанные audit.

Проверить:
- sandbox DatapackRoot exact `.`;
- ScriptRoot exact `./data/scripts`;
- canonical source data exists;
- canonical source sentinels/fingerprint unchanged after run.

Добавить focused contract test, который строит sandbox layout без server spawn и проверяет gate.

## Copy semantics
Helper должен:
- создавать directories;
- копировать files в fresh run root;
- не следовать unsafe links за пределы canonical data root;
- fail closed на unreadable/missing source;
- не копировать working logs;
- cleanup только run root.

Production runtime API не добавлять.

## Validation budget
Known runtime-layout fix выполняется ДО full verify.

### Phase A
1. compile affected;
2. Goal034 runtime-layout contract PASS;
3. DB negative guard PASS.

### Phase B
Fresh `ant verify`.
Допускается максимум 2 новых independent predecessor blockers: exact root cause -> minimal fix -> focused regression -> repeat verify.
Максимум 3 full verify runs.

После green verify: один standalone final `ant jar`.

### Phase C
Запустить `ant phantom-black-box-local-stack-goal034-test`.

Допускается максимум 3 новых independent Phase-C blockers, потому что это первый полный real-stack traversal после исправления runtime layout.
Для каждого: exact evidence, minimal fix, focused regression, required verify/jar post-fix cycle при code change, затем repeat black-box.
Known runtime-layout blocker не считается новым.
Четвёртый новый blocker => `BLOCKED`.

Environment blocker классифицировать отдельно с точным prerequisite; не гадать.

## Mandatory final evidence
LoginServer: real JVM READY, login-client + GS-login listeners.
GameServer gen1: real JVM READY, complete startup, test-LS registration, Phantom running.
Phantom: managed=10, ACTIVE cap=5, durable ecology, catch-up convergence, no duplicates.
Restart: native Shutdown, Phantom drain, no forced kill on success, LS stays alive, exit 2 allowed.
Gen2: second GS JVM READY, same durable identities/ecology, managed=10, no duplicates, registration/owners restored.

## Cleanup/integrity
PASS/FAIL:
- exact run-owned DB cleanup;
- exact process cleanup;
- run ports free;
- `orphans.none=true`;
- `working.integrity=true`;
- canonical `dist/game/data` sentinels/fingerprint unchanged;
- no global kill.

## Out of scope
Goal035/036/037/038, production DB, quest/rate semantics, gameplay, schema redesign, production path refactor, permanent API, scheduler/thread, unrelated user files.

## Docs/report
При SUCCESS:
- current Roadmap v4/current status/handoff: Goal034 `SUCCESS`;
- historical BLOCKED reports не переписывать;
- создать `docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-success.md`.

При BLOCKED — новый resume exact blocker.

Report <=160 строк: status/parent, cwd-root audit, runtime-layout implementation, data snapshot files/bytes/copy duration, verify/jar counts, PIDs/ports, readiness, 10/5, restart/drain, gen2, cleanup/integrity, environment evidence, changed files, elapsed/tokens, commit/push.

## Git/final checks
Before: fetch + HEAD/origin exact required parent.
After: Goal034 contract final pair, `git diff --check`, mojibake, escaped Cyrillic/XML, exact-path diff/status.
Разрешены bounded Git read, exact-path add, один commit, non-force push.
Запрещены reset/restore/rebase/merge/amend/force/history rewrite.

SUCCESS subject:
`phantom(goal-034): complete real-stack black-box acceptance`

BLOCKED subject:
`phantom(goal-034): record runtime-layout closure blocker`

## SUCCESS
Goal034 => SUCCESS только если runtime-layout contract green, verify green, jar green, real LS + GS gen1 READY, registration, Phantom LIVING 10/5, native restart/drain, gen2 continuity, exact cleanup/no orphans/integrity, production DB unused, docs/report/commit/push complete. После SUCCESS следующий Goal — Goal035.
