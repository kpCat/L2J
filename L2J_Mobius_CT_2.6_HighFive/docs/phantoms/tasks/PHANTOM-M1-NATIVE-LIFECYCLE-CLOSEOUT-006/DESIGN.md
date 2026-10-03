# Design: ownership, first failure, terminal outcome, useful native progress

## D1. Managed identity независима от наличия registration

Добавить в Player один volatile sticky флаг происхождения native automation:
`private volatile boolean _phantomAutoPlayManaged;`
`public final void markPhantomAutoPlayManaged()` устанавливает только true;
`public final boolean isPhantomAutoPlayManaged()` читает его.
Оба startPhantom... с non-null policy устанавливают флаг ДО публикации membership.
Это внутренний server-side API, не client packet/admin bypass. Регулярные startAutoPlay
и startAutoUse не устанавливают его. Он не идентифицирует REAL_LOGIN и не заменяет
PhantomIdentityLeaseRegistry/epoch/ActionLease.

Почему не использовать только GameClient==null: synthetic и offline REAL не Phantom.
Почему не только hasOwnedStoreBoundary/hasHeadlessOutboundSession: эти attachments
могут быть сняты, тогда уже выбранный stale Player вновь попадает в ordinary branch.
Флаг живёт ровно столько, сколько сам Player; статических tombstones нет.
Existing hypothetical same-object Phantom→REAL takeover не включать: реальный login
не должен использовать старый Phantom instance без существующего явного handoff.
Проверить positive REAL login regression; если он реально переиспользует тот же объект,
отметить DESIGN_CONFLICT и использовать exact lifetime owner token существующего handoff,
а не выключать флаг без drain. Не объявлять неподтверждённый путь поддержанным.

Tick с managed=true и observedPolicy=null: SKIP без действий и без unconditional stop.
Tick с P1: все rejection/failure cleanup только conditional P1; никогда удаление P2.
Проверка выполняется до ordinary branch online/config stop. Обычный REAL остаётся stock.

## D2. Conditional stop и lock discipline

На каждом manager start/stop/membership mutation сериализуются его registration monitor.
Добавить `stopPhantomAutoPlay(Player, PhantomPolicy)` и
`stopPhantomAutoUse(Player, PhantomPolicy)` → boolean: false при null/не том policy.
Сравнение и снятие membership/flag выполняются в одной критической секции.
Существующие unconditional public stop... тоже используют этот monitor; тело native
cleanup и distinction phantom/summon сохранить. Нельзя CAS-remove policy, а затем вне
lock удалять membership: это новое окно уничтожения P2.

Worker не держит manager lock во время native effect/DB/admission drain.
Conditional methods не вызывают другой manager. В catch/rejection сначала закончить
операцию своего manager, затем conditional operation второго на том же P1.
Запрещено: lock AutoPlay → AutoUse, и обратный порядок где-либо ещё.
Short register/stop critical sections не должны дожидаться lifecycle/Player locks;
аудировать existing calls onActionRequest, followOwner, resetSkillOrder перед расширением
lock. При обнаружении reentrant call-out вынести side effect после registration mutation
с expected-generation guard; не строить глобальный lock поверх native gameplay.

## D3. Exact paired Session

Session сохраняет immutable `PhantomPolicy policy` наряду с Player/goalId/revision.
Policy остаётся существующей реализацией (exact Player + goal revision + ActionLease).
Добавить manager query `hasPhantomRegistration(Player, PhantomPolicy)`.
Health = exact current Session + same Player/goal/revision + legal current ownership +
оба manager registrations принадлежат той же policy + native autoPlaying.

Partial pair не считается running. Repair заменяет/регистрирует pair для того же Player
через existing native APIs. На исключении откат только собственных registrations.
Session removal делается `_sessions.remove(profileId, expectedSession)`; stale callback
не должен удалить опубликованную новую session. Старт/stop одного profile требуют
сериализованной публикации с existing per-profile lifecycle/action mechanism либо
коротким striped owner lock (bounded 64 stripes, без Player/DB calls под ним).
Не держать этот lock при manager/native вызовах: использовать publish/validate token;
при invalidation after registration выполнять conditional own rollback.
Тест P07 обязан проверить interleaving публикации, а не только наличие synchronized.

## D4. First-failure record

Сохранить firstCleanupFailure (неизменный в рамках materialization epoch) и latestFailure.
Записывать sequence, profileId, objectId, materializedAtNanos, firstUtc, phase, hook,
admittedActions, exception class/message, cause chain и suppressed. Ограничить:
message 160 characters; до 8 cause/suppressed nodes; суммарно 32 stack frames и 8192 UTF-8
bytes в подробной detached записи; truncation явно обозначать. No Throwable/Player refs
в service archive. First stack записать в logger один раз, retries только sequence/count.

