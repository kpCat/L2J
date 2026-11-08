# SOURCE MAP — проверенные реальные seams на WIP `8140167705`

Все относительные пути далее от `L2J_Mobius_CT_2.6_HighFive/`. Эти ориентиры проверены чтением файлов GitHub на 2026-11-08; перед редактированием сопоставить с фактическим WIP HEAD.

## 1. Planner / Party target creation
`java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java`
- ~59–62: `LEVEL_RADIUS=2`, `LOWER_LEVEL_TIERS=2,5,10`, `MAXIMUM_TARGETS=64`, `FALLBACK_TARGETS=256`, `MAXIMUM_TARGET_PAGES=8`: **используются и SOLO**, нельзя глобально менять ради ±4 Party.
- ~171–188: `replanVisibleRendezvous(...)`, в т.ч. перегрузка `supportTravelOnly`; раздельно учитывать явное Party purpose, не считать каждого не-support Solo DD.
- ~190–221: `replanLogicalRendezvous(...)`, `proposeLogicalActivity(...)`, `rendezvousGraphPlan(...)`: текущий путь отправляет `PlanningCombat.from(state)` при `supportTravelOnly=false`, затем `plan(...)` снова вызывает `_authority.viableTarget()`.
- ~222–262: `rendezvousIngressPlan(...)`: та же Solo viability и Solo-диапазон; обойти только для shared Party travel, сохранив реальный Geo/GK/route/fee.
- ~264–292: `rendezvousSources(...)`: текущая причина 70=0, 72=58; `!support && !_authority.viableTarget(PlanningCombat.from(state), npcId)`.
- ~295–299: `logicalRole(...)` только support vs damage; class90 Phoenix Knight становится damage, хотя у него есть Aggression.
- ~303–335: `rendezvousRoutePreview/Available/Absent(...)`: различать UNKNOWN, TEMP_BLOCKED, REACHABLE, UNREACHABLE. UNKNOWN не доказывает отсутствие маршрута.
- ~453–560: `plan(...)` и `targets(...)`: общие с Solo; не изменять глобальный Solo search policy. Создать explicit Party-range и group-purpose путь, переиспользующий server knowledge и bounded paging.

`java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java`
- ~65–77: `PlanningCombat.viable(Target)`, `PlanningCombat.from(BackgroundState)` — Solo feasibility, НЕ admission группы.
`java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java`
- ~324–350: `viableTarget(...)` восстанавливает mechanics из progression и оценивает бой; `mechanics=null` от `from(state)` само по себе не причина отказа. Solo оставить.
- ~365+: `nativePlanningCombat`, `nativePlanningTarget`, `captureOwnedNative` — реальные snapshots и STORE.
`java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeQuery.java`
- ~167–206: `suitableTargets(TargetQuery)` по NPC, level, kind, spawns; не вводить собственную таблицу монстров.
`java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundModel.java`
- ~395–450: `estimateEncounter`, `viableEncounter` — индивидуальная модель, не применять к каждому члену Party. `MAXIMUM_TIMEOUT_MILLIS` не выключать глобально.

