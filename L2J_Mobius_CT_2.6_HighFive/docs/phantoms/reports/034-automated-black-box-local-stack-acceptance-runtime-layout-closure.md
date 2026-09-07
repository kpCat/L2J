# Goal034 closure 3 — runtime layout and final stack report

## Статус

`BLOCKED` — canonical runtime-layout blocker закрыт, но обязательный LIVING 10/5 real-stack gate не достигнут. Goal034 не переведён в `SUCCESS`; Goal035 не начат.

Branch: `feature/phantom-world`.
Required parent: `82f6c562bc14bd18eeffead03172d71124fbedee`.

## Read-first и scope

- Прочитаны root/operator instructions, closure3 TASK/ACCEPTANCE/CONTEXT/package manifest, Roadmap v4/current status/handoff, два ближайших Goal034 BLOCKED-report, harness и production owners runtime layout, population schedule/admission и native restart.
- Root `README.md` прочитан; module `README.md`, project `AGENTS.md`, отдельный code-map/pattern-файл не найдены и повторно не искались.
- Переиспользованы existing `copyTree`, guarded DB validation, exact PID/process ownership, Goal032 reset cleanup, durable identity fingerprint и предыдущий immutable BLOCKED-report pattern.
- Scope: один harness, current Roadmap/status/handoff, новый report и переданный closure3 task package. Production runtime API/schema/data/gameplay не менялись.

## Runtime-layout closure

- Fresh GameServer sandbox получает полный ordinary-file snapshot canonical `dist/game/data`; unsafe links и non-regular entries rejected, working logs не копируются.
- Exact sandbox properties: `DatapackRoot = .`, `ScriptRoot = ./data/scripts`.
- Pre-spawn gate требует `data/mapregion`, `CategoryData.xml`, scripts, Master/Effect handlers, skill effects, `config/Scripts.xml`, `ipconfig.xml`, `hexid.txt`, `log.cfg` и isolated log directory.
- Source/sandbox structural fingerprints, file/directory/byte counts и selected content hashes входят в integrity/manifest evidence.
- Focused contract: PASS 7/7, включая missing-tree rejection before spawn.
- Canonical snapshot: 25,508 files, 975 directories, 1,171,328,742 bytes; fingerprint `f793178f4ab857bcf7f260a7d8db21d086980e9e6c1dc5f61f9caf12eb28395d`.
- Наблюдавшаяся copy duration: 22,678–26,119 ms; финальный run 25,284 ms.

## Validation

- Goal034 runtime-layout contract: 3 runs, все PASS 7/7.
- DB negative guard: PASS; production rejected before driver load/spawn, exit=2, driverLoads=0, connectionAttempts=0.
- Full `ant verify`: 3/3 PASS; последний 23:21. Разрешённый максимум 3 исчерпан.
- Standalone `ant jar`: 3/3 PASS; последний 0:18.
- Real LS/GS: 3 traversal после runtime-layout closure; все достигли LoginServer READY, GameServer fully started и test-LS registration.

## Real-stack evidence и blocker

- Run 1 `20260907-102731-13fd756e`: managed=10, online=2, terminal=true, unique=true; PIDs LS/GS/cleanup `10416/35844/38076`; ports `60160/60161/60162`; elapsed 372,747 ms.
- Run 2 `20260907-110759-dab4912a`: explicit population zone `Asia/Magadan`; managed=10, online=3, terminal=true, unique=true; PIDs `17384/21844/6380`; ports `53088/53089/53090`; elapsed 378,061 ms.
- Run 3 `20260907-114731-8d838a32`: process JVM and restart calculation also use `Asia/Magadan`; server log time `20:48 GMT+11`; managed=10, online=2, terminal=true, unique=true; PIDs `38712/24460/22220`; ports `53419/53420/53421`; elapsed 376,026 ms.
- Exact current cause boundary: `PhantomPopulationActiveTarget=5` is a cap. Admission uses `min(activeTarget, maximumMaterialized, desired ACTIVE count)`; selecting a broad wall-clock window does not prove five effective ACTIVE identities. Current harness does not persist schedule template/phase/effective-state evidence before cleanup, so deeper cause must not be guessed.
- Required resume: first capture the ten identities' schedule template, phase and effective/admission state; then make the harness choose/prove a deterministic five-ACTIVE acceptance instant/fixture using existing production contracts. Production population semantics must not be weakened.
- Latent native-restart blocker is independently exact: sandbox inherits `ServerRestartDays = 4`; final log scheduled `20:55` for Wed Sep 09 instead of +7 minutes. Resume must set the sandbox day gate to current day or all days and assert the scheduled instant before waiting.
- Native restart/drain, generation 1 snapshot, generation 2 continuity and duplicate comparison were not reached and are not claimed.

## Cleanup, integrity и safety

- Final database: `127.0.0.1:3308/l2jmobiush5_phantom_test`; `database.production.used=false`.
- Final cleanup: population=10, registration=true, forced=false.
- `orphans.none=true`, `working.integrity=true`; source/sandbox fingerprints equal.
- Production `l2jmobiush5` не probe/read/cleanup; `ant prepare-phantom-test-db` не выполнялся.
- Wildcard/global process kill не использовался; cleanup только exact run-owned PIDs, rows, registration, ports и run root.

## Budget, files и handoff

- Goal usage snapshot перед final checks: 729,266 tokens, 7,820 seconds.
- Изменены harness, четыре current docs и этот новый report; closure3 task package добавляется как переданный task artifact. Unrelated user changes не изменялись и не staging-уются.
- Full verify budget 3/3 не позволяет ещё один обязательный code-change → verify/jar cycle; поэтому closure завершена `BLOCKED` без фиктивного SUCCESS.
- После нового explicit Goal034 resume budget: focused evidence/fix → fresh verify → final jar → real LS/GS → LIVING 10/5 → native restart/drain → gen2 continuity → exact cleanup. Goal035 остаётся `NOT_STARTED`.
- Bounded Git read/exact-path staging/один BLOCKED commit/non-force push разрешены TASK; immutable SHA и push result сообщаются в final handoff.
