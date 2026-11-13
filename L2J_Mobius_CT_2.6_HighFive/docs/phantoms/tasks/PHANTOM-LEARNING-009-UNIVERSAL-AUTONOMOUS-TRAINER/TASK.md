/goal PHANTOM-LEARNING-009-UNIVERSAL-AUTONOMOUS-TRAINER

МЫШЛЕНИЕ: Extra High.
НОВЫЙ ДИАЛОГ CODEX.
ЯЗЫК: русский.

# PHANTOM-LEARNING-009 — УНИВЕРСАЛЬНЫЙ АВТОНОМНЫЙ ТРЕНЕР БОЕВОГО ИИ

## 0. ГЛАВНОЕ ТРЕБОВАНИЕ ВЛАДЕЛЬЦА

Прекратить разрабатывать систему обучения по принципу «одна задача — ещё несколько классов и навыков».

Мне нужен универсальный TRAIN-конвейер, который самостоятельно:

1. Обнаруживает все классы и их игровые возможности.
2. Получает фактически изученные навыки каждого персонажа, включая навыки предыдущих профессий, текущие уровни и заточку.
3. Классифицирует активные действия, пассивные эффекты и пока неподдерживаемые механики.
4. Создаёт законные тренировочные ситуации без ручного прописывания Skill ID и класса для каждого эксперимента.
5. Сам запускает GameServer, проводит TRAIN, собирает опыт, обучает общую V3, выполняет независимые проверки и сохраняет результат.
6. Позволяет мне нажать «Учиться», оставить компьютер работать хоть на сутки, нажать «Стоп», а затем в другой день «Продолжить».
7. Работает без постоянно запущенного Codex. Codex должен создать и проверить систему, а не быть её оператором.
8. Сохраняет старые веса, optimizer, checkpoints, TEST SQL, опыт и отрицательные результаты.
9. Не повышает плохую модель автоматически.

**Запрещено объявлять универсальность на основании поддержки нескольких заранее перечисленных профессий.**

Универсальность обнаружения игровых возможностей и универсальность их тактического исполнения — разные свойства. Реестр обязан охватывать все обнаруженные навыки, но не должен ложно утверждать, что умеет исполнять все типы механик.

В этой задаче создать универсальный тренер для уже поддерживаемых безопасных action families, с архитектурой расширения на остальные механики. Полная тактика PvP, AoE, summons, raids и Olympiad — будущие этапы, не имитировать их готовность.

## 1. REPOSITORY / BASE

GitHub:
https://github.com/kpCat/L2J

Основной checkout владельца:
D:\Codex\repos\L2J-phantom-world

Модуль:
L2J_Mobius_CT_2.6_HighFive

Сохранённый независимый TEST:
D:\Codex\repos\L2J-learning-006

Исходная ветка:
wip/phantom-learning-008

Точный проверенный исходный HEAD:
85b1c3558e35c1df0a7625fcf5a9e5c119c18b96

Новая ветка:
wip/phantom-learning-009

Последний проверенный feature HEAD:
7e997636dbac9d5e1754e00c793cd6fac858b0c6

Проверить все refs заново. Новую ветку создавать от exact008 source в существующем TEST-клоне только после проверки рабочего дерева.

008 остаётся PARTIAL / NO_TUNING_CHECKPOINT_IMPROVEMENT с отдельным manual gate. Новый Goal009 не означает задним числом FULL008 или автоматическое принятие её результата.

Нельзя менять:
- feature/phantom-world;
- master;
- MAIN/PLAY;
- release057–061;
- чужие SQL;
- dirty109;
- другие worktrees;
- существующие исторические checkpoints;
- чужие процессы и порты.

Публикация только обычным non-force push в wip/phantom-learning-009. Это моё явное разрешение на scoped commit/push этой задачи, несмотря на общий консервативный Git workflow. Автоматического merge не делать.

Новый многогигабайтный clone/worktree не создавать без доказанной необходимости.

Прочитать актуальные:
- AGENTS.md;
- Agents.md модуля;
- PHANTOM_DEVELOPMENT_MASTER_PLAN.md;
- docs/phantoms/PHANTOM_CURRENT_STATUS.md;
- docs/phantoms/CODEX_WORKFLOW_CONTRACT.md;
- docs/phantoms/TASK_PACKAGE_STANDARD.md;
- документацию и evidence 006R1, 007, 008.

