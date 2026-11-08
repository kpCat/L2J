# Safety / forbidden shortcuts / blast radius

## Protected physical and Git state
PRIMARY GIT ROOT: `D:\Codex\repos\L2J-phantom-world` user branch `feature/phantom-world`, user's old WIP109, large untracked docs, `.phantom-local`, existing `refs/stash` (23 source/test changes) and possibly local tag `archive/stash-20261106`. **READ-ONLY**. No staging, deleting, applying/drop stash, tag edits, branch switches, clean/reset/rebase, automatic packaging in primary checkout.
Only Codex's own independent clone may be modified, on `wip/coordination-130-preserved`, normal scoped commits/push. Preserve `wip/task129-preserved-20261106` HEAD `5cb04fe8a669ebf68e5f9ae0fa927055f81a4efe` and original feature. User may independently handle stash later; this task may not.

## SQL and player safety
- TEST130 only with established OS PID/owner, principal, socket, database/schema; G0 attestation before tests and GameServer. No guessed credentials, no SQL into MAIN/PLAY/TEST129.
- Fully preserve profiles70/72 and other managed data, native player identities, historical Catchup COMPLETE hashes/cursors, goal revision, inventory/EXP/SP. Postflight hash and DB witnesses before/after; any lawful update to owned native state explained exactly.
- No manual `UPDATE characters` HP/MP/SP/EXP, teleport/pos, inventory/equipment/skills, fake kill/reward, SQL insert of native Party or spoil corpse **after CUT**. Before CUT only isolated legal TEST130 fixture as already permitted by original acceptance, never quietly reseed old managed profiles.
- Stock Monster, Skill, Party, Heal, Geo, GK are sole authorities. No bypass for test player, no ghost damage, no skill condition overrides.
- F7 receipt/checkpoint exact; F8 human Heal semantics and real Phantom Heal. No free extra resources or altered skill formulas. Preserve native party/clan leader ownership.

## Execution and lifecycle
- One existing Scheduler/Decision/Background/Materialization/Party path, no alternate AI/combat/nav clock or second authoritative database ledger.
- No re-materialization in ongoing combat, store, player-owned Party, trade/craft, inflight; no despawn while visible; no force-stopping unmanaged JVM.
- No changes to original G0/CUT/time fences for test convenience. Config/rewards/rates/enchant/premium not in this TASK.
- No broad update to base `Heal.java`, Player, Clan/Siege/Olympiad without targeted P1-level evidence, human regression and owner approval.

## Fail-closed done correctly
- Safety fail-closed, but concrete `reason`, profile/group, nextDue/requiredChange. Do not silently leave repeated RETRY for 10+ hours.
- Candidate rejected due to lack of DPS/heal/aggro/mana → lawful WAIT/REPLAN; not dead/blocked artificial profile.
- Unknown route/role/lease ≠ success. A proof deficit is not authorization to ignore it.
- No broad deletes, no large nested archival stages. Full raw logs in Codex's own local evidence, append succinct report to WIP.
