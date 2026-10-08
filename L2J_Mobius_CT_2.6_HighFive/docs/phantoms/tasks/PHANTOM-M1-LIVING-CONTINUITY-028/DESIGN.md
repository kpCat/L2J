# DESIGN028 — одна существующая миссия, два независимых канала наблюдения

## 1. Продуктовая граница
Долгосрочный farm.goal определяет подходящую область/вид деятельности.
Конкретный NPC, маршрут и resource rest — временные действия. Использовать уже
существующие exact goal/revision/epoch, leases, cancellation и native исполнение.
Не создавать второй scheduler, ownership registry, combat formula или generic Brain.

Наблюдаемый BehaviorStatus — проекция существующих владельцев, не новая authority.
Допустим небольшой immutable record/enum в BackgroundDecision или VisibleAutoPlay:
`profile/object/epoch, goal/revision, state, reason, enteredAt, deadline, actionOwner`.
Типизированный статус нужен только там, где доказано, что boolean/reason скрывает
переход или его окончание. Не требуется рефакторить исправно работающие сервисы.

## 2. Control отдельно от telemetry
- One existing FULL_OBSERVE capture на замороженный набор профилей; native и initial
  epochs отдельны. Добавить observer identity/XYZ и per-profile native locality/retention
  facts в существующий ограниченный экспорт, не создавать новый агент на каждый режим.
- После enrollment периодические samples читать из full-native. НЕ выполнять
  SNAPSHOT_PHANTOMS/STATUS на каждом тике только ради получения уже доступных данных.
- Capture period целевой1–2с, max gap5с. Timestamp создаётся внутри native snapshot,
  не через snapshot(now), вычисленный раньше. Нет fake fresh timestamp/forward-fill.
- Mailbox нужен для START/STOP и действительно необходимых MOVE/SETUP/one-shot discovery.
  Единственный ordered sender; sequence/cap/deadline считаются ДО старта.
- Один heartbeat writer с exact runtime/session/run, период5с, живёт от успешного START
  до подтверждённого STOP и не зависит от command loop/sleep/build/census. Собственный
  lifecycle+finally, watchdog/ACL/PID/incarnation guards не ослабляются.
- Если existing Invoke тоже пишет heartbeat, выбрать одного автора: можно добавить
  backward-compatible режим external heartbeat для own Synthetic. Default RealClient
  остаётся прежним. Нельзя просто остановить оба источника и надеяться на быстрый цикл.

## 3. Отдельное разрешение UNCERTAIN
Inspector proposals/mailbox_audit.py читает точный результат/claim без dispatch.
После timeout нельзя повторять request ни с тем же, ни с новым ID автоматически.
Сначала read-only resolution текущего ID, identity и свежего native состояния.
ACCEPTED/MOVE означает только admission; arrival подтверждается fresh same-session XYZ.
Если идентичность или outcome не установлены, этот action gate UNPROVEN, не SUCCESS.
Read gaps не склеивать. Если synthetic закрылся, это конец исходного episode.
Новый session не продолжает старые timestamps/epochs или 380сек окно.

Fix transport допускается только по собственному RED: expiry advertisement,
точный publication race/oversized page/наблюдательный snapshot race, доказанный в
source+capture. Сохраняются XML/64KiB/ACL, bounded pagination и claims. Не маскировать
любое RuntimeException как безопасный read и не делать uncertain native body retry.
Не перекладывать actual execution в новый pool. No native action while holding
new observer/global locks; ни одного DB/full World scan на каждый telemetry sample.

## 4. Исполнение и bounded recovery
STATE_MACHINE.md задаёт state-specific obligations. При доказанном отказе:
- exact здоровая native session продолжает работу после перепланирования wrapper;
- stale revision/epoch callback ничего не пишет; отмена относится к точному действию;
- недоступный маршрут исключает конкретный witness, не все виды мобов/всю область;
- доступность native target подтверждается видом/уровнем/instance/LoS/path/floor/water;
- новая задача ставится существующим atomic replacePlan + permit rebind + runtime reload;
- отдых возвращает к paid native actions, не продлевает себя повторной меткой REST;
- safety incident остаётся закрытым. Это не повод «переоткрыть owner потому что прошло30с».
Если healthy028 работает, изменений behavior-кода НЕ делать ради выполнения списка.

## 5. Soft-away: закрыть новые roots, не потерять заработанное
После фактического ухода ВСЕХ реальных/synthetic observers из native locality:
сохранить текущие hard holds до окончания действия; recent-human grace не обновлять
от фонового heartbeat. Не выдавать новую farm action бесконечно ради удержания actor.
Если self-pin доказан, в существующем boundary выставить retire intent: прекратить
НОВЫЕ AutoPlay/AutoUse roots, дать earned hit/cast/reward и принятому checkpoint
закончиться; использовать обычный retention→dematerialize027. Никакого kill/cancel
опубликованной earned работы и видимого teleport Phantom. При возвращении до seal
решение принимает существующий exact lifecycle, а не гоняющиеся start/stop callbacks.

Background меняет зону законно, когда actor отсутствует. Не заставлять его физически
бежать через полмира. Возврат observer проверяется по актуальной canonical позиции,
а не по требованию «тот же Phantom обязан ждать в старом XYZ».

## 6. Неизменяемые гарантии
Сохраняются b4 owned-store, native damage/reward, lifecycle027 и cleanup deadline.
Никаких слияний эпох, reset counters/overflow, подмены старых raw FAIL или лечения DB.
Observer/helper не является production behavior controller. Нормальная игра без
подключённого диагностического collector должна вести себя тем же образом.
