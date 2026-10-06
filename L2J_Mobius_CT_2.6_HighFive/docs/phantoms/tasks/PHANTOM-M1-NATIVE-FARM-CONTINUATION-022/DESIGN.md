# Архитектурное решение TASK022

## 1. Сохраняем механику Mobius

Phantom выбирает намерение и управляет своей регистрацией. Target/attack/cast/reward/pickup исполняются штатно. Нужен корректный жизненный цикл существующих работ, а не новая боевая система.

Один authority key: profile + Player reference/objectId + materialized epoch + goalId/revision + session generation/exact policy. Старый callback не может остановить новую регистрацию, завершить новый cast, опубликовать награду в новый sensor или продлить старый episode.

## 2. Состояние регистрации и lastDecisionReason — разные данные

Добавить только необходимые read-only поля в существующую census/recording infrastructure. `lastDecisionReason` всегда сопровождается decisionAgeMillis/sequence. `liveAutoPlay`, `liveAutoUseRegistered`, `liveAutoPlayRegistered`, current-session/policy identity и last-stop reason/time показываются отдельно. Одно non-atomic чтение не доказывает divergence: нужны два сопоставимых snapshot и progression timestamps.

Предпочтительная точка API: immutable nested `ContinuationSnapshot` и `snapshotContinuation(long profileId)` в PhantomVisibleAutoPlay; экспорт через уже существующий includeCensus. Публичные сигнатуры уточнить под существующий scalar-map стиль без новых protocol operations. Если уже есть все нужные fields — переиспользовать.

Freshness: если decision sequence/last completion не продвигается при due work, сначала выяснить `_inFlight`, queued/running/finished work и стек потока. Не выдавать «AutoPlay работает» по старому reason. Никакого блокирующего snapshot, который ждёт тот же lock, ради которого собирается диагностика.

## 3. Точное tick admission, если RED подтвердит временное отключение

Концептуально результат tick acquisition: ACQUIRED(lease), PAUSED(reason), REVOKED(reason).

ACQUIRED исполняет ровно stock body, lease закрывается в finally. PAUSED ничего не исполняет и не удаляет current registration: только доказанный временный checkpoint/control transition с живым тем же owner, без failure/permanentSeal/cleanup/чужого goal. REVOKED удаляет только expected policy/session. Не превращать все null в PAUSED.

Преимущественно реализовать через default-compatible extension существующего PhantomPolicy/TickLease, а не менять все callers. Обычные REAL игроки (policy==null) сохраняют прежний путь. Длительный pause не получает вечное продление: после существующего recovery window публикуется точная причина, не бесконечный restart.

Не вводить эту semantic ветку профилактически: если root — lock/callback, достаточно исправить его и добавить правильные diagnostics.

## 4. Lock/callback completion

До patch записать wait-for граф/причинную трассу. Не выполнять native abort/cast/store/drain под registry-wide monitor. Не ждать собственного ticket; checkpoint выполняется после закрытия выполняющей работы на существующей control boundary. Никаких новых executors/общих ThreadPool policies.

Завершение/cancel/rejection — exactly once для ticket и его scalar combat reservation. Отмена queued cancellable work освобождает только его reservation; cancellation running work не означает completion его тела. Earned reward callback сохраняет прежние гарантии. Не заменять DRAINING на OPEN после unknown failure.

Один неисправный Phantom не должен заморозить выполнение остальных в shared pool. Это проверяется отдельным native test. Исправление только на доказанном callsite/Phantom integration hook, не переписывание stock dispatcher.

## 5. Evidence — наблюдение, не источник игровых наград

Первое состояние UNPROVEN должно иметь bounded reason и timestamp: PHASE_DEADLINE, TIME_REGRESSION, TARGET_CAP, INVALID_TARGET, COUNTER_OVERFLOW, OWNER_FAILURE и фактически найденные причины. Использовать small enum/поля, не per-event unbounded log. Legacy overflow/snapshot API не ломать; не сбрасывать sticky invalidity.

Фактические damage/reward/kill/loot счётчики обновляются только после native writers. Farm cycle требует причинно связанных target identity+spawn generation, собственного damage, kill, положительных EXP/SP и выбора следующей цели. reward-before-kill и late reward после next-target должны обрабатываться без двойного учёта. Если это не текущий root, отрицательные ordering-тесты всё равно нужны для достоверного PASS.

Просроченная фаза не лечится увеличением120s. Если phase оставлена producer'ом после фактического completion — чинить его clear/retire; если phase действительно активна слишком долго — сохранить UNPROVEN и исправить stall. Диагностика не должна менять gameplay ради улучшения показателей.

## 6. Script boundary Q00255

Установить actor/NPC/event/epoch, стек и последствия. Допустим минимальный null-state guard в onKill только при доказанном допустимом отсутствии quest state; сравнить поведение обычного REAL с существующим QuestState. Не создавать фиктивный quest, не выдавать его награды, не отключать tutorial глобально, не запускать весь EnterWorld для headless Player.

Если сообщение относится к другому NPC/owner или изолировано штатным dispatcher, отметить NOT_CAUSAL_FOR_SELECTED_STALL. Не тратить остаток задачи на него; разрешение на условный файл не означает обязательную правку.

## 7. Что является завершённой вертикалью

После устранения остановки должно продолжаться не меньше5циклов в одной жизни Player без ручного target/attack/reset. Не скрыть отсутствие loot: native fixture гарантированно проверяет разрешённый pickup/autoloot и отсутствие подбора чужого защищённого предмета; connected-run учитывает только реально выпавшее. Мобы/награды не подставляются в connected-мир.

Тестовое setup допускается в guardedTEST, но не выдаётся за natural production proof. В native fixture использовать production bindVisibleLife/current intent и реальную асинхронную работу, не mock «success» и не вызовы урона/выдачиEXP тестом.