В старом Agents.md имеются исторически устаревшие абсолютные пути. Фактический Git root, текущая задача, подтверждённый TEST ownership и защищённые системы имеют приоритет. Не менять старые инструкции молча.

## 2. ПРОВЕРЕННОЕ СОСТОЯНИЕ

007:
- принята владельцем;
- TRAIN exploration и action advantage работают;
- модель самостоятельно меняет реальные действия;
- DRAFT11, Adam855/191.

008:
- PARTIAL;
- модель DRAFT12 сохранена;
- Adam855 → 887 updates, epochs191 → 207;
- 42 completed collection episodes из48;
- EVAL94/103/110/HEAL105;
- обучение008 не улучшило выбранный TUNING checkpoint;
- selected epoch0;
- новая DRAFT12 фактически использует стартовые terminal weights Adam11;
- game verdict INCONCLUSIVE.

DRAFT12 selected SHA:
6f3dfa43ef8a8335c9ebee16498d7a6406de629c6a85bdd66755675fb55b9546

Adam12 SHA:
85cea56889abeef7b82807ddeba950f588d9bc4e84340c6d454a84bafe72670e

Terminal optimizer weights Adam12 SHA:
7d3c8fc1f2ae5fc199cfb7517f71743189fdd3fbb4b959a318753637e2d3bc37

**Не путать selected checkpoint и terminal optimizer state.**

Accepted V1 №1, V3 №2, DRAFT9/11/12, старые dataset/replay/optimizer/application markers сохранить побайтно.

Нельзя выдать 008 gradient steps за улучшение: выбранная политика не улучшилась.

Главная следующая цель — убрать ручной orchestration и сделать накопление опыта автономным.

## 3. ОБЯЗАТЕЛЬНЫЙ АРХИТЕКТУРНЫЙ АУДИТ

Перед реализацией изучить:

### Canonical game data
- PlayerClass;
- SkillTreeData;
- SkillData;
- Skill / effects;
- Player.getAllSkills();
- Player.getKnownSkill();
- фактические skill levels и enchant/sublevels;
- SkillTreeData.getCompleteClassSkillTree();
- наследование профессий;
- skill enchant metadata;
- приобретение/изучение навыков;
- предметы, экипировку, weapon restrictions;
- AutoPlay и native readiness.

### Phantom World
- PhantomNativeCombatCapability;
- PhantomCombatCapabilityResolver;
- PhantomCombatLoadout;
- PhantomCombatService;
- PhantomPartyTactics;
- PhantomProgressionSourceParser;
- PhantomProgressionCatalog;
- L2jProgressionBackend.

### Learning
- TacticalModelV3;
- TacticalObservationV3;
- TacticalSnapshotV3;
- TacticalPolicyV3;
- TacticalStoreV3;
- TacticalTrainerV3;
- TrainExplorationV1;
- ActionAdvantageV1;
- ActionAdvantage008;
- TerminalRewardReceiptV1;
- TrainingStore;
- TrainingConfig;
- Training.ps1 / TrainingOwner.ps1;
- existing TEST scenario/actor/evaluation adapters.

Не создавать второй Combat, Party, Player, SkillTree или Trainer.

Предпочитать расширение существующих владельцев и источников истины.

## 4. УНИВЕРСАЛЬНЫЙ РЕЕСТР НАВЫКОВ

Это центральная часть задачи.

Создать единый read-only механизм обнаружения игровых возможностей, пригодный как для TRAIN, так и для будущей обычной V3 inference.

Он обязан различать:

A. Навыки, которые класс теоретически может выучить.

B. Навыки, которые конкретный персонаж действительно изучил.

C. Фактический уровень каждого изученного навыка.

D. Enchant route/level или другую каноническую идентичность заточенного навыка.

E. Навыки, которые персонаж может законно использовать в текущем состоянии.

F. Навыки, которые понимает и умеет оценивать текущая нейронная action family.

Это не одна и та же коллекция.

**Критически важно:**

Получение третьей профессии не удаляет навыки предыдущих профессий.

Например, Mystic Muse уровня77+ может продолжать пользоваться Hydro Blast1235, Frost Bolt1236 и другими законно изученными навыками из прежних классов.

Hydro Blast должен определяться по фактически изученной версии персонажа, а не как жёсткая константа1235:28.

