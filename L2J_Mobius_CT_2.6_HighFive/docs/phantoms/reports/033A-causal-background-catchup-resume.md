# Goal 033A — Causal historical Background catch-up resume

Дата: 2026-09-05
Результат: `SUCCESS`

## Status

Goal033A реализован в bounded scope после принятого Goal033A1. Реализация добавляет deterministic real-data planner, initial canonical Background baseline через существующий materialization/store lifecycle, durable exactly-once minute cursor и fail-closed normal materialization/Decision fence. Goal033 ecology не реализовывалась.

Goal033A causal blocker закрыт: focused gates и единственный финальный `ant jar` прошли.

## Exact baseline/branch

- Модуль: `L2J_Mobius_CT_2.6_HighFive`.
- Ветка: `feature/phantom-world`.
- Exact required parent: `67cc9fc1911a37644064854d964fd8191d8f24f2`.
- До изменений `HEAD` и `origin/feature/phantom-world` совпадали с exact parent.
- Divergence отсутствовал; reset/rebase/force не применялись.
- Все unrelated untracked local task packages сохранены и исключены из Goal033A scope.

## Read-first audit

До изменений прочитаны:

- `Agents.md`, `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/PHANTOM_BOTS_ROADMAP.md`, `docs/phantoms/PHANTOM_CURRENT_STATUS.md`, `docs/phantoms/NEW_DIALOG_START_MESSAGE.txt`;
- reports `033-living-population-ecology.md`, `033A-causal-background-catchup.md`, `033A1-canonical-population-topology-ingress.md`;
- основной resume task и присутствующий original local Goal033A task package;
- `PhantomBackgroundGoalSpec`, `PhantomBackgroundService`, `PhantomBackgroundAuthority`, `L2jPhantomBackgroundAuthority`, `PhantomBackgroundModel`, `PhantomBackgroundTransaction`, `PhantomBackgroundOperationKey`, `PhantomBackgroundState`;
- GameKnowledge target/spawn query model, Topology loader/query/snapshot и Goal033A1 evidence manifest;
- Goal store/codec, profile component repository/atomic mutation seam;
- materialization service/player/lifecycle ports, population manager/store и `PhantomSystem`;
- ближайшие Background, Navigation, Goal033A1, Goal032, Goal031 и Goal030 CP3 suites и соответствующие Ant routes.

`README.md`, `docs/phantoms/CONTEXT_INDEX.md` и `docs/phantoms/DEVELOPMENT_CHAT_HANDOFF.md` в модуле не найдены; повторный поиск не выполнялся.

Найдены и переиспользованы локальные аналоги:

- deterministic source selection и immutable generation/hash checks из acquisition planner;
- optional atomic component mutation из Background acquisition transaction path;
- existing `PhantomMaterializationLifecycleBridge` chain и `Player.storeMe()`/`afterStore()` baseline capture;
- strict bounded component codec/store из profile component state;
- Goal033A1 production-composed population/topology ingress suite и existing guarded test launcher.

Учтены ограничения: Java/API версии подтверждены текущим кодом и build; публичные schemas/data topology не менялись; один existing Player lifecycle; без нового scheduler/thread/future; shipped fail-closed config не ослаблен. До реализации непроверенным оставался только causal catch-up path; он закрыт focused DB gate и regressions ниже.

## Production proof: Goal033A1 evidence is consumed

Production chain:

```text
PhantomPopulationStore.createShell()
 -> PlayerCreationInitializer.resolveCreationLocation()
 -> canonical persisted creation coordinates
 -> existing PhantomMaterializationService materialize(HISTORICAL_BASELINE, claim)
 -> unchanged real Player at exact Goal033A1 ingress
 -> PhantomHistoricalBackgroundPlanner over PhantomSystem shared GameKnowledge/Topology/authority
 -> real attackable+targetable MONSTER spawn + instance-zero FARMING anchor
 -> contiguous traversable backgroundEligible BACKGROUND routeHint
 -> ACTIVE farm.background goal persisted atomically with catch-up plan
 -> existing Player.storeMe()
 -> existing PhantomBackgroundService.afterStore()
 -> L2jPhantomBackgroundAuthority.capture()
 -> BackgroundTransaction.captureBaseline() -> READY/DEAD
```

Goal033A1 production-composed gate повторно подтвердил все 7 population groups, exact ingress для normal managed creation, real spawn destination и factual route. `PhantomSystem` передаёт planner тот же production GameKnowledge/Topology snapshot и тот же `L2jPhantomBackgroundAuthority`, которые использует Background service; отдельного fixture/runtime graph в production нет.
## Implementation

### Canonical planner and goal

`PhantomHistoricalBackgroundPlanner` принимает только profile ID, собственные canonical level/class/anchor facts, immutable knowledge/topology generations и deterministic seed/ordinal. Он выбирает MONSTER в окне собственного level ±2, требует attackable/targetable, positive instance-zero spawn evidence, matching `FARMING` anchor и полный factual `BACKGROUND` route. Empty candidate set fails closed; hardcoded NPC/anchor fallback отсутствует.

