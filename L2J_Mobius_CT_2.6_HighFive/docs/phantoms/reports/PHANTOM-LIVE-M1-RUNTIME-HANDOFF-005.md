# PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005

Status: **AUTOMATED PASS — CONNECTED HARNESS RED, GATE REQUIRED**.

Calendar presence теперь публикуется независимо от ecology readiness. Существующие
ecology queues обслуживает один bounded worker общего ThreadPool; scheduler/local
pulse не исполняют historical/JDBC inline. Повторный demand сохраняет horizon;
материализация требует committed readiness и повторной проверки current locality.
Farm receipt потребляет только periodic handler. Stop сохраняет начатый commit;
generation fence исключает поздний запуск потерянной submission.

Native travel обрабатывает immediate и async terminal одним кодом, освобождает
hold при service refusal и не помечает cooldown/backpressure как map-unreachable.
Общий normalizer проверяет endpoint, native segments и прежние бюджеты. Существующий
progress tracker отличает полезное продвижение от stuck и выдерживает движение
через прежний минутный порог. Stock AutoPlay и native party/retention сохранены.

Pilot использует те же admission facts, календарный запас 180 секунд и естественную
когорту. Один runner выполняет обычный подход MOVE_SELF, записывает D6 и census
страницами внутри штатного лимита 64 KiB. Разные terminal failure sequences не
скрываются за IDLE. Возврат TestAdmin имеет отдельный bounded cleanup deadline.

Validation: aggregate **102/102 PASS**, single final broad **5/5 PASS**; guarded
native TEST включает physical movement/arrival/AutoPlay и управляемые +65 секунд.
Независимый review закрыт. Whitespace и обе отдельные Cyrillic scans PASS.
Точная историческая причина OFFLINE profile 5079 неизвестна.

Code SHA: **e92d7d438641f3f13158021675bd99e2489a7042**, опубликован в
`origin feature/phantom-world` (`kpCat/L2J`). Clean managed detached `ant jar`:
PASS, 22 секунды. Controlled deployment: owned Login=16840 / Game=29616,
CONFIG PASS, startup 50 секунд, Login registration подтверждена. GameServer hash:
**83A2691BC9A417CE9B1D408A735A1E2DCE5423AB658B6E082AF06D39A67DE8D6**.
Build/runtime/manifest совпадают. Старые артефакты и закрытые mailbox records
сохранены; новый mailbox пуст. Актуальный read-only calendar: 1760 online с
запасом nextBoundary>180 секунд. Подробности в
[EVIDENCE](../tasks/PHANTOM-LIVE-M1-RUNTIME-HANDOFF-005/EVIDENCE.md).

Exact scope, raw log paths, Git authorization and limitations находятся в EVIDENCE.
Чужие изменения сохранены. Нет schema/dependency/geodata/budget changes, ручных PLAY
mutations, отдельных прогулок по локациям или M2. Connected run
`f4970953-410f-4608-afc2-f38015427d7c` после свежего arm остановился до approach:
PowerShell DateTime из JSON повторно разбирался как строка другой locale. Прочитан
реальный payload, RED воспроизведён offline; runner исправлен существующим ISO UTC
`Read-Field`, без изменения Java/JAR. TestAdmin возвращён в origin/REAL_LOGIN,
Pilot run остановлен. Mandatory connected критерии NOT OBSERVED, M1 не GREEN.
Повторный connected-run требует явного разрешения владельца по TASK.
