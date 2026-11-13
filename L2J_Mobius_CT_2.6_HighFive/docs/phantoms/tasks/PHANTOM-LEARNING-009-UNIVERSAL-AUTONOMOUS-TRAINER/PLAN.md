# PHANTOM-LEARNING-009 — проект решения и PLAN

Статус: PLAN_APPROVED — явный ответ владельца «Подтверждаю PLAN — начать реализацию». Дата: 13 ноября 2026, Europe/Chisinau.

`/goal PHANTOM-LEARNING-009-UNIVERSAL-AUTONOMOUS-TRAINER`

PLAN утверждён владельцем одним явным подтверждением, включая exact38-path scope. Реализация ведётся в существующем TEST на wip/phantom-learning-009. Приведённые ниже исходные факты относятся к read-first pass перед изменениями; текущее состояние и выполненные проверки фиксируются в PROGRESS/EVIDENCE. Финальный manual gate009 остаётся REQUIRED.

**Цель:** один автономный TRAIN-конвейер существующей V3, работающий без Codex и открытого GUI, с полным обнаружением игровых классов/навыков и честным ограничением обучения поддерживаемыми native DAMAGE/HEAL adapters.

**Архитектура:** расширить существующие Training.ps1, ownership/broker и TacticalStoreV3/TacticalTrainerV3. Read-only inventory отделить от текущей eligibility и ограниченного shortlist. Код сценариев остаётся TEST adapter над настоящими Combat/Party owners; нового supervisor, Trainer, SkillTree или Combat нет.

**Стек:** Java25/Ant; существующие MariaDB TEST и V3 state128/action64/Adam; Windows PowerShell и штатный WinForms без нового проекта или зависимостей. C# пульта совместим с compiler/runtime Windows PowerShell, без C#14/.NET10 API из Studio.

**Задание:** исходный полный текст сохранён в [TASK.md](TASK.md). Аудит и точные read-first пути находятся в private task artifact AUDIT.md и компактном evidence archive. Это тот же Goal009, задача №3 из13 после006R1; после выдачи осталось10, новых /goal не создаётся.

## Подтверждённая исходная точка

|Проверка|Факт|
|---|---|
|TEST root|D:/Codex/repos/L2J-learning-006, самостоятельный физический checkout|
|Локальная ветка / HEAD|wip/phantom-learning-008 / 85b1c3558e35c1df0a7625fcf5a9e5c119c18b96|
|Рабочее дерево TEST|Tracked clean; единственный untracked root — сохранённый .phantom-local/|
|GitHub008|Тот же exact HEAD, независимо прочитан connector|
|GitHub009|Ветка wip/phantom-learning-009 не обнаружена|
|Основной checkout / GitHub feature|7e997636dbac9d5e1754e00c793cd6fac858b0c6; основной checkout dirty, read-only|
|GitHub master|16d61833b3983a3976583d0e4813e0de9457a52f, read-only|
|TEST runtime|director.json: STOPPED; прежние Director/Native/Worker PID не соответствуют живым процессам; TEST listeners33357/18001/19301:0|
|DRAFT12 selected|6f3dfa43ef8a8335c9ebee16498d7a6406de629c6a85bdd66755675fb55b9546|
|Adam12|85cea56889abeef7b82807ddeba950f588d9bc4e84340c6d454a84bafe72670e;887updates/207epochs|
|Adam12 terminal weights|7d3c8fc1f2ae5fc199cfb7517f71743189fdd3fbb4b959a318753637e2d3bc37|

Последние SHA/счётчики прочитаны из сохранённого evaluation JSON, не из вновь запущенной SQL. Полная проверка blobs, lineage, Geo203/protected207 и сохранности historical tables ещё REQUIRED.008 остаётся PARTIAL с отдельным manual gate;007 принята владельцем.

## Выбранный подход и границы

Выбор продиктован заданием: использовать существующий Director и native owners. Отдельный supervisor и замена V3 не соответствуют заданию. Расширение production Combat до16 кандидатов увеличило бы горячий scope; в009 предлагается сохранить bound4 и ротировать TRAIN shortlist между эпизодами. V3 сохраняет общий предел16; HEAL остаётся выбором законных Party directives/targets.

