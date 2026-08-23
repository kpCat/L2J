# Goal 028 Checkpoint 4 — bounded operator economic audit (resumed)

## Status

- Delivery status: `SUCCESS`.
- Goal 028B: `ACCEPT`.
- Goal 028 Checkpoint 4: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- Goal 028 overall: `IN_PROGRESS`.
- Required parent: exact `13e57cfa4dd1824d544e82654985152ef1dde41e`.
- Branch: `feature/phantom-world`.
- Upstream: `origin/feature/phantom-world`.
- occurred_context_compaction: `no`.

## Summary

Реализована только read-only команда:

`//phantom economy <profileId>`

Для одного положительного profile ID она показывает текущую nonterminal Goal022 operation, только количество её reservations, retained-window summary максимум по 256 terminal audit rows, максимум 8 newest-first audit rows и отдельную latest durable Goal014 NPC commerce receipt section.

Новый ledger, persistence schema, DDL, migration, cache, polling, worker, timer и all-profile scan не добавлялись. Accepted Goal028B index/migration не изменены. Production DB не открывалась.

## Changed files

1. `build.xml` — focused resumed CP4, exact Goal022 и exact Goal014 receipt targets.
2. `dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java` — syntax/delegation/rendering `//phantom economy <profileId>`.
3. `java/org/l2jmobius/gameserver/phantoms/PhantomEconomicAuditView.java` — bounded immutable read-only projection, retained summary и safe receipt view.
4. `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — same receipt-store wiring и typed operator facade.
5. `java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyReservationService.java` — smallest SELECT-only `findAudit(profileId, limit)` и immutable `AuditRecord`.
6. `test/java/org/l2jmobius/gameserver/phantoms/PhantomEconomicAuditGoal028Checkpoint4Suite.java` — focused six scenarios и отдельный exact Goal022 mode.
7. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — focused suite modes registration.
8. `docs/phantoms/reports/028-checkpoint-4-economic-audit-resumed.md` — этот отчёт.

User-owned untracked task packages оставлены read-only и не входят в commit.

## Goal022 query and limit proof

`PhantomEconomyReservationService.findAudit(long profileId, int limit)`:

- отклоняет `profileId <= 0`;
- отклоняет `limit < 1` и `limit > 256`;
- выполняет ровно SELECT;
- использует exact access clause:

```sql
WHERE profile_id=? ORDER BY audit_id DESC LIMIT ?
```

- bind order: profile ID первым параметром, limit вторым;
- возвращает immutable `List.copyOf` в DB newest-first order;
- `AuditRecord` immutable и не содержит `consequence_payload`;
- SELECT projection также не читает `consequence_payload`.

Mock-JDBC focused proof подтвердил exact SQL, binds `[77,2]`, order audit IDs `[9,8]`, bounds 0/257 rejection и отсутствие raw payload.

## Current operation race semantics

Current operation переиспользует только:

1. `findActive(profileId)`;
2. `findReservations(operationId)` только для `size()`;
3. bounded confirmation `findActive(profileId)`.

Если operation исчезла или изменилась между reads, view возвращает `CHANGED` без operation/reservation details. Если первый read пуст — `NONE`. Ни один mutation/reconciliation path не вызывается.

## Retained-window summary and overflow semantics

Summary всегда явно называется `retained-window (max 256, not lifetime)`.

Она содержит:

- terminal state counts;
- `itemsConsumed`;
- `itemsProduced`;
- `adenaSource`;
- `adenaSink`;
- `crystalsProduced`;
- `targetItemsDestroyed`;
- `totalsSaturated`.

Каждый nonnegative long total складывается saturating-add: если `Long.MAX_VALUE - current < value`, итог этого total фиксируется на `Long.MAX_VALUE`, а общий `totalsSaturated` становится `true`. Остальные totals продолжают независимо суммироваться. Focused proof: `Long.MAX_VALUE + 1 -> Long.MAX_VALUE`, flag `true`; соседний nonoverflow total остался exact `14`.

Admin rendering ограничен `Math.min(RENDER_LIMIT, rows)`, где `RENDER_LIMIT = 8`.

## Same receipt-store ownership proof

В production construction `PhantomSystem` создаёт ровно один:

```java
_commerceReceiptStore = new PhantomCommerceReceiptStore(productionProfiles);
```

Тот же exact instance передаётся и в:

- `new PhantomCommerceService(..., _commerceReceiptStore, ...)`;
- `new PhantomEconomicAuditView(_economyReservations, _commerceReceiptStore)`.

В audit view используется только read seam `receipts::find`. Второго receipt store owner и вызова `save` нет.

Latest receipt DTO содержит только `operationKey/goalId/goalRevision/kind/state/resumeCount`, signed `before -> expectedAfter (delta)` для primary/secondary/object counts и `positionChanged`. Координаты, instance ID, request, NPC/object IDs и raw inventory не попадают в DTO/rendering.
## Typed facade and lifecycle semantics

`PhantomSystem.operatorEconomicAudit(profileId)` возвращает immutable typed result:

- `AVAILABLE`;
- `EMPTY`;
- `INVALID`;
- `RUNTIME_NOT_CONFIGURED`;
- `ECONOMY_UNAVAILABLE`;
- `READ_FAILED`.

Facade не запускает runtime. Drained/unconfigured runtime остаётся unconfigured; stopped runtime возвращает economy unavailable. Focused test подтвердил, что audit request после `operatorDrain` не меняет desired mode и не auto-enables runtime.

## Read-only and privacy proof

Audit production path не содержит и не вызывает:

- `reserve`;
- `transition`;
- `reconcile`;
- `renew`;
- `save`;
- `setGoal`;
- `clearGoal`;
- `operatorEnable/operatorDrain/operatorDisable`;
- domain actions.

Не добавлены all-profile scans, threads, timers, scheduled work, polling, cache или persistence. Admin вызывает только typed facade. Audit SELECT и DTO не содержат `consequence_payload`; receipt view/rendering не содержит coordinates/raw inventory/object/request data.

## Commands and test results

JDK: `C:\Program Files\Java\jdk-25.0.4`.

Ant: `.phantom-local\apache-ant-1.10.17\bin\ant.bat`.

Ordered final gates:

1. `phantom-economic-audit-goal028cp4-test` — PASS, 6/6.
2. `phantom-economic-audit-goal022-test` — PASS, 2/2.
3. `phantom-commerce-receipt-goal028cp4-test` — PASS, 7/7.
4. `phantom-operator-observability-goal028cp1-test` — PASS, 6/6.
5. `phantom-operator-runtime-controls-goal028cp2-test` — PASS, 6/6.
6. `phantom-selected-slow-stuck-goal028cp3-test` — PASS, 6/6.
7. Cheap Goal028B static contract — PASS: accepted DDL/migration zero diff; exact index remains present.
8. Ровно один final `jar` после final gates — PASS; GameServer/LoginServer/DatabaseInstaller jars built, server jars copied в `dist/libs`.

Не запускались broad/performance/stress/soak/replay gates.

Дополнительные bounded checks:

- required parent/branch/upstream — PASS;
- `git diff --check` — PASS;
- exact scope inspection — PASS;
- UTF-8 without BOM — PASS;
- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

Pre-final диагностические остановки, не являющиеся final gate failures:

- plain `ant` отсутствовал в PATH; найден и использован existing local Ant.
- первый compile-tests обнаружил две неверно escaped test-string кавычки; исправлены exact-anchor edit, после чего focused CP4 6/6 PASS.
- historical `phantom-economy-reservation-schema-test` остановился в `before-all` из-за stale local schema manifest после accepted 028B baseline. Re-provision требует отсутствующих DB admin environment credentials; accepted 028B не трогался. Требуемый exact touched Goal022 gate выполнен отдельным non-DB mock-JDBC mode и прошёл 2/2.

## DB, schema, config and performance

Production DB не открывалась и не изменялась. Новые DB/schema/migration/config artifacts отсутствуют. Accepted 028B DDL/migration frozen и имеют zero working diff.

Performance/stress/soak запрещены task scope и не запускались. Structural bounds: один profile, audit limit максимум 256, rendering максимум 8, current race максимум два `findActive` и один `findReservations`; no background execution.

## Deviations, limitations and risks

- Test DB reprovision не выполнялся из-за отсутствующих explicit admin env credentials; вместо stale historical aggregate использован новый exact touched Goal022 non-DB gate.
- Audit read является snapshot-like bounded view, а не transactional cross-table snapshot. Current operation disappearance/change намеренно показывается `CHANGED`; terminal audit и receipt могут отражать соседние durable commits.
- Totals относятся только к retained Goal022 window, никогда не к lifetime.
- Independent review CP4 остаётся обязательным.

## Git and delivery

Разрешённые Git-команды использовались для required parent/branch/upstream, bounded status/diff/scope/frozen-028B verification, ordinary commit и push. Amend/rebase/reset/squash/merge/force push не использовались.

Commit subject: `feat(phantoms): add operator economic audit`.

Commit SHA: фиксируется в финальном delivery сообщении (self-referential SHA невозможно записать внутрь того же atomic commit).

Push result: фиксируется в финальном delivery сообщении после ordinary push.

## Next step

Independent review Goal 028 Checkpoint 4. До acceptance CP4 остаётся `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`, Goal 028 — `IN_PROGRESS`.