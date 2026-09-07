# Goal034 closure 4 — schedule-aware acceptance and residual gen2 blocker

## Статус

`BLOCKED` — ложный exact-five oracle и inherited restart-day gate исправлены, gen1 прошла schedule-aware acceptance и native restart/drain, но финальная gen2 не достигла schedule/admission parity: `desiredActive=5`, `expectedAdmitted=5`, `actualOnline=4`. Это третий новый Phase-C blocker после двух доказанных и исправленных причин; по TASK новые fix/run запрещены. Goal034 не переведён в `SUCCESS`; Goal035 не начат.

Branch: `feature/phantom-world`.
Required parent: `b43959bcfec2d21840b1abc8f54a7bc6900d849e`.

## Read-first, scope и переиспользование

- Прочитаны closure4 TASK/ACCEPTANCE/CONTEXT/package manifest, closure3 report, Roadmap v4/current status/handoff, harness, production schedule/catalog/admission/ecology/materialization/startup/restart owners и ближайшие Goal033 tests.
- Root `README.md` и module `Agents.md` прочитаны; отдельный code-map/pattern-файл в bounded read-set не найден и повторно не искался.
- Переиспользованы canonical `PhantomPopulationCatalog.evaluate`, durable `PhantomPopulationState`, existing guarded DB/process/manifest/cleanup paths и Goal033 focused-test style.
- Bounded 13-file exception охватывает одну closure-family: 3 code/test, 4 current docs, этот report и 5 переданных task artifacts. Unrelated user changes не тронуты.

## Реализованная schedule-aware семантика

- `PhantomPopulationActiveTarget=5` трактуется как cap, не постоянная обязанность держать ровно пять online.
- Для одного captured `acceptanceInstant` harness декодирует все 10 durable `scheduleTemplate/phaseMinutes`, вычисляет desired state через canonical catalog и строит `desiredActiveIds`/`actualOnlineIds`.
- Gate требует `actualOnline == min(5, MaxMaterializedPhantoms, desiredActiveCount)`, `actualOnline subset desiredActive`, READY/MANAGED, terminal catch-up, unique ownership и canonical online status.
- Non-ACTIVE online profile отвергается. Production schedule/admission/cap semantics не изменялись ради exact five.
- Headless Player canonical DB online status `2` принимается как online; synthetic/fake Player state не вводился.
- Fail-manifest сохраняет bounded summary; successful generation snapshot сохраняет по каждому профилю `profileId/scheduleTemplate/phaseMinutes/homeRegion/desiredState/online`.

## Native restart closure

- Только sandbox config получает `ServerRestartScheduleEnabled=True`, `ServerRestartDays=1,2,3,4,5,6,7` и exact future `HH:mm`.
- До ожидания log schedule instant обязан попасть в bounded lead window; inherited wrong-day instant rejected focused contract.
- Gen1 final run: configured `2026-09-07T12:46:01.131783100Z`, expected/observed `2026-09-07T12:53:00Z`, exit code `2`.
- Log доказал native countdown и `GM restart` ровно в scheduled instant; `Phantom World: Initial subsystem drain completed, stopped=true(41ms)`.
- Gen2 lead увеличен до 7 минут, чтобы не пересекать обязательное 5-minute convergence window; contract фиксирует этот bound.

## Focused/full validation

- Goal034 focused contract: 3 green runs; финальный `17/17 PASS`.
- Goal033 ecology regression: сначала fixture mismatch `7/8`, затем исправленный durable-schedule fixture `8/8 PASS`.
- DB negative guard: PASS; production URL rejected before driver/spawn. `prepare-phantom-test-db` не выполнялся.
- Full `ant verify`: `3/3 PASS`; финальный post-fix run `BUILD SUCCESSFUL`, `21:52`.
- Standalone `ant jar`: `3/3 PASS`; финальный post-fix run `BUILD SUCCESSFUL`, `0:18`.
- Real Goal034 black-box: 3 runs, все завершены честным FAIL + exact cleanup; validation budget исчерпан.

