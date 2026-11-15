# PHANTOM-LEARNING-014 — FAST LAWFUL COHORT & SKILL/TARGET AUDIT

## 1. Идентификатор и приоритет

**№8 из13** после принятия **013 как PARTIAL** (manual-owner decision в диалоге). После выдачи 014 — 5 задач до обязательного пересмотра бюджета. Уровень Codex: **Extra High**, отдельный новый диалог. Язык: русский.

**Главная цель:** сделать pre-CUT подготовку самостоятельного TRAIN существенно более быстрой, объяснимой и завершённой, сохранив законные Solo+Party сцены и источники. **Вторая цель:** доказать корректность назначения фактически изученных навыков и адресатов в уже поддерживаемых боевых семьях; провести аудит существующего баффинга **без изменения его production-логики**.

Нельзя подменять эти цели очередным увеличением timeout или искусственной выдачей skills/MP/HP. Не обещать общий тактический ИИ по всем типам навыков: V3 обучает пока только single-target DAMAGE и ограниченное HEAL.

## 2. Git, пути, исходная точка

- GitHub: https://github.com/kpCat/L2J
- Единственная ветка публикации: `feature/phantom-world`.
- **Проверенный HEAD на момент выдачи:** `42d6b675a9cf57faa4df8a74cc52a8b4adda97fa`; проверить remote заново перед каждой записью.
- Main локальный checkout, который нельзя перезаписывать: `D:\Codex\repos\L2J-phantom-world`.
- Существующий safe source checkout: `D:\Codex\repos\L2J-learning-004`.
- Private TEST/runtime/SQL: `D:\Codex\repos\L2J-learning-006`.
- Модуль: `L2J_Mobius_CT_2.6_HighFive`.
- Читать `AGENTS.md`, модульный Agents.md, `CODEX_WORKFLOW_CONTRACT.md`, релевантные PRE_AUDIT/RESULT013 и RESULT012. Не запускать новый worktree/clone.

## 3. Предпосылка

013 завершена `PARTIAL013_HEADLESS_TIMEOUT_NO_FULL_ADMISSION`: **5 partial headless proofs за 992298 ms (16:32.298), zero sealed TRAIN cells/cohort, 0 CUT, 0 GS, 0 FIT**. Автоматическое остановление и очистка процессов PASS. Frozen: accepted TEST2, DRAFT14, Adam903/223, apps48, 16 gradient episodes, cycle pointer12, 183 SQL rows; неизменны. `Geo203`, protected207. Бюджеты009–013 не сбрасывать.

В `PhantomAutonomousTrainingNative.prepareSources` заново идут `discoverSources` + `planCells` + native pre-CUT обеих рук каждой cell. `prepareAcquired()` в TEST может ожидать естественного восстановления ресурсов до `preparationSeconds` (типично 180). В preflight возможны expensive materialization/recovery, создание новых incarnations и отказ после траты времени. **Это гипотезы для профилирования, не уже доказанный root cause.**

Существующий `PhantomSkillInventory.acquired(Player,...)` знает изученные skills, levels, enchant route; поддержка TRAIN ограничена SINGLE_TARGET_DAMAGE + HEAL. Существующие `PhantomPartyTactics.addMaintenance` и `L2jPhantomPartyBackend.supportEffectStatus` уже поддерживают rebuff / song / dance; **не переписывать их без доказанной проблемы и отдельного согласования**.

## 4. Рабочие результаты

A. **Профиль стоимости подготовки на настоящем TEST:** phase timings, counters, распределение по источникам/классам/навыкам/сценам/отказам, время до 1-го / 2-го / 3-го полного `HEADLESS_READY` cell. Отдельно время безопасного запуска SQL, cold-read, materialization, natural recovery, roster, per-side admission, геометрии, preservation/drain. Достаточно coarse-grained structured receipts, не создавать high-rate логи.

B. **Устранить измеренный dominant cause**, позволяя быстро отбрасывать невозможные заявки ДО дорогого создания игроков, а дорогие операции выполнять только для правдоподобно допущенных кандидатов. Fast checks — только доказуемые консервативные отрицания; окончательная законность всегда full native admission на текущем состоянии до CUT. Не сокращать реальное естественное восстановление по искусственному таймеру, не нормализовать resources.

C. **Тот же канонический тренер:** >=3 fully headless native-admitted cell в TEST, >=2 класса, Solo и Party. Каждый cell имеет обе уникальные A/B incarnations и matched pre-CUT physical proof, не менее двух legal abilities в соответствующей action family. Только тогда sealed `training-admission013.json`. Отказы и незавершённые attempts сохраняются с точными причинами. Не считать partial readiness готовой.

D. **Боевая семантика:** доказать фактический learned skillId+level+enchant/classIndex, тип действия, target type, MP/HP/charges/items, cooldown, skill condition, distance, Geo/LOS, native owner, effect family и результат. Для DAMAGE — реальный Monster/законная offensive цель, для HEAL — lawful friendly/party recipient, не враг. При сомнении `UNASSESSED`/`NOT_SUPPORTED` и штатный fallback, а не фиктивное знание. Ротация более 4 eligible навыков по reproducible shortlist, а не по старому allowlist класса. Проверить наследование навыков до3-й профессии (включая Hydro Blast у обучившего его Mystic Muse), enchant и ситуации отсутствия learned skill.

