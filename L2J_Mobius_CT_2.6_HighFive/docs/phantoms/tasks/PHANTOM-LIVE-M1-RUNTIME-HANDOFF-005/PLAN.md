# PLAN — один task, пять внутренних этапов

## 1. Начальный контроль без нового live-прогона

Проверить branch/HEAD и task paths, сохранить dirty baseline. Прочитать обозначенные методы SOURCE_MAP, сопоставить с DESIGN. Не повторять поиск архитектуры по всему репозиторию.

Один раз сверить старый TSV с existing admission/calendar/ecology данными; исторический nextBoundary может не сохраниться. В STATE указать: «profile 5079 точная причина OFFLINE известна/неизвестна» отдельно от доказанного смешения readiness и presence. Не тратить arm на выяснение уже прошедшего состояния.

Добавить один общий TEST-контрпример D1–D3: calendar-online + pending catch-up + human demand. Он обязан использовать реальные coordinator/port glue, а не reconcile-предикат, всегда возвращающий true. Протокольный navigation rejection также воспроизвести до изменения consumer. Это два опасных стыка, а не тест на каждую строку.

## 2. Реализация readiness handoff одним согласованным изменением

Изменить Manager presence publication → ecology async due APIs/worker ownership → все System callers → typed reason port/scheduler/snapshot. Использовать существующие queues, transactions, lifecycle leases, event `ecologyFenceChanged`. Нельзя остановиться после добавления API, оставив production на старом синхронном вызове.

Добавить запуск/завершение worker ownership в общий lifecycle. Проверить claim release, stop/drain, locality/ready/position race и отсутствие callbacks под противоположными locks. Не повышать budgets и не выносить каждый профиль в отдельный executor.

## 3. Travel consumer и исполнимый маршрут

Обработать immediate + async terminal status единым кодом; убрать ожидание reject-id. Подключить общий route contract к runtime и совместимой TEST-проверке. Сохранить current bounded failure/exclusion logic, добавить различение временного service refusal и route/capability failure; удержание travel только при ограниченном pending или реальном прогрессе.

Общий no-progress/deadline должен переживать обычную длинную поездку, но не вечный retry. На имеющемся TEST Player увидеть native displacement/arrival, после чего запускается stock AutoPlay. Никаких fixes по расе/NPC/уровню.

## 4. Один финальный автоматический набор, затем публикация

Добавить один aggregate Ant target `phantom-m1-runtime-handoff-test`, зарегистрировать новый `PhantomM1RuntimeHandoffSuite` в existing launcher. Это предлагаемые новые имена, а не якобы существовавшие команды. Содержание — ACCEPTANCE A/B/C, с переиспользованием существующего guarded TEST fixture и suites. Не копировать отдельный test framework.

Запускать aggregate после завершения связанных правок. Затронутые existing scheduler/navigation/background tests включить в aggregate либо выполнить одним набором; broad `phantom-live003-runtime-authority-proof-test` — один раз в конце, поскольку меняется route contract. Не перезапускать legacy 10k scale, все геоданные и все content suites.

Отдельные encoding/whitespace/scoped-diff checks → code commit/push → чистый detached code-SHA `ant jar` → backup и controlled deploy через existing LocalPlay scripts. Build от dirty рабочей копии не годится. Если необходима исправительная сборка после ошибки, проверить изменённую зависимость, не скрывать её ради лозунга «одна сборка».

## 5. Одна connected-сцена и closeout

До arm проверить health, matching deployed hash, diagnostics, mailbox headroom и расписание подходящей естественной когорты. Архивировать при необходимости только закрытые старые lease по штатным owned-path правилам, не удалять текущий consent/evidence.

Для runner взять проверенную основу `Run-M1SmartContinuity.ps1` из 004, сохранить в текущем task как `Run-M1RuntimeHandoff.ps1`; не плодить параллельные proof runners. Дополнения — новые admission reasons, calendar invalidation и нормальный cohort census. Сценарий — ACCEPTANCE D.

Запросить один свежий arm, выполнить один runner, вернуть TestAdmin и остановить Pilot. Не продолжать ту же прогулку новой батареей проверок, не гоняться за успешным профилем после RED. Сохранить raw result, компактно отчёт, scoped docs commit/push. При недостигнутом native gameplay M1 остаётся RED, даже если shell/структура/85 уровней PASS.