Если навык заточен, учитывается именно текущая игровая версия и её характеристики.

Нельзя считать, что у каждого персонажа все доступные ему по классу навыки уже изучены.

Не выдавать и не изучать навыки автоматически ради прохождения neural eligibility.

### Обязательные свойства реестра

Для каждого активного навыка, насколько это достоверно известно GameServer:

- skillId;
- фактический уровень;
- enchant route/level/sublevel;
- класс/подкласс и источник;
- тип действия;
- тип цели;
- дальность;
- single-target / area;
- радиус/максимум целей;
- MP/HP/item/charge/soul consumption;
- cast time;
- reuse/cooldown;
- magic/physical;
- элемент;
- damage/heal/effect семантика;
- duration;
- stacking/overwrite;
- условия оружия;
- доступность в текущем состоянии;
- реальные препятствия к применению.

Не выдумывать свойства, если API их не предоставляет.

Не интерпретировать неизвестную метрику как ноль.

Пассивные навыки учитывать в характеристиках и эффектах персонажа, но не предлагать как активный cast.

При временной недоступности навыка сохранять причину:
COOLDOWN, MP, HP, RANGE, LOS, WRONG_TARGET, WEAPON, ITEM, DISABLED, DEAD, OWNER и т.д.

### Категории

Минимальная классификация:

- SINGLE_TARGET_DAMAGE;
- HEAL;
- SELF_BUFF;
- PARTY_BUFF;
- DEBUFF / CONTROL;
- AOE_DAMAGE;
- SUMMON;
- RESURRECTION;
- MOBILITY / ESCAPE;
- OTHER_ACTIVE;
- PASSIVE;
- UNKNOWN.

Если категории невозможно достоверно определить по native mechanics, оставлять UNKNOWN.

Для каждой категории отдельно отмечать:

DISCOVERED,
SUPPORTED_FOR_SELECTION,
SUPPORTED_FOR_TRAIN,
SUPPORTED_FOR_NATIVE_EXECUTION,
NOT_SUPPORTED,
TEMPORARILY_UNAVAILABLE.

Не объявлять control/AoE/summon реализованными только потому, что SkillData сообщил их ID.

### Запрет жёстких списков

В универсальном механизме не должно быть логики вида:

class94 -> [1230,1339]
class103 -> [1235,1340,1342]
class110 -> [1239,1341,1343]

Такие списки допустимо оставить только внутри исторических неизменяемых tests/fixtures007/008.

Новый путь обязан получать способности автоматически.

Не создавать новую ручную таблицу «класс → разрешённые ID» вместо старой.

## 5. ЛИМИТЫ КАНДИДАТОВ

Сейчас в обычном PhantomCombatCapabilityResolver.resolve() максимальный selected skill bound —4.

TacticalPolicyV3 принимает до16 вариантов одного решения.

Полный список изученных навыков персонажа может быть существенно больше.

Не скрывать это противоречие.

Разделить:

1. Full acquired skill inventory.
2. Native eligibility.
3. Supported action family.
4. Bounded candidate shortlist для V3.
5. Фактически выбранное и исполненное действие.

Нельзя просто брать первые четыре skillId.

Нельзя навсегда исключать остальные законные навыки из обучения.

Нужна bounded, детерминированная или воспроизводимая политика формирования shortlist из полного допустимого набора.

В TRAIN допускается покрытие разных кандидатов между эпизодами; если применён sampling, сохранять inclusion probability и ограничения статистической интерпретации.

Stock baseline должен присутствовать в допустимом shortlist, когда сравнение с ним является целью.

Не создавать fake legal alternatives.

Сохранить текущие runtime latency и memory safety. Если потребуется изменение production Combat resolver, объяснить минимальный контракт и не активировать новую модель на основном сервере в009.

## 6. УНИВЕРСАЛЬНАЯ ГЕНЕРАЦИЯ ТРЕНИРОВОЧНЫХ СЦЕН

Убрать зависимость нового TRAIN от заранее написанного набора профессий и их skill IDs.

Создать один versioned scenario generator.

На входе:
- canonical class catalog;
- доступные уровни и роли;
- реальные изученные skills;
- текущая экипировка;
- NPC catalog/spawns;
- reward eligibility;
- GeoData/LOS;
- Party roles;
- supported action families;
- ограничения CPU/RAM/GameServer;
- критерии покрытия опыта.

