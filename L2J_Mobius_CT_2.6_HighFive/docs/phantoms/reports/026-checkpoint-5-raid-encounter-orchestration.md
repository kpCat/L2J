# Goal 026 Checkpoint 5 — raid encounter orchestration

## Status

`PARTIAL`

Continuation выполнялся от required parent `a44421c1cec30e027aeb33e5588fb00373e30f1b` на ветке `feature/phantom-world`. Первый automatic context compaction сработал до завершения remaining vertical slice; согласно прямому указанию TASK новое discovery и дальнейшая реализация остановлены. Оставлен только безопасный компилируемый PARTIAL, Goal 026 overall остаётся `IN_PROGRESS`, success token не выдаётся.

`occurred_context_compaction: yes`

## Summary

Сохранён additive raid Combat из parent и закрыта review-находка по combat-level death: `VICTORY` теперь допускается только после exact совпадения `objectId + npcId + ContentKind/NpcKind + instanceId`. Добавлен exact live/dead authority evidence seam.

Добавлены ограниченные encounter contracts для Queen Ant `epic.29001` и Zaken83 `epic.zaken.83`, typed `ENTRY_GATED`, exact entry NPC locator через `SpawnTable`, staging от entry NPC, bounded reload-safe script registry и Zaken83 adapter, который переиспользует те же `checkConditions` и `InstanceScript.enterInstance`, а candle interaction физически делегирует существующему `onFirstTalk`. Canonical native reward/finish остаются в script `onKill`; Phantom custom loot/reward не добавлен.

Полный orchestration не завершён: `PhantomRaidAttemptService` после compaction-stop не оставлен, `attemptAuthorityHash` ещё не mint/owned exact AttemptService evidence, `PhantomRaidDecision` и production wiring не изменены. ENTRY_GATED readiness integration была точечно снята после affected CP1 regression; новые profile/staging/script contracts компилируются, но production attempt пока их не вызывает.

## Mandatory reading and reuse

Перед изменениями были прочитаны только completion `TASK.md` и его `PRIOR_INDEPENDENT_REVIEW.md`, `TEST_CASES.md`, `PACKAGE_MANIFEST.json`, `CODEX_LAUNCHER.txt`, а затем bounded affected fragments. Whole Combat, whole Party, historical task chain и запрещённые broad areas повторно не читались.

Переиспользованы локальные паттерны:

- существующий bounded shared `PhantomCombatService`, а не второй combat engine;
- `PhantomRaidAssemblyService` и существующий `PhantomPartyRouteCoordinator` для per-Party staging contracts;
- canonical Zaken83 `checkConditions`, `enterInstance`, `onFirstTalk` и `onKill`;
- `SpawnTable.getSpawns(exactNpcId)` для exact entry NPC;
- `PhantomRaidAuthority` для read-only canonical boss evidence.

Непроверенным остаётся end-to-end attempt/Decision lifecycle, потому что его реализация остановлена обязательным compaction gate.

## Changed files

Production/data:

- `dist/game/data/phantoms/knowledge/high-five-core-v1.xml`
- `dist/game/data/scripts/instances/CavernOfThePirateCaptain/CavernOfThePirateCaptain.java`
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java`
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java`
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomRaidCombatRequest.java`
- `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java`
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/L2jPhantomRaidAuthority.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAssemblyService.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAuthority.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidEncounterCatalog.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidEncounterProfile.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidEntryNpcLocator.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidModel.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidScriptAdapter.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidScriptRegistry.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidTargetEvidence.java`

Focused verification/documentation:

- `test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeContentSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidCombatGoal026Checkpoint5Suite.java`
- этот отчёт;
- completion task package `docs/phantoms/tasks/026-checkpoint-5-completion/`.

User-owned untracked `docs/phantoms/tasks/026-checkpoint-2-command-channel-lifecycle/CODEX_LAUNCHER.txt` и `PACKAGE_MANIFEST.json` не изменялись и не включаются в commit.

## Architecture decisions

- Dead raid snapshot не является победой без exact request identity; wrong object/NPC/kind/instance остаётся `TARGET_LOST`.
- `targetInstanceId` входит в raid request и idempotent operation identity; `matchesRaidSession` также требует exact generation, target identity и authority hash.
- `attemptAuthorityHash` остаётся opaque Combat input. Без завершённого AttemptService он не считается canonical attempt authority — это явный незакрытый acceptance criterion.
- Encounter catalog разрешает generic `RAID`, Queen Ant `29001` и Zaken83 `29181`; другие EPIC fail closed.
- `ENTRY_GATED` добавлен в модель и CP4 staging contract, но не включён в CP1 readiness до появления полного attempt owner.
- Zaken adapter не раскрывает hidden `isBlue`; public candle evidence содержит только object/position/used. Physical interaction проверяет exact actor, instance, live unused candle и дистанцию, затем вызывает тот же `onFirstTalk`.
- Script death evidence записывается до существующего native reward/finish и bounded до 256 instance entries.
- Новые worker/thread/scheduler/DB query/custom reward не добавлены.

