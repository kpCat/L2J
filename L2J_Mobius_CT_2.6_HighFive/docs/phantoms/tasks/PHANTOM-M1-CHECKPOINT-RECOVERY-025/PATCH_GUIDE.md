# PATCH_GUIDE — точные исходные точки и требуемая форма

Base07df36d…; выдержки лежат в source-excerpts, source blob IDs в SOURCE_MAP.
Локальные line numbers могут сместиться после patch; ориентир — named methods.

## A. PhantomHistoricalBackgroundService.ensureBaseline (base примерно308–360)
CURRENT: VERIFY_PENDING обрабатывается только при retained owner; owner отсутствует →
общая проверка READY/DEAD → catchup.baseline.conflict. В advance FAILED baseline.conflict
также может отсечь восстановление раньше resolver.
REQUIRED: один приоритетный prerequisite-control путь до ordinary baseline/FAILED gate;
он разрешает exact owned pending независимо от presence/goal suitability, не выполняет
simulation. После SUCCESS заново читать catchup/goal/state. Если старый FAILED был
только следствием pending, заново проверить exact prerequisites; не менять request/cursor.

## B. BackgroundService.recoverAbandonedMaterialization (~780–830)
CURRENT: profile link + BACKGROUND lease + absence World/autosave + state==MATERIALIZED.
REQUIRED: отдельный recovery helper для VERIFY_PENDING или typed generalization.
Оставить существующие проверки ссылок, lease и отсутствия live/retained Player.
Внутри использовать `_transactions.reconcileVerifyPending(profileId, characterObjectId)`
для VERIFY_PENDING и existing abortMaterialization для abandonedMATERIALIZED.
No extra canonical writer. Если первый вызов вернул MATERIALIZED без owner, законно
завершить abandoned transition до gameplay. Никакой рекурсии без state/version progress.

## C. BackgroundService.captureVisibleArrival (~933–985)
CURRENT:
```
try { return PlayerNativeWork.checkpoint(player,
    () -> captureVisibleArrivalQuiescent(profileId, player, goal, anchorId)); }
catch (RuntimeException failure) { return false; }
```
PROBLEM: теряются стадия/first exception; caller не отличает invalid preflight от pending
commit/успешного durable commit с неудачным index. Нужен typed result, совместимый boolean
wrapper допустим только для старого caller, не для production path после025.

REQUIRED SHAPE (имена record уточнять единообразно):
```
record VisibleCheckpointResult(Status status, CheckpointKey key,
                               Stage phase, String reason) {}
```
Сохранять first throwable отдельно bounded/no repeated stringify. Поле noMutationVerified
выдаётся только preflight, а не определяется по отсутствию `_intent`.
Обычные `tryAcquireAction`/permitsDecision не используются для уже существующего control.

## D. PhantomNativeWorkScope.checkpoint (~179–241)
CURRENT: successful=false после RuntimeException; finally оставляет SEALED, но при
pending=false не создаёт доступного resume. `_checkpointThread` уже null.
REQUIRED: для typed owned caller завершать фазу через DESIGN outcome. Безопасный no-write
отказ возвращает OPEN только после exact owner/permanentSeal/incident/work validation.
Неизвестный write outcome сохраняет закрытый lifetime и bounded verification task.
Не менять conservative behavior generic Supplier checkpoint без phase witness.

## E. PhantomVisibleFarmTravel.arrive (~90–170)
Обработать typed outcomes в одном switch: WAIT (одна coalesced continuation),
RESOLVE_RECEIPT, RETRY_PREFLIGHT, COMMITTED_PUBLICATION, RESUMED, FENCED.
Существующий pending контроль исполняется раньше ordinary `_permitsOrdinary`.
Не удалять Journey/контроль из-за закрытого собственным checkpoint ActionLease.
После SUCCESS ещё раз проверить настоящую local position и goal/epoch, только затем
start native farming. Terminal policy ошибку передать existing failure/replan, не retry forever.

## F. earned callback continuation
WorkScope.onQuiescent/MaterializationService.registerDrainRetry уже существуют.
Обобщить exact request completion для временного checkpoint только после доказательства
очереди/locks. Один callback publish, не inline store внутри complete. Если не due —
WAIT с прежним сроком, не новый timer для earned body. Если deadline реально пропущен,
сохранить причинную историю и old owner; не отменять обязательство.

## G. Authority guarded producer
Если first exception — combat flag/unsupported simulation context:
- native capture preflight отделить от background-policy проверки;
- собственно simulation guard оставить;
- exact-native-only capture делать только при sealed owner/no writers;
- если policy требует defer: до irreversible stage, затем bounded retry/control;
- ни один generic context false не оправдывает принудительный READY/SUPPORTED.
Это scope резерв, а не команда безусловно менять requireSupportedPlayer.
