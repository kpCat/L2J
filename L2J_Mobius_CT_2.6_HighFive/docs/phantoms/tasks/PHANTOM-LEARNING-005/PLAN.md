# PHANTOM-LEARNING-005 — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** честный TEST-only comparator реальных парных PvE-эпизодов stock против принятых V1 DAMAGE и V3 HEAL, с независимым игровым вердиктом.

**Architecture:** существующий Director/G0 запускает штатный GameServer; evaluator подготавливает независимые arms, получает native pre-CUT facts, действия и terminal receipts. Отдельный fail-closed match validator и immutable ledger дают offline статистику; обучение не вызывается.

**Tech Stack:** Java25, Ant, существующая MariaDB11.4.13, Windows PowerShell и текущие зависимости. Новых библиотек и второго supervisor нет.

**Spec:** `TASK.md`, `ARCHITECTURE.md`, `EXPERIMENT_PROTOCOL.md`, `METRICS_AND_DECISION.md`, `ACCEPTANCE.md`, `SAFETY.md`, `TEST_CASES.md`, `WORKFLOW_FAST.md` этого пакета.

## Текущий gate

**План подтверждён пользователем: «Подтверждаю план, продолжай автономно».** Реализация начата, native испытания ещё не начаты. Разрешение на рутинную own TEST-работу из TASK.md:3 действует без новых промежуточных подтверждений. Финальная приёмка остаётся впереди.

## Global Constraints

- Independent clone `D:/Codex/repos/L2J-learning-005`; новая ветка `wip/phantom-learning-005`, exact base `dff800f4b95232c132b374f8ec97c932eda4a6b1` подтверждены. Абсолютный путь — факт этой сессии, в исходники не переносится.
- Пользовательский checkout и clone/SQL004 read-only. MAIN/PLAY, releases, клиент, чужие процессы и защищённые refs не изменяются.
- Только SOLO_DAMAGE_V1 и PARTY_HEAL_V3; COMBINED и следующие goals не выполняются.
- V1 model1 SHA `e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2`; V3 model2 SHA `6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc`. DRAFT8 не eligible ON.
- После CUT никакого назначения HP/MP, teleport, изменения Skill/NPC/rates/RNG, forced выбора или SETTLE outcome. До CUT исходные состояния разрешены и должны быть раскрыты и сопоставлены.
- EVAL не поступает в TRAIN/TUNING/AUDIT. Старые model/optimizer не изменяются; исходные V1/V3 defaults OFF/SHADOW сохраняются.
- Без SQL/сети/обучения в combat tick, нового потока на actor или второго игрового движка.
- Восстановленные 17 файлов входного пакета immutable. Все 15 MANIFEST payload hashes и размеры совпали; MANIFEST SHA совпал с SHA256SUMS. Записи не исправляются ради форматирования.
- Git разрешён TASK.md:18: exact staging, scoped commit и обычный non-force push только в новую ветку005. Никаких force/reset/clean/stash/broad restore.
- FULL005_EVALUATOR требует G0–G8; manual gate агент самостоятельно не принимает. При реальном blocker — честный PARTIAL и безопасная полезная часть.

## Read-first и подтверждённые аналоги

Прочитаны все 17 входных файлов, корневые AGENTS.md/README.md, модульный Agents.md, CODEX_WORKFLOW_CONTRACT.md, TASK_PACKAGE_STANDARD.md, актуальный срез PHANTOM_CURRENT_STATUS.md и релевантные разделы PHANTOM_DEVELOPMENT_MASTER_PLAN.md; отчёт004, его RESULT/HANDOFF и релевантные COMMANDS/PLAN.

Подтверждённые исходники и seams:

1. `PhantomTrainingNative`: один реальный GameServer, own root/config/JAR checks, socket attestation и shutdown. Здесь нужен небольшой выбор evaluator route до `LearningPolicy.configured`/training observer initialization и перед003/004 collection.
2. `PhantomLearning001ShadowNative.Runtime`: `prepareTraining`, `prepareHeal`, `inspectHeal`, `pulseHeal`, `runLearning004Encounter`, `settleLearning004`; штатные Combat/Party owners и drain. Нужны только ограниченные TEST observation/admission seams для005, без смены владельца действий.
3. `PhantomLearning003Damage.prepare` и `Learning003DamageImport.load/verifyUnchanged`: fresh actors и authenticated accepted V1 import с отдельным cold SQL readback. Import находится в test source tree, не production.
4. `TacticalStoreV3.ensureLegacyParent/accepted`: authenticated ZIP003 import и readback accepted V3 model2. `TacticalPolicyV3` создаётся с OFF/SHADOW/ON только для нужного arm.
5. `TacticalEpisodeV3`: canonical native kill/EXP/SP/end-state, UNKNOWN masks, snapshot после drain. Версию004 не переписываем; оборачиваем в versioned005 arm receipt.
6. `Training.ps1`: reusable build/stage, SQL prepare, held process identity/socket checks, native resources, stopped boundary. EVAL должен обходить существующие actor TRAIN preparation и оба вызова trainer, включая recovery-path; существующие G0/Job/Win32 файлы не меняются.
7. `LearningPolicy.collecting/record/actorKey` сейчас жёстко завязаны на `LearningStore.Writer`; публичный constructor без writer не даёт полноценного exact V1 receipt path. Поэтому подтверждён необходимый минимальный production observation seam: отдельный TEST-opt-in EVAL sink/identity вместо training Writer, с тем же native `LearningCastEvidence` и прежним выбором действий. Это предлагаемый новый seam, не существующий API. EVAL обязан реально не писать training rows; одного отключения trainer недостаточно.

AGENTS.md выше Git root (`D:/`, `D:/Codex`, `D:/Codex/repos`) не найдены. Дополнительные nested AGENTS в проверенных test/learning/training путях и отдельные code-map/pattern файлы не найдены; SOURCE_MAP.md пакета имеется. Повторного широкого поиска не требуется.

## Exact исходный allowlist

