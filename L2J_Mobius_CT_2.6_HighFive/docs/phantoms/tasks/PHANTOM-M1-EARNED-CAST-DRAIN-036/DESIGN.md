# DESIGN036 — единый путь действия, не новый протокол

## Решение
Оставить Mobius исполнителем действий. Продолжить существующую границу lifetime:
`OPEN → принять cast → reserve exact earned ticket → submit → run native body/children
→ retire ticket → quiescence → PREPARE/native store/FINALIZE → release`.
Изменять только установленное нарушение этой цепочки. Новая система планирования не нужна.

Разделить diagnostics, semantics и forensic conclusion. Установленный diagnostic gap исправим
без гипотезы о потере callback: расширить существующий dispatch trace на нужные cast kinds.
Production-fix earned/start/cancel/drain допустим только после причинного RED на current base.

## Диагностические поля
Использовать existing ticket id/epoch, kind, semantics, parent/depth, reserve/submit/dequeue/start/
bodyExit/end timestamps, planned delay, actual Future done/cancelled/due, thread и first failure.
Parent хранить scalar ID (если добавляется), не ссылку на весь owner graph.
Снимок owner+Future не атомарен: публиковать sampleNanos и source kind; сравнение разных samples
не выдавать за единый момент. NONE Future означает «не записан/ещё не привязан/уже освобождён»,
а не автоматическое «submit отсутствовал». Inline/zero-delay completion может опередить SUBMITTED.

Фильтр точных cast kinds — в PATCH_GUIDE. Сохранить текущий EVENT trace. Ограничения активного
снимка/кольца не повышать без нужды; пропуск/переполнение указывать, не трактовать как отсутствие
события. При focused-тесте использовать барьеры вне player/status/owner/DB locks, чтобы захватить
конкретную границу; итоговую natural сцену не приостанавливать debugger'ом.
Никаких SQL, file I/O, sleep/wait, socket, внешних locks в native/FINALIZED hooks.
Exporter/пулы/flight-recorder существующие. Default production без diagnostics не нагружать.

## Дерево решений о правке
A. Current base без ошибок: покрыть scheduled/running/stale/drain native windows; завершить
  GREEN_CURRENT_PATH только после всех обязательных доказательств. Historical27 UNKNOWN сохраняется.
B. Reserve есть, submit отсутствует: найти точный publisher exception/return; paired rejection
  и original cause. Нельзя объявлять невыполненную заработанную операцию успешной.
C. Future опубликован, но due ещё не настал: штатное ожидание в едином deadline, не повторный submit.
D. Due истёк, нет executorEntered: доказать queue/pool/cancellation или holder thread. Исправлять
  исходное order/admission, не увеличивать число threads и не делать inline replay unknown callback.
E. ExecutorEntered есть, start/body нет: найти точный lock/owner check; исключить debugger pause.
F. Body завершился, outstanding остался: once-only completion bookkeeping/notification под
  текущим monitor; original exception и все остальные participant tickets сохранить.
G. Ticket завершён, Player retained: проверить control cleanup result/retry/finalized; не трогать
  earned protocol, если дефект расположен после него.
H. First failure другого owner прервал штатную остановку: отдельный двухакторный causal тест;
  разрешён bounded control-order fix внутри existing stop flow, но первый FAIL/deadline сохраняется.

## Неприкосновенные свойства
- RESERVED EARNED нельзя отменить потому, что бот больше не нужен. Действительно прерванный
  stock cast может выполнить штатный no-op callback; wrapper всё равно должен закончить учёт.
- EARNED — обязательство закончить обработку, а не гарантированная награда.
- Нельзя «докомпенсировать» HP/MP/EXP/SP/drop; результат native body — единственный источник.
- Старый epoch/заменённый MagicUseTask не пишет в replacement. Identity не освобождается заранее.
- Новый queued action после закрытия roots не маскируется под earned continuation.
- Ошибка одного recipient не бросает незавершённый учёт остальных, но исходная ошибка не исчезает.
- Общая остановка bounded; долговечная ошибка не превращается в COMPLETE после повторного poll.
- 36/45 сохраняются по035 с прежними252 предметами; Phantom max250 не меняется.

## Границы текущего риска
Календарь, death, MP regen и совместная атака сохраняют stock/существующее поведение.
Q15 (1 vs20) нужно классифицировать на действительном reward source/rates и числе вызовов,
если его path относится к изменённому native completion. Не запускать весь неверно выбранный
24-case suite на неподходящей БД. Нельзя просто исправить assert1→20, отключить сотрудничество
или сменить threshold. Связанный реальный reward defect блокирует GREEN036.
