# Точные точки правки и границы доказательства

`source-excerpts/` — фрагменты required base с нормализованными отступами, а не готовые замены.
`proposals/LocalExecutionDecision026.java` — чистая reference-таблица порядка решений;
не обязательный новый production class, не auto-applied patch.

## PhantomVisibleFarmTravel.advance / isUsableLocalFarmPosition / walk
CURRENT: existingAttempt.terminal проверяется до проверки usable текущего положения;
isUsable требует geoHeight == nativeZ; arrival вызывается с radius=0.
PROBLEM: эти условия способны превращать неудачу одной stand-point в запрет всей миссии.
REQUIRED SHAPE: native opportunity проверяется отдельно от exact route attempt.
Только fresh independent safe witness может разрешить gameplay после rejected route;
повтор того же unsafe сегмента всё ещё запрещён. No setXYZ/tolerance blanket.
Checkpoint при factual смене привязки остаётся обязательным exact control, не side store.

## PhantomVisibleAutoPlay.noTargetExpired / nativeProgress
CURRENT: one abort repair at30s, terminal at90s; opaque availability of NPC does not
show resources or scheduler health. Low MP plus CAST in one sample is not proof.
REQUIRED SHAPE: narrow stateful RESOURCE_RECOVERY vs NATIVE_IN_FLIGHT vs LOCAL_FAILURE.
Получать реальные skill/cost facts, не задавать MP вручную. Tick policies делают PAUSED
только для доказанного exact transient state; REVOKED для сменённого owner/revision.
После bounded recovery нужен native effect, а не только новое reason string.

## PlayerNativeWork / NativeEventWork / PhantomNativeWorkScope
CURRENT: NativeEventWork -> execute/schedule -> ParticipantWork -> tryStart -> body ->
finally complete. Existing event type/participants сохраняются.
REQUIRED SHAPE: диагностика reservation/submission/start/completion и узкая корректировка
доказанного producer. Не complete RESERVED по таймеру и не запускать task второй раз.
Предложенный non-invasive event payload: ticketId,parentId,kind,ownerEpoch,reserveNanos,
submitNanos,startNanos,endNanos,submitOutcome; max1024 unfinished + bounded retired sample.
Без stack на каждом tick: stack только первый incident. При diagnosticsOFF no allocation
для tracing payload, нет новых waits/locks в production hot path.

## N02/S12
Смотри lateNativeDamage: second.callSkill vs original cast(first,..); обе реальные HP
записи проверены. Найти первый упущенный observation hook. Нельзя просто поменять
assert kill==1 на>=0. При lawful fixture correction оставить отдельный genuine native
original-entry+delayed-entry control и same actual damage/reward semantics.
PlayerNativeEvidence conditional: только доказанная привязка native after-write события;
MAX_TARGETS/MAX_PHASE_NANOS/overflow first cause и definition completedCycle не менять.

## Recovery barrier
Существующий PhantomBackgroundTransaction resolver не переписывать ради позднего XYZ.
Точка свидетеля — confirmed after commit до ordinary actor admission. Если callback
hook внедряется, получать exact profile/request/after state аргументом, не находить
его через перебор materializedPlayers внутри checkpoint. Test-only observer bounded,
исключения observer не должны превращать successful commit в failed product operation.
Если настоящее отличие уже на этой границе — отдельный RED и narrow R fix.

## Hot-path review
Не вводить новое SELECT на каждый AutoUse/AutoPlay tick. Использовать existing snapshots
и in-memory per-epoch state. Смена durable goal только через существующий CAS path,
а не на каждом target/касте. Native combat formulas, rewards и GeoEngine неизменны.