На выходе:
- проверенный сценарий;
- class/level/skills fingerprint;
- actual actor/target setup;
- owner;
- mode SOLO/PARTY;
- цель;
- legal actions;
- split;
- seed/catalog SHA;
- причины допуска или отказа;
- immutable campaign ID.

Генератор должен сам выбирать разнообразные корректные классы/уровни/навыки/NPC, а не требовать редактировать Java при появлении новой профессии.

Нельзя заставлять бой начинаться при невалидной Geo, отсутствии способности к атаке или неверных native conditions.

Всё до CUT проверяется; после CUT действуют только обычные механики GameServer.

### Coverage

Реестр должен инвентаризировать все доступные классы из PlayerClass и их навыки, без заранее заданного списка трёх классов.

Не требуется за одну задачу натренировать все классы до высокого качества.

Обязателен автоматический отчёт:
- сколько классов обнаружено;
- сколько имеют поддерживаемые действия;
- сколько могут быть использованы в TRAIN;
- сколько недоступны из-за отсутствующего action adapter;
- сколько имеют достаточный набор альтернатив;
- сколько реально охвачено обучением;
- сколько не встречалось в TRAIN/AUDIT;
- какие типы навыков остаются неподдерживаемыми.

В качестве проверки универсальности использовать минимум несколько разных законных классов и уровней, включая уже проверенные магические и HEAL-профили, но не внедрять их ID в универсальный алгоритм.

Классы без подходящих действий показывать как неподдерживаемые в данном TRAIN action family, а не автоматически исключать из отчёта.

### Curriculum

Создать автоматический выбор следующей тренировочной ситуации по дефицитам покрытия.

Не бесконечно повторять одного Archmage против NPC21798.

Нужна ротация:
- классов;
- уровней;
- навыков;
- ситуаций;
- диапазонов HP/MP;
- NPC;
- SOLO/PARTY;
- TRAIN/TUNING/AUDIT.

Не выдавать больше веса классу только потому, что у него проще и быстрее создавать эпизоды.

Возможна class/episode-balanced sampling strategy с bounded replay.

Сохранить все реально плохие исходы, поражения, отказы и timeout; не формировать датасет исключительно из побед.

## 7. АВТОНОМНЫЙ TRAINING DIRECTOR

Использовать существующий Training.ps1 и единственного Training Director.

Создать долгоживущий автономный режим, который проходит цикл:

PREFLIGHT
→ PLAN NEXT COHORT
→ NATIVE COLLECTION
→ SAVE RECEIPTS
→ FROZEN TRAIN DATASET
→ OFFLINE FIT
→ TUNING
→ INDEPENDENT AUDIT
→ SAVE DRAFT
→ CHECKPOINT
→ NEXT COHORT.

Не создавать второй независимый supervisor.

После завершения одного батча Director должен самостоятельно перейти к следующему допустимому батчу, пока пользователь не остановит обучение или не сработает ресурсный/защитный лимит.

Не запускать GameServer заново для каждого отдельного эпизода, если можно безопасно выполнять несколько эпизодов в одной собственной native-сессии.

Использовать bounded batch sizes, сохранение опыта и periodic checkpoints.

### Состояния

IDLE
PREFLIGHT
STARTING
COLLECTING
FITTING
TUNING
AUDITING
CHECKPOINTING
PAUSING
PAUSED
STOPPING
STOPPED
FAILED
RESOURCE_LIMIT
INSUFFICIENT_EVIDENCE.

Состояния должны быть реальными и проверяемыми, не декоративными строками.

### Управление

START / «Учиться»:
запускает самостоятельную тренировку из разрешённого checkpoint.

PAUSE / «Пауза»:
безопасно завершает текущую атомарную операцию и переводит цикл в PAUSED.

STOP / «Стоп»:
останавливает owned workers/GameServer/SQL согласно действующему ownership contract, после завершения транзакций и сохранения доказательств. Не убивать чужие процессы.

RESUME / «Продолжить»:
восстанавливает прежний тренинг, не повторяя committed receipts и не обнуляя optimizer.

STATUS:
показывает фактическое состояние, а не последний устаревший status.json.

EXPORT:
создаёт проверяемый checkpoint/export.

Не создавать дублирующие команды для каждой новой модели009,010 и т.д. Это должен быть постоянный интерфейс управления.

### Работа без Codex

