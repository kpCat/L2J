# DESIGN034 — небольшая причинная вертикаль

## Продуктовый смысл
Материализованный Phantom должен сохранить реальный заработанный прогресс и после ухода человека/закрытия календаря освободить только **своё** удержание, не оставляя phantom-specific Player, native work, таймер, autosave, action или lease. Если работа ещё не завершена, отказ остаётся честным и не должен быть скрыт. Старые инциденты из стартовой БД не считаются новыми по одному совпадению числа.

## Разделение источников
1. **Startup/pre-Synthetic baseline:** точная новая JVM incarnation, linked profileId/objectId/epoch, full current `PhantomMaterializationService.snapshot()` и `cleanupBlockers()`; separate archived `cleanupIncidents` from retained map. Inspect known retained DB via READ ONLY; original data untouched.
2. **During live activity:** actual birth/retirement with time-ordered JFR/JDWP/read-only fields/flight recorder. Do not use long VM suspension, global method-exit trap or synchronous SQL/DB inside gameplay callbacks.
3. **After shutdown:** typed `PhantomSystem.shutdownProgress()` with `StopPhase/StopOutcome/blocker`; actual materialization service state, activeByProfile count, first/latest cleanup incident; real World/AutoSave/native lease, owned pending store and OS stock shutdown. Preserve exact process/incarnation and code/JAR hash.
4. **Persistence:** exact canonical native FINALIZED receipt and subsequent version/payload lineage, independently from cleanup admission; missing intermediate receipts => OPEN, not silent latest-SQL substitute.

## Исполнение
Изучить причины каждой из трех retained записей и самый ранний causal failure (например cleanupPhase/callback/owned scope/store/identity); один проверенный источник исправлять минимально на его boundary. Do not change numeric timers/cap/phase timeouts/goal/amounts/DB schema/GeoEngine. If proven first producer is a stale owned timer callback, a bounded cancellation/epoch guard fix inside permitted source is allowed with RED→GREEN; any shared core timer API change requires a separate explicit boundary decision. Native work completion must precede identity release.

## Контроль риска
- Existing `PhantomMaterializationService`: `cleanupBlockers`, `requestCleanup`, `performCleanupEntry`, `registerDrainRetry`, `releaseStoredEntry`, `snapshot`, `shutdown` и `runDrainAttempt`.
- Existing `PhantomMaterializedPlayer`: `cleanup`, `claimCleanup`, `recordCleanupFailure`, `prepareCleanupDrain`, `terminalCleanupReady`.
- Existing `PhantomSystem`: `DRAIN_PLAYERS → requestShutdown` и `failStop`.
- `PhantomCleanupIncident` bounded capture first/latest; first evidence must never be replaced by latest.
- Owner identity `REAL_LOGIN` never recovered/removed by destructive shortcut.

No new watchdog thread, telemetry framework, artificial shutdown PASS, new C# subsystem, no broad rewrite. Report if incident exists in initial snapshot and what new activity changes. **No automatic SQL deletion or state rewrites of suspect entries.**