Service archive: insertion-ordered bounded 256 incidents keyed profile/object/epoch,
first/latest detached records; evict oldest finished incident, при переполнении всех
unresolved — eviction счётчик и явный evidenceIncomplete. Это diagnostic storage, не
replacement recovery state. Active actor retains own first regardless archive eviction.
Census отдаёт короткий incidentId/firstphase/class/msg/seq/count; подробности отдельно
в sanitised report/log. Существующий envelope max/page budget не раздувать stack trace.

Любая вторичная ошибка в finally добавляется к primary как suppressed; первичная снова
выбрасывается. Ошибка только finalizer тоже выбрасывается. Для Error не подавлять.
Применить к cleanup stopAllTasks, native Player.store и pending resume, а также abort
materialize cleanup. Не менять факт вызова finalizer и completed-флаг.

## D5. Native quiescence и owned store

Ни dispatch-drain, ни stopAllTasks отдельно не доказывают остановку delayed native work.
Сначала Q01–Q05 на настоящих native actions/callbacks с точными барьерами в TEST seam.
Если RED подтверждён: использовать existing native abort/queued-cast/AI movement stop
и completion ownership/drain на выявленной границе до immutable snapshot. Не ловить
его простым sleep и не переносить deleteMe перед durable store.

Условие продолжения: не осталось mutator, способного менять authoritative snapshot
после PREPARE вне owned protocol. Callback уже завершивший удар может законно дать reward
до snapshot; нельзя терять EXP, добавлять reward дважды или искажать kill attribution.
Если safe finite drain не доказан — retained/blocked, не READY и не reopening admission.

b4f PREPARE → immutable NativeStoreSnapshot → canonical store → FINALIZE → reconcile
сохраняется. RUNTIME_CHANGED_AFTER_FINALIZE не лечится removal guard/переснимком intent.
Старое dirty JVM evidence read-only экспортировать; clean JAR не загружать в него.

## D6. Typed terminal travel outcome

В PhantomVisibleFarmTravel.Failure добавить exhaustive disposition (или отдельный enum
рядом): ROUTE_UNUSABLE, TRANSIENT_SERVICE, NATIVE_ACTION_REJECTED, STORE_PENDING,
PROTOCOL_VIOLATION. Код, рождающий terminal, обязан задать disposition явно.
Строка reason остаётся для compatibility/diagnostics, но не управляет молча bool whitelist.

ROUTE_UNUSABLE/NATIVE_ACTION_REJECTED → existing history exclusion/replan текущего
farm target/step на TTL, без изменения canonical topology. TRANSIENT_SERVICE → bounded
cooldown/attempt budget exact goal/revision, не eternal route ban. PROTOCOL_VIOLATION →
fail closed, incident и stopped same-revision retry до explicit resolution.
STORE_PENDING → existing owned receipt resume; no new intent/farm/goals поверх unresolved.
Deadline after повторов должен завершить класс попытки и дать observable resolution.
Успешный реальный progress или новый revision сбрасывает appropriate counters; пустое
пересоздание Journey их не сбрасывает. Кеши bounded existing maximumTrackedProfiles.

## D7. Truthful acceptance

Наблюдение пассивное. Использовать existing Monster AggroInfo.getDamage и Player EXP/SP,
existing native attribution hooks, не инициировать атаки/спавны из observer.
Добавить scalar fields для selected и census: exact identity/epoch/goalRevision,
observation timestamp, selected target NPC/object, fresh attributed damage delta,
EXP/SP delta, native kill/reward evidence (когда доступно), pair health/incident.

Атрибуция привязана к текущему actor epoch и native target incarnation. Старая aggro
запись на первом sample — baseline, не новое действие. Сброс/исчезновение aggro при death
не считать отрицательным damage; подтверждать reward/kill или сохранить bounded event
sample существующим hook. Observer не пишет PLAY DB.

NATIVE_LIFE(selected) = fresh attributed damage >0 и подтверждённый native farm progress
(EXP/SP рост либо native kill+reward). Точный факт для самого selected, не «кто-то рядом».
COHORT: исходные >=4 natural eligible profiles; все отслеживаются даже при последующем
FAILED/closed admission. Как минимум 2 distinct profiles с native farm progress; остальные
должны показывать bounded useful travel/death-recovery progress либо честное объяснённое
ограниченное ожидание. Нельзя считать undefined idle объяснением. Persistent FAILED,
admission closed outside short bounded cleanup, no-progress/navigation loop → RED.
Отсутствие достаточного времени/сэмплов → UNPROVEN, не PASS. Новый world.spawn в native
TEST не является natural PLAY candidate. Не удлинять timeouts ради GREEN втайне.
