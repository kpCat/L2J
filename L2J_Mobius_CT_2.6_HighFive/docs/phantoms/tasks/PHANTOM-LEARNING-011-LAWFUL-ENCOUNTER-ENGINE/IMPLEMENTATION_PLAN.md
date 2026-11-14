# План исполнения011

Дата: 14.11.2026. Baseline GitHub/source004/TEST006: `f153d137a7d666ea0c28586a45a429a94a9d1c44`, feature/phantom-world. Основной dirty checkout защищён, его HEAD `7e997636dbac9d5e1754e00c793cd6fac858b0c6` не меняется.

Исходный пакет найден в корневом `docs/phantoms/tasks/`, а не по указанному module path. Девять файлов побайтово скопированы в этот каталог source004; SHA-256 совпали. Исходные TASK/ARCHITECTURE/PLAN/TEST_MATRIX/ACCEPTANCE/HANDOFF сохраняются. Результаты исполнения добавляются отдельно, исходные ограничения не переписываются задним числом.

## Прочитано и переиспользовано

Корневые AGENTS/README; module Agents, PHANTOM_CURRENT_STATUS, CODEX_WORKFLOW_CONTRACT, TASK_PACKAGE_STANDARD; семь документов011 и RUNBOOK; корневые RESULT/EVAL-REPAIR010; build.xml; autonomous/scenario/native fixtures; full acquired inventory; canonical Combat request/service/backend и Party coordinator/tactics.

Локальные аналоги: ManualDispatcher в PhantomCombatCoreSuite, acquiredSoloRequest, requestAutonomousFarm, phaseConfiguration, существующие source-contract/AutonomousTrainingChecks. Отдельных AGENTS в целевых java/test/docs подкаталогах не найдено. Устаревшие абсолютные пути Agents не используются. Новый clone/worktree/branch не создаётся.

## Exact source allowlist

Все пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`:

1. `java/org/l2jmobius/gameserver/phantoms/learning/EncounterAdmissionV1.java` — versioned facts/proposal/receipt, различение headless/live и parity.
2. `test/java/org/l2jmobius/tests/phantoms/PhantomEncounterDispatchGate.java` — TEST worker gate через существующий Dispatcher.
3. `test/java/org/l2jmobius/tests/phantoms/PhantomEncounterDispatchGateChecks.java` — focused lifecycle checks.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` — доказанный blocker: реальная reservation до CUT, отмена и release после baseline evidence.
5. `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` — shared contract/pinned preparation, native admission и rich negative receipts.
6. `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingScenarios.java` — capability-before-placement, только native current/spawn points и полный faction census перед CUT.
7. `test/java/org/l2jmobius/gameserver/phantoms/learning/AutonomousTrainingChecks.java` — protocol/stage/timeout/parity focused checks.
8. `build.xml` — только focused DB-free target011.

Documentation allowlist: данный task directory (девять исходных файлов, IMPLEMENTATION_PLAN/PROGRESS/RESULT/EVIDENCE/COMMANDS/SCENARIO_COVERAGE/FAILURES, bounded logs/receipts/scope guard), `docs/phantoms/reports/PHANTOM-LEARNING-011-LAWFUL-ENCOUNTER-ENGINE.md` и `docs/phantoms/COMBAT-AI-ROADMAP.md` внутри модуля. Более десяти итоговых файлов — bounded exception одного семейства Encounter/Admission/evidence, не несколько подсистем. Runtime private state не публикуется.

## Шаги и gates

- [x] Read-only Git/owner/OFF/ports/history/cold export census, без GS/SQL.
- [x] Диагностика010: request после CUT; headless catalog не потребляется live; capability×NPC level и topology/faction placement.
- [x] Baseline Ant autonomous/008/007 — exit0.
- [x] Tests-first gate/protocol compile RED; отдельно behavioral lifecycle RED retained native claim.
- [x] GREEN protocol/lifecycle/source-contract и targeted regression/JAR.
- [x] Новый отдельный private campaign011 с 3 starts/45min; исторические009/010 сохранены.
- [x] Canonical headless prepare и frozen comparison выполнены;4preliminary PartyHEAL/SoloDAMAGE0, required coverage FAIL.
- [ ] Только при доказанных Solo+Party families — bounded GS pilot и собственные matched пары011. При отсутствии сцен GS0.
- [x] STOP/OFF/ports/cold frozen readback, scope/encoding, scoped publication и independent GitHub readbackb12d943d729; G8PASS. Native pilot NOT_RUN, manual gate REQUIRED.

Два прямых причинных направления: единый admission lifecycle и source-bound preparation/parity. Production Combat/Party/Player/Geo mechanics, optimizer/model/schema/rates не меняются. Модель DRAFT14/Adam903/223/apps48, TRAIN_CREDIT/FIT/PROMOTION011=0. Native budget011 ≤3/2700000ms; один старт в резерве, без автоматического третьего старта при непонятном отказе.

## Текущие непроверенные риски

Новые source changes ещё не доказывают lawful TEST world. Headless stage не является live/game outcome. Full support действия исполняются только после CUT обычным owner; до CUT native Party opening резервируется узким requestAutonomousFarm без tactical pulse. Worker gate не считается общим перехватчиком support. Нужны фактические headless/live parity и freshness, cancel/STOP, SQL183 readback; отсутствие73–74 topology/placement может дать честный PARTIAL с GS0.
