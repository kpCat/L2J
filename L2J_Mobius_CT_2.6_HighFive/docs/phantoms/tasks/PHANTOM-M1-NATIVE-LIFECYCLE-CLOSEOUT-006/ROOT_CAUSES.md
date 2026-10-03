# Findings: доказанный source-path ≠ установленная причина старого runtime

## RC-01 / HIGH — managed Player исполняется без policy

AutoPlay/AutoUse хранят pool membership и PHANTOM_POLICIES отдельно. Worker уже выбрал
Player; stop удаляет policy до pool.remove; worker получает null и выбирает NOOP lease.
Таким образом закрытое Phantom admission не участвует в разрешении native действия.
Для AutoPlay legacy-ветвь также зависит от ENABLE_AUTO_PLAY; для AutoUse buff/item branch
не требует isAutoPlaying. Состояние online в начале cleanup ещё может быть true.

Исправление: sticky managed-origin на данном Player + fail-closed при policy absence.
Решение «сначала удалить из pool» недостаточно: уже выбранную ссылку это не отзывает.
Решение «держать вечную tombstone Map<Player,...>» запрещено из-за утечки.
Тесты P01/P02/P03. Historical110/142/175 attribution: НЕ УСТАНОВЛЕНА.

## RC-02 / HIGH — старый tick отменяет новую session

P1 захвачен worker; P2 зарегистрирован для того же Player; P1.acquire отклонён из-за
revision/cancellation; unconditional stop(player) удаляет P2. Catch ветви также гасят
второй manager без expected owner. Это нарушает identity/version ownership.

Исправление: conditional stop exact policy по ссылочной идентичности под тем же
registration monitor, что start. Не держать одновременно monitor обоих managers.
Тесты P04–P07. Старый epoch и новый objectId — отдельные отрицательные контроли.

## RC-03 / HIGH — «здоровая session» без AutoUse

VisibleAutoPlay.start fast path и running проверяют isAutoPlaying, но не наличие обеих
регистраций. AutoUse может выйти на pending store/lease rejection отдельно от AutoPlay.
После снятия pending start считает session уже работающей; AutoUse не возвращается.
Для mage stock AutoPlay не обязан делать melee: визуальный idle возможен без exception.

Исправление: Session хранит policy; health = exact same policy в обоих pools + текущий
goal/Player ownership. Partial pair ремонтируется через native registration без смены
materialization epoch. Тесты P08/P09, настоящий mage + native damage.

## RC-04 / HIGH — потеря first exception

recordCleanupFailure перезаписывает единственный record при следующей ошибке.
finally stopAllTasks может заменить exception beforeStore/storeMe/afterStore.
Player.store и resumePendingOwnedStore вызывают boundary.afterStore в finally: это
второй узел маскирования primary. MaterializationService редуцирует ошибку в status,
а record теряется при удалении entry. Компактный census не передаёт даже все cause facts.

Исправление: preserve primary/suppressed, immutable first + latest; detached bounded
incident archive и once-per-first stack log. Тесты E01–E06.

## RC-05 / HIGH — acceptance способен дать ложный PASS

NATIVE_LIFE выводится из moving/attacking/casting и targetMonsterAlive. Это не проверка
attributed damage/EXP/SP; cast может быть self heal с сохранённым monster target.
COHORT проверяет >=4 eligible и узкий regex travel failure у ACTIVE_IDLE; FAILED или
admissionclosed без совпадения regex не блокируют PASS. Чужое действие может подтвердить
selected bot через ordinaryFarm. Исправление: отдельный selected proof и baseline cohort.
Тесты A01–A08. Менять eligibility, чтобы скрыть поломанных ботов, нельзя.

## RC-06 / MEDIUM-HIGH — terminal journey не обязательно вызывает bounded resolution

Journey удаляется после terminal, но Failure.routeFailure whitelist не включает
journey_deadline, native_move_rejected, native_gatekeeper_absent и ряд terminal/navigation
protocol/deadline причин. Production callback записывает route failure только при
routeFailure=true. Следующий arrive может создать Journey с тем же goal/revision и
новым startedNanos. Лимит отдельного Journey не ограничивает такую последовательность.

Исправление: исчерпывающий typed terminal disposition и resolution/cooldown на exact
profile/goal/revision, а не ещё один bool whitelist. Тесты T01–T05. Transient overload
не должен превращаться в «геодата навсегда плохая».

## H-01 / TEST REQUIRED — native work может пережить dispatch lease

Admission lease ограничивает диспетчеризацию, но не автоматически завершение ранее
запущенного удара/каста/reward callback. stopAllTasks не эквивалентен abortAttack/abortCast,
которые встречаются позже в deleteMe. Retention уже содержит native action guards;
поэтому наличие race на shutdown/forced boundary нужно доказать actual-native TEST,
а не править global combat на основании одного названия метода.

Операция afterStore может успешно FINALIZE, затем выбросить
OWNED_STORE_RUNTIME_CHANGED_AFTER_FINALIZE и очистить in-memory intent в finally.
Это конкретный путь к FAILED без pending. Сопоставление с 110/142/175 пока отсутствует.
Тесты Q01–Q05; producer fix только при правильном RED. Не удалять mismatch check.

## H-02 / CONTEXT — retained failure ожидает explicit retry

Scheduler намеренно оставляет RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY. Новый human
signal не доказывает recovery и не открывает admission. Не превращать UNKNOWN в
автоматический бесконечный retry. Персистентный retained failure обязан быть виден в
диагностике и запрещать cohort PASS. Исправление policy recovery не нужно без отдельного
доказанного retryable class; в этой задаче нет разрешения оживлять PLAY state вручную.
