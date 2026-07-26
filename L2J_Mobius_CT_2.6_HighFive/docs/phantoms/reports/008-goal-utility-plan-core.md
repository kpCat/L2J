# Goal 008 — goal model, Utility AI core and plan executor

## Status

```text
Status: SUCCESS
Manual gate: PENDING_INDEPENDENT_REVIEW
Accepted baseline: 357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018
Branch: feature/phantom-world
Subject: feat(phantoms): add goal utility plan core
Goal 007: ACCEPT after Goal 007A
Goal 007A: ACCEPT
Goal 008: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 009: NOT_STARTED
```

## Summary

Реализован domain-neutral decision core без concrete игровых действий:

- immutable/versioned `PhantomGoal`, `PhantomDomainRef` и generic capabilities;
- sealed candidate/handler registries с hard capacity 256;
- integer normalized Utility AI с deterministic ASCII tie и top-eight
  explanations;
- immutable typed plans максимум из 32 steps;
- one-handler-per-work executor с logical timeout, retry, replan, terminal
  status и cancellation generation;
- deterministic binary `goal.runtime` schema 1 через существующий component
  envelope;
- decision engine подключён как scheduler work sink;
- production запускается с 0 attached/registered profiles и пустыми sealed
  registries.

Goal 007A закрыта verdict `ACCEPT`. Bounded scheduler follow-up исправляет
только stale target после внешнего cleanup retry: current requested state
пересчитывается под scheduler monitor.

## Changed files

Production:

- `java/org/l2jmobius/gameserver/phantoms/decision/**`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java`;
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityWorkItem.java`;
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivitySnapshot.java`.

Tests/build:

- `build.xml`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPersistenceSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPerformanceSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java`;
- `tools/phantoms/verify-task-008.ps1`.

Documentation:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `docs/phantoms/architecture/DECISION_GOAL_PLAN_CONTRACT.md`;
- `docs/phantoms/reports/007a-scheduler-transition-ownership-hardening.md`;
- `docs/phantoms/reviews/007a-scheduler-transition-ownership-hardening-review.md`;
- пакет `docs/phantoms/tasks/008-goal-utility-plan-core/**`;
- этот отчёт.

Task 008 прямо разрешает bounded exception по числу файлов: immutable model,
registries, executor, persistence, integration, focused suites и gate artifacts
образуют одну artifact family. Другие хроники не изменялись.

## Architecture decisions

### Domain-neutral boundary

Decision package не импортирует `Player`, `GameClient`, packets, navigation,
combat, population, Game Knowledge, provider/LLM или external AI. Production
не содержит concrete candidate, handler или capability key.

### Deterministic model и scoring

Все identifiers bounded и валидируются при construction. Collections
копируются, canonical-sort-ятся и публикуются immutable. Requirements
проверяются до considerations. Score:

```text
floor(sum(score * weight) / sum(weight))
```

вычисляется через `long`; tie разрешается ordinal candidate key ascending.
Exception/null/score вне `0..1000` блокирует только один candidate.

### Plan executor

Один attached runtime хранит не более одного plan и одного handler in flight.
Scheduler work выполняет один decision slice и максимум один handler. Handler
вызывается вне global engine lock. Goal replacement, detach, activity generation
change и stop увеличивают cancellation generation; stale result отбрасывается.
Thread interruption и per-profile runtime primitives не используются.

### Persistence

`PhantomGoalStateCodec` хранит только goal:

```text
component_type = goal.runtime
component_schema_version = 1
payload <= 4096
```

Binary format имеет magic/version, bounded lengths/counts и exact-consumption
check. Reads происходят только при attach/reload; ordinary work не читает БД.
Optimistic conflict требует explicit reload. ACTIVE goal после restart получает
`NEEDS_REPLAN`; plan/progress/explanations не восстанавливаются.

### Scheduler follow-up

После успешного retained cleanup scheduler заново вычисляет requested state из
current signals/unregister truth. WARM/BACKGROUND/SLEEPING публикуются напрямую;
ACTIVE/NEARBY сначала публикуют SLEEPING и получают отдельную fresh
materialization opportunity. Activity generation меняется при effective state
или lifecycle ownership change, но не при harmless signal replacement.

### Production lifecycle

Startup:

```text
repository → materialization service → empty sealed registries
→ goal store/decision engine → scheduler(decision sink)
```

Shutdown:

```text
scheduler.beginStop → decision.beginStop → materialization drain
→ scheduler.finishStop → decision.finishStop
```

Незавершённый scheduler/handler сохраняет configured system в `FAILED` для
следующего explicit shutdown.

## DB, migrations and config

```text
Production DB: не использовалась
Test DB only: l2jmobiush5_phantom_test
Schema/migrations: unchanged
Config: unchanged
Profile component envelope: reused
```

## Tests and commands

Seed: `20260725001`. Apache Ant 1.10.17 запускался абсолютным локальным
launcher, так как `ant` отсутствует в `PATH`.

