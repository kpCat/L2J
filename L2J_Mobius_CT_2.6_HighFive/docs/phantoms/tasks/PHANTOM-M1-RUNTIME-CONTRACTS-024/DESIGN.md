# DESIGN024 — одно source-of-truth правило для каждого состояния

## Ключевые инварианты
Native Player владеет текущими HP/MP/EXP/SP/XYZ/inventory. Background model — допуском к
виртуальным переходам и их расчётом. Anchor — привязка плана/области, не обязательная точка
для записи characters. World indexes — реконструируемые оперативные представления.
Ни один новый контракт не разрешает повторное начисление, использование старого epoch или
запись после завершения ownership. Цель AI остаётся фармить подходящую область; NPC object
есть временный target, не вечная цель с бесконечным pursuit.

## CONTRACT-B — сначала сохранение, отдельно simulation eligibility
Минимальная выбранная модель не меняет background.state/owned-store bytes и SQL schema.
Position хранит точные native instance/X/Y/Z/heading; committedAnchorId остаётся доказанной
привязкой из previous state или exact current goal, но его наличие само по себе НЕ доказывает
геометрическое попадание. При nativePersistence допускается off-anchor snapshot с этой
привязкой. Не подставлять координаты anchor, не телепортировать персонажа для store.

1. При captureOwnedNative проверить exact owner/epoch/goal/drain как раньше.
2. Зафиксировать actual native XYZ и real counts. Если геометрия подходит предыдущему
   anchor/FARM area, сохранить это. Если нет, сохранить доказанную previous/goal reference
   как provenance, exact XYZ и non-simulation eligibility. Не выбирать случайный ближайший
   anchor и не выдумывать reference при её полном отсутствии.
3. Использовать существующий background.native-context как native eligibility proof:
   добавить В КОНЕЦ Eligibility `POSITION_REQUIRES_NATIVE`, не менять ordinal0/1/2.
   Доходящий до PREPARE NativeCapture получает эту eligibility, если position не разрешена
   для выбранной привязки. Points/vitality сохраняются без изменений. Позиционная причина
   имеет приоритет при off-area; при последующей реальной attestation внутри области заново
   вычисляются BOTH position и vitality (не sticky workaround).
4. Existing PREPARE → immutable OwnedStoreSnapshot → native store → FINALIZE вместе с
   pending/completed native-context остаётся единственным протоколом. Не добавлять второй
   независимый commit или best-effort journal. Существующий native-context уже привязан к
   state row/hash и receipt; используй эту привязку.
5. READY после cleanup означает пригодный сохранённый живой персонаж, НЕ автоматический
   допуск к virtual farming. `simulationEligible()` остаётся exact SUPPORTED; новый код не
   разрешает виртуальные rewards/travel при POSITION_REQUIRES_NATIVE. Проверить каждый
   farm/travel/acquisition/historical entry, а не только ordinary farm.
6. Rematerialize загружает точные native координаты, затем existing handoff/local replanner
   продолжает локальную игру. Обычный повтор Player.load на off-anchor без движения не
   должен «доказать» simulation eligibility. At real arrival в valid area существующий
   captureVisibleArrival/native attestation может снять только позиционный барьер.
7. Если действительно отсутствуют previous/goal anchor или identity/hash не подтверждены,
   это другой corruption case: fail-closed, сохранить evidence. Не ловить любой exception
   как «геометрия не подходит». Отдельная typed `NativePositionBinding` допустима, чтобы не
   превращать RuntimeException в разрешение.

Compatibility: PNC1 packet shape/действующие enum ordinals сохраняются; старые UNKNOWN/
SUPPORTED/VITALITY payloads читаются byte-exact, новый ordinal roundtrips. Старый бинарник
не обязан понимать новую eligibility: rollback требует pre-task DB backup, не старый JAR
на новой БД. Не менять MAGIC/state schema/receipt layout и общую TEST schema metadata.
Если repo требует version increment для добавления enum — только backward reader1+2 и
atomic component writer в том же протоколе, с отдельным compatibility test; без DDL.

Обязательный контроль: вне anchor сохранены точные EXP/SP/vitals/XYZ/items, background
income запрещён, same-DB load успешен. Никакого fake INCONSISTENT/VERIFY_PENDING как штатной
позиционной классификации и никакого очищения receipts ради следующего run.