Для UI выбран небольшой C# WinForms-пульт, загружаемый PowerShell launcher. Это позволяет соблюсти обязательный InitializeComponent в Designer.cs и не создавать отдельную .NET10 инфраструктуру. GUI запускает штатный CLI асинхронно, не становится владельцем Director. Нативная независимость процесса остаётся обязанностью существующего broker.

**Существенное решение о learned state:** старый createTraining автоматически изучает навыки из class tree. Новый путь не вызывает этот режим для получения neural eligibility. Генератор использует фактические сохранённые TEST actors и их learned-state snapshots; для свежего эпизода допускается только точное воспроизведение заранее сохранённого законного состояния в новой owned incarnation. Snapshot хранит source actor/profile, SHA и parent provenance, полный learned/passive set, subclass и items; неполный snapshot отклоняется, без repair через getAllAvailableSkills/add-missing-skills. Исходные actor/history не изменяются. Нельзя достроить skills по классу, повысить rank/enchant или выдать ресурсы после CUT. Если законное воспроизведение не подтверждено, сцена отклоняется. Классы без такого actor state остаются в inventory и получают NO_ACQUIRED_ACTOR; отсутствие actor/adapter не выдаётся за отсутствие игровых навыков. Новые классы/уровни становятся доступны через данные, без Java class→skill branches.

Это один связанный продуктовый конвейер, с внутренними проверяемыми этапами, без нового номера или отдельного промежуточного manual acceptance. Единственная предварительная остановка — согласование этого крупного PLAN; финальный manual gate остаётся REQUIRED.

## Контракты реализации

Имена новых типов и методов ниже — **предлагаемые**, а не утверждение о наличии API. Все native API берутся из прочитанного кода. Новые контракты проверяются RED→GREEN до native запуска.

### 1. Полный read-only inventory

Новый `PhantomSkillInventory` рядом с combat capability, без зависимости от UI, SQL trainer, LLM или внешних providers:

- `catalog(PhantomProgressionCatalog)` возвращает все classes и теоретические learn facts существующего canonical progression catalog; сверяет полноту с PlayerClass.values(). Источники уже построены L2jProgressionBackend/PhantomProgressionSourceParser и SkillTreeData.getCompleteClassSkillTree(), включая родителей. Второй XML/SkillTree loader не создаётся.
- `acquired(Player, PhantomProgressionCatalog)` читает текущий getAllSkills()/getKnownSkill(), class index и реальные версии Skill; не изучает, не кастует и не меняет AutoUse. Exact identity — id + canonical getLevel(). Для enchant использовать EnchantSkillLearn metadata, route/index и подтверждённую базовую версию. Если SkillData вернул fallback другой версии — отказ, а не принятие fallback.
- Факты: active/passive, target type, cast/effect/affect ranges, затраты, cast/reuse, magic/physical, element, известные native effects/abnormal metadata и ограничения. Неизвестные provenance, duration, max-target bounds и stacking details имеют явный unknown/reason. Совпадение с class tree не доказывает фактический источник приобретения навыка.
- Классификация содержит все запрошенные категории, включая UNKNOWN. Наличие ID и классификация не дают TRAIN/native execution support. Discovery, selection, train, execution и temporary readiness — независимые признаки.
- `eligibility(Player, target, inventory, owner)` — отдельная моментальная проверка внутри допущенного native owner: exact learned version, смерть, MP/HP, reuse/disabled, weapon/item/charge/soul, target/range/LOS, safety и native conditions. Переиспользует native readiness и существующие Combat/Party проверки. Необъяснимый boolean отказ native check получает NATIVE_CONDITION, без выдуманной причины.
- Пассивные skills не являются cast actions; их влияния сохраняются в настоящих Player stats/effects. Inventory refresh выполняется при изменении skill/class/loadout/catalog fingerprint вне горячего тика. Ready/LOS/MP не кэшируются как свойства inventory.
- Не вызывать `Skill.getAffectLimit()` ради статического факта: он использует Rnd. Нет подтверждённых min/max — поле UNKNOWN. Не переинтерпретировать ограниченный skills128 snapshot V3 как полный inventory.

### 2. Shortlist и lawful scenario generator

Новый versioned `AutonomousTrainingV1` содержит immutable campaign/cohort manifests и чистые правила curriculum/shortlist; он не запускает процессы и не выполняет optimizer:

- `shortlist(fullEligible, cursor, maximum=4)` формирует до4действий воспроизводимым циклическим обходом полного eligible набора. Курсор хранится durable; сортировка задаёт порядок обхода, но не вечное исключение навыков с большими ID. Отдельно сохраняются полный inventory, eligible set, supported set, shortlist, selected и фактический paid effect. Shortlist заморожен для comparison cell/эпизода; во время решения повторного сокращения набора нет.
- Подключение через существующий CapabilitySource в отдельном TEST Runtime текущего эпизода либо автоматически созданные private capability facts из exact acquired state. CapabilitySource принимает только classId: общий classId-cache для разных acquired actors недопустим. Production resolve/Loadout/Backend и их bound4 не изменяются. Новые факты генерируются из данных; ручной class→skill allowlist запрещён.
- Comparator закрепляется как **STOCK_ON_FROZEN_SHORTLIST_V1**: A и B имеют одинаковые legal facts/shortlist, а штатный Combat owner сам определяет stock baseline на каждом pulse через текущий ready/round-robin. V3 получает весь ready-набор, поэтому этот фактический baseline обязательно остаётся в offered set и native receipt. Это не обещание совпадения с production policy на полном наборе навыков; такое сравнение потребовало бы иного production seam и в009 не заявляется. Если фактический baseline/offer не удаётся подтвердить, сравнение отклоняется с NO_STOCK_BASELINE.
- Shortlist deterministic, не random sampling. Propensity TRAIN exploration сохраняется условно на предложенный shortlist; никаких выводов о всей политике по условному IPS. Если понадобится random inclusion, сначала versioned inclusion probability contract и test, без молчаливой смены метода.
- `planNext(catalog, acquiredActors, coverage, limits, campaignCursor)` использует дефициты class/level/skill/enchant/family/SOLO/PARTY/HP/MP/NPC coverage. Не выбирает по быстроте побед. Native spawn/reward/Geo/LOS источники — существующие scenario/authority adapters.
- Manifest закрепляет campaign ID, seed, catalog/source JAR SHA, exact actor/skills/gear/target/owner, split и fingerprint. Резервирование до dispatch; failed/timeout/losing slots сохраняются, reserved incarnation не переигрывается.
- Все классы остаются в coverage. Различать NO_ADAPTER, NO_ACQUIRED_ACTOR, NO_LEGAL_TARGET, INSUFFICIENT_ALTERNATIVES и temporary blockers. Поддержка в009: подтверждённый single-target DAMAGE и useful Party HEAL через текущие owners. Остальные механики инвентаризируются, но не считаются обученными.

### 3. Версионированные targets и непрерывная Adam lineage

