# L2-QOL-009 — усиление боя призывателя и Servitor

Статус: `SUCCESS` для реализации и полного delivery verify. Baseline fresh full `ant verify` остановился на combat integration 19/20, первое явно разрешённое повторение — на историческом `qol-shop.05` 7/8, второе явно разрешённое повторение — на QOL-009 `after-all` future 8/9. После bounded test-only fixture fixes четвёртый, отдельно разрешённый fresh full `ant verify` завершился `BUILD SUCCESSFUL` (39 min 13 s), включая QOL-009 8/8 и historical shop 8/8. Exact commit/push SHA проверяется в итоговой передаче.

## Исходная точка и границы

- Репозиторий: `C:\Users\ZBook\L2J_Mobius`.
- Продуктовый scope: только `L2J_Mobius_CT_2.6_HighFive`.
- Ветка: `feature/phantom-world`.
- Точный parent до начала изменений: `b6c609e270984d8ae6a3b14a8e7ca6acf642f564` (`qol(008): close economy and progression gaps`).
- Разрешён только один итоговый commit с subject `qol(009): harden summoner servitor combat` и обычный non-force push.
- Production DB, `prepare-phantom-test-db`, client patch, прямое создание Servitor, бесплатные ресурсы, reset reuse, teleport/path bypass и ручная правка PvP/PK/karma запрещены.
- До начала работы сохранены и исключены из commit три пользовательских tracked-изменения: `PhantomMaterializationService.java`, `PhantomClanDirectiveIntegrationGoal030C2ASuite.java`, `PhantomMultipartyEconomySuite.java`; существующие untracked artifacts также не входят в scope.

## Mandatory read-first census

Census завершён до первой правки production-кода.

Прочитаны:

- `Agents.md`, корневой `README.md`, module `readme.txt`;
- `docs/phantoms/PHANTOM_WORLD_MASTER_PLAN_RU.md`, workflow/task-package contracts и актуальные QoL/Goal039 handoff/freeze документы;
- весь пакет `docs/personal-qol/tasks/009-summoner-servitor-combat-hardening`;
- `docs/personal-qol/ROADMAP.md`, `CURRENT_STATUS.md`, `OPERATOR_GUIDE_RU.md`, отчёт QOL-008;
- `build.xml`, `PhantomTestLauncher` и ближайшие combat/PvP/progression/QoL test suites;
- stock `RequestActionUse`, `Summon`, `Servitor`, `Pet`, `BabyPet`, `SummonAI`, summon effect/condition handlers;
- `Player`/`Creature` native cast/PvP seams, `PetSkillData`, `NpcData`, `NpcTemplate`, `StatSet`;
- progression model/catalog/backend/parser, class trees Warlock/Arcana Lord/Elemental Summoner/Elemental Master/Phantom Summoner/Spectral Master, summon-skill XML и cat NPC/skill XML;
- `PhantomCombatBackend`, `PhantomCombatActorLease`, `L2jCombatBackend`, `PhantomCombatService`, `PhantomCombatSession`, combat step handlers;
- `PhantomPvpContext`, `PhantomPvpPolicy`, `PhantomPvpService`, PvP models/requests и materialization lifecycle wiring.

Дополнительный `AGENTS.md`, code-map или отдельный pattern-файл внутри продуктового дерева не найден; повторный поиск не выполнялся.

Найденные локальные аналоги:

1. Stock human Servitor command: action 22 в `RequestActionUse` проверяет Servitor, вызывает `Summon.canAttack(...)`, затем `Summon.doSummonAttack(...)`; движение и путь остаются у `SummonAI`.
2. Active Servitor skill: штатные helpers `RequestActionUse.useSkill(...)` берут skill из live NPC parameters/`PetSkillData` и вызывают `Summon.useMagic(...)`.
3. Phantom ownership: один общий worker и `PhantomCombatActorLease` в `PhantomCombatService`/`L2jCombatBackend`, с cleanup через `cancelOwnedAction(...)`.
4. Canonical summon facts: `L2jProgressionBackend` строит `SummonActorFact` из class skill trees, summon skill effects и NPC templates; `ActorKind` отдельно различает `SERVITOR`, `PET` и `BABY_PET`.
5. Deterministic regression style: `PhantomPvpCorrectiveSuite`, `PhantomCombatServerIntegrationSuite`, `PhantomProgressionCatalogSuite`, `QoLEconomyProgressionClosureSuite`.

Переиспользуемый паттерн:

- координация остаётся внутри существующего combat lease и shared worker, без отдельного scheduler/timer;
- перед каждым действием заново разрешаются Player, его текущий Summon, owner identity, instance, live/dead/spawned state и цель;
- атака, follow/return, resummon и active skill идут только через штатные `Summon`/`Player` APIs;
- enemy owner Player остаётся canonical PvP/consequence context, Servitor может быть только transient linked tactical subtarget;
- ordinary Pet/BabyPet не получает ни одного нового control path.

## Классификация исходного состояния

### `ALREADY_NATIVE`

- Human Servitor attack/follow/stop и active-skill execution уже принадлежат `RequestActionUse` + `Summon` + `SummonAI`.
- `SummonAI.onIntentionAttack` проверяет путь от координат самого Summon к target через `PathFinding`; owner LoS не является условием продолжения ATTACK.
- `Summon.useMagic` уже проверяет live owner, casting, target, reuse, HP/MP, path, peace/Olympiad/siege/attackability и передаёт исполнение AI.
- Summon effect handler создаёт `Servitor` только после штатной проверки summon skill; lifetime/upkeep и unsummon принадлежат `Servitor`.
- `Player.useMagic`/`Creature.doCast` сохраняют штатные skill condition, item/MP/reuse/zone/PvP semantics.

### `ALREADY_PRESENT`

- Phantom combat уже использует один bounded worker, action lease, exact target re-resolution и owned cleanup.
- Progression catalog уже знает summoner classes 14/28/41/96/104/111 и отделяет true Servitor от Pet/BabyPet.
- Warlock class tree содержит cat summons 1111/1225/1276/1331; Arcana Lord — Feline King 1406.
- Feline King live NPC parameters содержат native active skills 5135/5136/5137.
- Phantom PvP уже хранит owner Player как `Counterpart` и получает PvP consequences только по Player.

### `CHANGE_REQUIRED`

- Добавить в существующий combat lease bounded true-Servitor coordination: native resummon, target sync/retarget, no-spam, active skill и cleanup follow/return.
- Добавить linked hostile Servitor observation/selection и action routing, не меняя canonical owner Player context.
- Добавить QOL-009 focused suite и Ant route с explicit Warlock/Arcana/cat, Pet exclusion, resummon/active-skill, PvP owner/Servitor selection, lifecycle и stock owner-loses-LoS/path evidence.
- Добавить affected/QOL-001..008/Goal039 freeze route и итоговую документацию.

### `OUT_OF_SCOPE`

- Любая перепись `RequestActionUse`, `Summon`, `SummonAI`, stock Pet/BabyPet, geodata/pathfinding, summon lifetime/upkeep.
- Production DB/schema/provisioning, client changes, economy/progression redesign, unrelated lifecycle, siege/party/acquisition redesign.
- Реальные внешние providers/engines, новый per-actor scheduler или persistence для tactical subtarget.

## Реализация

- `PhantomCombatBackend` получил immutable linked-Servitor snapshot и отдельный transient tactical target, а owner `PvpTargetSnapshot` — факты invulnerable/reachable. Совместимый конструктор сохранён для существующих тестов.
- `PhantomCombatActorLease` сохранил прежние Player-only entrypoints и добавил overload owner+tactical для normal PvP.
- `PhantomCombatService` на каждом normal-PvP действии заново получает linked snapshot и выбирает owner либо Servitor; session/admission/consequence request продолжает хранить owner Player.
- `L2jCombatBackend` координирует только exact live owned `Servitor`, повторно проверяя owner identity, instance, world identity, spawned/dead state и target. PvE, raid и normal PvP используют тот же bounded seam; cleanup вызывает native cancel/follow.
- Legal resummon выбирается только из canonical `PhantomProgressionCatalog`/`SummonActorFact` для текущего summoner class и известного skill level. Перед `Player.useMagic` явно переиспользуется stock `ConditionPlayerCanSummon(true)`: общий `Skill.checkCondition` намеренно пропускает conditions для fake players, поэтому этот guard сохраняет spawn/teleport protection, stored/live summon/pet и mounted/flying semantics. Resource, MP, reuse и summon effect остаются у native cast.
- Active Servitor skills обнаруживаются только в live NPC template parameters, фильтруются до active damage/debuff skills, детерминированно ранжируются и вызываются через `Servitor.useMagic`; неуспех даёт stock `doSummonAttack`, а bounded lease throttle не допускает spam.
- Linked enemy Servitor не заменяет owner: fresh action routing допускает только exact `Servitor` текущего hostile Player в том же legal context; Pet/BabyPet, allied, dead, stale, wrong-instance и incompatible candidates не проходят.
- Stock `RequestActionUse`, `Summon`, `Servitor`, `SummonAI`, `Pet`, `BabyPet`, geodata/pathfinding и summon effect handlers не изменялись.

