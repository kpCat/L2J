# FIX_LEDGER027

Начало: 2026-10-08 16:52:42 UTC. Deadline: 22:52:42 UTC; semantic freeze: 21:22:42 UTC.
Первый full-server probe требуется до 18:22:42 UTC.

## Read-first и scope

Exact remote/base: 882afb37821bdc5d7b8ec4e982e4e1e2c411cbbf; native own worktree создан detached.
Main и foreign worktrees не изменяются. TASK027 copied из main; TASK026 читается из exact-base checkout.
Прочитаны High Five AGENTS, TASK/DESIGN/PLAN/ROOT_CAUSES/SCENARIOS/SOURCE_MAP/ACCEPTANCE,
workflow/package contracts, architecture master sections, RESULT026, runtime wrappers024/025/026,
System/Background/Death/Materialization/native-work и composed fixtures.

Bounded exception: максимум SOURCE_MAP путей; основной semantic scope System/Background/Death/
Materialization. Условные пути только после своего RED. Не менять schema, pool architecture,
global listener traversal, catalog, formulas или farm evaluator. Без субагентов.

Локальные аналоги: bounded ecology wait; configured native shutdown suite; native-context handoff
fixture; exact own026 test guard; retained native drain retry. Новые runtime wrappers: три;
один diagnostic observer reuse026. Две категории native epochs никогда не склеиваются.

Ruling: TEST027 lane встроен в existing026 guard — один preauthorized test-only equivalent
path (HeadlessPlayerTestEnvironment), exact manifest/config/export/database guards сохранены.
Общая TEST schema metadata не меняется.

## Command ledger

- read: git status --short; git rev-parse HEAD; git branch --show-current;
  git rev-parse --abbrev-ref --symbolic-full-name '@{u}'; git worktree list --porcelain;
  git remote -v; git ls-remote origin refs/heads/experiment/m1-candidate007-observe008.
  Разрешение: TASK027 initial base/worktree pin. Первый ls-remote sandbox network denied;
  elevated pin успешен. Main dirty foreign paths сохранены.
- own TEST: S01 composed actual native load/transition, latch before completion;
  проверка pending на unchanged production, без failure flag.
- own runtime: Prepare027 разрешает только отсутствующие own027a..h clones из immutable026
  read export. Product reseed/reset запрещён; TEST reset только existing guarded fixture route.

## Выполнение

S01 regression добавлен до изменения production. Первое blocker observation будет записано
runner-ом: background materializingTransitionClaims + exact profile/epoch.

S01 genuine RED: RED_S01_COMPOSED027B native-context-handoff 0/1, exit1.
Первый blocker: background.materializingTransitionClaims=1, profile10001,
epoch207292592836800. Initial hook false за1462800ns; systemFAILED, materializationRUNNING,
entries1, topologyRUNNING. Release350ms завершает тот же accepted native load; failure flag
не установлен. Предыдущие wrapper/setup ошибки INVALID, не RED; исходные logs сохранены.

Ruling S01: System boolean busy ошибочно terminal. Перенести существующий10s deadline на
все phases; один typed attempt на instance. Conditional Materialization/MaterializedPlayer/
NativeWorkScope необходимы для неблокирующего drain того же accepted owner, без ожидания
его callback на shared worker. Используется existing instant executor; beginTerminalDrain
закрывает только новые roots и unearned timers, оставляет earned tickets/owned-store guards.
Новые service CLEANUP_PENDING и System STOPPING обозначают незавершённую работу честно.

S01 GREEN_S01_R1_027 1/1 exit0, same accepted transition350ms, no retained owner.
D05 genuine RED_D05_QUEUED027 0/1 exit1: stock doDie -> native town return -> accepted
queued reconcile, но drained=true до executor entry. Ruling: accepted count increment до
publication; close сохраняет exact queued Death; stop continuation сохраняет native уже
возвращённого object/epoch, не делает новый revive/materialize. Старый running count отдельно.

R2 native suite: D03 cold DEAD и D05 accepted close PASS; S02 FALSE за2.93s без incident.
Причина: native callback публикует stop-control; concurrent continuation успевает COMPLETE
и очищает configured instance, пока внешний wait вызывает тот же exact attempt. R3 читает
COMPLETE на удержанном exact instance. R3 suite3/3; S02 callback1/child1, selfWait267600ns,
ownerDETACHED/outstanding0/timers0. Cold DEAD на base не требовал conditional admission fix;
не объявляется RED. E01 diagnostic entered-before-owner-monitor PASS1/1.

R3 build jar/compile-tests PASS; только два существующих runFinalization warnings.
First full-server candidate ещё не acceptance/frozen artifact. Pending background recovery
на scheduler и конечная cleanup negative coverage остаются непроверенными.