## DB, migrations and configs

DB, migrations, schema, production DB и config keys не менялись.

## Commands and test results

Read-only Git/scope commands включали `git status --short --branch`, `git rev-parse`, `git branch --show-current`, bounded `git diff`, `git diff --stat`, `git diff --name-status` и финальные scope checks. Git mutation до delivery не выполнялась.

Verification:

- `ant ...focused targets...`: не стартовал, потому что `ant` отсутствует в `PATH`.
- Первый local-Ant focused run: raid Combat 4/4 PASS; initial CP1 дал 4/6 из-за незавершённой readiness integration. После её rollback capability regression устранена; повторный CP1 дал 5/6, единственный FAIL — legacy static boundary `no-orchestration` видит `Navigation` в новых поздних raid contracts.
- `.phantom-local/apache-ant-1.10.17/bin/ant.bat phantom-raid-recruitment-checkpoint3-test phantom-raid-assembly-checkpoint4-test phantom-combat-core-test phantom-game-knowledge-content-test`: `BUILD SUCCESSFUL`; CP3 9/9 + current-capability 5/5; CP4 3/3 + 5/5 + 4/4, affected command-channel 7/7 и raid-authority 4/4; Combat core 50/50; GameKnowledge 18/18.
- Единственный финальный `.phantom-local/apache-ant-1.10.17/bin/ant.bat phantom-raid-encounter-goal026cp5-test`: `BUILD SUCCESSFUL`, raid Combat 4/4, seed `26002651`. Текущий aggregate покрывает только inherited raid Combat и не доказывает отсутствующие attempt/Decision acceptance tests.
- Единственный `.phantom-local/apache-ant-1.10.17/bin/ant.bat jar`: `BUILD SUCCESSFUL`; собраны и скопированы штатные server JAR.
- Один targeted `javac -source 25 -target 25 ... CavernOfThePirateCaptain.java`: PASS; изолированный временный output удалён.
- Plain `git diff --cached --check` отметил CR в новых строках Zaken script как trailing whitespace. Byte-аудит подтвердил единый исходный стиль `CRLF=681`, `CRCRLF=0`, `bare LF=0`; реальных trailing spaces нет. Command-scoped `git -c core.whitespace=cr-at-eol diff --cached --check`: PASS, repository config не менялся.
- Negative static gates: в добавленных строках Zaken нет hidden `isBlue`, custom reward/finish/death mutation; в core contracts нет direct script/reward mutation. Exact Combat death identity и authority death match присутствуют.

Не запускались plain `ant verify`, Goal025, broad Goal017, all-Combat, all-Phantom, unrelated economy/social/PvP/Rift и stress loops.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены: совпадений нет;
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

## Performance and lifecycle

Новый runtime worker отсутствует. Registry ограничен 16 adapters, script death evidence — 256 instances, SpawnTable staging выбирает один deterministic exact NPC spawn. Performance/stress smoke не запускался по TASK.

## Deviations and diagnostics

Built-in `apply_patch` не мог читать workspace files из-за Windows sandbox ACL (`apply deny-read ACLs`). После каждой неудачи применялись bounded exact PowerShell replacements с проверкой ожидаемого match count. Созданный до compaction незавершённый untracked `PhantomRaidAttemptService.java` был точечно удалён, чтобы не оставить некомпилируемый production-код; он не восстанавливается через Git и не содержал законченной реализации.

## Limitations and risks

Checkpoint 5 не завершён и не получает verdict `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`:

- отсутствует bounded no-worker AttemptService;
- authority hash не mint/owned AttemptService evidence;
- нет per-Party support execution, PHANTOM offense ownership и objective retreat orchestration;
- нет combined actual-death + authority/script-confirmed victory owner;
- `raid.prepare`/`raid.participate` не переведены на attempt terminal lifecycle;
- CP5 entry/profile/attempt/Decision focused suites и полноценный aggregate не созданы;
- final affected CP1 static gate остаётся красным.

## Git and delivery

- branch: `feature/phantom-world`
- required parent: `a44421c1cec30e027aeb33e5588fb00373e30f1b`
- commit subject: `feat(phantoms): finish raid encounter orchestration`
- commit SHA: ordinary commit, содержащий этот отчёт; exact SHA фиксируется в final handoff
- push target: `origin feature/phantom-world`
- push result / remote HEAD: фиксируется в final handoff после push
- amend/rebase/squash/reset/force-push не выполнялись

## Next step

Продолжить тот же Goal 026 Checkpoint 5 только в новом непрерывном context с оставшимися AttemptService/Decision/support/retreat/victory/native-settlement acceptance criteria. До этого independent success review невозможен.