## CONTRACT-C — зависимости живут дольше их завершающих пользователей
Порядок: прекратить новые demands/plans → дождаться in-flight producers → закрыть action
admission → завершить опубликованные earned callbacks → PREPARE/native/FINALIZE/POST_STORE
→ освободить World/identity → остановить topology/index/progression dependencies → pool.
Topology RUNNING нужен для существующих lifecycle updates до конца materialization drain.
Preferred fix: перенести topology.beginStop в фазу после materialization shutdown и
background finish, одинаково для RUNNING/FAILED retry веток. Scheduler/другие producers
уже не запускают gameplay; не открывать обратно action admission.
Если отдельный POST_STORE требует maintenance операции после STOPPING, разрешён точный
lifecycle-only метод, а не глобальное расширение runningView() или разрешение всех сигналов.
Проверить actual lifecycle chain и signal cleanup; не ориентироваться только на порядок
двух строк. Deadline waiting — вне system/topology/Player monitors.
Повтор stop идемпотентен. После committed native FINALIZE отказ index publication не должен
выдавать второй reward/store или переписывать before snapshot; хранить два отдельных результата.

## CONTRACT-A — начало действия ≠ продолжение чужого действия
Existing generic run/schedule и delayed HitTask/MagicUseTask остаются строгими.
Добавить узкий `runOriginalCombat`/equivalent entry для НОВОГО doAttack/beginCast.
Новое действие получает явный Origin.NEW_NATIVE_ACTION, независимо от наличия чужого EARNED
ниже по стеку. Никакого ThreadLocal.clear/remove на весь stack, никакого `if isNpc allow`.

Алгоритм: захватить exact current actor/direct target references; проверить stale captured
actor/frame отдельно; для КАЖДОГО managed participant нового действия получить здоровый
CANCELLABLE admission root ДО doAttackNative/beginCast и их MP/shot writers/publication.
Новые roots допустимы только OPEN/current. Даже ambient running EARNED не даёт права
создать новую retaliation на DRAINING/FAILED owner. Start всех exact roots, затем existing
combat observation и native action. Публикуемые hit/cast children захватываются под этими
roots; они EARNED только в своей существующей publication point. Finally все roots закрыты.
Не повторять исчерпанную операцию после partial mutation. Отказ ДО publication = bounded
native defer/no action, не poisoning исходного заработанного удара и не fake success.
Старый delayed callback с target/owner старого epoch не переводится в NEW_NATIVE_ACTION.
Точки вызова origin выбираются статически в approved original entry, не по kind string и
не по наличию exception. Damage/reward fresh write boundary из023 переиспользуется как
отдельный контракт, не заменяет оригинальную attack admission.

Позитивный RED: A hit вызывает native AttackableAI, NPC выбирает другой exact OPEN B,
retaliation публикуется и реально бьёт B, A продолжает. Парный SEALED B: новая атака не
публикуется, не пишет B; уже заработанный A hit законно завершается. Никто не reopened.

## CONTRACT-D — locality по области, маршрут по waypoint, фарм по возможности
Текущее назначение FARM area/npc сохраняется, пока подходит level/resource/native legality.
Расстояние до representative anchor point само по себе не делает большую FARM area недоступной.
Предпочитай уже подходящую область, содержащую Player, и доступные native цели.
Для outside area — bounded distance-to-area/достижимая local approach, не global run.
Arrival определяется current useful position в выбранной factual area и stock доступными
действиями, а не обязательным визитом в искусственный offset32/64/96 около центра.
Проверка уже выполненного arrival идёт раньше истёкшего deadline старого journey. Но старый
terminal не очищается одним сдвигомXYZ: новый маршрут должен получить новый валидированный
plan/attempt identity, отменённые callbacks не выдают MOVE_TO.

Failure registry разделяет exact attempted route/waypoint и непригодность entire npc@area.
Не исключать весь farm target навсегда после единственной ошибки пути. Bounded TTL только
для route failure; same failed witness не повторять бесконечно. При genuinely unavailable
native targets — limited alternate local waypoint/area, затем понятный native rest/return;
healthy actor не стоит до restart. No visible teleport, no sensor reset, no changing spawn.
Смена плана использует existing atomic replacePlan + runtime reload/permit rebind023/021.
Не вводить ещё один Brain/DecisionEngine; stock AutoPlay/AutoUse/GeoEngine исполняют.