E. **Баффы — audit only**: на реальных/канонических snapshot и focused tests проверить missing/near expiry, отсутствие бессмысленного rebuff, mage/fighter/hybrid recipient, songs/dances, правильный scope, эффект, длительность, abnormal stacking/stronger existing buff, MP и priority heal/defense. Сделать таблицу `PASS / FAIL / UNKNOWN / NOT_SUPPORTED`. Не внедрять новый подбор баффов и не затрагивать production до отдельного согласования.

F. Если A–E прошли и достаточные полноценные native TRAIN данные доступны в новом ограниченном бюджете, запустить **один** законный collection cohort и, если thresholds `ActionAdvantageV2` соблюдены, настоящий offline FIT/cold restore; без необоснованных утверждений об улучшении. В этой задаче новая независимая многосерийная EVAL V2 **не обязательна**: не объявлять улучшение без неё; нельзя делать promotion TEST/PLAY. Если данных нет, честный PARTIAL по границе actual native.

## 5. Exact source scope и запреты

По умолчанию разрешён source allowlist не более **8 существующих путей**, перечисленных в `GIT_AND_SAFETY.md`. Перед записью Codex должен сделать PLAN со ссылкой на реальные причины и нужные пути, не расширять list без согласования владельца. **Production party/skills/buff/combat/config, исходный reward/objective/SQL, A/B outcome codec, navigation не меняются.**

## 6. Бюджеты

- Сначала DB-free и read-only profiling, frozen replay, measured phase proof. Никаких обещаний качества без экспериментальных данных.
- До GS — **не более двух** ограниченных TEST headless readiness sessions; суммарно не более **20 минут headless wall** включая startup и cleanup. Отдельно журналировать processing vs drain; timeout — TIMEOUT, не NO_DATA.
- **GS max 3 запуска / 60 native минут**; разрешён исключительно после completely sealed headless TRAIN proof, cold guards, проверенных identity и shutdown. Дополнительных стартов/времени автоматически не запрашивать. Меньше — лучше.
- Один новый controlled offline FIT допускается **только** при новых legal TRAIN gradient data и достаточных TUNING/AUDIT; иначе FIT0 и отдельная причина. Не повторять reserved/committed episodes или credits.
- Точки остановки: Gate G0 source/frozen safety, Gate G1 profiling, Gate G2 cheap viability + semantic tests, Gate G3 true legal admission. Пока G3 FAIL — `GS0`.
- Максимум **два direct fixes** на causal path после утверждённого плана. Третий независимый blocker → PARTIAL с точной диагностикой; не растягивать.

## 7. Качество и отсутствие подмен

- Никаких изменённых seed/class/NPC/equipment, подобранных по победе LEARNED. Randomness native untouched.
- Не уменьшать `min cells`, `2 classes`, `Solo + Party`, `>=2 legal skills`, native Geo/LOS, MP, Party admission или handoff, чтобы получить зелёный отчёт.
- Не поднимать timeout013 / budget без доказанного профиля и отдельного согласования. При нехватке lawful inputs корректно сообщить `INSUFFICIENT_EVIDENCE`; при истечении времени `TIMEOUT`.
- Не переписывать исторические V1/V2/013 evidence. Новые versioned receipts additive, immutable; сохранять SHA/CRC/lineage.
- Некорректное применение навыка — safety issue, проверяется до «более умного» скоринга. Никаких игровых фейков.

## 8. Приёмка

Смотри `ACCEPTANCE.md`, `TEST_MATRIX.md`. Отдельно `COHORT_READINESS`, `SKILL_TARGET_SAFETY`, `BUFF_AUDIT`, `MODEL_ADVANTAGE`. FULL014 только при G0–G8 PASS по своей ограниченной задаче. Технический FULL не доказывает превосходство модели. При PARTIAL все выполненные результаты сохранить, честно сообщить оставшиеся blocks.

## 9. Отчёт, Git

`docs/phantoms/tasks/PHANTOM-LEARNING-014-FAST-LAWFUL-COHORT-AND-SKILL-AUDIT/` — PLAN/PROGRESS/RESULT/EVIDENCE/HANDOFF/COMMANDS/ACCEPTANCE; `docs/phantoms/reports/PHANTOM-LEARNING-014-FAST-LAWFUL-COHORT-AND-SKILL-AUDIT.md`. Обновить бюджет `docs/phantoms/COMBAT-AI-ROADMAP.md` новым current блоком без переписывания старого.

Разрешены exact scoped commits и обычный `git push origin feature/phantom-world` после всех guards; независимый readback GitHub refs/diff/full source/evidence. Отчёт на русском, default OFF; новая задача автоматически не запускается.
