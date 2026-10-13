# DESIGN041 — закончить существующий контур, не строить очередную надстройку

## A. Один владелец и конечное действие

Сохранить native Player/stock AI/AutoPlay/AutoUse, действующий scheduler, PhantomEngagementPlanner/Controller и navigation. Различать: допустимость цели, готовность capability, состояние пути, earned работа, законный отдых, исчерпанная попытка. При idle сначала установить причину по тем же source fields, которые управляют игрой. Запрещено вводить таймер «через N секунд телепортировать/пересоздать», обнулять debt или лечить каст общей метлой.

В040 уже согласованы фактический AutoUse cursor/range и завершение resource tuple. Сначала сохранить их тесты. Разрешены чистый helper или разделение перегруженного owner, но не второй Supervisor, общий fallback по class/NPC/zone или новая библиотека.

## B. G4 — передача владения, а не смена флага

ACTIVE native profile → soft locality release → дождаться earned drain и commit → BACKGROUND с полезной работой → восстановление при реальной death, если случилась → reentry того же profile в новую legal native epoch → полезное native действие.

Продуктивность привязана к прошедшему реальному времени и действительному producer. Две одновременно пишущие стороны запрещены. Native присутствие observer при soft-away не должно случайно удерживать исходный район: позицию и роль источника locality проверять. Не снимать hard pins, чтобы «добиться» ухода.

Если исходный живой профиль законно умирает в фоне, не заменять его удачливым: сохранить positive work/death loss и довести этого же profile через существующий recovery до useful return. Допустимо не монотонное EXP; недопустимы исчезнувший инвентарь, неатрибутированная награда и вечный VERIFY_PENDING.

## C. G3/G5/G6 — один источник сохранённой истины

Реальные atomic operations и их existing CAS/owned-store — основание сравнения. Использовать039 immutable witness и040 native-load capture. Для goal/context найти фактического изменяющего writer и его commit boundary; codec roundtrip или version+1 не доказывают происхождение перехода.

Если не хватает только наблюдаемости: один optional immutable completion witness в существующем owner, публикация ПОСЛЕ подтверждённого commit, rollback/unknown не дают success. Никаких blocking SQL/fsync/I/O на gameplay callback, новой audit-таблицы/WAL или second scheduler. Recorder может сломаться — продукт и commit не должны зависеть от него.

Initial cut включает исходный cohort и реально существующие проверяемые identities, далее добавляет ВСЕ рождения за эпизод. Final stopped cut — same DB, consistent transaction после завершения writers. Сравнивать payload/hash/version своего commit, а позднее состояние связывать реальными переходами. Goal ephemeral runtime-поля отделять по существующему контракту от durable семантики, но не отбрасывать неудобные durable поля ради равенства.

## D. Конечная доказательная область

Для041 заранее объявить FINAL_ACCEPTANCE_SET041: final product/config/evaluator и конкретные обязательные эпизоды SCENARIOS. Исторические incomplete/FAIL039–040 остаются такими; их отсутствие RUN_END нельзя восстановить и оно не означает «вечно открытый М1» при новом полном испытании. Это новое явно ограниченное испытание, не изменение фактов старого G3 всей кампании040.

ВСЕ births/writes внутри каждого включённого final episode обязательны; не фильтровать неудачные ID. Нерешённый product failure на действительном final candidate/preconditions блокирует gate даже при другом удачном run. Только установленная ошибка измерения допускает пометку INVALID_EVIDENCE, её минимальную причинную починку и новый полный run с сохранением старого. Старые product failures требуют объяснённого fix или доказанно иных предусловий, не просто нового имени эпизода.

Recorder один, bounded; закрытые JSONL/сжатые streams, не файл на каждую запись. Устранять ненужное удержание подписок/лишний fsync/ошибку ожидания/LASTEXITCODE, а не увеличивать ring/disk cap. До длинного run короткий positive+negative на полноту, версии/epoch/отсутствующий tail; этого достаточно. Не писать framework проверки verifier.

## E. Observer — фокус, не управление

Сохранить LocalPlayM1Observation.Focus и текущий LocalPlay worker. AUTONOMOUS существует без heartbeat shell; current REAL connection получает focus; disconnect старой connection не выключает новую. На реальном выходе fallback, при входе fresh REAL, при движении/ТП правильные XYZ/instance. Ни focus, ни auto-attach не разрешают исполнять команды за пользователя. Consent для активного Pilot отдельный и не продлевается ради observer.

В обычной поставке сохраняется ownership/security model. Не отключать safeDirectory/privateAcl/manifest/current-client guards ради переносимого пути. Тонкий tracked launcher может создавать необходимые локальные manifests из фактической конфигурации своей поставки. Продукт не должен требовать сам код ops040/ops041 после упаковки.

## F. Интеграция вместо «полезно только в worktree»

Все semantic changes в tracked java; нужные configs/data в tracked dist; повторно используемые build/start/stop helpers в tracked tools и обычной поставке. Test-only selectors/injectors/JDI/recorder остаются вне release. Сборка из чистого tracked snapshot доказывает включение035–041, не простое копирование готового старого runtime.

DELIVERY.md — обязательный acceptance R1 и сохраняемый M7 contract. Сейчас нужен минимальный работающий операторский комплект текущего М1, а не все игровые features М7. Никакой автосинхронизации поверх играющего040a и никакой незаметной потери прежних QoL/config/data.
