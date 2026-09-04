# Goal 032 — Phantom Reset Operator Control

## Status

`SUCCESS`. Safe operator reset/reseed, ownership audit, guarded MariaDB regressions, operator documentation and finite Roadmap v3 are complete.

## Exact baseline/branch

- Branch: `feature/phantom-world`.
- Baseline `HEAD`: `89a292d34880faafbfa40e1d688d9765469485d6`.
- Baseline `origin/feature/phantom-world`: `89a292d34880faafbfa40e1d688d9765469485d6`.
- `HEAD` and upstream matched before implementation.
- Pre-existing unrelated untracked task packages were not modified, staged or deleted.

## Pre-audit

Read-first covered the Goal 032 package, root policy/README, master plan, canonical roadmap/status/quickstart/handoff, shipped config/parser/preset, Phantom lifecycle/population/profile/materialization code, canonical character deletion, account/character/mail/clan schemas, build/test/provisioner infrastructure and Goal 028/030/031 analogs.

The audit found six Phantom-owned tables: `phantom_profiles`, `phantom_profile_components`, `phantom_economy_operations`, `phantom_economy_reservations`, `phantom_economy_audit` and `phantom_economy_offers`. `population.state` is the sole production creation provenance. `GameClient.deleteCharByObjId` is unsuitable because it is not one transaction, swallows SQL failures and does not cover the installed private-state surface.

Local patterns reused: Goal 028 bounded operator drain/enable, Goal 030 CP3 restart/failure recovery, Goal 031 production-composed 10/5 lifecycle, existing guarded MariaDB provisioner and `PhantomTestLauncher` registration.

## Durable ownership graph

The reset owner starts only from every row in `phantom_profiles` and requires a decodable schema-v2 `population.state` component. It verifies:

- exact reserved account `p + base36(profileId)`;
- exact account password equal to durable ownership token and access level `-1`;
- exact character name/account/object-ID/profile-link agreement when the creation stage requires those rows;
- uniqueness of account, character name and object ID;
- immutable snapshot hash over profile/component versions, payloads, identities, counts and blockers.

Missing/unknown/invalid provenance, account or character mismatch, duplicates, a changed snapshot, or more than 10,000 preview identities fails closed before mutation. Shell-stage partial identities without account/character are valid and removable; an unproven profile blocks reset.

## Shared-world semantics

Private profile/component/economy, account, character, inventory/pet and installed character-owned rows are deleted only by exact proven IDs/names/accounts. Contacts, friends and offline-group links are safe-detached by exact endpoints.

Mail, forum/topic/post history, hero/olympiad history and prime-shop audit are preserved and reported. Transferred human-owned items are not selected. Clan membership/leadership/components, item auction, cursed weapon, grand-boss, clan-hall, wedding, airship and incoming human mail-attachment ambiguity return explicit blockers before mutation.

## Operator command UX

- `//phantom reset preview` performs a read-only inspection and prints identity/account/character counts, exact delete/detach counts, preserved effects, blockers, snapshot hash and a random one-time token.
- `//phantom reset confirm <TOKEN>` drains and resets only the armed snapshot.
- `//phantom reset confirm <TOKEN> reseed` additionally calls the existing PopulationManager lifecycle with already loaded settings.
- `//phantom reset cancel` invalidates the armed token.

Tokens expire after 120 seconds. Wrong, cancelled, expired, consumed and stale tokens cannot mutate data. Blocked previews issue no token. No startup path invokes reset.

## DB transaction/recovery model

Before mutation, confirm re-inspects the snapshot, performs the existing bounded runtime drain, re-inspects again, then locks the proven profile/character/account ownership rows. All exact-ID detach/delete operations and zero-residue verification execute in one `DatabaseFactory` transaction in batches of 250. Any SQL/runtime failure rolls back. Failure injection at the cleanup boundary and immediately before commit proved no partial state.

Shipped GameServer/LoginServer configuration uses the same database, so accounts and game data participate in this transaction. A future split Login DB is intentionally unsupported and fails the current operational prerequisite.

## Reset/reseed lifecycle

Reset reaches a terminal drained state before DB work. Plain reset leaves no configured runtime population. Reseed delegates to the existing enable/PopulationManager path; it does not create a second generator. Production-composed 10/5 reset/reseed produced a disjoint new profile/account/character set, correct Scheduler/Decision lifecycle, and restart did not duplicate identities. With shipped `EnablePhantomSystem=False`, destructive reset still succeeds and reseed reports `CONFIG_DISABLED` without weakening fail-closed defaults.

## Human sentinel evidence

The ownership suite snapshots a real human test account, character, inventory, skills and quest row. It adds a human↔Phantom friendship to prove safe detach and an item-auction bid to prove pre-mutation blocking. After allowed reset the human snapshot is byte-for-value unchanged, the link is detached, all proven Phantom residue is zero, failure-injection attempts roll back, a partial shell is removable and an empty second reset is `RESET_NOOP`.

## Config/tuning inventory

