# TEST_PLAN — быстрые RED→GREEN перед одним native

**Старт baseline**: тестовые исходники по WIP `8140167705`, MODEL44/44, SQL existing authority/receipts/restarter. Любые новые проверки запускаются на exact source HEAD. Фальшивый Player/mocked NPC допустимы только для MODEL policy, НЕ для production/GameServer claims.

## A. Чистая политика уровней, без сервера
A01 members [76,76] => NPC [72,80] inclusive; 71/81 reject.
A02 [74,78] => [74,78]; [72,80] => [76,76]; [76,81] => [77,80].
A03 [70,79] => empty; [85,85] => [81,89]; player invalid0,86 -> reject (не урезать npc на 85).
A04 legal group size2,3,9 accepted; 1,10 rejected; changes on level-up recompute and invalidate old goal.
A05 Solo planner original 66–78 for level76 (historical baseline SOLO search), Solo viableTarget still rejects unsafe; no change to Solo receipt/reward engine.
A06 all group target callers use same policy and NPC level bounds, not just preflight test helper.

## B. Deterministic party-source / role tests (no World)
B01 старый RED уже доказан native evidence: 70=0 vs 72=58; НЕ запускать GameServer ради повторения. В дешёвом тесте задать native-supported target с solo viable=false, доказать отказ старого SOLO фильтра и допуск нового PARTY source mode, только с настоящими knowledge/anchor/route; NPC только72–80. Не хардкодить ожидание NEW=58.
B02 attackable Monster with server authoritative spawn/anchor and certified route → selected for tank even when Solo viableTarget=false; same NPC may be rejected later for insufficient GROUP DPS.
B03 support/guardian/spoiler do not need solo kill, but non-attackable/raid/invalid instance/missing spawn/non-farming anchor/expired route is rejected.
B04 one common source after 64 earlier noncommon / fifth anchor / sixteenth eligible peer: bounded paging reaches it without scanning all5000; prove fairness/nextDue for exhaustion.
B05 UNKNOWN route != definitely reachable; GUI/host can report 'WAIT_ROUTE_UNKNOWN' without fake physical proof; paid GK verified, no free teleport.
B06 original `LogicalParty.Member.role` legacy damage/support survives readback; new guardian/support/spoiler/damage roles stable and group membership/reservations can recover in separate JVM.
B07 native `MemberSnapshot` without actual Player cannot be forged as readyNow, especially profile70 Aggression; honest pending role.
B08 guardian leader (not DD), support leader (when lawful), DD leader, hybrid spoiler/DD and 2–9 selected; no forced healer or 3rd DD; person with no actual combat readiness not admitted to unsafe PvE.
B09 role match uses existing PartyRoleMatcher; no one player fills two REQUIRED slots while their secondary weapon DPS may contribute once.
B10 preservation of stable party preferences <40 solo, 40–51 20%,52–60 35%,61–75 50%,76–85 80%, human party invites unaffected.

## C. Cross-stage integration / real isolated TEST130 SQL
C01 `previewLogicalRendezvous → discover → agree/CAS → stored group → state revision` produces exact groupId at 0 World. Test group2,3,9; storage restart in NEW JVM, no fabricated native Party.
C02 route and role change between preview and commit ⇒ refuse safely with reason, no double claim / lost root.
C03 group objective member±4 enforced after solo→party transfer, after level-up and when activity replan, before root publish and native preflight.
C04 completed historical authority hashes survive current knowledge already refreshed, no extra STORE / no replay; F7 receipt mismatch/retry remains protected.
C05 fail/remove member and concurrent invitation → no stealing claim or unretired owner, no premature dematerialization; existing Supply/Learning/Career/Commerce/Store pending blocks.
C06 `replanLogicalRendezvous`, `rendezvousGraphPlan`, `rendezvousIngressPlan`, `replanVisibleRendezvous`: explicit PARTY mode, no secondary Solo viability. Source->root->route->party reservation consistent; after stage break exact same source and role or typed REPLAN.
C07 5000 durable and 250 offline traders: bounded page costs; 128 cap/target not conflated; no per-pulse 5000 SQL; retain previous census tests.
C08 Spoiler: role constructed only with true learned Spoil/Sweeper evidence; target scoring considers existing authoritative spoil facts; no phantom loot or corpse. If user code cannot finish native spoil within this package, record precise TASK131 interface seam.

