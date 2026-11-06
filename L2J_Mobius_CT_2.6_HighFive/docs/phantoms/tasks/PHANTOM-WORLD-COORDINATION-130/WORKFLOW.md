# Один productive flow — дорого тестировать только то, что требует настоящего GameServer

## Рабочая среда
1. Непосредственно в новом диалоге Codex прочитай документы TASK130. Подтверди `ls-remote` для двух веток, origin ancestry, `Java25`, Ant, MariaDB 11.4.13; источник WIP TASK129 remote preserved. Работать в **ОДНОМ** независимом clone (например `D:\Codex\TASK130-20261106\source`) + **одном** минимальном runtime stage с новой защищённой TEST130. Не разворачивать 10 клонов и не создавать 30ГБ рекурсивных preimage на каждую promotion.
2. Не создавать отдельный git-root в основным WIP109. Пакет задачи распаковывается как новые docs в WIP checkout — только чтение после распаковки. Не переносить старые временные базы, JAR или raw logs из TASK129 в новый stage. WIP remote branch и bundle — recovery point.
3. Сначала источник и exact diff F1–F9. Зафиксировать причинную зависимость в **одной странице схемы**: current Intent/Presence/Ecology/Materialize/Party/Background/Scheduler owners. Кратко обосновать, какие существующие интерфейсы расширять; не создавать `NewPhantomManager` и второй «универсальный AI» ради абстрактности.

## Инженерная реализация — один цельный результат
- В одном кодовом направлении соединить durable group (0 materialized), identity saga/CAS/recovery, typed demand, background/foreground boundaries, ephemeral native Party, physical target+census и finite scheduling. Поднимать/интегрировать имеющийся WIP129 код без wholesale cherry-pick заново.
- Сразу закрыть F7/F8 из SECURITY: их опасные изменения нельзя не замечать до публикации. Если безопаснее убрать observer из `Heal.java`, убрать и заменить подтверждённым read-only instrumentation; тест ordinary human semantics обязательно.
- Проверять cheap model+SQL integration по фактам до создания тяжёлого runtime. Крупная работа НЕ разбивается на TASK130A/B/C, но промежуточные RED/GREEN — часть обычной разработки. Пользователя по рутинным решениям не тревожить; если появилось новое **существенное** игровое правило/власть Clan или необратимое действие — согласовать.

## Стоимость цикла
- DB-free/policy tests: ожидание 1–30 секунд; полноценные targeted integration tests с малой test DB: обычно 1–3 минуты; стандартный профильный build/test-проход стремиться <=5 минут (измерить реальное базовое время и оптимизировать на данных). Не превращать «5 минут» в запрет на правильно обоснованный GameServer boot.
- Профильные тесты на фоне 5000 durable должны оставаться bounded и не требовать создания физически 5000 L2J Player или 5000 thread. Виртуальное время/ticks для 40–85 и background допуска; реальная MariaDB SQL test для aggregate/cursor/ledger.
- Для native до CUT получить **реальную preflight capability**: роли/класс/уровень/скилл, позиция, допустимая помощь, ресурсы, Geo/route, player epoch, расходники. Если readiness не выполняется, не запускать 20минутное окно `WARM` в надежде на удачу. Подготовка до CUT разрешена, но после CUT все игровые действия строго native.
- После первого полноценного native smoke запустить sameDB/sameFINALJAR restart. Все другие длительные soak/performance scenarios не исполнять как часть fix-loop; benchmark отдельно уже после достигнутой полезной vertical. Не запускать GameServer ради текста отчёта или нового `if` без targeted passing integration.
- Две одинаковые failed native попытки без новой доказанной причины = остановить lane и вернуться в model/integration diagnosis, не продолжать дорогую кампанию. Если долго нет продвижения, зафиксировать durable checkpoint с версией кода и сравнением цена/результат, а не просить «ещё 90 минут» автоматически.

## Reusable diagnostics вместо одноразовых инструментов
- По возможности один `run-focused`/`run-integration` entrypoint в существующем test runner; один компактный `status` endpoint/TSV с owner, goal, group, lastUsefulAction, waitReason/nextDue; один проверенный G0/postflight wrapper. Каждый запуск пишет маленький immutable manifest с hashes/log paths, без дубликатов всей Stage.
- Не плодить новые 150–900-файловые preimages и вложенные `preimage/private/promotion` деревья. Backup только изменённых файлов/manifest, reuse hardlinks в обычных временных копиях **только если безопасно и допускает установленная процедура**; не делать их на protected source/WIP.
- При исключении ошибка должна быть fail closed, но `profileId/groupId/reason` регистрируются с rate limit. Не пересоздавать giant observer/agent только ради HP/MP: stock read-only native evidence по существующим callbacks.

## Git/report
- После здоровых contract tests делать обычные scoped commits в independent clone; remote backup WIP129 не менять. `git diff --check`, compile, focused, baseline product regression, native JAR/SQL provenance, protected seal. Изменения клановых правил, PvP/PK, Rates запрещены.
- Если A–G достигнуты и F7/F8 безопасны, обычный non-force fast-forward push в `feature/phantom-world`, со свежим `ls-remote` и checked ancestor. Если partial, опубликовать чисто зафиксированный source прогресс в **одной** отдельной `wip/coordination-130-preserved`, report честный; main не трогать. Никаких force, cross-branch accidental push, purge temp TEST129 или удаления чужого.
- Итог компактно: что реально работает (LOGICAL/SQL/NATIVE/RESTART), diff/new architecture seams, tests and runtime counts, metrics actual vs target, reason not FULL if not, GameServer error audit, source+JAR SHA, seconds/minutes per feedback loop, сколько этапов ни разу не запущено и почему.
