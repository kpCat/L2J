# Runtime/TEST safety и конечный эпизод

## До запуска

Retained observe021 не менять: содержит нужные следы8retained actors. Не «восстанавливать» его marker'ы SQL. PLAY l2jmobiush5_localplay3 — SELECT/export only. Guarded l2jmobiush5_phantom_test — существующий test gate; не отключать restore checks. При грязном test journal сначала существующий authorised exact restore; неизвестная разница — STOP с сохранением данных, не DELETE ради GREEN.

Fresh clones от PLAY: l2jmobiush5_localplay_observe022a, при необходимости022b. Не перезаписывать существующие clones. Source PLAY config/runtimes не меняются. TestAdmin100 применяется только в clone, если уже не унаследован; account/name/object exact verify и offline; kpCat не трогать. Clone — временный эксперимент, его прогресс не переносить в PLAY автоматически.

Effective settings: target1280 / active8 / materialized8 / maxScheduled10000; diagnostics=True; Pilot/AutoAttach TestAdmin; Synthetic=False для connected acceptance; GM hide/invisible/invulnerable/silence=False. Остальные production rates/data/геодата неизменны. Проверить actual parsed config и engine enabled до приглашения пользователя. Физические paths private runtime принадлежат isolated TASK022.

Сначала проверить порты2106/9014/7777 и точные owned JVM. Не завершать чужие процессы. Для занявшей порт прошлой owned JVM с online TestAdmin сначала user logout/save; не действовать вслепую.

## Ручной gate

Сообщить один раз: «Сервер готов. Войди вручную TestAdmin и напиши “в игре”.»
Закончить turn и ждать. Вопрос обычным сообщением, без10-секундного автоистечения. Отсутствие ответа не означает BLOCKED_FAILURE и не даёт разрешения продолжить. Не генерировать3повторных blocked-сообщения.

После ответа: exact IN_GAME + REAL_LOGIN + ARMED_IDLE безarm. Если получен новый клиент/session identity, перепроверить; не fakeREAL.

## Наблюдение

Не использовать PREPARE_M1_ENVELOPE, не двигать/таргетить/атаковать TestAdmin или Phantom. Разрешены только существующие read-only census/trace и добавленные scalar continuation snapshots. Не вызывать диагностикой abort/restart/force nexttarget.

Выбрать первичный natural eligible actor до первого sample, а не победителя постфактум. Предпочесть110 только если действительно естественно присутствует и eligible. Зафиксировать также до3других natural actors; не исключать молча idle-соседей.

Один script process: baseline + sample каждые2секунды; cohort каждые10секунд; max300секунд от первого успешного baseline. Использовать Stopwatch для лимита, preserved UTC-ISO для отчёта; PowerShell DateTime coercion не должна менять длительность. В каждом sample native epoch и timestamps. Не складывать counters разныхepoch, не переснимать baseline при неуспехе.

Trigger:10секунд неизменного decision/tick completion при due work или зависшем cast → 3bounded thread dumps по OBSERVABILITY. Если всё ещё unknown, сохранить freeze snapshot и максимум30секунд диагностического хвоста; дальше cleanup, анализ вне runtime. Sticky UNPROVEN не маскировать новыми baseline.

При5cycles не останавливаться мгновенно: ещё минимум30секунд наблюдения с продолжающимися native действиями либо законным target/MP ожиданием с актуальными heartbeat; никаких непрерывных perma-restarts. Итоговый лимит300с всё равно действует. Если ожидание respawn/MP реально мешает5cycles, зафиксировать условия; не спавнить цели/ману ради PASS.

## Что считать циклом

Один и тот же profile/object/epoch, отдельные native target-life identities. На каждом цикле: native target/damage/kill, фактическая положительная EXP/SP, следующий выбранный monster. Связь подтверждается native event/evidence и direct Player deltas; `AutoPlay=true` или packet/animation недостаточны. Чужое убийство не приписывается primary. При наличии реально выпавшего доступного loot — native pickup/inventory; если drop нет, честно NO_APPLICABLE_DROP, pickup proof остаётся из TEST.

Если первый connected episode вскрыл новую причину внутри SOURCE_MAP и budget/rounds позволяют: logout→save→graceful stop→focused RED→patch→GREEN→new source commit/clean build→fresh022b→один новый ручной вход. Live JVM не патчить. Оба эпизода сохраняются; два эпизода не склеиваются в пятьциклов.

## Остановка/сохранение

Попросить выйти доcharacter select, дождаться «вышел». Server-side online0 и saved TestAdmin exact fields до/послеstop; не принять молчание за выход. Stop active Pilot run и confirm OFF/no active run через серверные инструменты/disconnect.

Для Phantom раздельно: registrations removed, work outstanding/pending timers, earned rewards drained, canonical saved values, retainedMaterializationEntries. Нельзя повторить «GRACEFUL_STOP=PASS» как полныйcleanup при8retainedentries. При неполном drain сохранить work snapshots/first incident/clone; не сбрасывать ownership flags. Stock shutdown только exact owned processes; force без отдельного разрешения запрещён.

Диагностический episode до semantic fix: допустим без уже успешных5циклов только по PLAN-A; обязательны focused diagnostics checks, clean committed build и готовый collector. Не выдавать его за финальный farm proof.