## Изменённые файлы и bounded exception

Изменены 11 файлов QOL-009 feature family и один явно разрешённый test-only historical gate fixture:

1. `build.xml`;
2. `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java`;
3. `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java`;
4. `java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java`;
5. `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java`;
6. `test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java`;
7. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`;
8. `docs/personal-qol/ROADMAP.md`;
9. `docs/personal-qol/CURRENT_STATUS.md`;
10. `docs/personal-qol/OPERATOR_GUIDE_RU.md`;
11. `docs/personal-qol/reports/009-summoner-servitor-combat-hardening.md`.
12. `test/java/org/l2jmobius/gameserver/qol/QoLShopSuite.java` — только socket-backed fixture detach/invariant, без QOL-001 product/economy изменений.

Bounded exception к ориентиру 8–10 файлов обоснован обязательным сквозным contract: четыре существующих combat seams, один существующий integration suite/launcher, Ant wiring и четыре требуемых task-документа. Двенадцатый файл — отдельно разрешённое пользователем точечное исправление исторического test-only fixture, обнаруженного в full gate. Product economy/progression, schema, config, scheduler или новый слой абстракции не менялись.

## Focused evidence

Последний `ant -q qol-summoner-servitor-combat-test` — `BUILD SUCCESSFUL`, 8/8:

- stock human cat command/path ownership и canonical summoner census;
- Warlock skill `1225/18`: native summon attempt, exact Spirit Ore/MP/reuse debit, true Servitor, target sync, no-spam, retarget, no-target follow и cleanup;
- owner-LoS source ownership + reachable summon path/ATTACK и native rejection z-unreachable target;
- missing reagent, zero MP, active reuse, teleport protection и existing-Servitor guards без free resource/duplicate;
- Arcana Lord skill `1406/1`, live Feline King parameters `5135/5136/5137`, native Slash `5135`, anti-spam и MP/reuse attack fallback;
- Pet/BabyPet/dead/stale/wrong-instance/same-party/incompatible exclusions;
- owner-winning и Servitor-winning pure PvP policy;
- live exact hostile Servitor threat, fresh linked observation, native tactical attack, unchanged owner consequences, no karma mutation и cleanup.

Детерминированный seed: `1009001`. Allowlisted schema из отчёта: `l2jmobiush5_phantom_test`; schema aggregate SHA-256: `394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E`.

## Preflight и freeze

- `ant -q compile-tests` — PASS; остаются два существующих JDK removal-warning для `System.runFinalization()` в Goal029 tests.
- `ant -q qol-summoner-servitor-combat-test` — последний PASS, 8/8, 36 s после исправления test-owned class-refresh timing. До этого targeted suite повторил третий full failure на `after-all` future, хотя все 8 сценариев прошли. Предыдущие focused-итерации выявили и исправили fake-player summon-condition bypass и test-only `setXYZInvisible` lifecycle; production stock summon path не менялся.
- `ant -q qol-009-affected-test` — последний PASS, 2 min 32 s после условного class-reset test fixture fix. Aggregate включает combat ownership/server integration, Goal025 PvP, Goal026 CP5 raid combat, progression, materialization и shutdown. Встроенные Goal026 raid contract/dynamic suites прошли 4/4 + 4/4. Первый preflight остановился на историческом Goal025 exact-working-tree scope guard; QOL-009 wiring исправлен на динамические Goal025 focused+affected suites, сам исторический verifier не изменён.
- `ant -q qol-009-freeze-test` — последний PASS, 17 min 13 s после shop fixture fix: QOL-001…008 и Goal039 static/documentation freeze. Позднейшая QOL-009 class-refresh fixture правка не затрагивает эти historical routes.
- Первый fresh full `ant verify` — **FAIL**, 4 min 32 s: `PhantomCombatServerIntegrationSuite` 19/20, test 02 `Victory cleanup retained the exact dead combat target.` Все suite до этого места прошли.
- Диагноз: `PhantomCombatService.terminalLocked(...)` публикует terminal result до асинхронного `attemptCleanup(...)`, тогда как старая проверка читала `_player.getTarget()` сразу после terminal snapshot. Production cleanup не менялся; проверка переведена на существующий bounded `await(...)`.
- После стабилизации `ant -q phantom-combat-server-integration-test` — PASS, 1 min 5 s; повторный focused QOL-009 — PASS, 8/8, 59 s.
- Пользователь явно разрешил **один** повторный fresh `ant verify`; второй запуск — **FAIL**, 31 min 45 s. Прежний combat integration suite прошёл; исторический `qol-shop.05-community-board-combat-and-store-controls` получил Adena `0` вместо `10000`, summary `qol-shop` 7/8. Все завершённые suites до этого места прошли.
- Диагностический `ant -q qol-shop-test` после второго full failure — **PASS**, 37 s, 8/8, включая test 05. Read-first выявил точный fixture contract defect: прежний socket-backed `NetworkBackedClient.close()` закрывал async connection, но оставлял `GameClient._player`; поздний `ReadHandler.failed` мог вызвать `GameClient.onDisconnection` → `Disconnection.of(oldClient)` → cleanup Player после нового attach. Ближайший `QoLTestClient` уже очищал ссылку до socket close. Shop fixture теперь очищает старую ссылку и проверяет exact old/new session ownership. После fix `ant -q qol-shop-test` — PASS, 33 s, 8/8; historical freeze — PASS. Точный timing потери Adena во втором full run не доказан, но полный shop outcome после fix теперь доказан четвёртым full run (8/8). QOL-001 product/economy не менялись.
- После отдельного разрешения пользователя на test-only diagnosis/fix и ещё один fresh full run третий `ant verify` — **FAIL**, 34 min 1 s. `combat-server-integration` прошёл 20/20, QOL-009 все 8 сценариев PASS, но `qol-summoner-servitor-combat.after-all` увидел live `Player._skillListTask`; summary QOL-009 8/9. Следующий historical shop gate этот full run не достиг.
- Целевой `ant -q qol-summoner-servitor-combat-test` непосредственно после третьего full failure воспроизвёл тот же `after-all` FAIL за 40 s. Source diagnosis: test-owned `prepareSummoner/resetActor` вызывает stock `Player.setPlayerClass`; тот запускает untracked 100-ms `applyItemSkills/sendSkillList`, затем native `sendSkillList` создаёт 300-ms `_skillListTask`. При быстром test teardown delayed refresh может прийти после штатных `stopAllTasks` в `PhantomMaterializedPlayer.cleanup`. Test fixture убрал повторный same-class reset и ждёт bounded завершения one-shot refresh **до** shutdown; строгий `assertClean` после shutdown сохранён. Production `Player`/materialization lifecycle не изменялся. Последующий QOL-009 focused и affected PASS; четвёртый full run подтвердил отсутствие `after-all` failure.
- Четвёртый отдельно разрешённый fresh full `ant verify` после обоих bounded test-only fixes — **PASS**, `BUILD SUCCESSFUL`, exit 0, 39 min 13 s. В durable reports QOL-009 все 8 сценариев PASS, historical QOLShop все 8 PASS, combat-server-integration 20/20 PASS. Это обязательный полный delivery gate, а не targeted substitute.
- Standalone `ant -q jar` после успешного четвёртого full verify — PASS, 20 s; `jar tf ..\build\dist\libs\GameServer.jar` и `jar tf ..\build\dist\libs\LoginServer.jar` — оба PASS (exit 0). Предыдущая проверка standalone jars после третьего full failure также прошла, но только текущий full run даёт delivery evidence.
- Первый sandbox-запуск целевого `ant -q phantom-combat-server-integration-test` остановился на известном `JDK 25 zipfs AccessDeniedException` при закрытии `dist/libs/HikariCP-7.0.2.jar`; та же команда вне sandbox завершилась PASS. Ошибка компилятора не интерпретировалась как product test failure.
- Ошибочный alias `ant -q qol-009-focused-test` завершился `Target ... does not exist`; корректный declared target `ant -q qol-summoner-servitor-combat-test` после этого завершился PASS.
- `git diff --check -- L2J_Mobius_CT_2.6_HighFive` — PASS; остаются только информационные line-ending warnings Git для существующего Windows working-tree режима.
- После обновления SUCCESS-документов `ant -q phantom-full-vision-goal039-documentation-test` — последний PASS, 26 s. Предыдущий запуск после третьего full failure также прошёл.
- Отдельный `ant -q phantom-combat-performance-smoke` — PASS, 37 s: 10 000 sessions, 100 000 pulses, `maximumWorkers=1`, `actorLeasesAfterRun=0`, cleanup/dispatch failures `0`. Этот targeted smoke не выдаётся за замену full verify.
- Production added-line scan: `prepare-phantom-test-db`, `teleToLocation`, прямые `new Servitor/Pet/BabyPet`, `setKarma/setPvpKills/setPkKills`, reuse enable/reset — `0` совпадений. Три `enableSkill(...)` есть только в test fixture cleanup/setup.
- Mojibake-маркеры в изменённых файлах проверены отдельно после обновления SUCCESS-документов: `0` совпадений.
- Escaped Cyrillic / XML escaped Cyrillic в изменённых файлах проверены отдельно после обновления SUCCESS-документов: `0` совпадений.
- Strict UTF-8 scan изменённых текстовых файлов: `0` invalid files.
- Control-character scan изменённых файлов: `0` совпадений.

## Completion audit по ACCEPTANCE.md

- Пункты 1–2 и 5–42, 44–48 имеют current source/live/test/document evidence, перечисленный выше. Для пункта 8 используется прямо разрешённый task-пакетом fallback: exact source ownership плюс strongest deterministic summon-to-target path/intention test и явно записанное ограничение загруженной geodata.
- Для пункта 18 canonical skills `1225` и `1406` не объявляют отдельного zone condition в H5 XML: их explicit condition — `player canSummon="true"`. Поэтому тест не выдумывает несуществующий zone denial; production вызывает штатный `Player.useMagic`, а значит сохраняет все реально объявленные skill и generic cast/transport/zone checks. Детерминированно проверены reagent, MP, reuse, teleport-protected illegal state и existing-summon denial.
- Пункт 35 теперь дополнительно защищён существующими Goal026 CP5 raid contract/dynamic suites внутри affected aggregate; production `attackRaid/castRaid` используют тот же bounded `coordinateServitor(...)`, что live PvE scenario.
- Пункт 3 **PENDING_AT_REPORT_WRITE**: ровно один final commit выполняется после финального scope/staged-diff guard; его SHA фиксируется в итоговой передаче, не в дополнительном коммите.
- Пункт 4 **PENDING_AT_REPORT_WRITE**: normal non-force push и remote-SHA comparison выполняются после commit; результат фиксируется в итоговой передаче.
- Пункт 43 **ACHIEVED**: четвёртый отдельно разрешённый fresh full `ant verify` завершился `BUILD SUCCESSFUL` (exit 0, 39 min 13 s). Первые три FAIL сохранены как диагностическая история, но не выдаются за PASS.

## Ограничения доказательства

- Загруженная test geodata не дала детерминированной blocking-wall точки: `loadedGeodataOwnerStillVisible=true`. Поэтому owner-loses-LoS acceptance закрыт разрешённым strongest deterministic evidence: exact source audit доказывает отсутствие owner-LoS query в `SummonAI`, runtime подтверждает path от координат Servitor, ATTACK на reachable target и rejection unreachable target. Через стену движение не эмулировалось и geodata не обходилась.
- Headless scheduler не гарантирует стабильное полное завершение длинного summon cast. Fixture поэтому сначала доказывает canonical `Player.useMagic` CAST/currentSkill и для Warlock точный native resource/reuse debit, затем materializes Servitor штатным загруженным `Summon` effect для дальнейших live проверок. Прямой `new Servitor` не используется.
- Live H5 client/UI не запускался: `NOT_TESTED_CLIENT_UI`. Client patch не нужен и не применялся.

## Запрещённые поверхности

- `prepare-phantom-test-db`: **NOT_RUN**.
- Production DB: **NOT_USED**.
- Client patch: **NOT_USED**.
- External provider/runtime LLM: **NOT_USED**.
- Destructive Git/force push: **NOT_USED**.

## Git и post-commit evidence

До реализации выполнен разрешённый bounded Git census для exact parent/branch/dirty-scope; пользовательские изменения исключены из staging. После отдельно разрешённого четвёртого fresh full `ant verify` PASS разрешено завершить delivery одним commit с subject `qol(009): harden summoner servitor combat`, normal non-force push и проверкой remote SHA == local HEAD. Эти post-commit значения нельзя записать внутрь самого единственного коммита без дополнительного изменения истории; они будут приведены в итоговой передаче. Исторический economy product scope не расширялся ради обхода gate.

Final read-only Git preflight: `git branch --show-current`, `git rev-parse HEAD`, `git diff --name-only -- L2J_Mobius_CT_2.6_HighFive`, `git status --short -- L2J_Mobius_CT_2.6_HighFive`, `git diff --check -- L2J_Mobius_CT_2.6_HighFive`, `git ls-remote origin refs/heads/feature/phantom-world`. Branch `feature/phantom-world`, local HEAD и remote branch указывают на точный required parent `b6c609e270984d8ae6a3b14a8e7ca6acf642f564`; diff-check exit 0. Tracked task diff ограничен одиннадцатью QOL-009/test-only fixture файлами, двенадцатый — новый evidence report. Три исходных пользовательских tracked edits и множество исходных untracked task/history files не входят в planned staging.
