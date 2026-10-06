# Решение: согласованный цикл локального намерения

## 1. Что доказано и что не доказано

В EVIDENCE020.json две census-точки одного epoch разделены всего 1.790001 секунды.
Обе показывают stored goalRevision29 и runtimeGoalRevision28, currentActionGuard=
DECISION_GOAL_MISMATCH, terminal water failureSequence1, native counters0.
Текущий persisted NPC=20525; для него exact/short/longTargets=0 возле Player.
Нельзя переносить эти target counts на runtime goal28: его полный spec там не сохранён.
Нельзя выдавать две точки за полный 120-секундный soak.

Source подтверждает три связанные слабости:
- travel.advance() до движения не сверяет переданный goal с текущим persisted goal;
- terminal Attempt возвращает false, а bindVisibleLife превращает false в start_retry;
- recordVisibleTravelFailure отклоняет failure от устаревшего goal, а
  replanVisibleFarmIfOutgrown для non-COMPLETE catchup вообще не меняет план.

TASK020 foreground permit намеренно привязан к точным catchup/goal components. Просто
ослабить сравнение нельзя: собственная смена плана должна иметь отдельный проверяемый
переход. Именно этот переход, а не случайная подстановка новой revision, нужен здесь.

## 2. Сначала синхронизация, затем движение

Единственный источник актуального goal — текущий StoredGoal, принадлежащий текущему
ordinary/foreground owner. Runtime slot — исполняемая копия, не источник для rollback БД.

Добавить в History узкий pre-work метод, рекомендуемая сигнатура:
`public boolean prepareVisibleDecision(long profileId, PhantomDecisionEngine decision)`.
В productionWorkSink PhantomSystem вызвать его для effectiveState.requiresMaterialization()
ДО `decision.accept(item)`, вне monitor DecisionEngine, Player monitor и native ActionLease.
Сохранить existing background-readiness branch и existing store-decision fence.

Если runtime отстаёт от принадлежащего этому owner StoredGoal:
1) доказать актуальную identity/epoch и существующий admission;
2) вызвать существующий decision.reload(profileId), не setGoal со старой копией;
3) проверить goalId/revision/status/component rowVersion после reload;
4) не исполнять старый work до успешного совпадения.
BUSY означает ограниченное ожидание следующего tick, не force и не новый goal.

Для pending native handoff допустима синхронизация только к компоненту, который уже
авторизован exactForegroundHandoff, либо к exact результату собственной операции §4.
Чужое изменение request/goal не признавать автоматически. Для обычного COMPLETE
catchup также проверять текущий goal и отсутствие pending store.

В travel перед выдачей MOVE_TO и при получении asynchronous route проверять, что
переданный goal всё ещё current. Устаревший результат — STALE_GOAL, не bad geometry.
Старый callback не должен портить exclusions нового goal. Старый Journey отменяется
только с совпадением profile/object/epoch/goal; existing remove/stopOwnedMove переиспользовать.
Не сбрасывать весь travel registry.

## 3. Окончательный отказ не является обычным retry

Сохранить boolean arrive API для совместимости, но production binding должен различать
ARRIVED, PENDING, STALE_GOAL и TERMINAL. Добавить типизированный результат/наблюдение
существующего Attempt, а не второй travel state machine.

PENDING — текущая navigation ещё работает; разрешён retry в существующем бюджете.
STALE_GOAL — остановка старого исполнения и pre-work reload.
TERMINAL — ровно один recovery request на точные epoch/goal/failureSequence. Следующий
pre-work tick, когда handler уже отпустил _inFlight, выполняет recovery. Не вызывать
decision.reload/setGoal внутри исполняющегося handler. В handler terminal возвращается
как REPLAN с сохранением точной причины, а не start_retry.

Повторный terminal receipt идемпотентен. Пересоздание decision plan не возобновляет
тот же terminal Attempt и не сбрасывает общий бюджет recovery.

## 4. Собственная смена локального плана — один согласованный переход

