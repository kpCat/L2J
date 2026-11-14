# TASK011 — единый достоверный генератор законных боевых сцен

`/goal PHANTOM-LEARNING-011-LAWFUL-ENCOUNTER-ENGINE`

**Уровень мышления: Extra High. Новый диалог Codex. Документы/отчёты — на русском.**

## 0. Что хочет владелец

Прекратить бесконечные частные исправления генератора тестов и запускать GameServer наугад. Построить **один повторно используемый контракт подготовки и допуска игровых столкновений** для автономного тренера и независимой EVAL. Сценарии строятся из фактических законно изученных навыков персонажей, состава группы, обычных NPC и штатных механик L2J, а не из двух-трёх ID профессий/навыков.

Главная измеримая цель011: **надёжный Solo + Party PvE encounter admission и состоявшиеся честные сравнительные бои**. Тот же контракт проектировать расширяемым под PvP, рейды/охрану и командные тактики, **но НЕ объявлять эти action families реализованными в 011**.

Ключевое отличие от010: недостаточно увеличивать количество точек/радиус перебора. Нужно доказать согласованность фаз планирования, штатного игрового допуска, параметров и живого состояния перед CUT. Если в данных/мире объективно нет законной сцены — выдавать воспроизводимый конкретный отказ и **не запускать GameServer ради пустого поиска**.

## 1. Источник, Git и владение

GitHub: https://github.com/kpCat/L2J

ОБЯЗАТЕЛЬНАЯ постоянная ветка разработки и публикации: `feature/phantom-world`.

На момент формирования: HEAD `f153d137a7d666ea0c28586a45a429a94a9d1c44` (14.11.2026). Заново проверить фактический `origin/feature/phantom-world` и не предполагать, что HEAD остался прежним.

Основной checkout владельца, **грязный/защищённый**: `D:\Codex\repos\L2J-phantom-world`. Не делать там reset/clean/FF, пока не доказана сохранность локальной работы.

Существующий источник на feature: `D:\Codex\repos\L2J-learning-004` (проверить текущий branch/status). Изолированный TEST с SQL/checkpoints: `D:\Codex\repos\L2J-learning-006` (проверить ветку и owner). Можно использовать другой **уже существующий безопасный** checkout, но не создавать ещё один многогигабайтный clone/worktree.

Единственный серверный модуль: `L2J_Mobius_CT_2.6_HighFive`.

Сначала прочесть корневой `AGENTS.md`, ближайший `Agents.md`, `PHANTOM_CURRENT_STATUS.md`, `CODEX_WORKFLOW_CONTRACT.md`, `TASK_PACKAGE_STANDARD.md`, настоящий `docs/phantoms/tasks/<TASK>/TASK.md`, а также **корневой** отчёт предыдущей задачи `docs/phantoms/tasks/PHANTOM-LEARNING-010-FEATURE-CONSOLIDATION-AND-EVAL-CLOSURE/RESULT.md` и `EVAL-REPAIR.md`. Учесть: старый Agents.md содержит устаревшие абсолютные пути.

**Разрешаю** создавать ограниченные commits и обычный `git push origin feature/phantom-world` в рамках этого Goal, если безопасны source, status и scope. Не разрешаю новые WIP branches, squash, rebase уже опубликованной истории, reset --hard, clean, force push, удаление worktree/stash, изменение MAIN/PLAY или чужой SQL.

### Frozen lineage

- Последняя модель TEST: **DRAFT14**, Adam **903 updates / 223 epochs**, **48 applications**, **16 gradient episodes** (из009).
- Accepted TEST2 и completed-cycle pointer12 — разные сущности; не переставлять pointer вручную.
- В011 **никаких новых fit, gradients, reward changes, checkpoint promotions или training credits**. Сравниваем frozen DRAFT14 vs STOCK, с неизменяемой историей.
- Сохранить старые 009/010 negative campaigns, журналы/бюджеты, 183-row frozen SQL lineage, private Geo203/protected207.
- 010: CONSOLIDATION VERIFIED; EVAL_CLOSURE PARTIAL; G3/G4 FAIL, `4/5` native starts, **715447 ms / 45 min**. Старый бюджет 010 НЕ переносится как разрешённый бюджет 011 и НЕ обнуляется в историческом журнале.

## 2. Реальная root cause, которую требуется устранить

Опубликованная 010 завершилась без завершённых Party пар:

- Пять кампаний: A=4 lawful cells/8 arms/1 Solo pair, B=0 cells, C=4 cells/0 settled, D=4 cells/8 arms/0 pairs, E=0 cells.
- Последний mandatory preflight: **0 lawful cells**, `84 TARGET_BOUND_ALTERNATIVES`, `1656 NO_ISOLATED_TARGET_POINT`.
- В GS4 — `NPC_FACTION_NOT_ISOLATED` и `MATCHED_PHYSICAL_FACTS_DIFFER` (HP/config parity); поздние исправления конфигурации и HP не доказаны новым успешным native.
- Ошибка `episodeSeconds=180` против максимум120с CombatRequest уже исправлена fail-closed, но ситуация показывает недостаточность единого конструирования request до запуска.
- Прогноз движения NPC и spawn drift не гарантируют реальное состояние живого World.
- Нельзя ложно объявить 0 lawful cells полной невозможностью PvE в High Five; надо отличить недостаток конкретного source/roster/world от ошибки генератора и неверно выбранных порогов.

**Главный запрет:** не добавлять ещё один «поиск 400 точек», «расширить радиус до5000», «взять первый NPC» или `class→skills` как единственное решение. Параметры поиска допускается делать bounded и адаптивными, но сначала — доказать источник истины и согласованность admission.

## 3. Контракт единого генератора

Сделать минимальный versioned **Encounter/Admission Contract**, переиспользующий текущие:

- `PhantomAutonomousTrainingNative`, `PhantomTrainingScenarios`, `PhantomTrainingNative`;
- `AutonomousTrainingV1`, `PhantomSkillInventory`, `PhantomNativeCombatCapability`, `PhantomCombatCapabilityResolver`;
- `PhantomCombatRequest`, `PhantomCombatPolicy`, `PhantomPartyTactics`, реальную Party admission;
- canonical `Player`, `SkillData`, `SkillTreeData`, NPC/spawn, GeoEngine, ZoneManager, original owner leases;
- `TrainingConfig`, `Training.ps1`, `TrainingOwner.ps1`.

Не делать второй Combat/Party/NPC manager и новую тренировочную нейросеть.

### 3.1 Три разные сущности

1. **Capability inventory** — все фактически изученные активные/пассивные/заточенные навыки исходного персонажа, без принудительной выдачи; не означает, что они доступны сейчас.
2. **Encounter proposal** — проверенный, но *ещё не запечатанный* кандидат: actor/roster/source snapshots, допустимая action family, режим, NPC/area/faction/Geo envelopes, window/risk, limitations.
3. **Live admitted encounter** — фактически восстановленный/материализованный runtime Player/Party/Monster/World, проверенный непосредственно перед CUT на полный набор условий; только тогда seal физической идентичности и резерв A/B.

**Не запечатывать сцену до полной live admission**. Дешёвый headless PASS — это готовность кандидата к попытке, НЕ гарантия живого мира, не `EVAL_COMPLETE` и не game result.

### 3.2 Два этапа допуска

**STATIC/HEADLESS** без расхода GameServer starts:

- canonical class and acquired ranks/enchant, реальный learn state;
- candidate shortlist, минимум2 законные альтернативы того же supported action family (для обучения), stock skill и readiness/fallback;
- Solo/Party роли и helpers — реальные имевшиеся у персонажей способности, соответствующий magic level для NPC;
- NPC/Geo/LOS/faction/aggro/spawn/profile-source binding с проверенной моделью на момент snapshot;
- корректный `CombatRequest` и Party request через их реальные validation constructors (включая Solo<=120000ms, Party<=60000ms);
- полная config parity со штатным loader: `EnchantItemHPBonusData`, items/skills/HP/equipment, а не ручное дописывание отдельных HP factors;
- conflict/unknown provenance и динамические свойства — отдельные категории, без подмены UNKNOWN нулём.

**LIVE PRE-CUT** под реальным owned GameServer:

- свежие оригинальные acquired Player incarnations (не выдавать skills/items);
- фактическая готовность навыков, MP, disable, range, cooldown, target rules, current owner, LOS/path/height;
- live NPC identity/current position/AI/faction neighbors, Zone/Geo, aggro/pursuit и допускаемый drift;
- физические факты A/B, roster/Party, risk, slot/time window, отсутствие чужих/retained owners;
- полный штатный Party/Combat admission, не только helper estimate;
- если changed world/proposal: **revalidate→bounded replan→seal** ДО CUT, либо отказ с реальной причиной. После CUT ни перемещения NPC, ни HP/MP/RNG, ни conditions не подменять.

Инвариант: `headless.plan` и `live.admit` основаны на одном contract/schema. Отличающиеся runtime facts приводят к явному DIFF/PARITY_REJECT, а не к silent selection, изменению DATASET или неравному A/B.

### 3.3 Поиск должен быть источнико-ориентированным

Не перечислять миллионы координат ради совпадения.

