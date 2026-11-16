# PHANTOM-LEARNING-016 — план реализации

> Для исполнителя: после утверждения владельцем использовать `superpowers:executing-plans`, выполнять этапы последовательно в этой сессии. До утверждения разрешены чтение и этот план; реализация и активные TEST-запуски не разрешены.

**Цель:** накопительно подготовить один новый законный cohort, доказать STOP/RESUME и seal, затем получить настоящий native TRAIN без независимой EVAL и promotion.

**Архитектура:** additive TEST-only `TRAIN016_INCREMENTAL_PRECUT_V1` внутри существующих `Training.ps1` и `PhantomAutonomousTrainingNative`. Источник истины — immutable записи переходов с полным состоянием отбора и связями SHA; изменяемый индекс, если нужен, только производная. `PAIRED_PRECUT` не даёт TRAIN credit. Существующие Player/Party/Combat/World, `TrainingStore`, `TacticalStoreV3` и `AutonomousTrainingV1.cohortSlots` остаются authority.

**Стек:** JDK25 / Ant, Windows PowerShell5.1; новые зависимости не добавляются. Спецификация: все документы данного пакета, прежде всего `TASK.md`, `INCREMENTAL_PROTOCOL.md`, `TEST_MATRIX.md`, `ACCEPTANCE.md`.

## Проверенная база и read-first