Особое требование:

Оператор закрывает окно Codex — TRAIN продолжает работать как самостоятельный локальный процесс.

Оператор закрывает GUI — TRAIN продолжает работать, если явно не нажата остановка.

Оператор запускает GUI позже — видит существующую тренировочную сессию и может её остановить.

После штатного Stop и нового Resume сохраняется вся история.

После аварийного завершения PC/worker система должна fail closed и иметь проверяемую процедуру восстановления, а не потерять SQL/weights или начать заново.

Без отдельного согласия не создавать автозапуск при старте Windows.

Не отключать сон Windows или менять системные power settings автоматически.

## 8. ПРОСТОЙ ЛОКАЛЬНЫЙ GUI

Мне нужен реальный пользовательский интерфейс с кнопками:

«Учиться»
«Пауза»
«Стоп»
«Продолжить»

Дополнительно:
- «Состояние»;
- «Экспорт»;
- текущий статус;
- время с начала;
- количество completed/failed episodes;
- covered classes / skills;
- номер модели;
- optimizer updates;
- наличие улучшений;
- TRAIN/TUNING/AUDIT;
- CPU/RAM/disk;
- ошибки;
- последний checkpoint.

Предпочитать минимальный нативный Windows UI без новых тяжёлых зависимостей. Можно использовать штатные PowerShell/WinForms, если это действительно устойчиво и позволяет проверять действия. Не добавлять отдельную большую WPF/C# инфраструктуру без необходимости.

GUI не должен содержать собственный тренер. Он только управляет существующим Director.

Кнопки должны реально выполнять операции, а не просто показывать состояние.

После перезапуска GUI автоматически присоединяется к существующему owned training state.

Не блокировать UI-поток ожиданием GameServer или trainer fit.

Все сообщения пользовательского интерфейса — на русском.

Подготовить понятный запуск двойным щелчком, без необходимости вручную составлять длинные PowerShell-команды.

При этом CLI должен оставаться полностью работоспособным без GUI.

## 9. СОХРАНЕНИЕ ОБУЧЕНИЯ

Использовать существующую V3 и её Adam lineage.

Не менять историческую семантику 004–008.

Сохранять и восстанавливать:
- выбранный checkpoint;
- terminal optimizer weights;
- Adam moments;
- optimizer updates/epochs;
- exact data splits;
- applied action markers;
- replay;
- dataset SHA;
- reward/advantage revision;
- campaign identity;
- catalog SHA;
- source JAR SHA;
- model schema;
- owner identities;
- checkpoints и историю оценок.

Не склеивать Adam moments с selected weights другой эпохи.

Не применять одни и те же terminal receipts дважды.

Не превращать старые TUNING/AUDIT/EVAL в TRAIN.

Исторические datasets оставлять immutable.

Если trainer objective или feature schema изменились, миграция должна быть явно версионирована и безопасна.

### Отрицательный результат

008 завершилась с selected epoch0.

Новый Director обязан воспринимать такой результат нормально:

- терминальное optimizer-состояние сохраняется;
- выбранная модель не объявляется улучшенной;
- плохой кандидат не повышается;
- статистика регрессии сохраняется;
- можно продолжать тренировки с другими новыми ситуациями;
- при повторяющейся стагнации включается ограничение или рекомендация, а не бесконечное повторение одного и того же опыта.

Не гарантировать, что любая следующая эпоха будет лучше.

## 10. AUTO-EVALUATION И ПРОДВИЖЕНИЕ МОДЕЛЕЙ

После каждого достаточного нового обучающего набора:

1. Проверить TRAIN.
2. Выполнить TUNING.
3. Выполнить независимый AUDIT.
4. Проверить отсутствие ухудшения критических игровых endpoints.
5. Сравнить с предыдущей лучшей моделью.
6. Сохранить DRAFT, метрики и ограничения.

Нельзя использовать EVAL для gradient или подбора гиперпараметров после просмотра результата.

Нельзя подгонять reward по AUDIT.

Разделить:
- обучаемая рабочая модель;
- лучший проверенный TEST checkpoint;
- accepted production model;
- DRAFT;
- rejected/regressed candidate.

**Автоматическая установка новых весов в MAIN/PLAY в009 запрещена.**

В будущем отдельная задача010 подключит проверенные веса к обычному GameServer.

Факт низкого MSE не означает лучшего поведения.