Planner создаёт строго валидный ACTIVE `farm.background` goal. Resource constraints получаются через authority planning snapshot из текущего Player loadout; произвольные/free shots/items не добавляются. Обычная физическая атака представлена existing zero-resource `Loadout.none()` contract, а не invented skill/item.

### Initial baseline and lifecycle fence

`PhantomHistoricalBackgroundService.begin()` создаёт/claim’ит `background.catchup`, materializes Player с purpose `HISTORICAL_BASELINE` и exact owner claim, строит plan, атомарно сохраняет goal+catch-up plan до store, затем dematerializes через существующий service. Existing Background lifecycle callback capture’ит canonical baseline и transaction verification возвращает только READY/DEAD.

Lifecycle bridge остался единственным. `NORMAL` materialization и Decision admission fail closed, пока catch-up имеет blocking status. Historical maintenance допускается только для exact request claim; COMPLETE снова открывает normal work. Partial lifecycle-chain admission выполняет abort ранее принятых owners.

### Exactly-once cursor and restart

`background.catchup` schema v1 хранит request/generation, `[from,target)`, current minute cursor, interval/plan ordinals, goal identity/revision, plan identity, model version и authority generations/hashes. Codec строгий, bounded до 4096 bytes и отклоняет trailing data.

Каждый positive one-minute interval формирует отдельную `HISTORICAL_BACKGROUND_V1` operation identity. Background canonical/player/inventory write, receipt и cursor N→N+1 коммитятся одной `PhantomBackgroundTransaction` транзакцией. Pre-commit fault откатывает оба состояния; lost post-commit response наблюдает committed cursor; duplicate identity возвращает IDEMPOTENT без повторного EXP/item/resource mutation. Live/acquisition/historical identities не пересекаются.

При изменении собственного level/состояния planner повторно проверяет текущие real data и при необходимости сохраняет новый plan с тем же goal ID и revision+1. Hash/generation drift переводит state в explicit `FAILED_REPLAN_REQUIRED` без Background mutation. Bounds: максимум 64 intervals и 1440 simulated minutes за вызов; elapsed time не является reward multiplier.

Если canonical farm приводит к DEAD, последующие historical минуты используют отдельный `HISTORICAL_DEAD_IDLE`: никакого respawn/recovery/reward/RNG consumption, DEAD state и inventory сохраняются, а receipt+cursor всё равно атомарно продвигаются до target. Это устраняет вечный fence без direct teleport и без реализации Goal033 schedules/pace/ecology.

### Population initialization compatibility

Managed POPULATION character получает минимальный shipped vitality contract (`PlayerStat.MIN_VITALITY_POINTS`) вместо disabled-zero multiplier, чтобы existing Background model мог выполнить обычный causal encounter. CLIENT path, level, EXP, SP, adena/items и creation coordinates не изменены; прямого level/EXP/resource seed нет.

## Changed files

Bounded exception: 24 exact paths одной cohesive Goal033A artifact family.

- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
- `build.xml`;
- `docs/phantoms/NEW_DIALOG_START_MESSAGE.txt`;
- `docs/phantoms/PHANTOM_CURRENT_STATUS.md`;
- `docs/phantoms/reports/033A-causal-background-catchup-resume.md`;
- `java/org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.java`;
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundCatchupState.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundCatchupStateCodec.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundCatchupStore.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundOperationKey.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java`;
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java`;
- `java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java`;
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecycleBridge.java`;
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecyclePort.java`;
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomHistoricalBackgroundGoal033ASuite.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`.

Master-plan change исправляет только required Goal031 docs marker (`Goal 031`). Handoff пока сохраняет gate и указывает следующий этап repeat Goal033, not Goal034. Другие chronicles, task packages, topology data/schema, GamePackage/Unity/Lua/provider/media scope не менялись.

## No-cheat audit

- Нет `setLevel`, direct EXP/SP injection, reward multiplier или manually granted adena/items/shots.
- Нет hardcoded mob/anchor fallback; Java literals используются только как schema/reason/digest identifiers.
- Нет direct teleport; historical travel вызывает existing factual `advanceTravel()`.
- Нет второго Player lifecycle, per-profile timer, scheduler, thread или future.
- Нет human-level/histogram input.
- DEAD idle не восстанавливает HP и не меняет progress/vitals/inventory/position/RNG.
## Tests/results

Final-code focused and regression results:

- `compile-tests`: PASS; 2225 production sources + 133 test sources; только 2 existing `System.runFinalization()` deprecation warnings вне scope.
- `phantom-historical-background-goal033a-test`: PASS, 4/4 after final death-idle fix.
  - strict codec + disjoint live/historical identity;
  - production population/planner/baseline/fences/COMPLETE;
  - atomic rollback, ambiguous commit reconciliation, duplicate idempotency, byte-identical restart and per-interval continuous deterministic oracle for progress/EXP/SP, vitals/death, inventory/resources, position, RNG, receipt, goal and cursor;
  - stale hash fail-closed + Goal032 cascade reset.
- `phantom-background-test`: PASS after final transaction change, 4:51; все model/transaction/lifecycle/decision/server-integration/performance/materialization-abort/quiescence/inventory/authority/audit/recovery/real-login suites green.
- `phantom-decision-core-test`: PASS, 36/36.
- `phantom-canonical-population-topology-ingress-goal033a1-test`: PASS, 4/4.
- `phantom-topology-production-corpus-test`: PASS, 7/7.
- `phantom-navigation-core-test`: PASS, 50/50.
- `phantom-background-model-test`: PASS, 7/7.
- Goal030 CP3 restart/release rollback: PASS, 3/3 + 3/3.
- Goal031 preflight/readiness/docs: PASS, 8/8 + 3/3 + 4/4.
- Goal032 ownership/reseed/docs: PASS, 3/3 + 2/2 + 1/1.
- Финальный `ant jar`: PASS, выполнен ровно один раз после всех focused gates; 2225 production sources, LoginServer/GameServer/DatabaseInstaller jars собраны.

Первый усиленный focused rerun намеренно обнаружил `catchup.dead_replan_required` после real canonical farm death (3/4). Реализация была исправлена отдельным zero-reward DEAD-idle transaction action; следующий запуск прошёл 4/4. Failure не скрыт и production DB для диагностики не использовалась.

## Production DB statement

Production DB `l2jmobius` не использовалась. Все DB-mutating suites запускались только через existing hard guard с exact target `l2jmobius_phantom_test`. Focused suite удаляет только созданные им profile/character/account rows; broad cleanup отсутствует.

## Encoding/static checks

Перед commit выполняются две отдельные проверки по exact changed paths:

- mojibake markers;
- escaped Cyrillic (`\\u04xx`/`\\u05xx` и XML `&#x04xx;`/`&#x05xx;`).