- Основной checkout: `D:/Codex/repos/L2J-phantom-world`, HEAD `7e997636dbac9d5e1754e00c793cd6fac858b0c6`, ветка feature, чужой Studio dirt. Рабочие исходники здесь не менять; сейчас добавляется только этот план.
- Source004: `D:/Codex/repos/L2J-learning-004`, feature, HEAD `eafcee66c18212ee9a2cdc08225ba4d5ecb0e302`, `git status --short` пуст. Fresh fetch и независимый GitHub ref показали `2ae5d18302ba82704541e1ee483d7c2c880cacf2`: параллельная Studio работа. Exact diff root AGENTS/High Five пуст; ancestry подтверждена. После утверждения — повторный fetch и безопасный fast-forward чистого source004; при новом пересечении scope остановиться.
- TEST006: `D:/Codex/repos/L2J-learning-006`, HEAD `42d6b675a9cf57faa4df8a74cc52a8b4adda97fa`, feature с восемью изменёнными файлами прежнего overlay. Их принадлежность и SHA ещё нужно сверить; не считать их disposable. Доступ к private train root из текущей sandbox-проверки отказан; G0 этим не подтверждён.
- Прочитаны root/module `AGENTS.md`, README, `PHANTOM_CURRENT_STATUS.md`, `CODEX_WORKFLOW_CONTRACT.md`, релевантные разделы `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `TASK_PACKAGE_STANDARD.md`, `COMBAT-AI-ROADMAP.md`; весь пакет016 с `MANIFEST.sha256` (11/11 совпали); RESULT/отчёт/review/amendment/commands015.
- Код: `planCells`, `admitHeadlessCell`, `prepareSources`, `run`, `collectEpisode`, headless deadlines и immutable writer; `AutonomousTrainingV1`; `TrainingConfig`; `Training.ps1`, `TrainingOwner.ps1`, `Test-Training.ps1`; relevant `AutonomousTrainingChecks`, `TrainingStore`, `TacticalStoreV3`, `TacticalTrainerV3`, `build.xml`.
- Локальные аналоги: A/B physical/freshness checks в `admitHeadlessCell`; fixed schedule и skip reserved в `run`; owner atomic writer и native budget/held-exit recovery в `TrainingOwner`. Они переиспользуются, V2 evaluation codecs не меняются. Отдельные code-map/pattern-документы и дополнительные AGENTS по целевым подпапкам не найдены; повторный широкий поиск не требуется.
- Причина015 подтверждена: `84/17=4`, готовые пары и selection state живут в памяти до финальной записи `cells.tsv`. Дополнительно обе общие проверки конфигурации сейчас требуют `%12==0`, поэтому одного wrapper guard на51 недостаточно. JDK `25.0.4.1`, Ant `1.10.17` доступны; build/tests/SQL/headless/GS/FIT в016 ещё не запускались.

## Решение и границы

Предлагается один совместимый путь: journal рядом с существующим cohort, replay в том же planner, затем связанный seal. Повторный подбор с нуля теряет fairness и готовые пары; переоформление raw015 запрещено. Отдельный planner/SQL schema/production refactor не требуются.

- Явный opt-in `autonomous016Version=1`; без него сохраняется legacy поведение. Для016 обе конфигурационные проверки требуют `autonomous013Version=1`, `episodesPerBatch=51`, evaluationOnly=false, TRAIN_ONLY и один campaign/cycle. `84`, `48`, чужая версия/кампания/015 receipt отвергаются до SQL. Это исправление расписания, игровые eligibility gates не ослабляются.
- Campaign: `autonomous009-learning016-v1`; dataset: `autonomous009-learning016-v1-c000001`. Одна durable campaign identity, отдельная от меняющихся worker/process nonce. Все config/JAR/source/control/actor/inventory/Geo/protected/model/terminal-Adam pins заморожены до первого native admission; смена pins после записи — STOP/STALE, без перепривязки старых пар.
- Три distinct сцены, минимум два learner classes и Solo+Party немедленно завершают подбор; после этого четвёртый native candidate не вызывается. До admission третьей сцены проверить, что она закрывает недостающее покрытие; иначе сохранить отказ по coverage и продолжить fair selection, не фиксируя непригодный набор из трёх и не расширяя cohort. Расписание строго штатное: 51 слот =33 TRAIN +9 TUNING +9 AUDIT, порядок/AB-BA назначены до outcomes.
- Journal сохраняет source order, seed, sequence, visits, shortlist/page cursor, roster visits, scene counts, отказы/их причины и исходные immutable snapshots. Каждый proposal резервируется до native; каждый отказ и каждая подтверждённая пара завершают атомарный переход с последующим selection state. Пара ссылается на обе raw руки, physical/source/ability hashes и разные incarnation IDs/epochs.
- STOP между руками сохраняет первую как историческую incomplete; cold resume создаёт новую попытку обеих рук того же незавершённого кандидата с новыми live identities. Полностью committed пары только перечитываются и не воспроизводятся. Частичный marker/pin/hash/reparse/неизвестная запись блокирует готовность; нельзя домыслить успешный commit или обнулить счётчики.
- Запись016: staging в той же физической директории, проверка bytes/SHA, атомарная публикация immutable файла; готовность определяется последним complete marker. Использовать штатный atomic-move подход, без fallback к частично видимой записи. Отказ атомарной публикации — INCOMPLETE/STOP.
- Seal: проверить coverage и все ссылки, подготовить совместимые `cells.tsv`, `training-admission013.json` и `schedule.tsv`, hash-linked `cohort016.json`; `cohort016.complete.json` публикуется последним. Повторная валидация замыкания обязательна в wrapper до GS и Java до dispatch. Legacy отсутствие маркера не даёт016 допуска. Дозавершение прерванной публикации разрешено только из доказанного immutable журнала с точными bytes, без изменения исторических файлов.
- Перед каждым настоящим CUT заново воспроизводятся Player/цель и native learned level/enchant, inventory/equipment/shots/effects, MP конкретного каста, owner, target, Geo/LOS и Party checks. Pre-CUT IDs не становятся действующими после restart. Forecast MP остаётся риском по015, смерть — допустимым терминалом.
- Native reservation публикуется атомарно до CUT. Каталог без валидной reservation не означает settled/успешный эпизод: он блокирует или явно классифицируется INCOMPLETE. Неоднозначный reserved slot не dispatch повторно; credit остаётся штатным SQL exactly-once. `collection.complete` не доказывает sufficiency.
- В016 director заканчивается после COLLECT и условного одного FIT/cold checkpoint: ни child probe, ни EVAL/DECIDE через независимую EVAL, ни новый cycle. FIT только по достаточности новых законных016 данных существующего `freezeAutonomous`/trainer; повторный credit исключён. При недостатке `NO_FIT_INSUFFICIENT_VALID_DATA`, FIT0. acceptedTEST2 и исходный checkpoint сохраняются отдельно; новую модель не принимать автоматически.

## Exact allowlist и максимум изменений

Все пути далее относительно `L2J_Mobius_CT_2.6_HighFive/`. **Ровно7 source/test/script/build файлов**, новые production классы и новые test projects не нужны:

| Путь | Разрешённая дельта |
|---|---|
| `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` | Private016 journal/replay/seal, точечные seams существующих planning/admission/run/reservation, DB-free `incremental-contract016`/failure injection. |
| `java/org/l2jmobius/gameserver/phantoms/learning/TrainingConfig.java` | Только opt-in016 config51/TRAIN_ONLY/campaign/version/budget validation. |
| `tools/phantom-runtime/training/TrainingOwner.ps1` | Паритет config016; отдельные016 budget primitives по существующему reservation/held-exit паттерну, legacy013 ledger неизменен. |
| `tools/phantom-runtime/training/Training.ps1` | `Readiness016Session` 1–3, durable cumulative ledger, resume/seal checks, существующий director TRAIN_ONLY до завершения016, max one FIT. |
| `tools/phantom-runtime/training/Test-Training.ps1` | Focused `-Readiness016`: полный config path до SQL, actual director branches, budgets/drain/recovery/no EVAL/one FIT. |
| `test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java` | `config016`: реальные Java load/validation cases51/48/84/version/evaluation, legacy regression. |
| `build.xml` | Только targeted DB-free `phantom-learning016-test`, использующий существующие runners/compile-tests. |

`AutonomousTrainingV1.java`, `TrainingStore.java`, `TacticalStoreV3.java`, `TacticalTrainerV3.java`, `TacticalModelV3.java`, reward/objective, production Party/Combat/Spawn/SkillData, UI и bundled INI **вне allowlist**. Добавление восьмого исходника или изменение этих контрактов требует отдельного owner review, не выполняется автоматически.

Документы — bounded exception к общему числу8–10: это обязательная поставка одной подсистемы, а не cleanup. В этом task directory разрешены новые `IMPLEMENTATION_PLAN016.md`, `RESULT.md`, `EVIDENCE.json`, `ACCEPTANCE_RESULT.md`, `COMMANDS.md`, `HANDOFF_RESULT.md`, `READBACK.json`, `NEGATIVE_TESTS.md`, `PROFILING.md`; вне него только `docs/phantoms/reports/PHANTOM-LEARNING-016.md` и новый раздел `docs/phantoms/COMBAT-AI-ROADMAP.md`. Входные12 файлов `START_HERE.md`, `TASK.md`, `PRE_AUDIT.md`, `ARCHITECTURE.md`, `INCREMENTAL_PROTOCOL.md`, `PLAN.md`, `TEST_MATRIX.md`, `ACCEPTANCE.md`, `GIT_AND_PRESERVATION.md`, `REPORT_CONTRACT.md`, `HANDOFF.md`, `MANIFEST.sha256` можно добавить в source004 только точными побайтовыми копиями пакета. Историю015 и ранее не править.

Runtime scope после G0: только существующий TEST006 `.phantom-local/learning002/train`, штатные owner/control/exit/export outputs и новая кампания выше. Journal — `autonomous/v1/campaigns/autonomous009-learning016-v1/cohorts/autonomous009-learning016-v1-c000001/incremental016/`; новые cumulative ledgers — `autonomous/v16/`. Исторические namespace/SQL rows запрещены к переписыванию. Перенос в TEST только exact7 обновлённых файлов, относящихся к ним compiled test classes и собранный source JAR; перед заменой — внешняя byte backup/SHA и сверка expected prior bytes. Существующий чужой/неидентифицированный overlay блокирует перенос. Source `build/` и source `dist/libs` — лишь штатные build outputs; MAIN/PLAY/защищённые поставки не затрагиваются. Приватные actors/SQL/config credentials/models не входят в Git.

## Выполнение после одного утверждения

1. [ ] **G0:** повторить fresh fetch/ancestry/dirt, fast-forward чистый source004; проверить дополнительные инструкции до записи. Побайтово сохранить заменяемые TEST/source/runtime inputs вне checkout, SHA-256. Штатными Preflight/Export/cold reader проверить owner/process/ports, пять SELECT и historical183rows SHA, TEST2/DRAFT14/Adam903updates223epochs/apps48/grad16/pointer12, Geo203/protected207. Export не readiness, но его SQL lifecycle отдельно учесть в cost evidence. Никакой собственной SQL schema или GS. При несовпадении/неподтверждённом exit — STOP.
2. [ ] **G1–G2 RED:** существующими runners получить поведенческий отказ на недостающих016 контрактах; compile/fixture error не считать RED. Три focused группы: config/director/budget; pair journal/replay/failure injection; seal/reservation/native boundary. R01–R14/R16–R18 из матрицы распределить внутри этих групп, без множества новых harness.
3. [ ] **G1–G2 GREEN:** реализовать только7 файлов выше; сохранить signatures существующих `planCells`, `admitHeadlessCell`, `prepareSources`, `collectEpisode`, `run`. Новые private016 helpers не являются public API; exact persisted schema/transition contracts определены выше. Проверить сразу: запись пары до следующего admission, восстановление без повторного pair admission, честные negative counters/cursors и seal после третьей сцены.
4. [ ] **Регрессии:** последовательно `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-learning016-test`, `phantom-learning015-test`, `phantom-autonomous-training-test`, `phantom-learning008-test`, `phantom-learning007-test`;015 включает011–014,009 покрывает autonomous route. Затем `jar` и `phantom-humanized-v3-content-validate` только в source004. Имена существующих targets подтверждены в build.xml. PowerShell5.1: `powershell -NoProfile -ExecutionPolicy Bypass -File .\L2J_Mobius_CT_2.6_HighFive\tools\phantom-runtime\training\Test-Training.ps1` с отдельными аргументами `-Readiness016`, `-Readiness015`, `-Readiness014`, `-Headless013`, `-Action Test-Cycle013`, `-Action Test-Config`, `-Action Test-Atomic`. Каждый exit code/log фиксируется; GUI не менять и не объявлять проверенным. Широкий `ant verify` запрещён.
5. [ ] **G3:** frozen TEST config016,51; максимум3 readiness, всего1800000ms wall с SQL startup/worker/drain. Cap сессии = `min(600000ms, remaining cumulative millis)`;210000ms внутри cap резервировать на drain. При недостатке drain reserve новый сеанс не стартует. Ledger резервируется до SQL; незакрытый exit блокирует следующую сессию. Вторая/третья продолжают тот же journal; после seal оставшиеся сессии не запускать. Плановый реальный STOP/RESUME — только если первая подготовка ещё не завершена; готовый cohort ради демонстрации не перепроизводить.
6. [ ] **G4–G5:** только после полного G3, максимум3 GS и4500000 native-ms, штатный owned launcher/held exit, cumulative016 reservation до boot. Train51 schedule неизменен; STOP/RESUME продолжает незарезервированные slots. Учитывать настоящий CUT, paid MP/shots/effect и terminals, включая смерть; нулевые/unknown/неоднозначные исходы сохранять без fabricated credit. Новая EVAL/probe всегда0.
7. [ ] **G6:** frozen sufficiency по существующему objective/validator, без снижения порогов или старых samples. FIT0 при недостатке; иначе один атомарно зарезервированный FIT и cold readback selected/terminal Adam, accepted best отдельно. Crash recovery проверяет SQL lineage, не запускает optimizer повторно. Затем остановка016, без promotion и следующего cycle.
8. [ ] **G7–G8:** postflight historical rows по исходным IDs/SHA отдельно от новых строк, модели/Adam/pointer, Geo203/protected207, OFF/owned0/portsfree; фактические time/CPU/RAM/disk. Exact allowlist diff guard, две отдельные проверки encoding, scoped commits/non-force push feature; independent GitHub branch/parent/path list и полные тела всех опубликованных source/evidence, hashes/readback. Документы показывают G0–G8 и final manual gate REQUIRED; его принимает только владелец.

## Проверки наиболее рискованных границ

- До SQL: полный Java/PS config51 path, не отдельная stubbed проверка;84/48/EVAL/version/foreign campaign reject. Legacy config не расширяется.
- Crash points: после STOCK, после LEARNED до pair marker, после pair marker, до seal и после seal. Replay даёт ту же логическую selection sequence/coverage/cursors/refusal history, что непрерывный synthetic fixture; live IDs при повторе incomplete попытки новые. Synthetic данные никогда не TRAIN.
- Tamper: один SHA/owner identity/source/skill/Geo/model/JAR, truncated transition, чужой файл или reparse → STOP; raw015 даёт0completed016.
- Три законные разные сцены/2classes/Solo+Party дают51 слот и0вызовов четвёртого admission. Первая рука/созданная директория не дают pair/seal/settlement.
- Actual director COLLECT/FIT/resume не вызывает `Invoke-TrainEvaluation013`; budget deletion/replay/pending exit и повторный FIT запрещены. Live target movement/despawn/current-cast MP refusal проверяются до CUT существующими gates.

## Бюджет, остановка и непроверенное

Никакого автоматического расширения: ≤3headless/30wall минут, ≤3GS/75native минут после G3, ≤1FIT, один cohort/cycle. Не более двух точечных причинных исправлений вне запланированной реализации; третий независимый blocker или необходимость выйти за allowlist → PARTIAL/BLOCKED с сохранением evidence. После начала frozen campaign source drift не обходится перепечатыванием pins.

До утверждения не подтверждены: private TEST ACL/owner/process/ports, полная идентичность прежнего TEST overlay, свежие SQL/model/Adam/Geo/protected pins, фактическая атомарность016 и runtime G3–G6. Проверки реализации NOT_RUN; readiness/GS/CUT/FIT016=0. Общая read-only inventory не является G0 PASS.

FULL016 возможен только по G0–G8 и настоящим native paid effects/terminal; не означает завершение51эпизода или улучшение модели. Отдельно: TRAINED_MODEL=YES/NO, GAME_ADVANTAGE=NOT_EVALUATED, PRODUCTION=OFF. При отсутствии G3 — PARTIAL016_UNSEALED; при G3 без G5 — PARTIAL016_SEALED_NO_NATIVE; при unsafe credit/recovery — BLOCKED016_UNSAFE. Shots20+/Blessed/покупки, три сопоставимые смерти/адаптация, PvP/raid/buffs остаются отдельными незакрытыми требованиями. Задача№10/13, осталось3; следующий Goal не начинать.

**Owner approval: APPROVED — владелец 15.11.2026 явно утвердил этот план, TRAIN016_INCREMENTAL_PRECUT_V1 и exact7 allowlist в чате; рутинная реализация и проверки разрешены.** Финальная manual acceptance результата по G0–G8 остаётся REQUIRED.

## Исполнение016 и граница продолжения

Выполнение завершено как `PARTIAL016_UNSEALED`; утверждение архитектуры выше не является приёмкой результата. Source fast-forward: `eafcee66` → `2ae5d183` → `591c5261`; два последующих изменения затрагивали только Studio и сохранены. Exact7 исходников соблюдён.

При проверке всех3069 Java/SQL/build inputs обнаружены36 различий SOURCE/TEST только CRLF/LF, смысловых различий вне allowlist0. Эти36 файлов не переписывались. Штатный `Ensure-TrainBuild` в разрешённом Prepare сам собрал TEST и записал его настоящий stamp; ложного равенства byte fingerprints нет. Это bounded изменение оркестрации сборки, не архитектуры или source scope. Исходная оговорка о запуске Ant только в SOURCE оказалась недостаточна для фактического TEST stamp; дополнительная TEST сборка выполнялась штатным wrapper до SQL/headless, её стоимость раскрыта в PROFILING. Некоторые DB-free PowerShell проверки шли одновременно с source Ant; Ant targets между собой выполнялись последовательно. Полную последовательность всех инструментов не заявляем.

Фактическая session1:72,430s issued window; весь Prepare вместе со сборкой172,590s. Две свежие Solo pre-CUT руки,0PAIRED_PRECUT,0seal,0GS/FIT/EVAL/credit. Worker held exit2, SQL held exit0, OFF/own0/portsfree. Отказ `TRAIN016_PRECUT_MATCH_OR_FRESHNESS` вызван новой ошибочной проверкой глобальной уникальности NPC epoch: штатный `TacticalSnapshotV3.epoch(Monster)` возвращает локальную spawnGeneration, которая у разных новых NPC равна1. Исправлено только в SOURCE с RED/GREEN; повторные objectId и actor owner epochs по-прежнему запрещены. Gameplay eligibility не менялась.

Исправление не перенесено в frozen TEST. Его contract/JAR/build pins и59 файлов сохранены, исходный журнал не перепечатан и не сброшен. Повторный сеанс с тем же дефектом бессмысленен; подмена pins нарушила бы утверждённую границу. Автоматической migration/новой кампании нет.

Для продолжения016 требуется отдельное согласование владельцем **нового идентификатора frozen-подготовки и явного перехода от сохранённого состояния**. Предлагаемая граница: тот же exact7, сохранить исходный journal/receipts как immutable parent; перенести логические sequence/visits/cursors/отказы и порядок незавершённого выбора через проверяемый transition;0старых completed pairs/credit; заново заморозить исправленные source/build pins и создать свежие объекты. Это пока предложение, не реализованная migration. Session1 остаётся в общем бюджете; максимум ещё2headless и не более исходных30мин суммарно,3GS/75мин только после G3,1FIT. Требуется предварительный точный review такого перехода; согласование текущего V1 не распространяется на repin/reset. Следующий Goal не начинался.

## Git-команды при read-only аудите плана

### Разрешённое продолжение016 от 16 ноября 2026

Владелец явно разрешил ровно одну новую frozen generation и доказуемый переход; это продолжение того жеGoal, не новая задача. Предыдущий PARTIAL, исходная кампания/59файлов/SHA/errors/cost остаются историей. Новая identity: `autonomous009-learning016-g2-v1`, dataset `autonomous009-learning016-g2-v1-c000001`. Третья generation запрещена validators. Source актуализирован до `00afe4a0f3b4c70dc94fd9b4cdd4b24e7171766e`, новые34Studio paths сохранены, HighFive/AGENTS diff отсутствует.

Bounded реализация в прежнем exact7: config Java/PowerShell допускают только две точные identities; существующий Journal получает read-only replay для parent и один genesis import; existing016 planner/директор используют текущую campaign identity. Новый planner/SQL schema/gameplay rule не вводятся. Изменить предполагается6из7 (build.xml target016 уже существует). Артефакты/журнал исполнения — внешний g2 directory; входные12task files неизменны.

1. Parent validator сверяет все59ожидаемых файлов по первоначальным опубликованным hashes, точный file set, оригинальные base/director SHA, chain/head,0paired/0seal, fixed parent identity. Parent replay ничего не создаёт и не исправляет, включая head. Новый issuance marker/parent link публикуются только в новых paths.
2. Сравнить полную конфигурацию, исключив только campaign/dataset и lifecycle enabled/phase; все остальные значения должны совпасть. Проверить owner/Geo/protected/model/terminalAdam/seed и точный ordered source+physical+ability inventory. JAR/build/source меняются явным новым freeze, а не repin parent. При несовместимости STOP без сброса/подбора с нуля.
3. Единственный импорт переносит sequence/visits/cursors/scene+roster counts/refusals/samples/pending index и порядок строк. Незавершённые raw A/B/attempt identities не переносятся. Pending descriptions декодируются тем же codec против актуальных sources/skills; только новый protocol/config SHA пересчитывается и связывается с parent state SHA. Каждая новая рука получает fresh objects/owner и полный native recheck. Genesis transition идемпотентен после crash;0старых training credits/pairs.
4. RED→GREEN: только identity2 accepted/identity3 rejected; parent59/head/base/pins/config/source tamper rejected; перенос точных logical counters/order без pair credit; crash до/после импортного marker; NPC distinct objectId с generation1 accepted, reused object/actor owner rejected. Штатные targeted Java/PS/legacy tests, source jar и fresh independent review continuation delta до live.
5. Original session1-issued/result не менять. Global016 budgets остаются общими. Новая подготовка использует только session2 и при необходимом штатном STOP/TIMEOUT session3. Консервативно учитывать дополнительную build-часть session1:100160ms сверх её issued72430ms; суммарно исходные172590ms сохраняются. Старый cohort/control не архивировать с удалением; новый generation session2 лишь проверяет его held exits. Session3 может архивировать только собственные generation2 controls.
6. После3complete distinct lawful pairs/≥2classes/Solo+Party немедленно seal, без четвёртого native admission. Только после полногоG3 штатный GS TRAIN, максимум3/75native минут и1conditionalFIT, без EVAL/promotion. Если новая подготовка получает новый blocker — остановить активную работу, сохранить диагностику, закрытьPARTIAL; никаких новых generations/лимитов/цепочек исправлений.
7. Postflight/history/scope/two encoding checks, scoped commits/non-force feature push и полный independent GitHub readback. Отчёт обязательно разделяет actual pre-CUT, реальные TRAIN события/расходы/terminal и offline fixtures. Manual acceptance результата REQUIRED, следующий Goal не начинать.

Ruling: подробный generation-переход выше развивает прямо разрешённое продолжение и не вводит дополнительный существенный архитектурный выбор вне него. Риск несовместимости закрывается отказом полного validator, не молчаливой конверсией; runtime переноса до его GREEN нет. Новый parent issuance публикуется с известными первоначальными59SHA, а не выводится задним числом из текущих неизвестных bytes.

Разрешение: текущий TASK требует fresh remote/ancestry/dirty inventory; root AGENTS разрешает bounded inspection. Ниже точные уникальные команды; повторы проверки и fetch не меняли рабочие файлы. Commit/push/merge/reset/checkout не выполнялись.

```text
# MAIN
git rev-parse --show-toplevel HEAD
git symbolic-ref --short HEAD
git status --short
git status --short --untracked-files=no
git status --short --untracked-files=normal -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-016-INCREMENTAL-COHORT-AND-NATIVE-COLLECT
# SOURCE004
git rev-parse --show-toplevel HEAD
git symbolic-ref --short HEAD
git status --short
git status --short --untracked-files=no
git status --short --untracked-files=normal
git worktree list --porcelain
git fetch --no-tags origin refs/heads/feature/phantom-world:refs/remotes/origin/feature/phantom-world
git rev-parse HEAD origin/feature/phantom-world
git merge-base HEAD origin/feature/phantom-world
git merge-base --is-ancestor HEAD origin/feature/phantom-world
git merge-base --is-ancestor eafcee66c18212ee9a2cdc08225ba4d5ecb0e302 origin/feature/phantom-world
git diff --name-status HEAD origin/feature/phantom-world -- AGENTS.md L2J_Mobius_CT_2.6_HighFive
git diff --name-status HEAD origin/feature/phantom-world -- PhantomSemanticStudio
git remote get-url origin
git for-each-ref --format='%(refname) %(objectname) %(upstream)' refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world
# TEST006
git rev-parse HEAD
git symbolic-ref --short HEAD
git status --short --untracked-files=no
```