Основные метрики:
- реальные outcomes;
- продолжительность;
- выживание;
- exact EXP/SP;
- известные расходы;
- meaningful paid action divergence;
- score margin;
- confidence limitations;
- rollback/fallback;
- объём и независимость evidence.

UNKNOWN adena, loot, deaths, consumables, whole-party expenses не подменять нулями.

## 11. РЕСУРСЫ И НОЧНАЯ РАБОТА

Конфигурируемые пользователем:
- максимальное число часов;
- CPU budget;
- memory budget;
- minimum free disk;
- checkpoint interval;
- batch size;
- replay retention;
- частота evaluation;
- допустимые action families;
- диапазоны уровней;
- class distribution;
- включение/отключение TRAIN exploration.

Не заставлять пользователя перечислять Skill ID.

Типичный пользовательский сценарий: запуск на несколько часов или на сутки.

Долгую тренировку выполнить способен сам Director, без Codex.

### Доказательство автономности в рамках текущей задачи

Не держать Codex запущенным сутки ради acceptance.

Достаточно ограниченного настоящего native smoke и проверки:
- минимум двух последовательных автономных training cycles, если данные позволяют;
- продолжения циклов без команды Codex между ними;
- отдельного Pause/Resume;
- Stop;
- cold restoration;
- повторного открытия GUI;
- восстановления process ownership;
- сохранения checkpoints и статистики.

Если в smoke недостаточно данных для реального optimizer update, это должно быть явно доказано; полноценный FULL009 нельзя объявлять только за запущенный цикл.

Нужен хотя бы один настоящий autonomous fit/checkpoint, выполненный новым циклическим Director без ручного участия Codex в каждом шаге.

Для разработки ограничить дорогостоящие native проверки, ориентировочно максимум6 GameServer starts /90 native minutes.

Это граница Codex acceptance, а не ограничение пользовательских ночных TRAIN-сессий.

Не увеличивать бюджет молча.

## 12. ПРОИЗВОДИТЕЛЬНОСТЬ

Никакого отдельного потока на фантома.

Никаких SQL, optimizer, UI-синхронизации или долгого XML parsing в горячем пути боевых действий.

Full skill discovery можно выполнять при изменении skills/class/loadout и кэшировать, с инвалидированием при:
- изучении навыка;
- enchant;
- смене класса/подкласса;
- изменении skill catalog;
- изменении доступных предметов, когда это влияет на eligibility.

Мгновенная ready/MP/cooldown/LOS проверка проводится на актуальном состоянии перед выбором/кастом.

Не кэшировать «готовность к касту» как постоянное свойство навыка.

Измерять CPU/RAM, policy p50/p95/p99, allocations, скорость сбора эпизодов, disk usage, число GameServer boots и время подготовки относительно настоящих боёв.

Не путать benchmark128 logical actors с 128 действительно материализованными фантомами.

## 13. ОСОБЫЕ НЕГАТИВНЫЕ ТЕСТЫ

Обязательные focused RED→GREEN:

R01. Класс третьей профессии наследует изученные навыки предыдущих классов; Hydro Blast1235 сохраняется при соответствующем learned state.

R02. Два персонажа одного класса имеют разные реальные выученные навыки. Недоступный/неизученный навык не появляется в legal action set.

R03. Изменение уровня или заточки навыка обновляет его характеристики и идентичность; старая версия не используется после refresh.

R04. Пассивный навык не предлагается для каста, но его реальные эффекты не игнорируются в canonical actor state.

R05. Cooldown/MP/range/LOS/item/owner/safety исключают временно незаконные действия с диагностикой причины.

R06. При количестве legal actions больше4 нет молчаливого отсечения только по skillId; полный список и shortlist различимы.

R07. Неизвестная механика не превращается в DAMAGE/HEAL без подтверждения canonical semantics.

R08. Новый класс, отсутствующий в исторических списках94/103/110, обнаруживается без изменения Java source.

R09. TRAIN/TUNING/AUDIT/EVAL изолированы; один native action не получает два training credits.

R10. Cold optimizer lineage восстанавливает реальные Adam moments, а не только selected weights.

R11. Сбой между model/optimizer/applications не оставляет половинчатый commit.

R12. После Stop/Resume отсутствуют повторные finished episodes и duplicate applications.

R13. Два одновременно открытых GUI не могут запустить двух независимых Directors для одного installation owner.

