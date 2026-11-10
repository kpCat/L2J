# PHANTOM-LEARNING-001 — первое обучаемое ядро Phantom World

## 1. Идентификатор
`PHANTOM-LEARNING-001`. Новый большой work item, НЕТ связи с утверждением FULL130. Codex: **Very High**. Одна новая сессия Codex; продолжать только локальную отладку этой одной задачи. Новый номер — новый диалог.

## 2. Цель
Построить минимальное реальное **общее обучаемое ядро**: наблюдаемое состояние + допустимые PvE-действия -> обученная оценка -> выбранный штатный skill -> наблюдённый исход -> batch training -> версия модели MariaDB -> повторная загрузка -> другое решение в сходной, но не идентичной ситуации. Одна общая модель используется многими профилями, не 5000 отдельных моделей.

В первом релизе обучаем **выбор между 2–4 действительно законными PvE offensive skills** в существующем `PhantomCombatService.issueAction` в SOLO и автономном Party (Party обращается к тому же Combat owner). Без изменения механики кастов, урона, регистрации навыков, агрессии и наград. Предлагаемые будущие альтернативы MOVE->HEAL, RETREAT, RESURRECT, SPOIL/SWEEP, PvP/PK/raid/siege/olympiad учитываются в формате действий, но не реализуются в этой задаче.

## 3. Зависимости и статус
GitHub `https://github.com/kpCat/L2J`. Модуль `L2J_Mobius_CT_2.6_HighFive`. Исходная точка `wip/coordination-130-preserved @ c5b81f89ce02030eff8441395b0411137a110b2f` (сверить live; если SHA отличается, не подменять молча). Пользовательский локальный Git-root `D:\Codex\repos\L2J-phantom-world` — **только чтение**. Основной `feature/phantom-world` @ `faec72161f9e4f6231764e17d6f2637a4db080af`, сохранять. В собственном отдельном clone создать `wip/phantom-learning`, никакого rebase/force в другие ветки.

TASK130 заморожена как `PARTIAL`; реальные Party/GK/earned EXP/SP/Heal/loot/STORE/recovery уже доказаны на нескольких JAR, но FULL A–G на одной финальной версии не был принят. Не объявлять TASK130 FULL за счёт LEARNING-001. Preserve `wip/coordination-130-preserved` неизменной. Зависимости от текущей WIP кодовой базы документировать, чтобы интегрировать будущие исправления TASK130 явным последующим cherry-pick/merge после тестов, без смешения релизных веток.