Все пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`. Новые имена — предлагаемые файлы005, не утверждение о существующих API.

| Путь | Ответственность |
|---|---|
| `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Evaluation.java` | versioned plan/arm/pair, matching, unknown-aware метрики и статистика |
| `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Ledger.java` | immutable durable pair/arm записи, integrity/dedup, cross-process resume |
| `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Checks.java` | 10 focused DB-free contracts и negative controls |
| `test/java/org/l2jmobius/tests/phantoms/PhantomLearning005Native.java` | native evaluator, lawful actor setup, admission и export/report main |
| `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java` | TEST-only dispatch005 вместо training collection |
| `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` | минимальные native facts/receipt seams для005 |
| `tools/phantom-runtime/training/Training.ps1` | EVAL profile/dispatch/report на существующем lifecycle; блокировка TRAIN и auto model selection в EVAL |
| `tools/phantom-runtime/training/Evaluation005.ini` | отдельный disabled-by-default bounded EVAL config, явные model pins |
| `java/org/l2jmobius/gameserver/phantoms/learning/LearningPolicy.java` | минимальный TEST-opt-in exact receipt sink без Training Writer; Combat/Party/action selection не меняются |

Это 9 source/config/test файлов. Отдельный bounded exception для обязательной доставки: 17 immutable входных файлов, task PLAN/PROGRESS/RESULT/HANDOFF/COMMANDS/SCOPE_ALLOWLIST, русский report и компактные redacted evidence. Runtime SQL/datadir/config secrets, Geo, большие binaries и полные private logs не публикуются. Новая SQL migration пока не нужна: preferred immutable JSON ledger с atomic writes и process lock; codec выбирается по уже имеющемуся строгому validator, без новой зависимости.

Если для законного решения понадобится production Combat/Party правка, новая migration либо расширение исходного allowlist, сначала установить точную причину и зафиксировать bounded изменение плана; не подменять архитектуру.

## Task 1 — достоверные contracts и ledger

- [ ] Написать focused checks T01–T09 и source-safety T10; увидеть RED из-за отсутствующего поведения.
- [ ] Ввести schemaVersion005 plan/arm/pair: campaign/source/JAR/model/schema pins, mode, ordinal/order/seed, actual pre-CUT facts и отдельные incarnation identities.
- [ ] Matching сравнивает фактические class/level/learned skills/equipment/enchant/armor/jewelry/buffs/HP/MP/CP, NPC template/level/start state/instance/Geo/LOS, Party roles, config/rates/objective/timeout. Actor/target object IDs и epochs обязаны быть независимыми, а не равными. Разное физическое условие => UNMATCHED, delta отсутствует.
- [ ] Дедупликация по arm/event и distinct NPC/spawn/instance; персональные EXP/SP кредиты отдельно. SKIP/FAILED/INCOMPLETE сохраняются в знаменателе; UNKNOWN не превращается в0.
- [ ] Ledger удерживает cross-process lock, проверяет schemas/hashes/provenance, атомарно сохраняет записи. Изменённый JAR/model/scenario не продолжают старую campaign. STARTED закрывается INCOMPLETE только после доказанного exit собственного процесса.
- [ ] Статистика: paired deltas B−A, n, медиана/разброс, 95% bootstrap CI с fixed offline analysis seed и оговоркой малого n. RNG анализа отделён от native RNG. При n<20 ADVANTAGE_SUPPORTED запрещён; objective заранее native time-to-goal при отсутствии регрессии выживания, resource trade-offs отдельно.
- [ ] GREEN: `ant -f ./L2J_Mobius_CT_2.6_HighFive/build.xml compile-tests`, затем main005 через существующий classpath. Mutated pair, duplicate event, missing receipt и bad model обязаны быть отвергнуты.

## Task 2 — минимальная native интеграция и первый smoke

- [ ] EVAL profile не вызывает trainer ни при cold recovery, ни после native arm, ни через report/export. Не меняет обычные V1/V3 launch defaults; master OFF до штатного bootstrap.
- [ ] Focused regression для `LearningPolicy` EVAL sink: TEST opt-in обязателен, callbacks/attribution сохранены, training Writer не создаётся, обычный SHADOW/ON путь неизменен. Native SQL readback подтверждает нулевой прирост training receipts, а не только отсутствие fit.
- [ ] Выбрать новые свободные loopback SQL/game/login порты с owner checks; Preflight уже прошёл на исходном профиле, но это ещё не own SQL admission.
- [ ] Создать own TEST через existing G0, импортировать только exact accepted ZIP и выполнить независимый cold SQL readback. Проверить неправильный SHA/класс/статус и DRAFT8 rejection до любого CUT.
- [ ] Создавать свежие actor cohorts алгоритмом из canonical game data. Каждая arm получает новый lawful target incarnation; native pre-CUT matching фиксируется до начала действий. Если существующий runtime не может закрепить и доказать target freshness/physical parity, сохранить UNMATCHED и точную причину, не объявлять такую пару fair.
- [ ] SOLO: A=V1 SHADOW без перестановки, B=accepted V1 ON; V3 OFF в обеих. PARTY: V1 одинаковый SHADOW в обеих; A=stock HEAL, B=accepted V3 HEAL ON. Emergency stock остаётся авторитетным.
- [ ] Одна настоящая Solo A/B и одна Party A/B. Для Party сначала доказать допустимую цель всего эпизода и enemy pressure, затем lawful ≥2 noncritical HEAL alternatives; изолированный одиночный heal без завершённого PvE не считать completed Party pair.
- [ ] Зафиксировать реальные decision baseline/offered/selected и attributable actual cast/effect. При отсутствии различий осмотреть законные alternatives и другую ограниченную сцену, не форсировать выбор и не повторять бесконечно.
- [ ] Проверить отрицательный same-policy control; случайная разница native damage без policy change не является intervention.

## Task 3 — bounded 4+4, STOP/RESUME и доставка

- [ ] После успешного smoke расширить до минимум4 fair completed pairs каждого mode, минимум2 физических ситуаций и2 actor incarnations; порядок AB/BA сбалансировать алгоритмом с фиксированным campaign seed. Сохранить все отвергнутые и незавершённые попытки.
- [ ] Заморозить source/JAR на весь paired run; при исправлении начать новую явно versioned campaign. Не пересобирать и не boot ради оформления report.
- [ ] Измерить startup/prepare/encounter/drain отдельно, latency policy/dispatch с указанной областью, process CPU/RAM, throughput и incomplete fraction. Оценки будущих n=20+ только по фактическому throughput и с оговоркой.
- [ ] Штатный STOP, exact owner exit и свободные порты; fresh-process RESUME своего ledger, неизменный previous credit, новые incarnations, cold offline report/export.
- [ ] Affected runner routes: `learning001`, `combat-core`, `combat-action-ownership`, `party-tactics`, `training-contracts`, `post002-support` по действительно затронутым seams; существующие F7/F8 при затронутом launcher contract. Full DB `ant verify` не запускать вместо targeted checks.
- [ ] Последовательные `compile-tests`/`jar`; exact diff/allowlist guard, старые accepted ZIP и optimizer hashes, protected/Geo guard. Две отдельные проверки изменённых текстов: все mojibake markers и все6 escaped-Cyrillic regex.
- [ ] Русский report, PLAN/PROGRESS/RESULT/HANDOFF/COMMANDS, compact source-linked receipts; точные G0–G8 результаты. Самостоятельно gate passed не назначать; FULL только при выполненных автоматических критериях и required финальном review.
- [ ] Scoped commit и non-force push в005, independent GitHub HEAD readback и повторный protected refs snapshot; final SHA receipt отдельно от self-referential report.

## Review Focus

- Logical scenario совпадает, фактические target HP/position/instance или gear различаются: обязательный UNMATCHED, без скрытого допуска.
- V1 observer пишет обычные training rows либо Director вызывает trainer: EVAL должен быть исключён из обеих линий без изменения старого тренера.
- Party heal отличается, но terminal kill не относится к тому же target/arm: intervention и whole-episode outcome проверяются отдельно.
- Crash оставил STARTED или half-written receipt: не выполнять arm повторно и не менять историю без доказанного owner exit.
- OFF/SHADOW baseline расходится с B из-за side policy, seed exploration либо разных ready alternatives: causal label отклоняется.

## Что остаётся непроверенным

Сборка/tests005, собственная SQL и import/readback accepted models, физическая достижимость matched native target setup, actual policy divergence в обоих mode,4+4pairs, cold evaluator resume, F7/F8 и performance. Никакого игрового преимущества пока не измерено. Независимый read-only builder check плана: существенных неотвеченных вопросов не найдено; последующий source audit уточнил явный writer-free observation seam выше.

## Фактическое исполнение и остановка на G0

Task1 выполнен в DB-free scope:28 focused assertions, canonical compile-tests/jar exit0. Task2 интегрирован и проверен source review, но actual native испытание остановлено до SQL/GameServer на protected207 OwnerConfig mismatch; изменять guard или чужой файл не разрешено. Task3 native4+4/real Resume/performance — NOT_RUN. Safe delivery оформляется PARTIAL; подробности RESULT/EVIDENCE/отчёт. Full/manual native gate не passed.

Пользователь отдельно разрешил по окончании005 объединить накопленную цепь обратно в feature/phantom-world. Это последующая consolidation с exact dirt backups и обычным fast-forward push, а не расширение005 до следующих игровых goals.

## Утверждённое продолжение и фактический итог

Пользователь подтвердил первоначальный план и затем отдельное baseline005 исключение: «Разрешаю новый baseline005 и native испытания». Source exception9→10 включает только TrainingOwner.ps1; точный новый207manifest pin действует исключительно в EVAL005. Исторические входные17файлов не изменялись.

Native smoke005a и005b выполнены на своих SQL/GameServer. Измеренные ошибки sampling deadline и random initial NPC position исправлены до frozen основной серии005d; каждый старый arm сохранён. Pre-CUT setup раскрыт в отчёте; RNG/config/готовность skills не изменялись.

Объявленный бюджет005d полностью завершён:16arms,4pair attempts/режим. Fair completed: Solo2, Party1. Остальные — смерти/no-effect/rejected evidence/UNMATCHED с исходными terminal receipts. Solo имеет3native divergence в fair pairs; PartyHEAL divergence0. Поэтому G2/G3 не выполнены, FULL gate NOT_PASSED, статус PARTIAL. Новые серии ради желаемого победителя не запускались.

STOP/RESUME выполнен реально: новые Director/evaluator-worker, неизменные hashes frames/arms/pairs и summary; завершённый budget не запускает GameServer повторно. ПоследнийSTOP подтвердил свободные own33347/17991/19291. Offline Report/Export exit0; trainer не запускался.

Финальные source checks:28focused +143affected assertions;F7 exit0/F8 14/14;canonical compile-tests/jar exit0. Основной JAR aba85844f9afa9669f8ad32844fe3c315588acd7a706c32d3b232b1a96cc4e97, protocol0f2d23f4621e8934ec2c68d7b4bad6da4c8ab6a913580ba2519a5ca8e55d6bfd. Подробные gates и стоимость — EVIDENCE/RESULT/report.

Отдельный integration pass обнаружил старый index23файла/11UU безMERGE_HEAD. Пользователь явно подтвердил: «Старая работа — сохрани и разбери автономно». Working files, все stages и index сохранены внешними побайтовыми SHA backups.16файлов — старые/regressive варианты;7 — незавершённые предложения со старыми API/зависимостью TASK109. Их сохраняемый архив не активирует новые PvP/loot goals. Разбор и exact resolution — в отдельном CONSOLIDATION.md.
