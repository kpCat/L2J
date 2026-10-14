# SIGNAL_PATH044 — точная диагностическая гипотеза из исходников

## Измерять правильного владельца

`PhantomScheduler.find(profileId)` возвращает `PhantomActivitySnapshot(activeSources, effectiveState, requestedState, transitionStatus...)`, **но не возвращает ключи**. Внутри `PhantomScheduler.Slot._sources` хранится `Map<String,SourceEntry>`, содержащий accepted key, sequence, immutable signal(requiredState/ttlMillis) и `expiresAtNanos`. `PhantomRuntimeFlightRecorder` уже получает `SCHED_SIGNAL_ACCEPTED`/`COALESCED` с ключом. Первым шагом прочесть имеющийся raw043 для profile1189 и timeline источника без нового сервера.

Если raw не даёт точного remaining TTL на D1 boundary: допустима **одна узкая read-only функция снимка** в том же Scheduler под `_monitor`: profileId→только non-null accepted sources immutable `(sourceKey, sequence, requiredState, originalTtlMillis, signedRemainingTtlMillis, expiredAtRead)`; expiry вычисляется от того же MonotonicClock. **Просроченный, но ещё не обработанный scheduler pulse источник не скрывать**: иначе в snapshot activeSources=1, а диагностический снимок ложно показывает пустоту. Не вызывать `expireSignalsLocked`, не менять Slot, не делать network/SQL/I/O или создавать background threads. Если иначе нельзя достать её штатным оператором — минимальный existing local-only diagnostic seam PhantomSystem, без постоянной системы сбора или секретов. Не менять existing public `PhantomActivitySnapshot` contract без причины.

Focused tests: 1) две разные active sources видны по ключам и не мутируются при чтении; 2) accepted replacement обновляет sequence/TTL без ложной source; 3) withdrawn не появляется; expired-but-not-yet-pulsed виден как overdue и только штатный pulse удаляет его из active; 4) nonexistent profile returns empty; 5) copy snapshot immutable и нет превращения outdated signal в owner. Проверить, что прибавка наблюдения не изменяет scheduler state/generation/earned counts.

Вторым шагом сопоставить реальный `sourceKey` и `sequence` с **фактическим владельцем**: `PhantomHumanLocalityControl`, `PhantomPerceptionProvider` (локальный чат, боевое событие, targetability), population или другой конкретный отправитель. Уточнить, прекращает ли owner обновления и когда законно наступает expiry. Ключ `topology.combat` — лишь кандидат, не факт о D1. Long TTL может быть намеренным; требуется сравнить с продуктовыми правилами 120 и с фактическим источником.

## Фоновый календарь

`PhantomSchedulerPolicy.productionDefaults`: для BACKGROUND `300000 + deterministic jitter[0..600000]` ms; `firstWorkDueLocked` при переходе BACKGROUND ставит не мгновенную работу. В `PhantomSystem.productionWorkSink` есть отдельная проверка ecology `requestBackgroundReadiness`, затем `DecisionEngine.accept`. Проверить, что **именно** этот путь ожидался в C1; существует ли другое допустимое pending/catchup producer событие на ранней границе. Нет гарантии награды в первые77с.

Для native-life теста сначала frozen **actual due / eligibility** и реальный срок, затем wait по настоящему clock; не менять product timings или wall clock. Если требуется больше Synthetic TTL525s, законно закрыть первую сессию и создать вторую отдельную с новым runId в штатном лимите5, сохраняя human/source identity distinction и отсутствие одновременного native/background writer. Никаких heartbeat extensions или fake REAL_LOGIN.

STOP rule: не запускать повторный долгий G4, пока не получена атрибуция scheduler signal и объективная проверка BG due. Иначе TASK044 становится третьей бесполезной попыткой, чего пользователь явно не хочет.
