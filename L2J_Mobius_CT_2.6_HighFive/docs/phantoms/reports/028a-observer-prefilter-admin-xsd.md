# Goal 028A — observer prefilter + canonical admin XML

## Status

- Delivery status: SUCCESS.
- Goal 028A: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 028 CP1: `CHANGES_REQUIRED_PENDING_028A_INDEPENDENT_REVIEW`.
- Goal 028 overall: `IN_PROGRESS`.
- Required parent: exact `6147c60f79d2829082bbeb45e8ab93c2af4a1c6a`.
- Branch: `feature/phantom-world`.
- occurred_context_compaction: no.

## Summary

Исправлены ровно два дефекта independent CP1 review. Decision observer получил source-compatible prefilter до observer snapshot path, а Phantom admin entry переведён с invalid `requireConfirm` на canonical `confirmDlg` и защищён реальной XSD-валидацией.

Goal028 CP2 controls, replay, stuck/slow policy, economic audit, DB/domain, broad cleanup, performance/stress/soak не затрагивались.

## Changed files

1. `java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java` — default `DecisionObserver.interested(profileId)`, prefilter до lock/snapshot, isolation `interested` и `onDecision` exceptions.
2. `java/org/l2jmobius/gameserver/phantoms/PhantomSelectedDecisionTrace.java` — production observer implementation и cheap no-allocation `interested`/`isSelected` check через final fields и volatile selected id.
3. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — diagnostics-enabled wiring использует selected trace observer; diagnostics-disabled observer остаётся `null`.
4. `dist/game/config/AdminCommands.xml` — Phantom entry использует `confirmDlg="false"`.
5. `test/java/org/l2jmobius/tests/phantoms/PhantomOperatorObservabilitySuite.java` — focused prefilter/isolation/SAM/order regressions и actual XML/XSD validation.
6. `docs/phantoms/reports/028a-observer-prefilter-admin-xsd.md` — этот отчёт.

`dist/game/data/xsd/AdminCommands.xsd`, `AccessRight` и user task packages не изменялись.

## Preallocation proof

`notifyObserver` выполняет `_observer.interested(workItem.profileId())` сразу после null check и до:

- `synchronized (_monitor)`;
- `_slots.get(...)`;
- `snapshotLocked(slot)`;
- создаваемого внутри snapshot пути `List.copyOf(...)`.

`false` и любой `Throwable` из `interested` дают immediate return. `onDecision` по-прежнему вызывается вне monitor, а его `Throwable` изолируется. Default `interested=true` оставляет `DecisionObserver` functional interface: существующая lambda в focused suite компилируется и исполняется.

Production `PhantomSelectedDecisionTrace.interested` делегирует в `isSelected`, который выполняет только проверки enabled/profile id и volatile primitive comparison, не создавая объектов и не беря trace monitor. `observe` сохраняет повторную synchronized проверку selection. При diagnostics disabled `PhantomSystem` передаёт `null` observer.

Focused regression подтверждает false-prefilter без `onDecision`, isolation exception из prefilter, isolation exception из callback, lambda compatibility и structural ordering prefilter до observer lock/snapshot build.

## XML/XSD proof

Phantom entry теперь содержит `confirmDlg="false"` и не содержит `requireConfirm`. Focused CP1 suite загружает реальный `dist/game/data/xsd/AdminCommands.xsd` через JAXP `SchemaFactory`, создаёт validator и валидирует полный `dist/game/config/AdminCommands.xml`.

Canonical contract подтверждён без изменений schema/runtime:

- `AdminCommands.xsd` разрешает `confirmDlg` типа `xs:boolean`;
- `AccessRight(StatSet)` читает `confirmDlg` с default `false`;
- actual full XML validation прошла в финальном CP1 gate.

## DB, migrations and configs

- Production/test DB не использовались и не изменялись.
- Миграции, schema DB и Phantom config keys не добавлялись.
- Изменён только существующий native admin access XML entry.

## Commands and test results

Baseline:

- `git status --short --branch` — branch/upstream и user untracked packages зафиксированы.
- `git rev-parse HEAD` — exact required parent PASS.
- `git branch --show-current` — `feature/phantom-world` PASS.
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` — `origin/feature/phantom-world` PASS.

Final exact Ant sequence:

`& '.\.phantom-local\apache-ant-1.10.17\bin\ant.bat' phantom-operator-observability-goal028cp1-test phantom-decision-core-test phantom-skeleton-test jar`

- Goal028 CP1 focused — PASS, 6/6, seed `20260725001`.
- Exact DecisionEngine core — PASS, 35/35, seed `20260725001`.
- Exact PhantomSystem disabled/lifecycle skeleton — PASS, 14/14, seed `20260725001`.
- Ровно один реально запущенный final `jar` target — PASS; LoginServer.jar, GameServer.jar и DatabaseInstaller.jar собраны, рабочие LoginServer.jar/GameServer.jar скопированы в `dist/libs`.
- Total final Ant time: 1 minute 6 seconds.

Первая команда с bare `ant` завершилась до запуска Ant/targets: executable отсутствовал в PATH. Поэтому она не выполняла `jar` и не является jar invocation.

No DB/domain/broad/performance/stress/soak gates запускались.

## Static and encoding checks

- `git -c core.whitespace=cr-at-eol diff --check` — PASS.
- Strict UTF-8 decode changed production/test/XML allowlist — PASS.
- UTF-8 BOM — 0.
- mojibake-маркеры в изменённых файлах проверены — PASS, 0 совпадений.
- escaped Cyrillic в изменённых файлах проверены — PASS, 0 совпадений.
- `.028a.tmp` artifacts — 0.
- Generated jars не появились в Git diff.

## Performance and bounds

Отдельные performance measurements не запускались по прямому запрету task. Structural cost для unselected profile ограничен null check, observer call и primitive selected-id comparison; observer snapshot lock и allocations не достигаются.

Новых threads, timers, futures, queues, persistence или unbounded collections нет.

## Deviations, limitations and risks

- `apply_patch` не вызывался. Все изменения внесены unique exact-anchor UTF-8-no-BOM temp + atomic overwrite operations.
- Две editing attempts завершились до target write: первая обнаружила LF вместо ожидаемого CRLF anchor; вторая выявила unsupported null backup для `File.Replace`. После этого использован поддерживаемый atomic `File.Move(temp, target, true)`; temp cleanup проверен.
- Один PowerShell test-edit command и один initial encoding command завершились parser error до file access/write; повторены bounded безопасным способом.
- Independent review Goal 028A ещё не выполнен; статусы намеренно не повышены до ACCEPT.

## Git and delivery

Git использовался для обязательных baseline/status/exact-parent/upstream, bounded diff/diff-check/scope, staging, ordinary commit и push. User-owned untracked task packages остаются вне staging.

Commit subject: `fix(phantoms): bound operator diagnostics overhead`.

Commit SHA: this atomic report-bearing commit; exact SHA фиксируется в финальном сообщении после commit.

Push result: фиксируется в финальном сообщении после push.

## Next step

Independent review Goal 028A. Goal 028 остаётся `IN_PROGRESS`; Goal028 CP2 work не начат.
