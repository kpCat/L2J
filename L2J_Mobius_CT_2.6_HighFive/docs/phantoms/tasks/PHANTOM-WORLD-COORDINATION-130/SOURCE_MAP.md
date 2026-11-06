# SOURCE_MAP — реальные seams, проверенные в GitHub на 06.11.2026

Remote `https://github.com/kpCat/L2J`; **production reference** `feature/phantom-world` = `faec72161f9e4f6231764e17d6f2637a4db080af`; **preserved TASK129 code** `wip/task129-preserved-20261106` = `5cb04fe8a669ebf68e5f9ae0fa927055f81a4efe`, ahead 12 commits / 28 changed files. Проверить обе ветки `git ls-remote` до работы, не доверять stale pin без ревью. Локальный master checkout WIP109 неизменен.

Все относительные пути ниже от `L2J_Mobius_CT_2.6_HighFive/`.

**Party / group ownership**
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomAutonomousFarmParty.java` — WIP129 `rendezvousPulse`, `MAXIMUM_CANDIDATES=16`, materializedFarmMembers page, `SourcePreview.sources().distinct().limit(4)`, `selectRendezvousTeam`, rescue/follow. Не считать retained offer автономной group со 0 World.
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRendezvousState.java` — schema v1 `Participant(MemberRef actor,long epoch,...)` требует current `objectId/epoch`; нужен versioned logical format and safe migration, не silent v1 override.
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java`, `PhantomPartyStore.java`, `PhantomPartyPersistencePort.java`, `L2jPhantomPartyBackend.java`, `PhantomPartyRoleMatcher.java`, `PhantomPartyFollowPolicy.java`, `PhantomPartyRouteCoordinator.java`, `PhantomRealPartyInvitationPolicy.java` — existing native authority, persist, CAS, support/route/consent. Read bounded relevant functions, no wholesale rewrite.
- `java/org/l2jmobius/gameserver/model/groups/Party.java` и `PartyInvitationService` (проверить фактический путь) — authority only for materialized, real invite.

**Lifecycle, scheduler & intent**
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java` — composition: `periodicEcology.installMaterializationDemand(profileId -> hasAcceptedNativeJourney || presence.isOnline && humanLocality...)`, installs Presence readiness, background native handoff, Party/Clan, shared scheduler. Новые demand paths must join here, not bypass elsewhere.
- `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationManager.java`, `PhantomPresenceRegistry.java`, `PhantomPopulationEcologyService.java` — quotas: `limit=min(activeTarget, maximumMaterialized, desired eligible)`, `prioritizeHumanDemand` with small budget, `isOnline`, request due, ecology catchup/cursors. `128 logical admitted` is not 128 native Player.
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomReconcileFirstActivityPort.java` — `presence.no_current_local_demand`, request/accepted Journey/retention; needs one typed demand contract with reason and retry, not blanket unconditional local waiver.
- `java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationRetentionPolicy.java` — human/local/native visible/real Party/active action/grace; don't demat in sight or in unfinished action.
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java`, `PhantomMaterializedPlayer.java`, `PhantomIdentityLeaseRegistry.java` — one native `Player` owner, admission capacity, epochs and STORE. Preserve `_permits` physical cap.
- `java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStore.java`, `PhantomDecisionEngine.java`, `PhantomScheduler.java` — canonical goal, shared pulse/intent; no parallel loop.
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java`, `PhantomHistoricalBackgroundService.java`, `PhantomBackgroundTransaction.java`, `PhantomHistoricalBackgroundPlanner.java`, `PhantomVisibleFarmTravel.java`, `PhantomVisibleAutoPlay.java` — cursor/native context, legacy hash, route sources; WIP129 contains risk F7 in `offscreenReceiptHashCurrent`.
- `java/org/l2jmobius/gameserver/phantoms/commerce/L2jPhantomSupplyJourney.java` — accepted ownership/return, no force cancel for Party.

**Native and offliners**
- `java/org/l2jmobius/gameserver/phantoms/economy/PhantomOfflineMerchantService.java` — native offline stores via own leases and `World`, separately from `_materializationService` permits. Must count separately, while World total includes stores.
- `dist/game/data/scripts/handlers/skill/effects/Heal.java`, `java/org/l2jmobius/gameserver/model/actor/PlayerNativeEvidence.java` — F8 observer patch in WIP129, must not change human Heal semantics just to record test.
- `java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanService.java`, `PhantomClanDecision.java`, native `model/clan/Clan.java`, `ClanWarService` — clan leader controls official rights; regression-only, not a new autonomous clan system here.

**Config**
- `dist/game/config/Custom/PhantomPlayers.ini`: existing `PhantomPopulationTarget=5000`, `PhantomPopulationActiveTarget=128`, `MaxMaterializedPhantoms=128`, burst0 (read effective overlay at run, don't overwrite user effective values). WIP129 has `PartySeekingSettings` stable 0/20/35/50/80.
- `java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java`, `SaveOwnerConfig.bat`, `tools/OwnerConfig.ps1`, `dist/game/config/OwnerConfig.json`: preserve Save/Verify/Apply; new physical target config (if needed) via existing public INI + owner overlay, not new config subsystem.

**Previous evidence**
- GitHub published `docs/phantoms/reports/PHANTOM-LIVING-PARTY-PVP-PK-128.md`: 23 JVM, PARTIAL, individual real PvE and PvP.
- Local independent code review `reference/TASK129-INDEPENDENT-REVIEW.md` — F1–F9, 28-file patch; WIP129 remote HEAD as above. Old TASK129 TEST129/raw evidence preserved at `D:\Codex\_PRESERVE\TASK129-20261106` and separate stage, **read-only, not new test baseline**.
- Acceptance and architectural source of truth: `reference/ARCHITECTURE-CONTRACT-002.md` and `ACCEPTANCE.md`.

**Keep source reads bounded**: 30–150 lines around methods, diff-aware. This map is a starting point not a substitute for verifying actual packages, method signatures and Git HEAD.