Также проверяются added lines/new files на forbidden level/EXP/teleport/thread API, production DB literals, whitespace errors и exact scope allowlist.

## Known limitations / next boundary

- Goal033 schedules, productive-interval policy, pace и living-population ecology не реализованы.
- Catch-up owner не имеет собственного scheduler: caller обязан bounded-вызовами `begin/advance/status` довести request до terminal status.
- Изменение GameKnowledge/Topology/authority generations требует explicit replan-required handling; stale data никогда не применяется молча.
- Следующий этап после SUCCESS — повтор Goal033, не Goal034.
## Git command ledger

TASK-authorized read-only baseline/scope/self-review commands (повторные одинаковые `git status --short --branch` сведены в одну строку):

```text
git fetch origin feature/phantom-world
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git status --short --branch
git diff --check -- L2J_Mobius_CT_2.6_HighFive/PHANTOM_DEVELOPMENT_MASTER_PLAN.md L2J_Mobius_CT_2.6_HighFive/build.xml L2J_Mobius_CT_2.6_HighFive/docs/phantoms/NEW_DIALOG_START_MESSAGE.txt L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundOperationKey.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecycleBridge.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecyclePort.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
git diff --stat -- L2J_Mobius_CT_2.6_HighFive/PHANTOM_DEVELOPMENT_MASTER_PLAN.md L2J_Mobius_CT_2.6_HighFive/build.xml L2J_Mobius_CT_2.6_HighFive/docs/phantoms/NEW_DIALOG_START_MESSAGE.txt L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundOperationKey.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecycleBridge.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecyclePort.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
git diff -U4 -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java
```

Они использовались только для exact baseline, dirty-scope inventory, whitespace/stat review и production wiring self-review. Commit/push/history ими не менялись.
Additional forbidden-added-line audit command:

```text
git diff --unified=0 -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundOperationKey.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecycleBridge.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecyclePort.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java
```

TASK-authorized exact closeout commands after final jar:

```text
git add -- L2J_Mobius_CT_2.6_HighFive/PHANTOM_DEVELOPMENT_MASTER_PLAN.md L2J_Mobius_CT_2.6_HighFive/build.xml L2J_Mobius_CT_2.6_HighFive/docs/phantoms/NEW_DIALOG_START_MESSAGE.txt L2J_Mobius_CT_2.6_HighFive/docs/phantoms/PHANTOM_CURRENT_STATUS.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/033A-causal-background-catchup-resume.md L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundCatchupState.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundCatchupStateCodec.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundCatchupStore.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundOperationKey.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecycleBridge.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecyclePort.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHistoricalBackgroundGoal033ASuite.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
git diff --cached --check
git diff --cached --name-only
git commit -m "phantom(goal-033a): complete causal historical background catchup"
git status --short --branch
git rev-parse HEAD
git push https://github.com/kpCat/L2J HEAD:feature/phantom-world
```

Staging/commit ограничены перечисленными 24 paths. Push non-force. Unrelated untracked artifacts не включаются.
## Final acceptance

```text
Goal033A1 ingress consumed: YES
Canonical target/anchor planner: YES
Initial canonical Background baseline: YES
Exactly-once historical cursor: YES
Restart equivalence: YES
Normal materialization fence: YES
Direct XP/free resources/teleport used: NO
Human-level dependency: NO
Production DB used: NO
Goal033 causal blocker closed: YES
Next: repeat Goal033
```