- Новый `ActionAdvantageV2` обобщает существующий matched TRAIN_A/paid TRAIN_B контракт без fixed class IDs и teacher pins. Reward формула и native terminal receipts сохраняются; class/episode balance и bounded replay закрепляются revision до held-out оценок. Исторические V1/008 contracts/codecs не переписываются.
- База — только TRAIN controls, leave-own-block-out, минимум3 независимых controls в подтверждённой comparison cell; недостающий baseline не заменяется выдуманным counterfactual. Если межсценовый forecast необходим, это отдельный явно версионированный вариант, требующий пересогласования архитектурного изменения.
- Два разных ключа обязательны: comparisonCell без object/profile/incarnation IDs, но с совпадающими подтверждёнными pre-CUT class/level/skills/gear/HP/MP/target/shortlist facts; immutable receipt context с новым actorProfile/contextKey и split binding. Существующие bindSplit/validateDataset guards не ослабляются ради matching cells. Policy-cell mean не выдаётся за точный counterfactual той же decision-state/RNG.
- Для cohort fit минимум16 admitted TRAIN action targets,8TUNING,8AUDIT, минимум2class/role profiles и paid alternative; class/episode weighting не увеличивает число независимых episodes. Недостаток данных — INSUFFICIENT_EVIDENCE, пороги не снижаются ради smoke. Matching held-out cells допускает отдельные incarnations при тех же проверяемых физических фактах; отсутствие широкой географической независимости явно отражается.
- Credited unit — максимум1initial paid decision на эпизод. Поздние действия сохраняются как raw evidence, без дополнительных targets. Held-out quality также использует1initial paid decision/episode; action n, episode n и template clusters показываются отдельно. Минимальный план достаточного fit:16TRAIN_B+16matched TRAIN_A+8TUNING+8AUDIT=48native episodes; два достаточных fit — минимум96 плюс independent native evaluation. Cohort admission до boots считает фактические cells/ресурсы и ожидаемую стоимость; достижение2fits не обещается заранее.
- Новые TRAIN/TUNING/AUDIT incarnations получают свои split bindings до collection. Разрешено повторно использовать только чистые pre-CUT canonical template facts, без historical outcomes/receipts/labels. Общие template descendants не считаются независимыми actors/templates: выводы ограничиваются новыми native episodes в тех же setup cells, с явной cluster provenance. Исторические held-out evidence и их profile/context bindings не переходят в TRAIN под новым ID.
- Paid поражения/timeout с валидным terminal receipt проходят те же admission rules, что победы. Zero-paid/pre-CUT failures сохраняются в denominator и diagnostics без выдуманного gradient target.
- TacticalStoreV3 остаётся единственным владельцем freeze/model/optimizer/applications. Новая revision использует существующие transaction и application keys, отдельные campaign/dataset/config SHA. Схема БД и форматы старых blobs сохраняются; новая snapshot revision валидируется строго.
- Первый optimizer parent — проверенный terminal Adam12, а не selected DRAFT12. Следующие parents — durable latestOptimization с проверяемой ancestry. Selected weights, terminal weights, Adam moments и counters сохраняются раздельно.
- SQL COMMIT атомарно закрепляет frozen run, model, terminal optimizer и application markers. Filesystem checkpoint публикуется atomic replace после COMMIT и восстанавливается по committed SQL, если публикация прервалась. Повторный FROZEN recover не получает второй gradient credit.
- Bounded replay означает очередь ещё не применённых receipts и immutable archival readback. Уже зарезервированные/committed A/B/HELD_OUT natural keys нельзя повторно reserve/refit/re-gradient через новый campaign/dataset SHA. Восстановление того же FROZEN run — recovery, а не второй replay credit.
- TRAIN/TUNING/AUDIT/EVAL имеют immutable split provenance. Held-out данные никогда не становятся TRAIN. AUDIT не подбирает objective или параметры; candidate и критерии фиксируются до независимой оценки. Плохой/epoch0 candidate сохраняется DRAFT/rejected, не повышается автоматически. Повторное использование просмотренного AUDIT как нового независимого evidence запрещено.

### 4. Один автономный Director

Постоянный opt-in `autonomousEnabled`, предлагаемый CLI вход `Training.ps1 -Action Start -Autonomous`, без новых версионных START009/START010 команд. Один existing installation owner, lock, broker, lifecycle и CLI Start/Pause/Stop/Resume/Status/Export. Первый переход со старого008 профиля разрешён только при доказанном STOPPED: backup config, новый campaign manifest и явный opt-in; старые dataset/receipts сохраняются. Смешивать автономный и historical007/008 enabled routes нельзя. Resume продолжает уже закреплённый профиль.

Переходы: PREFLIGHT → PLAN NEXT COHORT → COLLECTING → SAVE/FREEZE → FITTING → TUNING → AUDITING → CHECKPOINTING → NEXT COHORT. Существующие исторические007/008 phased routes сохраняются. IDLE/STARTING/PAUSING/PAUSED/STOPPING/STOPPED/FAILED/RESOURCE_LIMIT/INSUFFICIENT_EVIDENCE соответствуют проверяемым причинам и операциям.