Сначала обнаружить из GameServer/topology существующие допустимые **семейства NPC и реальные spawn/anchor/area**, доступные под фактические acquired capabilities и Party role mix. Только затем решать placement: безопасные текущие/live positions или native-обоснованные test-only экземпляры. Не помещать NPC в произвольные точки, не ослаблять faction/LOS/mob AI/rates. Построение snapshot из текущей World не считать стабильным; движущиеся/следующие/агрессивные NPC могут быть исключены или включены только при доказанной динамической модели.

Если нет пересечения capability×target×placement, вывести bounded **unsatisfiable report** с конкретными категориями: SOURCE_SKILLS, MAGIC_LEVEL, PARTY_ROLE, TARGET_LEVEL_REWARD, FACTION, WORLD_AI_MOVEMENT, GEO_LOS, TARGET_PLACEMENT, RISK, REQUEST_TIMEOUT, CAPACITY, FRESHNESS. Указать отдельно отсутствие кандидатов и ошибку реализации.

Обязательно диагностировать, существуют ли истинно lawful сценарии в данном TEST world для **Solo DAMAGE** и **Party HEAL/DAMAGE**. Не подбирать сцены по результату neural scores, исходу боя или reward.

### 3.4 Компактный переносимый протокол на будущее

Контракт должен уметь представлять: mode Solo/Party, action family, list of entities, roles, expected targets, source provenance, time/risk, skill/action fingerprints, runtime admission status и reasons. Явно зарезервировать расширение для PVP_1V1, PVP_PARTY, RAID_WITH_GUARDS, но **не писать фиктивные adapters и не заявлять поддержку** этих режимов до реальной реализации. Контракт не должен кодировать class94/103/110 и skill1230/1235 в алгоритме.

## 4. Проверка реального игрового результата

В рамках011 **не обучать модель**. Проверить возможность честно оценивать уже существующую frozen DRAFT14.

После зелёных дешёвых gates разрешён bounded native. Желательный фактический результат: **минимум4 завершённые matched пары: 2 Solo и2 Party**, с AB/BA внутри направлений, actual paid actions + terminal outcomes. Минимум Solo+Party и обе очередности обязательны для FULL. В первую очередь достаточность admission, а не статистическая победа модели. При малом n игровой verdict обязательно INCONCLUSIVE, если не доказано иное.

Для каждой пары: реальные разные actor/target incarnations; равные pre-CUT physical facts для stock/learned и отдельные native risk checks; fresh event IDs; без replay/recredit, forced damage, artificial actor invulnerability или изменения монстра после CUT. Если есть только Solo-пары — PARTIAL, не FULL.

Для каждого упавшего admission — reason, stage, source SHA/capability SHA, NPC/spawn/Geo facts, сам request, фаза до/после CUT и risk без секретов. После CUT не переразыгрывать reserved slots; при честном отказе расходовать denominator.

## 5. Бюджет и stop conditions

**Новый бюджет011, независимый от исторических:** максимум **3 запуска GameServer** и максимум **45 минут суммарного native wall time**, включая boot/drain/ошибки и exact kernel exit. Оставить 1 запуск в резерве на regression/pilot failure. Остановить native при исчерпании *любого* лимита. Не ставить цель израсходовать всё.

Перед первым GS: доказанный PASS всех доступных headless checks, правильная конфигурация/HP/runtime-request schema, минимум2 устойчивых candidate families Solo+Party и pinned frozen dataset. Если 0 lawful в headless — **GS0, BLOCKED_PRE-CUT**, а не новая попытка «может повезёт».

Внутри живой сессии разрешены bounded pre-CUT replanning при изменении NPC/world facts; **не** после CUT. Не запускать новый GS для каждого эпизода. Точная стоимость по startup/shutdown отдельно от encounter time. После исчерпания 2 запусков и без положительного ответа — третий не запускать автоматически, пока root cause не понятна.

При третьем независимом классе ошибки, требующем архитектурного изменения, или противоречии safety rules — зафиксировать BLOCKED/PARTIAL и запросить отдельное решение владельца, а не бесконечно расширять задачу. Не проводить суточный soak, full PVP или многотысячные игры.

## 6. Изменения и разрешённый scope

Прежде чем менять код, составь точный allowlist из существующих семейств:

- `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingScenarios.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingNative.java`;
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` при доказанном блокере;
- `test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java`;
- `java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingV1.java` при необходимости чистого контракта;
- `java/org/l2jmobius/gameserver/phantoms/learning/TrainingConfig.java` только для проверки общей schema;
- `tools/phantom-runtime/training/Training.ps1`, `TrainingOwner.ps1`, `Test-Training.ps1`, `AutonomousTraining.ini` если необходимы и подтверждены;
- `build.xml` только для focused tests;
- новые **минимальные** shared encounter contract/fixture classes в уже существующих `learning/` или `test/.../phantoms/`, если существующие типы заведомо не подходят;
- `docs/phantoms/COMBAT-AI-ROADMAP.md` и `docs/phantoms/tasks/<TASK>/...`, `docs/phantoms/reports/<TASK>.md`.

**НЕ менять** `Player`, `GeoEngine`, боевые skill rules, `PhantomCombatService`, `PhantomPartyTactics`, `PhantomPvpPolicy`, `PhantomRaidAttemptService`, gameplay rates, V3 neural architecture, optimizer, SQL schema, MAIN/PLAY без отдельного согласования. Уровень headless/native fixture должен переиспользовать production mechanics, а не менять их.

Точный список путей/стоимости зафиксировать в PLAN; если новая архитектура требует затронуть иные публичные подсистемы — объясни владельцу варианты и дождись решения. Не создавать ещё один persistent WIP branch. Никакого «все файлы, потому что удобно».

## 7. Проверки и G0–G8

См. `ACCEPTANCE.md` и `TEST_MATRIX.md`. Особенно важны:

- Буквальная воспроизводимость отказов 010 на старом sealed evidence без переписывания истории.
- RED→GREEN паритет конфигурации, actual skills, target-bound vs bounded paging, moving vs stationary NPC, live faction/Geo, request window и physical A/B.
- Нулевая возможность заявить `lawful=PASS`, если реальный request constructor заведомо упадёт или персонаж не умеет применять stock candidate.
- Дешёвый verified readiness и минимум2 независимых Solo/Party источника до GS, а не «все классы гарантированно готовы».
- Существующие 007/008/009/010 targeted regressions и `ant jar`; DB-free и PS checks последовательно.
- Frozen SQL evidence, STOPPED, ports free, TRAIN OFF, foreign SQL untouched.
- GitHub feature HEAD/diff/readback независимо от сообщений Codex.

`FULL011` только если все G0–G8 выполнены, есть реальные завершённые Solo/Party A/B и доказанная безопасность. Если нет — честный PARTIAL или BLOCKED, без увеличения числа задач и без выдачи native proof за headless.

## 8. Порядок работы

1. Read-first/Git/TEST owner/lineage (без boot).
2. Краткая diagnosis matrix по010 + обязательный PLAN scope и нетехнические неоднозначности. Все рутины выполнять автономно.
3. OFFLINE RED negative fixtures по известным010 отказам.
4. Shared contract без hardcode class/skill; согласованная config/bootstrap parity.
5. Bounded source→target→placement search и explicit unsatisfiable evidence.
6. Headless/live shared admission source, canBuildCombatRequest, STOP/cancel handling.
7. Focused green tests, regression старых flows.
8. Если реальные lawful families предварительно доказаны, один supervised native GS pilot, затем при необходимости второй для full Solo+Party A/B. Не расходовать весь бюджет при отказе.
9. Frozen SQL/CPU/disk/actual native receipts; итоги и сравнение.
10. Shutdown postflight, scoped commit/push напрямую в feature; independent GitHub readback.

## 9. Отчёт и счётчик

Создай в `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-011-LAWFUL-ENCOUNTER-ENGINE/`:

`PLAN.md`, `PROGRESS.md`, `RESULT.md`, `EVIDENCE.json`, `COMMANDS.md`, `ACCEPTANCE.md`, `HANDOFF.md`, `SCENARIO_COVERAGE.md`, `FAILURES.md` (текущие выданные `TASK.md`, `ARCHITECTURE.md`, `TEST_MATRIX.md` не удалять).

Общий русский отчёт `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-LEARNING-011-LAWFUL-ENCOUNTER-ENGINE.md`.

Отдельно различить `DISCOVERED`, `PREPARED`, `LIVE_ADMITTED`, `CUT`, `PAID`, `TERMINAL`, `MATCHED_PAIR`, `TRAIN_CREDIT`, `FIT`, `PROMOTION`. Здесь TRAIN_CREDIT=0, FIT=0, PROMOTION=0.

Обязательно: все отказавшие сцены, бюджет всех запусков, GitHub hashes, real models, cost, performance, выводы и пределы обобщения. Проверка mojibake markers и escaped Cyrillic отдельно.

Счётчик: 007№1,008№2,009№3,010№4,011№5 из13; **после выдачи011 остаётся8** до обязательного пересмотра. PARTIAL и retry `/goal` расходуют бюджет. Новые задачи автоматически не начинать.