Финальные результаты:

| Gate | Result |
|---|---:|
| `ant compile-tests` | PASS; 1948 production / 35 test sources |
| Decision core | PASS `30/30 ×3` |
| Decision persistence | PASS `14/14` |
| Decision performance | PASS `2/2 ×2`, byte-identical canonical summary |
| Decision performance SHA-256 | `E99330F52AF575E9EF8C729571D52AF2D8F9463B6CA8C61EAB3AA368706C8E7C` |
| Scheduler regressions/integration | PASS `20/20 ×3` |
| Scheduler scale | PASS `2/2 ×2`, byte-identical canonical summary |
| Scheduler scale SHA-256 | `B5EB59EB05ABFB0449FA4D553ABA51997EB13D6ADE2BAB023F008E68D20490F4` |
| Production materialization | PASS `20/20 ×3` |
| Shutdown handoff | PASS `5/5 ×3` |
| Headless Player | PASS `18/18` |
| Profile persistence | PASS `18/18` |
| DB integration | PASS `9/9` |
| Harness unit | PASS `66/66` |
| Skeleton | PASS `12/12` |
| Scenario / harness performance | PASS `1/1` / `1/1` |
| `ant verify` | PASS; `1 min 30 s` |
| отдельный `ant jar` | PASS; `14 s` |
| Production `GameServer.jar` decision entries | `45` |
| Production `GameServer.jar` test entries | `0` |
| Goal 008 verifier | PASS `68/68 ×2`, byte-identical |
| Verifier output SHA-256 | `B8AB1B2861F2B79730DFBA9C66F13FA656C772CA13974B60762F95AAA5DB55C0` |

`ant test` и `ant verify` вывели ожидаемые внутренние negative-control results:
lifecycle `0/2`, runner negative `0/1`, guard/freshness exit `2`. Все
соответствующие Ant gates завершились `BUILD SUCCESSFUL`; проверки не
отключались.

Основные команды:

```text
<local-ant-1.10.17>\bin\ant.bat compile
<local-ant-1.10.17>\bin\ant.bat compile-tests
<local-ant-1.10.17>\bin\ant.bat phantom-decision-core-test ×3 successful
<local-ant-1.10.17>\bin\ant.bat phantom-decision-persistence-test
<local-ant-1.10.17>\bin\ant.bat phantom-decision-performance-smoke ×2+
<local-ant-1.10.17>\bin\ant.bat phantom-activity-scheduler-test ×3 successful
<local-ant-1.10.17>\bin\ant.bat phantom-activity-scheduler-performance-smoke ×2
<local-ant-1.10.17>\bin\ant.bat phantom-production-materialization-test ×3
<local-ant-1.10.17>\bin\ant.bat phantom-server-shutdown-handoff-test ×3
<local-ant-1.10.17>\bin\ant.bat phantom-headless-player-test
<local-ant-1.10.17>\bin\ant.bat phantom-profile-persistence-test
<local-ant-1.10.17>\bin\ant.bat phantom-db-test
<local-ant-1.10.17>\bin\ant.bat test
<local-ant-1.10.17>\bin\ant.bat phantom-skeleton-test
<local-ant-1.10.17>\bin\ant.bat verify
<local-ant-1.10.17>\bin\ant.bat jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-008.ps1 ×2
```

Первый ранний вызов `ant compile` без абсолютного launcher не стартовал:
`ant` отсутствовал в `PATH`. Сборка не выполнялась; после обнаружения
подтверждённого локального launcher все учитываемые команды прошли.

## Performance measurements

Focused smoke создаёт ровно 1000 attached in-memory runtimes, 64 sealed
candidates, 8 considerations на candidate и dispatch budget 32. Каждый из 32
work items выбирает `candidate.00`, вызывает один handler и выполняет 0 store
reads после attach. В engine/runtime fields отсутствуют `Thread`, `Future` и
`Executor`.

## Deviations, limitations and risks

- `ant` отсутствует в `PATH`; использован существующий локальный Apache Ant
  1.10.17 без изменения repository/build tooling.
- Goal 008 не создаёт concrete goals/actions/capabilities и поэтому production
  остаётся намеренно inert.
- Navigation, combat, Game Knowledge, population и Goal 009 не начаты.
- Manual gate Goal 008 не self-accept-ится и остаётся
  `PENDING_INDEPENDENT_REVIEW`.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены: 51 text artifact, 0 matches;
- escaped Cyrillic в изменённых файлах проверены: 51 text artifact, 0 matches.

## Git

Git разрешён прямым требованием Task 008 для baseline/scope audit, одного
ordinary commit и push.

```text
Expected commit parent: 357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018
Commit SHA: во внешнем final handoff для сохранения одного ordinary commit
Push result: во внешнем final handoff
```

## Next step

Только независимое review Goal 008. Goal 009 остаётся `NOT_STARTED`.

Result:
`GOAL_UTILITY_PLAN_CORE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