- Durable journal: campaign ID, generation, next cohort/cursor, committed receipt/run IDs, checkpoint ancestry и pending operation. Atomic file writes — existing Write-TrainText/Json; SQL authoritative для fit commit. Journal не заменяет application ledger.
- Pause завершает текущую атомарную операцию; Stop сохраняет receipts и штатно завершает только доказанно owned workers/GS/SQL; Resume сверяет owner/PID creation-time/image/command/config/source/port bindings и committed state. Неизвестная identity/crash/pending commit — fail closed; recovery детерминированно завершает/помечает незавершённое без потери истории и повторов.
- Несколько episodes в одном GS batch. Не больше одного своего native процесса одновременно. Fit только после native drain и requireStopped, без hot-path SQL/optimizer.
- Live Status получает ownership/liveness и heartbeat со свежим timestamp, маркирует stale/unknown; добавляется машиночитаемый CLI вывод для GUI, а не чтение последнего status.json как текущей истины.
- Настройки: duration, CPU/memory, free disk, checkpoint interval, batch size, bounded replay, evaluation cadence, family/levels/class distribution, exploration. Classes=auto для нового режима; старые numeric configs работают как раньше. Никаких обязательных Skill ID.
- При недостатке новых данных цикл выбирает новый допустимый cohort, но не refit того же committed dataset. Stagnation counter durable; после3 последовательных sufficient fits без улучшения — PAUSED с рекомендацией/ограничением. Resource/safety limits сохраняются через Resume.
- Обычная пользовательская сессия допускает часы/сутки и остановку оператором. Никакого Windows autostart или изменения sleep/power settings.

### 5. GUI

TrainingGui.ps1 загружает TrainingGui.cs + TrainingGui.Designer.cs через штатный Add-Type и показывает окно. Parameterless constructor безопасен для Designer; InitializeComponent содержит только layout. TRAIN_GUI.cmd запускается двойным щелчком, пути вычисляются от launcher.

Кнопки: «Учиться», «Пауза», «Стоп», «Продолжить», «Состояние», «Экспорт». Отображаются elapsed, completed/failed episodes, class/skill coverage, candidate/best/accepted model отдельно, Adam updates, improvement, splits, CPU/RAM/disk, ошибки и последний checkpoint. Команды выполняются асинхронным CLI child process; таймер UI только опрашивает завершение/результат, без WaitForExit в UI-потоке. Два GUI используют один installation lock/Director. Закрытие GUI не шлёт Stop и не закрывает broker; повторное открытие присоединяется по текущей identity.

## Exact proposed scope и bounded exception

**Дополнение, явно утверждённое владельцем в ходе native диагностики:** «Разрешаю добавить39-й путь java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java в scope Goal009». Текущий exact scope:25 source/config/test +14delivery =39paths. Единственный дополнительный файл получает optional acquired CapabilitySource для TEST; прежний публичный constructor и обычное поведение сохраняются. Других расширений, новых Goal или сброса budget нет. Исходный backend сохранён побайтно вне checkout, SHA5EBAFD4C8CF89FFFED82F83DBB0F8E43D11E91FA62D2E73A5FE93DC63030A5DB. Ниже сохранён первоначальный38-path PLAN; к его таблице применяется только это согласованное дополнение.

Нативная проверка первой кампании выявила разный source у Combat и Party planner. TEST Runtime передаёт один immutable acquired source обоим существующим владельцам. Старый constructor делегирует новому с null; non-null source запрещён без TRAIN opt-in. Native skill/mode/condition/MP guards и отсутствие grants сохраняются. Совместимость ABI и production opt-in rejection проверяются в focused source-contract;007/008 и native TEST проверяются отдельно.

Исключение >10files предлагается для согласования: максимум24 source/config/test paths +14 delivery paths =38paths. Это один TRAIN pipeline; Studio, schema migrations и production Combat execution не входят. Изменения вне списка требуют объяснения и пересогласования, лимит не заполняется ради числа.

Все следующие пути относительно `L2J_Mobius_CT_2.6_HighFive/`:

|Путь|Действие|
|---|---|
|build.xml|Focused DB-free target и существующий compile-tests|
|java/org/l2jmobius/gameserver/phantoms/combat/PhantomSkillInventory.java|Новый read-only inventory|
|java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingV1.java|Новые manifests/curriculum/shortlist contracts|
|java/org/l2jmobius/gameserver/phantoms/learning/ActionAdvantageV2.java|Новая generic targets revision|
|java/org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.java|Freeze/commit/recover новой revision в существующем store|
|java/org/l2jmobius/gameserver/phantoms/learning/TacticalTrainerV3.java|Generic fit/quality/report routing без второго trainer|
|java/org/l2jmobius/gameserver/phantoms/learning/TrainingConfig.java|Валидация автономных настроек|
|java/org/l2jmobius/gameserver/phantoms/learning/TrainExplorationV1.java|Новый permit route с сохранением007/008 semantics/codecs|
|test/java/org/l2jmobius/tests/phantoms/PhantomTrainingScenarios.java|Generic lawful scenario route|
|test/java/org/l2jmobius/tests/phantoms/PhantomTrainingActors.java|Actual acquired-state ingress|
|test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java|Новый режим того же native entrypoint|
|test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ActorFixture.java|Отдельный snapshot reproduction route без auto-learning|
|test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java|TEST-only CapabilitySource/Runtime integration|
|test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java|Native campaign adapter, не supervisor или trainer|
|test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java|Focused contract runner|
|tools/phantom-runtime/training/Training.ps1|Autonomous loop, status и постоянный CLI|
|tools/phantom-runtime/training/TrainingOwner.ps1|Config/atomic-state/resource validation с прежним ownership|
|tools/phantom-runtime/training/Test-Training.ps1|Расширение существующих lifecycle tests|
|tools/phantom-runtime/training/AutonomousTraining.ini|Новый opt-in OFF профиль|
|tools/phantom-runtime/training/TrainingGui.ps1|Тонкий WinForms launcher|
|tools/phantom-runtime/training/TrainingGui.cs|Команды/асинхронные handlers/runtime|
|tools/phantom-runtime/training/TrainingGui.Designer.cs|InitializeComponent/layout|
|tools/phantom-runtime/training/TRAIN_GUI.cmd|Запуск двойным щелчком|
|tools/phantom-runtime/training/RUNBOOK_RU.md|Обновление постоянной инструкции|

Delivery exact14: task-folder `TASK.md`, `PLAN.md`, `PROGRESS.md`, `ACCEPTANCE.md`, `RESULT.md`, `HANDOFF.md`, `EVIDENCE.json`, `COMMANDS.md`, `RUNBOOK_RU.md`, `SKILL_COVERAGE.md`, `AUTONOMY_PROTOCOL.md`; `artifacts/learning009-evidence.zip`; `docs/phantoms/reports/PHANTOM-LEARNING-009-UNIVERSAL-AUTONOMOUS-TRAINER.md`; `docs/phantoms/COMBAT-AI-ROADMAP.md`.

Task-folder: `docs/phantoms/tasks/PHANTOM-LEARNING-009-UNIVERSAL-AUTONOMOUS-TRAINER/`. Exact allowlist хранится в PLAN/EVIDENCE без дополнительного непредусмотренного delivery file. Архив имеет правильный module/task layout, /goal и hash manifest, без SQL credentials/datadir, Geo и больших private logs.

Private runtime mutations ограничены уже существующей owned installation `.phantom-local/learning002/train` и её новыми campaign/checkpoint/GUI artifacts после полноценного preflight. Перед заменой config/JAR и иных уникальных текущих файлов — byte backup с SHA вне checkout в task artifact root. Старые datasets, exports, optimizer/application markers, manifests и checkpoints не перезаписываются. Нет нового clone/worktree, SQL instance, портов или общей MariaDB. Historical protected207/Geo203 читаются только для guards.

## Последовательность и проверки