## 2. Durable lifecycle / role snapshots
`java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java`
- ~142–178: `planLogicalActivity(...)` сравнивает `Member.role()` со старым `logicalRole(state)` и заново вызывает `rendezvousSources`; без обновления этого места новые роли сломают recovery.
- ~1570–1588: `previewLogicalRendezvous(...)` строит Candidate через role="damage/support", настоящее Background и Goal; на 0 Player допустим **потенциальный** role, не ложно native-ready.
- ~1590–1668: `previewVisibleRendezvous(...)`: projection из настоящего Player и `rendezvousSources`; предобработка staged capabilities/readiness.
- ~1870–1905: `prepareVisibleDecision` / `replanVisibleRendezvous` используется при accepted sharedFarmSource; нельзя оставить вторую Solo-проверку downstream.
- ~638–717: `prepareCurrentAuthority`, safe recovery, F7/current history. **НЕ трогать без абсолютно доказанной необходимости**.
`java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- ~610–658: load role catalog and native party backend; `high-five-party-roles-v1.xml`, `PhantomLogicalPartyCoordinator`.
- ~988–1067: `PhantomAutonomousFarmParty`, `installActivityRevision`, `installLogicalParty` with `populationStore.loadManagedAfter(after,limit)`, never scan all 5000 each pulse; role/source changes must be compatible with production composition.
- ~1110: authority fence before execution; do not bypass.

`java/org/l2jmobius/gameserver/phantoms/party/PhantomLogicalPartyCoordinator.java`
- ~33–44: `Candidate(Member role, level, sources, reachable, current, durableCurrent)` limit sources<=64.
- ~49–94: `discover(...)` uses role=="damage" for administrative leader, drives group objective from leader's list, includes compatible peer when exact common `source`, 2–9, SQL CAS via `agree(...)`.
- ~120–213: `groupFor`, `transition`, `recoverActivity`, `replace/leave/expire`, always preserve atomic claims, groupId, cursor, ownership.
`java/org/l2jmobius/gameserver/phantoms/party/PhantomLogicalParty.java`
- `Member(profileId, String role, goalId, revision)` bounded text already supports role keys; no DB schema migration necessary solely for new role values; verify every comparison/consumer.

## 3. Materialized/native Party & combat
`java/org/l2jmobius/gameserver/phantoms/party/PhantomAutonomousFarmParty.java`
- ~495–579: rendezvousPulse for ephemeral native offers; `leader=damageDealer`, and `if (team.size()<3) continue` in this route, contradicts 2–9.
- ~812–854: `selectRendezvousTeam(...)` requires damageDealer on admin leader and `requirements(team)` labels slot 0 as DD unconditionally. Another selectTeam overload explicitly demands support and third DD. Do not fix one method only.
- ~893–936: native `damageDealer`, `guardian`, `support`, `damage`, `supportRole`, `damageRole`: role evidence and learn/readyNow.
- ~770–808: `formNativeOffer(...)`, existing `PartyRoleMatcher` and official `PartyCoordinator` invite. No fabricated native Party.
`java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java`
- ~396–432: `autonomousFarmTarget(...)`: real L2J World Monster, ready actors, geo/reward gate, then `groupViable()`.
- ~464–514: `groupViable`: sums native DPS but naively compares whole incoming threat envelope against each member's HP; ignores actual aggro allocation and heal budget. Keep nontrivial safety, change only role-aware interpretation.
- ~788–847: `snapshot/nativeCapabilities`: weapon damage from genuine equipped weapon/expertise; `combat.tank` only from learned supported Aggression skill; support skill from actual learned and availability; no assumed abilities from class name.
`java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRoleMatcher.java`
- `match(ObjectiveMode, List<RoleRequirement>, List<MemberSnapshot>)`: authoritative, per-slot real capability matching, not a second matcher.
`dist/game/data/phantoms/party/high-five-party-roles-v1.xml`
- Existing keys frontline.guardian, damage.melee/ranged, support.healer/recharge/enhancement, control.specialist; SPOILER key missing. If adding optional `utility.spoiler`, use existing XML schema and preserve catalog hash/version semantics; verify rollout/old groups.
`java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java`
- official form/invite/accept, native objective and role ownership. Official party leader and mixed-human laws remain authority.
`java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java`
- ~657–773: native `npcAggroSkill/castNpcAggro` for genuine threat. Check actual effect/target/MP results; no virtual aggro.
`java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java`
- ~599–615/1155+/1407+/1481+: existing Spoil → COMBAT → SWEEP state machine; do not duplicate or prematurely count products.

## 4. Tests/acceptance
`test/java/org/l2jmobius/tests/phantoms/PhantomWorldCoordination130Suite.java`
- `register(...)` with MODEL tests (as of HEAD 44/44 in report). Extend existing registry; prove all edges, including 2-person parties and post-CAS role validation.
`test/java/org/l2jmobius/tests/phantoms/PhantomCoordination130Integration.java`
- TEST130 SQL safety/owner/catchup/restart fixture, extend existing modes, no second giant harness.
`test/java/org/l2jmobius/tests/phantoms/PhantomCoordination130Native.java`
- ~148–245 `inspectLoadedPlanner`; MUST check same approved Party level/role policy as production; witness before/after knowledge recovery, scene check only after recovery. Existing initial scene 70/72 is not a guaranteed viable composition.
- ~786–920 native acceptance observer/Party, PvE, Heal. Keep source/JAR parity, real damage and rewards. Never manufacture post-CUT events.
`docs/phantoms/reports/PHANTOM-WORLD-COORDINATION-130.{md,evidence.json}`
- Current authoritative outcomes; append concise segment, no reformatting 1400-line evidence to make it look greener.

## 5. Reference: confirmed live facts, NOT a patch target
- 70: class90 Phoenix Knight lv76, 4556 HP/1479 MP, PA684, PDef791, learned Aggression 28:49, Power Strike 3:9, Forgotten Blade 6364, primary candidate guardian + optional weapon DPS.
- 72: class97 Cardinal lv76, 3119 HP/2480 MP, learned Heal1011:18/Greater Heal1217:33/Major Heal1401:11, candidate healer.
- Coordinates 170156,-36726,-3376, same committedAnchor generated.farm.38520d85788548d726048a27.anchor; native knowledge recovery verified.
- 70 Sources=0, 72 Sources=58 only because individual Solo filter on non-support; 58 refers to source KEYS, not distinct NPC IDs. Internal MP/timeout/death split UNKNOWN.
- Real `damageDealer(70)` is UNKNOWN absent a Player snapshot; no hardcoded profile70/class90 special-case.