Для текущего обычного COMPLETE goal и для exact active NATIVE_CONTEXT_HANDOFF разрешён
переход к другой локальной цели. Для произвольного pending request — запрещён.
Все проверки claim/goal/character/epoch/ACTIVE/World/admission/cleanup/pendingStore из020
сохраняются. Перепланирование только когда именно наш travel остановлен, AutoPlay этого
goal остановлен и Player не исполняет attack/cast/teleport.

Использовать существующий `PhantomBackgroundCatchupStore.replacePlan(...)`:
- он атомарно CAS-обновляет catchup plan и goal component;
- не делать `decision.setGoal(...)`, а затем отдельный `_store.replace(...)`;
- не ловить exception с молчаливым «когда-нибудь renewal исправит».

Сохранять requestId, deterministicSeed, from/target/cursor, intervalOrdinal, generation,
modelVersion, authority hashes, status/failureReason. Меняются только goalRevision +1,
planOrdinal +1 и данные нового factual плана. EXP/SP/items/receipt/background position
эта операция не меняет. Knowledge/topology должны оставаться теми же для этой операции;
при смене generation — defer к штатному recovery, не расширять scope.

ВАЖНО: существующий catchup.withPlan() превращает FAILED_REPLAN_REQUIRED в RUNNING и
стирает failureReason. Не применять его вслепую. Для foreground-only replacement
скопировать неизменяемые поля явно или добавить private copy-helper в History.
Никакого искусственного COMPLETE или продвижения исторического времени.

После successful CAS перепривязать ActiveForegroundHandoff только из exact старой
записи к exact возвращённым components, с тем же native epoch. Для COMPLETE обычного
пути такого permit не создавать. Затем decision.reload на следующей безопасной boundary.

При CAS conflict — никаких частичных writes и никакого нового разрешения. При сбое
между DB commit и in-memory publish держать этот profile закрытым; один сохранённый
in-memory completion receipt с exact before/after даёт идемпотентный retry. Cleanup
инвалидирует receipt; после restart обычная materialization заново аттестует durable
новый план. Не создавать новый durable journal. Не удерживать gameplay locks при JDBC.

## 5. Локальный план, не путешествие через весь мир

Исторический planner остаётся владельцем background-планов и глобальных переходов.
Его default методы/сортировка не меняются.

Добавить отдельный `replanVisibleLocal(...)` overload к существующему planner:
те же knowledge, ограничения уровня/класса, loadout и источники NPC; вход — актуальная
live позиция, а не только committed anchor. Кандидаты — factual FARMING anchors той же
instance в радиусе 2000 world units от live позиции. Применять local filter ДО выбора
уровневого tier; затем сохранять существующие lower-level fallback tiers.

Не выбирать NORMAL_GATEKEEPER/boat/global-travel как prerequisite первого локального
фарм-цикла. Если актуальный target снаружи local envelope, запрашивать этот local replan,
а не отправлять видимого Player через полкарты. Background глобальную жизнь не менять.

Проверять не более 8 ближайших подходящих локальных целей; стабильный tie-break уже
существует. Native маршрут исполняется через существующий NavigationService/GeoEngine.
Каждый native leg проходит текущий unsafeSegment; endpoint «на суше» недостаточен.
Если первая локальная цель недоступна — следующая, с exclusions; максимум 3 разных
(npcId,anchorId) за одну 60-секундную recovery episode данного object/epoch. Новая
revision не сбрасывает этот бюджет. Состояние без местной безопасной цели должно иметь
явную причину LOCAL_FARM_UNAVAILABLE и ограниченный cooldown, не hot retry.

Не телепортировать Phantom, не переписывать координаты, не дематериализовать перед REAL
ради успешного теста. Дальний уход остаётся фоновым после штатной soft-dematerialization.
No-local-target — честный диагностический результат, не разрешение игнорировать данные.

## 6. Бой и лут

При ARRIVED запускать существующий PhantomVisibleAutoPlay. Он, AutoUse и PlayerAI
выбирают и исполняют combat. Никакого setAutoPlaying(true) как доказательства, тестовой
атаки, выдачи EXP или подкладывания items. Исправления combat/death здесь не разрешены.