## D. Native member-/group-level test without full GameServer (existing real owners or justified small fixture)
D01 actual equipment/expertise + learned skills produce valid `nativeCapabilities` tank/DD/healer/spoil where supported, no class-only readyNow.
D02 `selectRendezvousTeam` accepts group2 when role/capability checks work; rejects no damage capability, inactive status, absent native Party owner, expired role.
D03 `groupViable` uses actual aggregate DPS, includes guardian + spoiler weapon damage once; no fake support DPS and no solo kill requirement.
D04 off-aggro support danger returns WAIT not PASS; actual aggro cast + target/AI observation can alter readiness; unknown taunt fails safe.
D05 healer with insufficient current MP/range/cooldown not magically save party; MP spend and HP delta only real native skill.
D06 target NPC 71 vs77 vs81 at party76/76: 71/81 are rejected even if `rewardingFarmTarget` accepts; age/leash/Geo unchanged.
D07 no stock Heal human regression, no PvP/PK/Clan/instance/raid path deterioration.

## E. One final native vertical AFTER A–D GREEN
Preflight in TEST130 on real configured production catalog, exact final JAR, real profiles/roles, consent/windows, current Context=COMPLETED/History COMPLETE/Goal bound, no dirty stores, Geo203 exact. Profiles70/72 may or may not be able to win. If no valid team in existing TEST130, output exact missing role/target/group resource evidence and DO NOT BOOT merely to learn it.
N01 fresh native authority accepted (already proved) and no erroneous re-STORE; exact knowledge hashes/current owner.
N02 at zero World, logical group persisted; native Player materializes lawfully, meets at reachable FARM and uses stock Party invite/accept; Player.getParty actual, rights exact.
N03 group attacks actual normal mob level within intersection, actual HP loss and at least one real EXP/SP/loot event attributable to group; no Solo / simulated credit.
N04 real support Heal effect/MP debit only if healer present; no fake mandatory healer on group without one. Existing TASK130 FULL compatibility F8 ordinary-human test still required.
N05 safe drain/STORE and same groupId/roles/cursors remain at zero Player, no re-credit.
N06 stock STOP/postflight exact, then **conditional** sameDB/sameFINALJAR restart and at least one subsequent useful group activity.
N07 native Spoil/Sweeper if spoiler actually selected and controller is ready; otherwise record NOT_PROVEN, do not claim native full group Spoil.

## F. Failure decision table
- A/B/C/D fail: fix precise code and rerun CHEAP checks; 0 JAR/0 boots.
- Native preflight no legal roster or no valid stage: stop, typed blocker, reason + missing concrete fact, no CUT no forced fixture.
- Native first bootstrap fails before useful action: safe STOP/postflight, no auto-rebuild/second boot. Explicit owner review before continuation.
- First native useful action passes but postflight fails: restart forbidden until source/SQL/identity safe.
- First native+STOP/STORE PASS: conditional ONE restart only.
- If group2 (70/72) lacks DPS, do not make it PASS; use lawful existing third DD or different nearby NPC within ±4 only when source/proof exists.

## Expected output format
`MODEL=N/N PASS/FAIL; SQL=...; NATIVE_GROUP=...; USEFUL_KILLS=N; EARNED_EXP_SP_LOOT=...; HEAL_NATIVE=...; SPOIL_NATIVE=...; STORE=...; RESTART=...; JAR_BUILDS=N; GAMESERVER_BOOTS=N; FULL130=...; FULL130-136=X/7`.