## Real-stack runs и blockers

| Run | Доказательство | Результат |
|---|---|---|
| `20260907-132303-b612ae6d` | gen1 `desired=5/expected=5/online=3`, subset=true, terminal/unique/ownership=true | Blocker 1: missing ecology-fence refresh после idle calendar advance; исправлен production event + focused regression. Дополнительно canonical headless `online=2` исправлен только в harness. |
| `20260907-140621-4dc282b3` | gen1 `5/5`, native restart/drain PASS; gen2 exited code 2 до завершения 5-minute gate | Blocker 2: 4-minute gen2 restart lead короче convergence window; исправлен на 7 минут + focused contract. |
| `20260907-144525-af64e169` | gen1 `desired=5/expected=5/online=5`; native restart/drain PASS; gen2 READY/registered, затем `desired=5/expected=5/online=4`, subset=true, parity=false | Blocker 3: residual gen2 production-composition mismatch после полного post-fix verify/jar cycle; closure остановлена. |

Final run gen1 schedule evidence:

| Schedule | Desired ACTIVE | Actual online |
|---|---:|---:|
| evening | 5 | 5 |
| morning | 0 | 0 |
| late | 0 | 0 |

Gen1 desired/actual IDs: `3945,3949,3950,3953,3954`; acceptance instant `2026-09-07T12:48:47.506374200Z`. Это доказанный результат конкретного schedule window, а не новая exact-five production semantics.

Final run gen2 bounded failure snapshot: profiles=10, desiredActive=5, expectedAdmitted=5, online=4, subset=true, parity=false, READY/MANAGED=true, catch-up terminal=true, pending=0, unique/ownership/catalog/canonicalOnline=true; acceptance instant `2026-09-07T12:58:39.803809300Z`.

Fail path не сохранил отсутствующий profile id и его internal owner state до cleanup. Поэтому точный корень residual gen2 mismatch не заявляется. После двух отдельных исправленных Phase-C причин новый mismatch считается третьим blocker; приравнивать его без evidence к первому ecology-event дефекту нельзя.

## Processes, cleanup и safety

- Final run PIDs LS/gen1/gen2/cleanup: `30712/9604/6188/26796`.
- Ports login-client/login-game/game-client: `65500/65501/65502`; LS READY, обе GS READY и registered.
- Final cleanup: population=10, registration=true, forced=false; `orphans.none=true`, `working.integrity=true`.
- Canonical/sandbox data fingerprint unchanged: `f793178f4ab857bcf7f260a7d8db21d086980e9e6c1dc5f61f9caf12eb28395d`.
- Database только `127.0.0.1:3308/l2jmobiush5_phantom_test`; `database.production.used=false`. Production `l2jmobiush5` не probe/read/cleanup.
- Wildcard/global Java kill не использовался; cleanup ограничен run-owned PID/ports/rows/registration.

## Files, budget и handoff

- Production: `PhantomPopulationEcologyService.java` — после idle calendar advance публикуется existing ecology fence event, если scheduling снова разрешён; новых API/thread/schema нет.
- Tests: Goal033 ecology regression и Goal034 schedule-aware/native-restart harness.
- Current docs: master plan, Roadmap, current status, handoff; historical reports immutable.
- Task package: closure4 `TASK/ACCEPTANCE/CONTEXT/PACKAGE_MANIFEST/CODEX_LAUNCHER`.
- Usage snapshot перед final documentation/scope checks: 762,943 tokens, 8,825 seconds.
- Разрешены exact-path staging, один BLOCKED commit и non-force push. Commit SHA и push result сообщаются в final handoff; amend не выполняется.
- Следующий шаг требует нового explicit Goal034 resume task/budget: сначала расширить fail evidence для отсутствующего gen2 profile/owner state, затем доказать минимальный root-cause fix. Goal035 остаётся `NOT_STARTED`.
