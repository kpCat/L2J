# Goal 026 Checkpoint 1 — Raid readiness

## Status

- Result: `SUCCESS`.
- Verdict: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 026 overall: `IN_PROGRESS`.
- Goal 026 Checkpoint 2+: `NOT_STARTED`.
- occurred_context_compaction: `yes`.

## Summary

Добавлена только passive read-only authority для текущей доступности raid/epic target и bounded snapshot текущих Party/CommandChannel. Readiness policy сопоставляет exact Goal 011 `ContentRequirementFact`/`NpcKind` с фактическим состоянием `RaidBossSpawnManager` или `GrandBossManager`, а требования группы — с существующим Goal 017 capability owner.

Не добавлены формирование CommandChannel, recruitment, gathering, navigation, entry, combat, retreat, persistence, worker или симуляция победы.

## Baseline и provenance

- Branch: `feature/phantom-world`.
- Required parent: `5517081fb2bbf2aa9ad8295130714df2d4b45921`.
- Independent truth: R025A-01/02 — `CLOSED`; Goal 025A — `ACCEPT`; Goal 025 overall — `ACCEPT`.
- Commit: текущий task commit; exact SHA приводится во внешнем final handoff.
- Required subject: `feat(phantoms): add raid readiness authority`.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidModel.java` — immutable availability/readiness facts и fail-closed policy types.
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAuthority.java` — read-only boss observation contract.
- `java/org/l2jmobius/gameserver/phantoms/raid/L2jPhantomRaidAuthority.java` — exact manager-backed read truth.
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidReadinessService.java` — stateless readiness join.
- `java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeQuery.java` — deterministic query по `ContentKind` поверх Goal 011 snapshot.
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java` — bounded current-force observation contract.
- `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java` — exact current Party/CommandChannel snapshot из actor identity.
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — passive construction/accessor без lifecycle worker.

Tests/build:

- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidReadinessSuite.java`.
- `test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeQueryTruthSuite.java`.
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`.
- `build.xml`.

Documentation/package:

- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`.
- `docs/PHANTOM_BOTS_ROADMAP.md`.
- `docs/phantoms/tasks/026-checkpoint-1-raid-readiness/*`.
- `docs/phantoms/reports/026-checkpoint-1-raid-readiness.md`.

## Architecture decisions

- Standard raid availability uses exact `RaidBossSpawnManager` definition/status/stored-info plus matching live boss identity. Contradictory or incomplete evidence maps to `UNKNOWN`.
- Epic availability uses exact `GrandBossManager` live boss and respawn stat truth. Raw script status alone does not imply availability.
- Party facts begin with the exact current actor, copy only bounded current Party/CommandChannel membership, and fail closed on concurrent identity drift or bounds overflow.
- Capability evidence is owned by the existing Goal 017 member-capability path. Required capabilities gate readiness; optional capabilities do not.
- Service is passive and stateless. It neither schedules nor mutates boss, party, command-channel, player, navigation, combat, persistence, or economy state.

## DB, migrations и configs

- DB changes: none.
- Migrations: none.
- Config changes: none.
- Working production DB was not mutated by CP1 tests.

## Commands и результаты

- Initial local `ant` lookup: command was unavailable in `PATH`; no build ran.
- Local Ant route: `.phantom-local/apache-ant-1.10.17/bin/ant.bat`.
- Initial focused multi-target run: production/test compilation passed; authority passed 3/3; force test exposed one test-only deterministic-order expectation. Production behavior was unchanged; expectation corrected.
- `ant phantom-raid-readiness-force-test`: `BUILD SUCCESSFUL`, 3/3.
- `ant phantom-raid-readiness-checkpoint1-test`: `BUILD SUCCESSFUL`; authority 3/3, force 3/3, policy 6/6, Goal 011 query truth 14/14, Goal 017 party server integration 8/8.
- Первый aggregate был запущен до context compaction, но его закрытая execution cell потеряла вывод; тот же aggregate вынужденно повторён один раз для проверяемого результата.
- `ant jar`: `BUILD SUCCESSFUL`; `LoginServer.jar`, `GameServer.jar`, `DatabaseInstaller.jar` собраны, server JAR скопированы штатной целью.
- Plain `ant verify`, Goal 025 aggregate и broad regressions не запускались.
- `git diff --check`: PASS.
- Final artifact scope guard: только High Five CP1 allowlist; другие хроники, binaries и IDE files не затронуты.
- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены: совпадений нет.

## Performance

- Новых threads, scheduled workers, polling loops, persistence или unbounded collections нет.
- Snapshot ограничен 16 parties и 144 members; overflow возвращает fail-closed fact.
- Readiness evaluation выполняется только по запросу и не добавляет hot-path logging.

## Deviations

- Bounded exception по размеру: 23 файла составляют один CP1 artifact family; 8 файлов — входной небольшой task package, остальные — четыре raid authority/readiness класса, ближайшие Goal 011/017 seams, focused tests/build и обязательные status/report docs. Split нарушил бы единый checkpoint gate.

- Из-за потери aggregate output при первом context compaction тот же финальный aggregate повторён. Test scope не расширялся.
- Один focused force gate был повторён после исправления только ошибочного ожидаемого deterministic order в тесте.

## Limitations и unfinished scope

- Checkpoint 1 не организует raid attempt и не доказывает raid victory.
- Не реализованы CommandChannel formation, recruitment, gathering, navigation, entry, combat, retreat, persistence, workers и victory simulation.
- Checkpoint 2+ не начат.
- Независимое review Checkpoint 1 остаётся обязательным.

## Risks

- Manager evidence может быть временно противоречивым при concurrent spawn/despawn; policy намеренно возвращает `TARGET_UNKNOWN`.
- Bounded snapshot может вернуть `GROUP_INCOMPLETE` при concurrent Party/CommandChannel mutation; caller должен повторно оценить позже, а не считать группу готовой.

## Git и push

- Git использовался только для обязательного baseline/scope/diff контроля, попытки применения локального unified patch после недоступности patch helper, exact commit и push.
- `git status --short`, `git diff --name-only`, `git diff --check` использованы для scope guard и проверки diff.
- История не переписывалась; force push не использовался.
- Push result и exact local/remote HEAD приводятся во внешнем final handoff.

## Next step

Независимое review Goal 026 Checkpoint 1. Не начинать Checkpoint 2 до принятия текущего gate.