- [ ] После approval повторить bounded status/HEAD/ref guard. Создать только wip/phantom-learning-009 от exact85b1c355… в существующем TEST. Записать task package/read-first pins и byte preservation manifest. Сначала проверить full own ownership/Geo203/protected207; до этого GS не запускать.
- [ ] Inventory RED→GREEN: R01–R08/R18 в existing JDK test pattern. Наследуемый Hydro Blast в fixture, два разных learned states одного класса, enchant/rank refresh, passive/unknown, отсутствие grants, точные reasons, >4rotation, новый класс вне historical списков. Подмена/удаление фиксированных fixture class/skill IDs не должна менять универсальный алгоритм.
- [ ] Generator RED→GREEN: canonical class discovery против всей PlayerClass, actual acquired actor manifests, no-auto-learning, valid native target/Geo/reward admission, stable split/campaign fingerprint, полный coverage census и сохранение rejected/timeout slots. До этого native starts=0.
- [ ] V2/Store RED→GREEN: R09–R12/R16/R17. Exactly-once, split leakage, terminal Adam12 cold recovery, fault до COMMIT и после COMMIT/до file checkpoint, повторный recovery, старые007/008 codecs, epoch0/stagnation. Только existing TEST tables/transactions.
- [ ] Director/GUI focused checks: R13–R15, existing Test-Training broker/job/atomic/bootstrap/isolation routes по затронутому scope. Два окна/один Director, закрытие/повторное открытие GUI, чужой PID/owner/port, corrupt checkpoint/disk/memory fail closed. Проверить реальные команды GUI, а не только text/layout.
- [ ] Canonical build: последовательно `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml phantom-autonomous-training-test phantom-learning008-test phantom-learning007-test jar`. Первый target — предлагаемый новый, ещё не существует. Targeted004–006/SQL contracts и F7 PhantomVisibleItinerary068Checks / F8 PhantomNativeEvidenceContinuation022Suite — по существующим runner/classpath, зафиксированным в COMMANDS. Не ant verify и не все DB/product smokes.
- [ ] Перед CUT freeze objective, matching/splits, exact source/JAR/catalog/model pins и acceptance budget. Native acceptance: максимум6GS starts и90native minutes cumulatively, включая startup/failures/drain. Предложенное распределение:2collection boots,2evaluation boots,2recovery reserve; несколько episodes в каждом boot, Pause/Resume по возможности в той же сессии. Недостаток data не даёт права увеличить бюджет или снизить пороги.
- [ ] N01–N09: разные data-selected classes/levels, actual ranks/enchant, >2legal alternatives, paid Combat DAMAGE/Party HEAL и terminal credit; минимум2последовательных автономных цикла, если данные позволяют, и минимум1настоящий autonomous fit/checkpoint. Stop/Resume с новым процессом, cold SQL/Adam readback и unchanged historical digests. Независимая native evaluation, отрицательные outcomes и unknown economy сохранены. Дальнейший continuous user mode проверяется коротким bounded smoke, без суток Codex monitoring.
- [ ] Измерить process CPU/RAM, free disk/рост, throughput/preparation-vs-fight, starts/native wall, policy p50/p95/p99/allocations/fallbacks; не выдавать logical benchmark actors за materialized. Сохранить прежние safety5ms/margin.025/cooldown2000ms/HEAL emergency.
- [ ] STOPPED/owned workers/SQL/GS0/listeners0; exact final38-path scope guard; две отдельные encoding проверки всех27mojibake markers и6escaped-Cyrillic regex; архив с SHA/CRC/readback. Roadmap009:№3из13, осталось10;008 PARTIAL gate не пересматривается.
- [ ] Один scoped source/evidence commit и ordinary non-force push только009, затем independent GitHub HEAD/parent/diff/source/evidence readback. При необходимости отдельный docs-only publication receipt. Feature/master/MAIN/PLAY и чужой dirt не затрагиваются. Финальный manual gate REQUIRED; FULL009 только если фактически подтверждены всеG0–G8.

## Что ещё не проверено

Никаких новых tests/build/GS/SQL/GUI runs пока не выполнено. Полнота native catalog, число фактических классов/learned skills, доступность dataset cells для2fit cycles и identity всех старых blobs требуют реализации/проверки. Готовность NIGHT mode, корректность snapshot reproduction, WinForms launch/detached broker на этом host и coverage за пределами проверенных actors не объявляются доказанными. Возможная нехватка native evidence внутри6/90 остаётся основанием для честного PARTIAL, без снижения G-критериев.

Сохранённая V3 feature schema ограничена: skill level в action vector нормализуется /100 с clipping, поэтому подробная enchant identity в inventory/receipts сама по себе не доказывает различимость всех enchant routes нейросетью. Изменение schema/topology не входит в009; реальные версии и характеристики обязаны быть корректны в legality/execution и coverage, а ограничение представления — в отчёте.

**Предварительное решение владельца получено:** «Подтверждаю PLAN — начать реализацию». Exact38-path bounded exception утверждён. Рутинные проверки, исправления, owned native start/stop и выполнение этапов продолжаются без повторного согласования. Это не принятие008 и не финальный manual gate009.

Builder review выполнен отдельным read-only reviewer: уточнены frozen-shortlist comparator/actor isolation, разделение comparison и split keys, source snapshot provenance, однократное применение replay и48episode/fit accounting. Неустранимых вопросов владельцу по review не осталось; это проверка проекта, не доказательство native/SQL/GUI.
