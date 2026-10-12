# DESIGN039 — доведение существующего контура, не новая архитектура

## Одобренное намерение
Пользователь просит одну полезную автономную ночь. Цель — настоящий native Player,
который выбирает действие, доходит, выполняет, замечает неудачу, корректирует решение,
сохраняется и возвращается в игру. M1–M7 за ночь не обещаются и не подменяются тестами.

## Сохраняем
PhantomEngagementPlanner/Controller038, общий session command gate,
stock PlayerAI/AutoPlay/AutoUse и очереди native navigation; существующие goal CAS,
identity/epoch/owned-store, background и LocalPlay Synthetic. Существующий объект
действия и состояние исполнения дорабатываются, а не создаётся параллельный Supervisor.

## A. Один замкнутый loop
FARM intent → native capability+target → action position/route → stock action
→ фактический progress → тот же action / alternative / legal recovery.
Доменные ограничения, краткосрочная готовность и исчерпанная попытка — разные состояния.
Не путать живой callback, реальный casting флаг и вечный stale intention.
Разрешены общие поправки в оценке capability/approach/retry при доказанном дефекте.
Одна и та же поправка должна работать на разных class/NPC/геометриях без ID exceptions.

Фактический progress обязан быть замечен даже при временной паузе. Same-epoch
смена goal/session не обнуляет долг. Но измеритель полезного прогресса не должен пропускать
реальный damage и затем ошибочно объявлять долг. Sticky native error не обнулять.
Busy/CANCELLED/NO_ROUTE/QUEUE_BACKPRESSURE не превращать в один вечный RETRY.
Не закрывать earned work ради удобного перестроения маршрута.

Позиция действия определяется действительной доступной capability, а не только названием
класса. Если текущий выбор умения и исполняемый AutoUse расходятся, доказать это native
тестом и согласовать существующий optional hook. Не заставлять всех магов бить кулаком,
не вводить универсальный запрет shared targets. Полноценные party roles — M2/M5.

## B. Commit boundary, а не поздняя фотографияSQL
Текущий verify_lifetimes038 сравнивает sealed payload с поздним отдельным SELECT.
Это корректная проверка равенства только если между границами не было другого writer.
В038 таких доказанных промежутков часто нет. Следующая версия сама по себе не corruption.
Одновременно native fields mismatch нельзя автоматически списывать на фон.

Выбираем commit-witness из существующего owning transaction: exact profile/object/
component/version/payload/context/full native comparison и подтверждённый commit.
Снять неизменяемые факты на уже имеющихся lock/read/write boundaries. Публиковать
в существующий bounded exporter только после подтверждённого commit; не добавлять
SQL/ожидание/fsync в gameplay/FINALIZED hooks. Никакой новой audit-схемы или второго WAL.
Если имеющихся journal/end и receipts достаточно — source hook не нужен.
См.COMMIT_BOUNDARY: приоритет reuse, затем один минимальный optional observer.

Один общий verifier для всех lifetimes, не точечная «доказательная починка» сотни ID.
Поздний SQL либо exact тому же commit, либо связан непрерывными подтверждёнными
переходами. Пропуск версии остаётся GAP; одинаковая версия/другой hash — CONFLICT.
Phase PREPARED/COMMIT_OUTCOME_UNKNOWN не доказывает committed write.

## C. Две разные вертикали поведения
Стабильный фарм: стартует живая естественная группа, и все проходят неизменённый
CONTINUITY_V2. Не стартовать starts-alive measurement с уже DEAD Player.
Реальная смерть/отход внутри окна сохраняет FAIL этой узкой проверки.
Отдельная lifetime-проверка доказывает законную смерть/возврат или soft away/background/return.
Она не требует бессмертия; требует настоящего полезного продолжения и сохранности.
Старые037/038 faults остаются как были. Новый дополнительный сценарий не переписывает их.

## D. Итоговый кандидат
До финального freeze допускаются несколько связных исправлений и проверок.
Если после первой failed пары осталось время, возвращаться к A/B и повторять затронутые
gates на новом committed кандидате. Это прямо разрешённая работа039, а не новый task.
После freeze не менять source/config/catalog/evaluator между финальными scenes.
При реальном дефекте после freeze сохранить partial verified и безопасно закончить;
не торопиться в небезопасную правку без бюджета проверки.

## Что не делаем
Не заменяем geodata/библиотеку AI, не увеличиваем радиусы или population, не переделываем
экономику/профессии/рейды. Не вводим истину по флагу autoPlay=true или по одному screenshot.
Никаких произвольных teleport/HP/MP/loot для скрытия зависания. Полный RETURN→GK→farm
может быть полезен, но не становится новым обязательным глобальным travel milestone M1.