R14. Закрытие GUI не завершает training; новое окно получает точный status действующего owned процесса.

R15. Исчерпание диска/памяти, повреждённый checkpoint, чужой PID, неправильный owner, занятый порт — fail closed.

R16. При стагнации модели не выполняется auto-promotion и не возникает бесконечного цикла повторения того же dataset.

R17. Старые 007/008 checkpoints и codecs читаются без изменения истории.

R18. Автоматический генератор не зависит от заданного в коде списка из трёх классов или трёх Skill ID.

Негативные тесты важнее красивого числа PASS.

## 14. РЕАЛЬНЫЕ NATIVE ПРОВЕРКИ

N01. Два и более различных класса используются в настоящем автономном TRAIN без class-specific Java branch в новом генераторе.

N02. Используются фактически изученные навыки с correct current levels.

N03. Хотя бы один персонаж имеет больше двух legal skill alternatives, и фактический shortlist/selection работает.

N04. Реальный Combat cast, HP/MP effect, terminal outcome и trainer credit.

N05. Реальный Party useful paid HEAL через существующего владельца.

N06. Два последовательных автономных цикла, сохранение checkpoint и продолжение без Codex между циклами.

N07. STOP/RESUME с новым процессом и сохранной SQL/optimizer ancestry.

N08. GUI реально выполняет команды Director.

N09. Независимая оценка сохраняет отрицательные результаты, не повышая плохую модель.

Не использовать synthetic damage/RNG для подмены этих native проверок.

Для механик, пока не поддерживаемых action adapters, обязательна точная coverage-отчётность вместо фиктивной native демонстрации.

## 15. КРИТЕРИИ G0–G8

G0 — exact исходный HEAD, source scope, отдельный TEST, Geo203/protected207, сохранность MAIN/PLAY/старых данных.

G1 — универсальный обнаружитель классов и skills без hardcoded class→skill allowlist. Наследование, текущие уровни, заточка и пассивные навыки корректны. Полный каталог и причины неподдерживаемых механик доступны.

G2 — автоматический lawful scenario generator, который запускает разные классы и навыки без изменения Java под каждую профессию. Есть настоящие native examples.

G3 — автономный Director выполняет последовательные collection/fit/evaluation/checkpoint cycles, без команд Codex между циклами; actual optimizer update и durable checkpoint доказаны.

G4 — реальные START/PAUSE/STOP/RESUME, cold recovery, exactly-once receipts, независимые splits и сохранение исторической lineage.

G5 — работающий локальный UI с кнопками, статусы и метрики; управление живым Director проверено, а GUI не является его владельцем и не должен держать обучение живым.

G6 — независимый TEST evaluation, честный verdict по данным, регрессии и отсутствие auto-promotion. Нет утечки TRAIN в EVAL.

G7 — focused regressions, canonical compile-tests/JAR, SQL contracts, F7/F8 по затронутому scope, CPU/RAM/latency/disk, bounded native smoke, STOPPED/ports free.

G8 — русский отчёт, точный список поддерживаемых action families и недостающих adapters, scoped GitHub push, independent HEAD/diff/source/evidence readback.

FULL009_AUTONOMOUS_TRAINER разрешён только при фактическом выполнении всех G0–G8.

FULL009 означает готовность автономной учебной платформы для поддерживаемых механик, **не** готовность полного PvP/raid/siege AI и не статистическое превосходство нейросети.

Нельзя ставить FULL только потому, что код компилируется или GUI имеет четыре кнопки.

## 16. ПОРЯДОК РАЗРАБОТКИ

1. Проверка GitHub/AGENTS/реального source.
2. Аудит действующих навыков, class trees, enchant и Combat source.
3. Краткий PLAN с точными контрактами и выбранным минимальным интерфейсом.
4. Дешёвые RED→GREEN tests на universal discovery.
5. Реализация универсального skill catalog.
6. Законная bounded selection и coverage matrix.
7. Scenario generator и class-aware curriculum.
8. Интеграция с существующим Director.
9. Автономный циклический режим и persistent state.
10. STOP/RESUME/recovery.
11. GUI и проверка его операций.
12. Native smoke с разными классами.
13. Реальные autonomous fit/checkpoint и independent evaluation.
14. Проверка долгоживущего режима без многочасового Codex monitoring.
15. Регрессии/performance.
16. Clean own scratch и postflight.
17. GitHub push/readback и русский отчёт.

