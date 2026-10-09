# DESIGN030 — полная проекция плана до исполнения

## A. Продуктовая граница и preflight
Goal — миссия farm; конкретный drop/target — действие. Смена тактики не должна менять
формат native receipt. Сейчас разрешено исправлять только фактический контракт предметной
проекции и его reuse для обычного/historical farm. Native reward math029 не переделывать.

В existing ordinaryFarmAttempt после получения авторитетного FarmInput, до _model.evaluate:
построить точный Footprint из ПОЛНОГО input.target().drops(), для ACQUIRE, с текущим
ordinary spoil eligibility, и существующих shot/summon consumption ID. Не результат roll.
Отклонённые immediate/timed drops не становятся разрешёнными. Metadata bind содержит
identity/class, goal id/revision/digest, NPC/anchor, authority hashes/generation, loadout
и skill evidence, expected inventory canonicalHash/context version. Максимум существующий
PhantomBackgroundState.MAX_MUTABLE_ITEM_IDS; ID и bounds не менять.

Минимальная новая вложенная immutable record в PhantomBackgroundAuthority допустима:
`OrdinaryInventoryProjection` с required sorted IDs и указанным provenance.
Предпочтительный метод authority:
`ordinaryInventoryProjection(state, goal, farmInput, learnedSkills)`.
Это producer-bound proof, не внешнее разрешение писать arbitrary IDs.
Обычная/farm историческая ветви используют один implementation, без копии.

## B. Reprojection BEFORE RNG, separate metadata-only atomic boundary
НЕ превращать additionalMutableItemIds в «разреши любые delta». Acquisition constructor
и mutateItems guard сохранить. Не менять набор tracked facts посреди проверки native load.

В existing PhantomBackgroundTransaction добавить узкий метод с предложенной сигнатурой:
`refreshOrdinaryInventoryProjection(expectedState, goal, expectedCatchup,
 expectedContext, projection, backgroundLease)`.
Использовать текущие identity/locks/codecs/helpers. До записи проверить:
- profile/object/class, exact goal/catchup (если historical), READY, completed context;
- expected payload/version, authority/projection provenance и current hashes;
- current exclusive BACKGROUND lease и отсутствие native World/autosave owner;
- pending owned receipt отсутствует;
- actual locked characters/skills/items соответствуют ПРЕЖНЕМУ state/hash.

При mismatch canonical-фактов — отказ, не "переучить hash" и не очистка INCONSISTENT.
При metadata-only projection mismatch с доказанным источником:
- построить новую InventoryFacts из actual locked rows + exact required IDs + paperdoll;
- можно убрать старые больше не требуемые ID из ПРОЕКЦИИ; реальные предметы не удалять;
- full inventory hash/load/slots остаются вычисленными от всей actual canonical inventory;
- атомарно записать background.state и rebind текущего PNC1/PNC2 к новой version/digest;
- НЕ менять characters/items/skills, HP/MP, EXP/SP/vitality, XYZ, RNG, residual time,
  requestId, catchup cursor/interval или goal revision ради этой проекции;
- если всё уже равно, no-op без версии;
- ambiguous commit -> exact expected/proposed read reconciliation, no reward replay.

После successful reprojection закрыть текущий execution attempt как typed RETRY,
перезагрузить committed state на следующем bounded turn; старый batch вообще не считать.
Новый reason `farm.inventory_projection_refreshed` не переводить в failed goal.
Не проверять только «выпавшие» ID; редкий drop должен быть разрешён ДО RNG.
Если exact footprint provenance нельзя проверить, допустим bounded normal native-attestation
путь, но не неконтролируемый цикл refresh и не SUCCESS без новой проекции.

Если RED доказывает, что достаточно исправить один существующий producer refresh без
нового transaction method, предпочтителен этот меньший patch с теми же тестами/atomicity.
Это единственная одобренная альтернатива, не разрешение менять архитектуру произвольно.

