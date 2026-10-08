# DESIGN — recovery есть управляющая операция, а не обычное действие AI

## 1. Общие инварианты
I1. Один exact Player reference + owner reference + epoch, один checkpoint/requestId.
I2. Snapshot/canonical writes — только после исчерпания всех относящихся earned writes.
I3. Нельзя принять `hasPending=false` за доказательство «никакой записи не было».
Inventory.updateDatabase уже расположен раньше prepareOwnedStore: учитывать эту фазу.
I4. Сохранённый receipt и before/after bytes сильнее текущего historical policy/readiness.
I5. Старый callback не открывает новую эпоху и не исполняется от имени нового объекта.
I6. После каждого checkpoint существует ровно одно продолжение: OPEN, ожидающий exact
control request, durable receipt recovery, либо явно terminal/retained с first cause.
I7. Sensor-invalid не является разрешением на запись и не лечится reset метрик.

## 2. Холодное восстановление — раньше baseline
Ввести узкий service entry `recoverUnownedLifecycle(profileId)` либо расширить имеющийся
recoverAbandonedMaterialization с сохранением всех identity/World/autosave проверок.

Порядок:
- claim transition; exact profile→character link;
- BACKGROUND identity lease; нет live/retained owner, World Player/Object и autosave;
- перечитать state и receipt под существующими transaction locks;
- VERIFY_PENDING: существующий reconcileVerifyPending/owned resolver;
- MATERIALIZED без owner: существующий abortMaterialization;
- если owned resolver законно вернул MATERIALIZED (targetState arrival), выполнить
  существующий abandoned transition в следующем bounded step под тем же исключением
  конкурентного ownership; не выдавать новый actor между этими шагами;
- READY/DEAD и native context exact после resolver; повторно load и только потом baseline;
- не двигать catchup cursor/requestId/goal для исправления receipt.

Старая ошибка catchup.baseline.conflict может быть повторно проверена ТОЛЬКО после
успешного exact durable recovery и подтверждения текущего goal/request/window. Не
разрешать любые FAILED_REPLAN_REQUIRED. Нет receipt/канонический neither/identity conflict
→ fail closed с причиной, никаких SQL resets или speculative reconstruction.

Normal background eligibility, например POSITION_REQUIRES_NATIVE, продолжает блокировать
виртуальное начисление; оно не должно блокировать exact owned receipt FINALIZE.

## 3. Live checkpoint — типизированный stage и конечный исход
Добавить один маленький outcome/control record (nested предпочтительно), не новый журнал:
identity/profile/epoch/goalRevision/requestId; phase; status; first failure; next control action.
Phase минимум: ADMITTED, WAIT_EARNED, SEALED, CAPTURE, INVENTORY_FLUSH,
PREPARE_ATTEMPTED, PREPARED, NATIVE_ATTEMPTED, FINALIZED, INDEX_PENDING, COMPLETED.

Отдельно хранить факты: была ли попытка необратимой записи; commit outcome known;
receipt exact; snapshot runtime unchanged. Эти факты поставляет executing boundary,
а не общий exception catcher, не лог и не XML test report.

Исходы:
- RETRY_BEFORE_WRITE: действительно не было DB/native/effect writes, owner current,
  нет failure/permanentSeal/pending. После завершения control claim разрешено восстановить
  OPEN; причина приводит к bounded replan/defer, а не повтору того же шага в вечном цикле.
- WAIT_EARNED: admission закрыт, заработанное ещё выполняется; control task снимается с
  blocking worker и продолжает после quiescence/срока. Не открывать новые ordinary roots.
- RESOLVE_RECEIPT: owned receipt создан/возможен; generic gameplay закрыт. Exact control
  resume проверяет commit, затем тот же receipt/FINALIZE, без повторного начисления.
- VERIFY_WRITE_OUTCOME: попытка записи была, receipt не найден/неизвестен. Нельзя reopen.
  Исполняемая bounded verification или terminal retain с first cause; не бесконечный retry.
- PUBLISH_COMMITTED: durable state уже exact finalized, index/permit publication не прошла.
  Повторяется только readback/publication, не native write/EXP. Существующая topology жива.
- RESUME: durable/runtime/epoch/goal и publication exact; temporary owner OPEN,
  revalidate local arrival, затем existing AutoPlay/AutoUse.
- TERMINAL_RETAIN: native body failure, unknown partial writes, permanent cleanup или
  нарушенная identity. Старый instance не открывать, incident не стирать.

Запрещён общий finally { if (!hasPending) open(); }.
Не считать Boolean false внутри Supplier успешным business checkpoint.
Не менять существующий generic checkpoint контракты по умолчанию: расширение только
для явно typed owned visible-store caller; остальные callers сохраняют консервативность.

## 4. Продолжение без ActionLease и без блокировки native-пула
Recovery работает через exact control request и не требует ordinary gameplay admission.
`arrive()` не проверяет ordinary permit раньше завершения собственного уже принятого
checkpoint. Claim не разрешает атаковать/двигаться; он позволяет лишь закончить store.
Повтор запроса с тем же ключом coalesce. Более новая goal не подменяет старый snapshot.

Предпочтение: существующие materialization onQuiescent/control callbacks. Добавить
ограниченный temporary-checkpoint callback по тому же шаблону, если нужен. Callback
только публикует продолжение, не выполняет store inline из WorkTicket.complete.
Начало control step/DB операция — вне owner/Player/status/system/topology monitors.
Не ждать заработанные callbacks на единственном доступном им scheduled worker.

Если существующий control executor непригоден и это доказано: один Phantom-owned
bounded executor для DB/control (≤2 workers, queue≤2*maxMaterialized, abort-on-overflow),
только для этого сервиса, со штатным shutdown. Это условный резервный вариант, не цель.
Не добавлять новый global ThreadPool, не увеличивать stock pools/timeouts.

## 5. Закрытие и таймеры
WAIT_EARNED должен различать ещё не наступивший due и просроченную/заблокированную работу.
Уже опубликованный EARNED event исполняется штатным EventDispatcher с прежними объектами.
Штатный shutdown остаётся вне мониторов из TASK024, native pools закрываются после drain.
Если timeout истёк, ownership/receipt сохранены, причина включает exact earliest pending
work и наблюдённый исполнитель; не выдавать CLEANUP_PASS на следующем пустом restart.

## 6. Диагностика без влияния на store
First error фиксировать там, где он произошёл, вместе с checkpoint phase и identity.
Внутри hook нельзя сканировать весь World/materialization registry и брать чужие locks.
Использовать exact текущий actor/snapshot аргумент. Ring bounded, no DB/FS/Wait из hook.
Экспорт вне critical section; плановый crash отдельно fsync'ит свой подготовленный
witness в fault window. Старый агент024 с глобальным обходом не копировать слепо.

## 7. Что не входит
Новый tactical AI, смена системы наград, класса/экипировки, M2, правила PvP/party,
глобальная геодата, respec и новое масштабирование. Death/soft-return проходятся как
регрессия этого жизненного цикла; их unrelated дефекты не расширяют scope молча.
