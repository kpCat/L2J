# Goal 026 Checkpoint 5 — raid encounter orchestration

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Checkpoint 5 завершён на ветке `feature/phantom-world` как continuation от required parent `a44421c1cec30e027aeb33e5588fb00373e30f1b`. Checkpoint 1–4 и correctives 026A/026B/026C остаются `ACCEPT`; Goal 026 overall переведён в `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`; Goal 027 не начат.

`occurred_context_compaction: yes`

## Summary

Сохранен additive raid Combat из required parent. Combat допускает dead `VICTORY` только после exact совпадения `objectId + npcId + ContentKind + NpcKind + instanceId`; replaced или mismatched dead target завершается как `TARGET_LOST`. Collector проходит только штатную loot-фазу Combat, custom drops и прямые награды не добавлены.

Завершён remaining vertical slice:

- `ENTRY_GATED` readiness и staging через exact `SpawnTable.getSpawns(entryNpcId)`;
- Queen Ant `epic.29001`, NPC `29001`, curse-boundary с допустимым level 48 и отклонением level 49 при включённой штатной curse-механике;
- Zaken 83 `epic.zaken.83` через тот же `CavernOfThePirateCaptain.checkConditions` и `InstanceScript.enterInstance`;
- публичные candle facts без hidden `isBlue`; физическое взаимодействие проверяет exact instance/distance и делегирует тому же `onFirstTalk`;
- bounded caller-driven `PhantomRaidAttemptService` без собственного worker/thread;
- required support через существующий per-Party `PhantomPartyTactics`, offense только для `PHANTOM` через additive raid Combat, `REAL` остаётся observation-only;
- objective retreat через существующий `PhantomPartyRouteCoordinator`;
- attempt authority mint/ownership только по exact `AttemptIdentity`, CP4 structural hash, recommendation/profile/encounter evidence и exact target;
- `VICTORY` только после фактической смерти exact target, authority/script confirmation и завершения native collector loot;
- `raid.prepare` завершается только по attempt `VICTORY`; `raid.participate` ждёт leader startup и следует exact attempt terminal.

## Mandatory reading and local reuse

Прочитаны completion `TASK.md` и только перечисленные им bounded fragments. Whole Combat, whole Party, historical task chain и запрещённые broad areas повторно не читались.

Переиспользованы локальные паттерны:

- `PhantomCombatService` и `PhantomRaidCombatRequest` для offense и native loot;
- `PhantomPartyTactics` для support;
- `PhantomPartyRouteCoordinator` для per-Party staging, mechanic routing и retreat;
- CP4 `ReadyReceipt`, exact structural hash и assembly identity;
- `PhantomRaidAuthority` для open-world target/death evidence;
- canonical Zaken `checkConditions`, `enterInstance`, `onFirstTalk`, `onKill`;
- `SpawnTable.getSpawns(exactNpcId)` для entry staging.

## Changed files

Production/data текущего Checkpoint 5:

- `dist/game/data/phantoms/knowledge/high-five-core-v1.xml`
- `dist/game/data/scripts/instances/CavernOfThePirateCaptain/CavernOfThePirateCaptain.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java`
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java`
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomRaidCombatRequest.java`
- `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java`
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/L2jPhantomRaidAttemptRuntime.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/L2jPhantomRaidAuthority.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAssemblyService.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAttemptRuntime.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAttemptService.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAuthority.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidDecision.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidEncounterCatalog.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidEncounterProfile.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidEntryNpcLocator.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidModel.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidReadinessService.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidScriptAdapter.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidScriptRegistry.java`
- `java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidTargetEvidence.java`
- `build.xml`

Focused tests/docs:

- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidCombatGoal026Checkpoint5Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidCombatDynamicGoal026Checkpoint5Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidEncounterProfileGoal026Checkpoint5Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidAttemptGoal026Checkpoint5Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomRaidDecisionGoal026Checkpoint5Suite.java`
- directly affected `PhantomRaidReadinessSuite.java`, `PhantomRaidRecruitmentSuite.java`, `PhantomRaidAssemblySuite.java` и `PhantomTestLauncher.java`;
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/PHANTOM_BOTS_ROADMAP.md` и этот отчёт.

User-owned untracked `docs/phantoms/tasks/026-checkpoint-2-command-channel-lifecycle/CODEX_LAUNCHER.txt` и `PACKAGE_MANIFEST.json` не изменялись и не включаются в commit.

## Architecture decisions

- `AttemptIdentity` содержит exact leader, goal id/revision, content id и CP4 structural hash.
- Authority hash создаёт только `PhantomRaidAttemptService` из exact attempt/recommendation/profile/encounter evidence. `ownsAuthority` требует тот же identity, hash и target identity.
- Один leader имеет не более одного live attempt; live/terminal state ограничен `64/256`; advance выполняется вызывающим Decision, отдельного scheduler нет.
- Все EPIC, кроме Queen Ant и Zaken 83, fail closed. Generic RAID остаётся open-world.
- Support provider должен быть живым `PHANTOM` с intrinsic + learned + usable skill/rank; reserved providers исключаются из offense.
- Offense не управляет `REAL`; для `PHANTOM` используется существующий Combat. Прямые HP/death/boss-state/instance/reward mutation запрещены и отсутствуют.
- Collector terminal признаёт native loot complete только при его canonical Combat `VICTORY`; другой terminal освобождает collector role.
- Scripted Zaken death подтверждается exact object/NPC/instance evidence до штатного `onKill` reward/finish, но не заменяет их.

## DB, migrations and configs

DB schema, migrations и config keys не менялись. Phantom World остаётся под существующим global feature flag. Новая orchestration при выключенной системе не создаётся.

## Commands and test results

Focused CP5:

- `phantom-raid-combat-goal026cp5-test`: PASS, contract 4/4 + dynamic 4/4, seed `26002652`;
- `phantom-raid-entry-profile-goal026cp5-test`: PASS 4/4;
- `phantom-raid-attempt-goal026cp5-test`: PASS 7/7 после collector hardening;
- `phantom-raid-decision-goal026cp5-test`: PASS 2/2. Первый запуск не дошёл до тестов из-за native JDK 25 `EXCEPTION_ACCESS_VIOLATION` в GC thread; один повтор того же target прошёл.

Directly affected:

- CP1 `phantom-raid-readiness-policy-test`: первоначально 4/6 из-за устаревших generic-EPIC/whole-directory assertions; после bounded adaptation PASS 6/6;
- CP3 `phantom-raid-recruitment-test`: первоначально 8/9 из-за устаревшего generic-EPIC success assertion; после fail-closed adaptation PASS 9/9;
- CP4 `phantom-raid-assembly-goal026c-test`: PASS 3/3 + 5/5 + 4/4;
- ordinary `phantom-combat-core-test`: PASS 50/50;
- `phantom-game-knowledge-content-test`: PASS 18/18;
- targeted `javac` для `CavernOfThePirateCaptain.java`: PASS.

Final gates:

- единственный final `phantom-raid-encounter-goal026cp5-test`: `BUILD SUCCESSFUL`, 21/21;
- единственный final `jar`: `BUILD SUCCESSFUL`; `LoginServer.jar`, `GameServer.jar`, `DatabaseInstaller.jar` собраны, server JAR скопированы в рабочий `dist/libs`.

Plain `ant verify`, Goal025, broad Goal017, all-Combat, all-Phantom, unrelated economy/social/PvP/Rift и stress loops не запускались.

## Performance and lifecycle

AttemptService не создаёт worker/thread/future. Live и terminal maps ограничены. Support/offense/route state ограничен live-attempt cap; actions очищаются до Assembly/Party/Combat/Navigation teardown. Target loss, structural drift, deadline, provider loss и wipe переводят attempt в bounded objective retreat. Runtime cleanup precedes Assembly cleanup во всех production stop/failure paths.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены;
- escaped Cyrillic в изменённых файлах проверены.

## Deviations and diagnostics

Built-in `apply_patch` не мог читать существующие workspace files из-за Windows sandbox ACL (`apply deny-read ACLs`). Новые файлы создавались через `apply_patch`; изменения существующих файлов выполнялись bounded exact PowerShell replacements с проверкой количества совпадений. Временные editor scripts, targeted compile output и JVM crash log удалены после использования.

В ходе continuation произошли automatic context compactions; работа не расширяла discovery scope и продолжалась только по зафиксированному CP5 slice.

## Limitations and risks

- Verdict не является self-accept: требуется независимый review.
- Универсальный epic solver, другие эпики, clans/sieges/quests, raid DB saga и REAL control не реализованы и остаются вне scope.
- Навигационная runtime-проверка без geodata остаётся общим ранее зафиксированным ограничением проекта; orchestration использует существующий route owner и fail-closed outcomes.

## Git and delivery

- branch: `feature/phantom-world`
- required parent: `a44421c1cec30e027aeb33e5588fb00373e30f1b`
- continuation base before final commit: `b7fccfc343893150e9de8de92737c7782e7c2796`
- exact commit subject: `feat(phantoms): finish raid encounter orchestration`
- commit SHA и remote HEAD: фиксируются во внешнем final handoff после commit/push
- push target: `origin feature/phantom-world`
- amend/rebase/squash/reset/force-push не выполнялись

## Next step

Независимый review Goal 026 Checkpoint 5 и Goal 026 overall. Goal 027 остаётся `NOT_STARTED` до review verdict.