## C. Расширяемость, сохранность и fault isolation
Отдельные allowlists для допустимой семантики и для конкретных snapshot-объектов.
Не привязывать product к110/447, item118, выбранному мобу или уровню.
Property: два разных legal farm каталога с разными drops проходят один и тот же native
store/receipt protocol. Arbitrary/foreign item delta по-прежнему отклоняется.
Подлинные INCONSISTENT, неподдержанные timed bonus и старые unknown receipts остаются fenced.
Не отравлять whole service из-за metadata-only preflight RETRY; настоящая corruption
сохраняет существующую safety реакцию и forensic evidence.

## D. Collector: streaming lifetime proof, не накопитель Player
Переиспользовать один Contract029Observer как Contract030Observer, прежний checkpoint/
birth/commit hook, один exporter. Не создавать вторую persistence-службу.
Стабильный RunKey=(runtime incarnation PID+start-time, code SHA, отдельный run UUID).
LifecycleKey=(RunKey, profileId, objectId, epoch). Epoch nanos не глобальный ID между JVM.

Разделить:
1) текущую fixed telemetry cohort;
2) active lifecycle registry — только live или terminal-but-not-yet-exported scopes;
3) append-only ledger на диске — все REGISTER/PREPARED/FINALIZED/TERMINAL/BACKGROUND events.

After terminal AND successful ledger export acknowledgement:
убрать strong Player/owner reference из active registry. История остаётся на диске;
background edges используют scalar subject identity и stream, не retained Player.
Никакого cumulative unique-profiles32 limit и lifetimes128 за всю JVM.
128 — максимум одновременно удерживаемых активных/невыгруженных scopes (проверить против
actual materialized+baseline concurrency). Ring512 records / <=16KiB scalar header per event;
большой immutable payload передаётся existing bounded representation, общий buffer<=16MiB.
Disk evidence budget512MiB на JVM/episode, измерять bytes. При исчерпании — evidence invalid,
bounded controlled stop вне native hook, но не изменение игрового поведения.

Hook: только exact event facts, bounded enqueue, никакого FS/SQL/ожидания exporter,
проверок всех исторических профилей либо foreign actor locks. Сохранять catch/isolation.
Sticky FIRST error через atomic compare-and-set, отдельные counters for refused birth,
queue overflow/export failure/missing terminal. Последующий успешный dump не стирает error.
Умышленный crash injector отдельно от пассивного collector, не перехватывать его как telemetry.
Не делать parent check по отстающему sampled TERMINAL: exact prior scope может уже быть
DETACHED до следующего sample. Записать actual evidence; ordering разрешает offline audit.
Duplicate immutable keys idempotent, conflicting duplicate -> proof FAIL. No silent eviction.
REGISTER birth hook before first receipt, terminal seal отделить от checkpoint SEALED.

FS errors — sticky proof failure, но не закрытие native admission. SQL exporter не должен
подавлять выгрузку receipt из очереди: raw commit event первичен; поздняя SQL — отдельный
view с captureVersion, который может включать дальнейшие background commits.

## E. Admission — измерять актуальный путь, не выбирать prewarm=eligible
Перед сценой source-pinned global census: human presence, actual observerXYZ, online schedule,
profile READY/DEAD/INCONSISTENT, ecology due/request, materialized count и exact first guard.
Выбрать cluster по CURRENT committed position и eligibility, до outcomes, не по прошлому ElfXYZ.
Historical step мог законно переместить/убить участника. Это не автоматически corruption.
Prewarm candidate652 != admitted candidate: фильтр должен проверять actual state/guard.
Весь исходный набор029110/142/175/260/278/404/447 остаётся longitudinal независимо от
scene selection. Не лечить их DML и не скрывать unavailable причин.

Разрешены до3 заранее ранжированных географических кластеров по текущему READY/ONLINE,
без сортировки по kill counters. Bounded discovery60s; после enrollment4..8 состав фиксирован.
Если cohort0, до abort сохранить exact actor-local path и причины всех8 ближайших
релевантных кандидатов. Если нужные факты можно снять read-only, не новый RUN ради логов.
