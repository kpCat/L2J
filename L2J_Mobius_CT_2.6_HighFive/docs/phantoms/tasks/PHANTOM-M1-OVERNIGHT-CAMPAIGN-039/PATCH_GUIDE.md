# PATCH_GUIDE039 — реальные точки текущего source

Все CURRENT ниже прочитаны на BASE8bf4df23; номера ориентировочные, искать точный метод.
Это карта проверяемых гипотез. Она НЕ утверждает, что уже найдена historical причина437.

## 1. Наблюдение progress перед ранними выходами
Файл:phantoms/background/PhantomVisibleAutoPlay.java, noTargetExpired(), ~235–298.
```java
// CURRENT
if (resourcePause(session, player))
{
    synchronized (session) { return session._resourceExpired || stalled(session, _clock.getAsLong()); }
}
if (session._engagement != null)
{
    final var outcome = session._engagement.advance(profileId, player, goal, npcId,
        session._policy::targetSelectionLoad, _clock.getAsLong());
    if (outcome == PhantomEngagementController.Outcome.EXHAUSTED) { return true; }
}
final var progress = nativeProgress(player, session._epoch);
// ... observeProgress(session, progress, now);
```
PROBLEM-кандидат: реальный ранее неучтённый damage может не попасть в debt observer,
если resource/action ветвь возвращает управление раньше. Native RED должен создавать
реальный progress плюс pause, а не вручную подменять counters/clock производства.
REQUIRED SHAPE при подтверждении: один exact owner snapshot и observeProgress перед
решением о просроченном долге; далее только existing pauses/controller. Не сбрасывать
usefulSince «потому что бот сидит/план поменялся». Новый epoch не наследует старый долг.

## 2. Busy до проверки состояния continuation
Файл:phantoms/actions/PhantomEngagementController.java, advance().
```java
// CURRENT
if (player.isCastingNow() || player.isCastingSimultaneouslyNow()
    || player.isAttackingNow() || player.getQueuedSkill() != null) { return _outcome; }
// ... native evidence/progress, target validity and attempt horizon later
```
Различить actual in-flight earned ticket и застрявшую ссылку/очередь/намерение.
Не делать setCastingNow(false) «для починки». Подтверждать reserve/submit/start/end,
cast target generation и native outcome. При awaiting native input не выпускать конкурирующий
root. Незавершённое тело законно ждётся; повторный выбор той же мёртвой цели — другой случай.
Тест: цель умерла между честным launch и impact → original native завершён → следующий
допустимый target. Ноль урона одного late cast допустим; бесконечный повтор не допустим.

## 3. Capability и фактическое действие
PhantomEngagementPlanner.inspect(Player, Creature, int), offensiveSkill(): выбирается
первое доступное отрицательное умение поID; при null вычисляется physical range.
Но окончательное умение выбирает stock AutoUse. Это точка сопоставления, не готовая причина.
REQUIRED SHAPE: согласовать фактически применимое действие/range/readiness в существующем
controller/optional directive. Повторная native legality остаётся обязательной. Fallback
melee только если он реально выбран policy и native допустим, не следствие null по cooldown.
Stock defaults обычного Player не менять. Read-only alternative snapshots короткоживущие.

## 4. Scope источника сохранения
PhantomBackgroundTransaction.finalizeOwnedStore(), ~351–371:
```java
// CURRENT
final Result result = resolveOwnedStore(connection, component, intent, profileId, objectId, epoch, false);
_faultInjector.inject(FaultPoint.BEFORE_OWNED_FINALIZE_COMMIT);
commitAttempted = true; connection.commit();
_faultInjector.inject(FaultPoint.AFTER_OWNED_FINALIZE_COMMIT);
return result;
```
resolveOwnedStore() уже lock/read canonical+items+skills, проверяет afterMatches,
пишет completed state/context и удаляет exact owned receipt. Это подходящая исходная
транзакционная граница для immutable witness. SUCCESS callback сам по себе не заменяет
проверку actual rows; использовать проверенные факты этой же транзакции, не новый поздний SELECT.

PhantomBackgroundService.afterStore(), ~1346–1378 уже имеет:
```java
// CURRENT
final var finalized = transaction(() -> _transactions.finalizeOwnedStore(
    profileId, player.getObjectId(), _intent.materializedAtNanos()));
status = finalized.status().name();
completedState = finalized.state();
if (!finalized.successful()) { throw new IllegalStateException("OWNED_STORE_FINALIZE:" + status); }
finalizedSuccessfully = true;
checkpointStage(player, "FINALIZED");
// ... LocalPlayPhantomStoreJournal.end(..., status, completedState);
```
Не использовать один только intent.after() вместо реально returned completedState:
targetState/каноническая нормализация должны учитываться exact source codec.
Не расширять synchronous LocalPlayPhantomStoreJournal на всех10000: у него свой bounded
selected8 и синхронный I/O. Для массового факта reuse имеющийся premain/export pipeline.

## 5. Поздняя SQL фотография не равна исходному commit
Текущий tools/verify_lifetimes038.py:
```python
# CURRENT
assert boundary.native_fields_exact(final, sections), 'Native characters/skills/full inventory SQL parity'
assert int(state['row_version']) == int(final['preparedRowVersion']) + 1 and bytes.fromhex(state['payload']) == payload, 'Late SQL boundary mismatch; producer/lineage UNKNOWN'
```
REQUIRED SHAPE: сохранять обе проверки на exact commit-boundary, а не удалять их.
Поздний read сравнивать с последним доказанным state через цепочку commit witnesses.
Нужен RED: save v→background mutation v+1→late read. Старый verifier даёт UNKNOWN;
новый различает доказанный successor и missing/tampered successor, не «всегда принимаетv+1».
71/66 native parity расследовать отдельно: совпадающая версия+несовпадающие canonical
поля не объясняется одной версией component и остаётся hard failure.

## 6. Возврат после смерти и фона
Reuse recoverOrdinaryNativeCorpse/requestRecovery в PhantomBackgroundService,
prepareNativeRecoveryControl/typed handoff в HistoricalBackgroundService,
existing Synthetic/soft retention и captureVisibleArrival. Не вводить вторую resurrection.
No-route → typed local replan уже существует; skill/target change должны отменять только
собственный request с exact epoch, не маршрут соседнего владельца.

Все изменения сначала в существующих methods из SOURCE_MAP. Новый helper только если
без него интерфейс действительно невозможно оставить небольшим; никакого общего AI rewrite.
