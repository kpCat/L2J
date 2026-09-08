# Goal035 — native castle siege gameplay slice

Status: SUCCESS

## Source-fact map recorded before production code

- Castle authority: `CastleManager.getCastleById(3)` returns Giran; the canonical
  owner, siege date, registration end, zone, doors and `Siege` instance remain
  owned by `Castle`/`Siege`.
- Schedule authority: `SiegeScheduleData.getScheduleDateForCastleId(3)` reads
  `data/SiegeSchedule.xml`; Phantom does not copy Sunday/16:00 into strategy data.
- Registration authority: the only production mutation is
  `Castle.getSiege().registerAttacker(player, false)`, followed by immediate
  re-observation through `Siege.checkIsAttacker(clan)` and the native attacker
  clan list. Phantom additionally requires the current native clan leader.
- Side and zone authority: `Siege.checkIsAttacker`/
  `Siege.checkIsDefender`, `Siege.isInProgress`, `Castle.checkIfInZone` and the
  native siege-zone state remain authoritative. Phantom never writes Player
  siege flags.
- Giran attacker staging: `112122,144855,-2751`, from
  `data/html/admin/teleports/CastleAreas/giran.htm` ("Out of Castle"), checked
  against castle/siege zone polygons.
- Giran defender staging: `116540,146655,-1866`, from Giran `owner_restart`
  territory in `data/zones/castle_hall.xml`.
- Giran approach/gate: door `23220001` at `112857,144729,-2540`, from
  `data/Doors.xml`; door-to-castle identity is re-observed through
  `Door.getCastle()`.
- Giran retreat: existing topology anchor `giran.city.center` at
  `82480,149087,-3350`, sourced from map-region data by
  `data/phantoms/topology/high-five-core.xml`.
- Shared owners reused: `PhantomClanService`/native `Clan`,
  `PhantomNavigationService`, `PhantomCombatService`,
  `PhantomSchedulerRelevanceSignalPort`, `PhantomGoalStateStore`, and the
  existing Decision engine. No parallel scheduler, combat, navigation, clan or
  siege authority is introduced.

## Scope note

The task-mandated vertical crosses more than ten files. This is a bounded
exception limited to the new siege owner/catalog/adapter, the additive shared
Combat seam, factual Giran topology/data, focused guarded tests, composition,
build wiring, and the four required current-state documents.

## Delivered architecture

- `PhantomSiegeCatalog` loads one strict, versioned Giran policy from
  `data/phantoms/siege/high-five-siege-v1.xml`; bounds, identities, roles,
  targets, TTLs and timeouts fail closed and have a stable content hash.
- `PhantomSiegeAuthority` and `L2jPhantomSiegeAuthority` isolate observation of
  native castle, schedule, registration, sides, zone and legal targets. The only
  production registration mutation is `registerAttacker(player, false)`.
- `PhantomSiegeService` owns a caller-driven, bounded operation lifecycle with
  deterministic roles, gathering, native-side combat, 25% retreat, recovery,
  finish/abandon/expiry reconciliation and restart-safe re-observation.
- `PhantomSiegeMovementCoordinator` delegates travel to the existing Navigation
  owner. `PhantomSiegeCombatRequest` adds an explicit shared Combat branch for
  opposing native-side `Player` and legal castle `Door` targets.
- `PhantomSystem` composes and drains the siege service in the existing
  lifecycle. No siege scheduler, thread, shadow timer/state, force-registration,
  direct HP/owner/online/siege-side mutation, or production teleport was added.
- Support remains owned by `PhantomPartyTactics` through
  `Combat.PARTY_SUPPORT`; siege does not introduce a second support engine.

## Acceptance evidence

- Focused `ant -q phantom-siege-goal035-test`: `8/8` PASS, seed `35003501`.
  It covers strict catalog/topology, deterministic bounded roles, support owner,
  typed combat legality, routing/restart/stop cleanup, retreat/recovery/native
  finish, production source safety, and the guarded native fixture.
- Registration fixture: current leader/minimum-level/owner checks reject the
  negative cases; the positive path goes through `PhantomSiegeService` and real
  `L2jPhantomSiegeAuthority`, registers once with native `false`, and a second
  service instance observes the same native row without duplication.
- Native lifecycle fixture: real Giran `Siege.startSiege()`/`endSiege()` yields
  native `ATTACKER`/`DEFENDER`; an opposing `Player` reaches canonical forced
  attack and door `23220001` reaches canonical AI `ATTACK`. Full
  `PhantomCombatService.startSiegeSession` is covered as well as the backend.
- Deterministic participant evidence is capped at eight. The representative
  assignment is `101:COMMANDER`, `102:SUPPORT`, `103:RANGED`,
  `104:FRONTLINE`, `105:RESERVE`; gathering uses the factual anchors above.
- Retreat/recovery evidence covers the 25% threshold, the existing Combat town
  recovery owner, native-finish-after-retreat terminalization, cancellation,
  restart and stop cleanup. No free revive or direct HP write exists.
- Topology composition:
  `ant -q phantom-topology-production-corpus-test phantom-siege-goal035-test`
  PASS (`2/2` aggregate routes).
- Affected regressions:
  `ant -q phantom-combat-server-integration-test phantom-pvp-goal025-focused-test phantom-raid-encounter-goal026cp5-test phantom-clan-checkpoint1-goal027-test phantom-party-state-recovery-test phantom-party-route-test`
  PASS (`6/6` aggregate routes, 2 minutes 34 seconds). Combat server integration
  itself is `20/20`.
- Short composition regressions:
  `ant -q phantom-skeleton-test phantom-server-shutdown-handoff-test phantom-topology-production-corpus-test`
  PASS (`3/3` aggregate routes, 1 minute 20 seconds).
- Fresh full `ant verify`: `BUILD SUCCESSFUL`, 23 minutes 51 seconds; Goal035
  within it is `8/8` PASS.
- Standalone final `ant -q jar`: `BUILD SUCCESSFUL`, 17 seconds. No production
  code changed afterward.

## Guard, cleanup and bounds

- All DB-backed evidence used only `127.0.0.1:3308/l2jmobiush5_phantom_test`.
  Production `l2jmobiush5` was unused and `prepare-phantom-test-db` was not run.
- The native fixture restored the original siege date and owner and finished
  with `registrations=0`, `clans=0`, `profiles=0`.
- The source `castle_siege.xml` and `castle_hall.xml` polygons exceed the
  topology model's `World` maximum Z; only their maximum Z was clipped to the
  existing topology bound `16000`. XY, minimum Z and factual anchors are intact.
- Siege work is caller-driven and bounded: at most 128 owned operations, at most
  eight participants per operation, strict candidate/target/range/TTL limits,
  no worker and no second authority.
- Goal034 black-box acceptance was not rerun. Goal036 was not started.

## Delivery

One task-owned commit uses subject
`phantom(goal-035): add native siege gameplay slice`; exact SHA and push result
are returned in the final handoff because they do not exist until after this
report is committed.