При значимом архитектурном конфликте сначала предложи владельцу варианты и последствия. Если AGENTS требует утверждения крупного PLAN — запроси одно краткое подтверждение.

Рутинные диагностику, исправления, native start/stop, tests и verification выполняй самостоятельно.

Не создавать несколько десятков промежуточных worktrees, клонов или микрозадач.

## 17. ДОКУМЕНТАЦИЯ

Создай:

docs/phantoms/tasks/PHANTOM-LEARNING-009-UNIVERSAL-AUTONOMOUS-TRAINER/

Файлы:
TASK.md
PLAN.md
PROGRESS.md
ACCEPTANCE.md
RESULT.md
HANDOFF.md
EVIDENCE.json
COMMANDS.md
RUNBOOK_RU.md
SKILL_COVERAGE.md
AUTONOMY_PROTOCOL.md

Развёрнутый отчёт:

docs/phantoms/reports/PHANTOM-LEARNING-009-UNIVERSAL-AUTONOMOUS-TRAINER.md

В RUNBOOK_RU.md должна быть понятная инструкция:

«Как запустить обучающий интерфейс, начать обучение, остановить его и продолжить завтра».

Без обязанности запускать Codex.

Обязательные таблицы в отчёте:

- discovered classes;
- total learned skills;
- active/passive/unsupported;
- supported mechanics;
- per-class training readiness;
- skill/enchant coverage;
- реальные TRAIN/TUNING/AUDIT;
- model/optimizer lineage;
- автономные циклы;
- CPU/RAM/disk;
- GUI operations;
- GameServer starts;
- сохранённые checkpoints;
- статус G0–G8.

Нельзя выдавать отсутствие adapter за отсутствие соответствующего навыка в Lineage 2.

## 18. УЧЁТ НАШЕГО БЮДЖЕТА

Обнови:
docs/phantoms/COMBAT-AI-ROADMAP.md

История:
LEARNING001–006R1 =7 заданий.

007 = задача№1 из13, принята.

008 = задача№2 из13, PARTIAL, manual gate отдельно.

009 = задача№3 из13.

После выдачи009 осталось10 задач до обязательного пересмотра прогноза.

Каждый новый /goal, включая R1/R2, расходует бюджет. Не добавлять новые номера и этапы молча.

Не придумывать проценты завершённости.

## 19. ВАЖНОЕ РАЗДЕЛЕНИЕ 009 И 010

009 = автономная система обучения и универсальное обнаружение игровых возможностей.

010 = отдельная интеграция проверенного V3 inference в обычный Phantom World, с общей моделью для ботов, безопасным включением, правильным Combat/Party ownership и fallback.

НЕ активируй DRAFT в MAIN/PLAY при выполнении009.

НЕ создавай отдельный Python/LM Studio процесс для V3.

НЕ делай весь проект зависимым от присутствия UI или Codex.

Обычный GameServer должен в будущем получать immutable verified weights и самостоятельно выбирать действие внутри существующего игрового сервера.

Эта задача должна оставить понятные и минимальные integration contracts для010, но не менять работающий production combat без отдельной приёмки.

## 20. ОКОНЧАТЕЛЬНЫЙ КРИТЕРИЙ

В конце я хочу иметь возможность:

1. Дважды щёлкнуть по ярлыку тренера.
2. Нажать «Учиться».
3. Увидеть, что тренер самостоятельно находит игровые классы, реальные навыки и подходящие native сценарии.
4. Закрыть Codex и GUI.
5. Позже открыть GUI и увидеть накопленный опыт, состояние модели и checkpoints.
6. Нажать «Стоп».
7. На следующий день нажать «Продолжить» без потери обучения.

Всё это должно происходить без новой задачи на каждую профессию, текущий уровень навыка или его заточку.

Если это реально работает, есть native доказательства и все G0–G8 выполнены — FULL009_AUTONOMOUS_TRAINER.

Если нет — честный PARTIAL с точным блокером, без подмены результата отчётностью.

Начинай с реального source audit, проверки SkillData/Player.getAllSkills и существующих ограничений resolver4 / TacticalPolicyV3 16. Прежде чем запускать GameServer, докажи дешёвыми тестами, что новый генератор действительно не зависит от фиксированных Skill ID.