## 4. Контекст и предварительный аудит
До правок прочитать `Agents.md` (исторические local paths устарели), `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `docs/phantoms/TASK_PACKAGE_STANDARD.md` и SOURCE_MAP. Git status/remote/HEAD/upstream; точечная инспекция текущих owner seams. Никаких иных хроник. Важная точка: `PhantomCombatService.issueAction` для обычного PvE сейчас выбирает `selectedSkills().get(_nextSkill++ % size)` — фиксированное чередование; party offense также проходит через `PhantomCombatService`. `PhantomPartyTactics` отдельно сортирует HEAL/RESURRECT/ASSIST по жёсткому priority, не менять этот оркестратор на первом этапе.

## 5. Архитектурное решение
Библиотечный `phantoms.learning` внутри JVM: immutable версии модели + чистая функция `score(context, legalAction)` + versioned feature encoder + trainer (отдельно от горячего GameServer) + repository для MariaDB + audit/rollback. Начальная настоящая маленькая обучаемая нейросеть: **MLP (128 нормализованных/хешированных входов -> 24 скрытых ReLU -> 1 ограниченный Q/score)**, детерминированное и ограниченное обучение из подтверждённых примеров с native-привязкой. Не LLM, не 5000 сетей, не новый GameServer, не внешний Python-сервис на каждое действие. Числа можно уточнить после лёгкого анализа производительности, но НЕЛЬЗЯ заменить обучаемость константным `if/priority` без явного согласования.

Генератор допустимых действий остаётся штатным. Нейромодель только **переупорядочивает доступные PvE-навыки** (не создаёт навыки, targets, телепорты, Heal, items). Если модель не загружена, неизвестная схема, нет достаточного проверенного обучения, broken checksum, низкая уверенность/нарушен бюджет — гарантированный исходный round-robin без ошибок запуска. Вторая граница native ownership, skill cooldown, MP, Geo, target validity проверяется перед исполнением как раньше. Нельзя отключить обязательный RESURRECT/HEAL/LOOT/SWEEP, F7/F8.

## 6. Scope
- Зафиксировать версионированную модель состояния и действия с контекстом SOLO/Party, role, level, classes, weapon/armor, buffs/debuffs, shot readiness, target NPC/type/level/HP, aggro, distances, Geo visibility/elevation, Party aggregates, skillId/level/cost/type. Недоступные факты — явная unknown mask, не выдумывать.
- Лёгкий синхронный inference в `issueAction` для 2–4 lawful skills. Проверить, что party offense с тем же owner фактически использует ту же модель, и no-op при одном навыке. Не добавлять SQL/GPU/network в hot path.
- Native evidence intake: фиксировать только завершённые, точно связанные с actor/epoch/session/action/target outcomes и выдавать подтверждённым событиям reward. Не приписывать одному навыку damage от союзников. Не учить по голому `ISSUED`, имитации EXP/SP/loot или неатрибутируемому target HP. Если исход не доказан — записать uncredited/unknown, НЕ обновлять веса.
- Отдельное offline batch-training API и команда на собственной TEST MariaDB, общие веса, версия/хеш/backup, атомарный promote в TRAIN environment; переключение модели только на безопасной границе, `shadow`/`off`/`on` и baseline fallback.
- Только существующие owner scopes и API. Никаких параллельных новых AI/Scheduler/Navigation/Combat engines и никакого отдельного потока на каждого профиля.
- Тестовый набор: generalization по классу, уровню, эквипу и ситуации; shared knowledge из одного профиля меняет выбор у другого; сохраняется после SQL reload; shadow не меняет native; выключение возвращает legacy; законные отказы не становятся положительным опытом.

## 7. Out of scope
Автоматический ночной Training Director, автономный GameServer сервис, многоклассовые генераторы сцен, нейроуправление Heal/Resurrect/Positioning, трансформации Inquisitor, PvP/PK (кроме неизменности прежних режимов), осады, территориальные войны, рейды, Олимпиада, новые карты, манипуляции шансом дропа, learning on production Main DB, обучение LLM. Это LEARN-002–004 и TASK131–136. Не делать масштабный redesign TASK130 или переносить её код в MAIN.

## 8. Требуемые изменения
Пример допустимой структуры — `java/org/l2jmobius/gameserver/phantoms/learning/` (FeatureSchema, ContextFacts, ActionFeatures, NeuralPolicy, Evidence/Trainer, ModelRepository, ModelSnapshot, LearningPolicyPort); точные имена за Codex. Один минимальный `install` seam в `PhantomCombatService`, точечная регистрация в `PhantomSystem`. БД SQL через **существующий** game db-installer (`dist/db_installer/sql/game`), test-only схема + idempotent migration strategy. Без изменения core `Player`/`GameClient`/native RNG.

## 9. Конфиги
Отдельные явные owner keys в текущем `PhantomPlayers.ini` (наименование интегрировать без дублирования механизма): `LearningMode=OFF|SHADOW|ON` default OFF, `LearningModelVersion=...`, `LearningMaxCandidates=4`, `LearningEvidenceEnabled=false` default, `LearningMaxExamplesPerBatch`, `LearningDeterministicSeed` only in TEST. Не добавлять 30 ненужных флагов. В отсутствии новых таблиц default OFF должен работать как раньше.

## 10. Производительность
Не читать SQL в игровом Decision/Combat tick. Для 128 активных с четырьмя кандидатами: независимый JVM microbenchmark warmed на JDK25, измерить p50/p95/p99, allocations/op, суммарное CPU, memory footprint. Цель p95 < 1 ms на выбор; если среда мешает абсолютному порогу — предъявить точные измерения и оптимизировать без скрытых допусков. Никакого полного обхода 5000 профилей на каждом пульсе. Training off-hot-path, ограниченные очереди, drop telemetry under backpressure без остановки боя, bounded SQL flush; без постоянного растущего списка.

## 11. Конкурентность и lifecycle
Immutable snapshot весов (atomic swap), отсутствие мутируемого процесса тренировки в runtime-выборе, deterministic seed в TEST, model/schema/version checksum readback, stop/close drains bounded writer, actor epoch и group/session lifetime проверяются. Во время fail stop не исполнять async native work из learner. Продвижение модели не должно менять действующий native lease посреди каста.

## 12. БД и транзакции
Глобальные versioned model rows (weights/schema SHA/algorithm version/checkpoint), подтверждённые training samples с idempotent keys (`actorEpoch+combatGeneration+actionSeq+mode+targetSpawnGeneration` или эквивалент). Контроль quotas/retention и только связанная анонимизированная тактическая информация. MariaDB source-of-truth для model versions; locked CAS/transaction при promote; версию модели хранить целиком, контроль SHA; никогда не записывать learning rows в рабочую TEST130/main/чужие схемы. Доказательство reconnect/reload на собственной схеме.

## 13. Автотесты
План в TEST_CASES.md. DB-free tests с deterministic fixtures; отдельный isolated MariaDB integration; реальный `CombatService` integration + минимум один headless/native proof, что два доступных skills выбраны по model score и штатно исполнены (без огромного повторного TASK130 GK/PvE). Проверить одинаковую модель в SOLO/Party-контексте. Не claim настоящий earned damage при одних mocks; если actual evidence нет, явно отметить.

## 14. Команды проверки
Ант `jar` и test compilation — только штатными существующими командами, из корректного module/dist/game layout. `git diff --check`, existing targeted tests `combat-action-ownership`, `party-tactics`, `world-coordination130` и выбранные SOLO/PvP/Supply/Heal regressions. G0 перед любым native. Точные команды определять из существующего `build.xml` и testing scripts, не придумывать. Новые named tests `learning001` или в существующем test registry. Проверки полного мира не обязательны.

## 15. Приёмка
Все G0–G8 в ACCEPTANCE.md. Синтетический learning PASS без влияния на production `issueAction` = PARTIAL. Импорт весов без реально обученной модели = PARTIAL. Native skill issue без causal evidence = PARTIAL для сбора опыта. Не требовать ни FULL130, ни kill/restart всей Party из TASK130.

## 16. Отчёт
`docs/phantoms/reports/PHANTOM-LEARNING-001.md` и короткий `RESULT.md` в текущем каталоге задачи. Изменения, real model/schema, реальные числа overfit/holdout, native evidence, SQL row readback, performance 128 actors, tests, exclusions, P1/P2, exact source/JAR SHA, GitHub branch, stopped processes/ports, честный статус SUCCESS/PARTIAL/BLOCKED.

## 17. Commit/push
**Прямое разрешение владельца именно для этого пакета:** работать и публиковать scoped commits только в новой `wip/phantom-learning` на GitHub kpCat/L2J, основание `c5b81f89...`, fast-forward, без force. Не пушить в `wip/coordination-130-preserved`, `feature/phantom-world`, master/main. Если Codex возражает из-за старого общего `CODEX_WORKFLOW_CONTRACT.md`, данный task package явно задаёт task-local exception от владельца; нельзя самовольно выбрать устаревший путь/ветку.

## 18. При блокировке
Один основной implementation + один адресный repair pass на доказанный дефект. MAX 2 clean JAR, MAX 2 GameServer boot на полностью собственном учебном изолированном контуре; перед каждым G0, rollback и cleanup. Если 3-й независимый блокер — остановить и оформить `PARTIAL/BLOCKED`, не открывать 10 подзадач. Никакого ожидания много часов без прогресса; зафиксировать usable milestone и короткое HANDOFF для нового Codex-диалога.