`PHANTOM_OPERATOR_TUNING_RU.md` documents every one of the 13 keys parsed from shipped `PhantomPlayers.ini`: type, actual effect, safe local range, restart requirement and risk. It separates population/active targets from performance budgets and keeps shipped defaults `False/0/0`. QuickStart now documents reset/reseed semantics.

Level distribution, progression pace/outliers, newcomer waves, personality percentages and fresh/living/mature ecology presets are explicitly absent from the current parser and deferred to Goal 033.

## Roadmap v3 changes

Master plan, canonical roadmap, Current Status and handoff now agree on the finite post-release path:

1. 032 — safe reset/operator control;
2. 033 — living population ecology;
3. 034 — black-box human-believability QA;
4. 035 — siege participation;
5. 036 — quests/instances;
6. 037 — final integration/freeze.

After 037 there is no automatic next Goal; only evidence-driven corrective work is allowed. The next handoff points to Goal 033.

## Changed files

Production/build:

- `java/org/l2jmobius/gameserver/phantoms/PhantomPopulationResetService.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- `dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java`
- `build.xml`

Focused tests:

- `test/java/org/l2jmobius/gameserver/phantoms/PhantomPopulationResetOwnershipGoal032Suite.java`
- `test/java/org/l2jmobius/gameserver/phantoms/PhantomPopulationResetReseedGoal032Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomPopulationResetDocumentationGoal032Suite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`

Documentation/report:

- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `docs/phantoms/PHANTOM_CURRENT_STATUS.md`
- `docs/phantoms/PHANTOM_QUICKSTART_RU.md`
- `docs/phantoms/PHANTOM_OPERATOR_TUNING_RU.md`
- `docs/phantoms/NEW_DIALOG_START_MESSAGE.txt`
- `docs/phantoms/reports/032-phantom-reset-operator-control.md`

This is a bounded 15-file exception to the usual 8–10-file limit because the TASK explicitly requires three independent artifact families: production control, three focused test contracts/build registration, and synchronized operator/roadmap/report documentation. No unrelated cleanup or abstraction layer was added.

## Tests/commands/results

- `git diff --check` — initial run found two new trailing spaces in roadmap; corrected. Final result: PASS.
- `ant prepare-phantom-test-db` — PASS; exact allowlisted `127.0.0.1:3308/l2jmobiush5_phantom_test`, 121 scripts, 214 statements, aggregate schema SHA-256 `394F26E9792EF56B77E1293DFCB7A336BEFE48F224140CCD7626475EDE1BE04E`. One earlier invocation without required admin environment was rejected before DB connection/mutation.
- `ant compile-tests` — PASS; 2220 production and 131 test sources; two pre-existing `System.runFinalization()` removal warnings.
- `ant phantom-population-reset-ownership-goal032-test` — PASS, 3/3.
- `ant phantom-population-reset-reseed-goal032-test` — PASS, 2/2.
- `ant phantom-population-reset-documentation-goal032-test` — PASS, 1/1.
- `ant phantom-local-play-readiness-test` — PASS, 3/3.
- `ant phantom-restart-failure-recovery-goal030cp3-test` — PASS, 3/3.
- `ant phantom-release-decision-rollback-goal030cp3-test` — PASS, 3/3.
- `ant jar` — PASS; executed once after all focused suites.
- Mojibake markers in changed files — PASS.
- Escaped Cyrillic/XML escaped Cyrillic in changed files — PASS.

No full soak was run because no finding required it.

## Production DB statement

Production database `l2jmobiush5` was not opened, provisioned, reset or otherwise used. All destructive test work was constrained by the existing guard to `127.0.0.1:3308/l2jmobiush5_phantom_test`. Credentials were supplied only to the local provisioner process and were not recorded in source or report.

## Known limitations

- Reset supports the currently shipped single-DB account/game topology; split-DB recovery needs a separate designed task.
- Ambiguous shared-world ownership requires operator cleanup and a new preview; Goal 032 does not silently delete or invent reconciliation.
- Preview is bounded to 10,000 identities.
- Reseed uses already loaded settings; changing `PhantomPlayers.ini` still requires GameServer restart.
- Preserved world/history rows may retain historical Phantom object IDs by design.

## Commit/push

- Exact Goal 032 paths only are committed with `phantom(goal-032): add safe population reset and operator roadmap`.
- Push target: `origin feature/phantom-world`.
- TASK-authorized Git usage is bounded to baseline fetch/rev-parse, status/diff/diff-check, exact-path stage, commit and non-force push. No branch/history rewrite or unrelated untracked package operation was used.

## Итог

Можно ли оператору безопасно сбросить только Phantom population: YES
Можно ли сразу reseed по текущему preset: YES
Есть ли риск удаления human data по доказанным tests: NO
Какие gameplay tuning knobs пока отложены до Goal033:
level cohorts, progression pace/outliers, newcomer waves, personality percentages, fresh/living/mature ecology presets.
Следующий Goal:
033 — Living population ecology
