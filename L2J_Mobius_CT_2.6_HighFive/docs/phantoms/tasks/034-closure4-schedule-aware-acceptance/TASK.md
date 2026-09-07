# Goal034 closure 4 — schedule-aware ACTIVE cap + native restart acceptance

## Baseline
Это bounded closure существующего Goal034, не новый gameplay Goal.

- Branch: `feature/phantom-world`
- Required parent/HEAD/origin: `b43959bcfec2d21840b1abc8f54a7bc6900d849e`
- Module: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Previous report:
  `docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-runtime-layout-closure.md`
- Goal035 НЕ начинать.

Parent уже доказал:
- runtime-layout contract 7/7;
- full canonical `dist/game/data` snapshot;
- DB negative guard;
- three green `ant verify`;
- three green standalone `ant jar`;
- real LoginServer READY;
- real GameServer READY + test-LS registration three times;
- managed=10, terminal=true, unique=true;
- online observed 2/3/2;
- clean exact cleanup/no orphans/working integrity.

## Critical acceptance correction — known before patch
`PhantomPopulationActiveTarget=5` НЕ означает "всегда держать ровно 5 online".

Authoritative facts:
1. shipped `PhantomPlayers.ini` calls it a maximum ACTIVE target/cap;
2. `PhantomPopulationManager.recomputeAdmissionLocked` uses:
   `min(activeTarget, maximumMaterialized, desired ACTIVE count)`;
3. a READY profile whose schedule desires ACTIVE but is not admitted is effectively WARM;
4. weekly schedule template and phase are durable in `population.state`;
5. LIVING deliberately assigns different morning/evening/late schedules.

Therefore prior Goal034 condition `online == 5` is an invalid black-box oracle for human-like scheduled population.

DO NOT change production population/schedule/admission semantics to force five bots online.

## Goal
Replace the false exact-five oracle with schedule-aware production-parity evidence, fix the already-proven `ServerRestartDays` sandbox issue, then complete:

`focused contract -> fresh verify -> final jar -> real LS/GS gen1 -> schedule/admission parity -> native restart/drain -> gen2 schedule/admission parity + identity continuity -> cleanup`

and mark Goal034 `SUCCESS` if all gates pass.

## Safety
Allowed DB only:
- database `l2jmobiush5_phantom_test`;
- host `127.0.0.1` or `localhost`;
- port `3308`;
- user `l2j_phantom_test`;
- canonical guard-approved mysql/mariadb URL.

Production `l2jmobiush5` forbidden even for probe/read/cleanup.
`ant prepare-phantom-test-db` forbidden.
No wildcard/global Java process kill.
Working `dist` and canonical data remain unchanged.

## READ_SET
Before patch:
1. previous Goal034 closure3 report;
2. `PhantomBlackBoxLocalStackGoal034.java` — population snapshot, timezone, restart;
3. `PhantomPopulationState.java` — schedule fields;
4. `PhantomPopulationCatalog.java` — `evaluate`;
5. `PhantomPopulationManager.java` — admission limit/effective state only;
6. `PhantomPlayers.ini` ACTIVE key comment;
7. `ServerRestartManager.java`;
8. `Server.ini` restart keys.

Repository searches <=4 before patch. No broad re-audit.

## Fix A — schedule-aware black-box oracle
Extend test-side population evidence; production code must remain unchanged unless a genuine mismatch is proven.

For one captured `Instant acceptanceInstant` in each GameServer generation:

1. decode each managed `population.state`;
2. retain:
   - profile id;
   - character id/account;
   - schedule template;
   - schedule phase minutes;
   - home region;
   - `c.online`;
3. load/evaluate the same canonical population catalog under the configured `PhantomPopulationTimeZone`;
4. compute each durable profile's desired schedule state at `acceptanceInstant`;
5. build `desiredActiveIds`;
6. build `actualOnlineIds`.

Expected admitted count:
`min(PhantomPopulationActiveTarget, MaxMaterializedPhantoms, desiredActiveIds.size())`.

Mandatory convergence:
- managed identities = 10;
- all are READY/MANAGED;
- ecology initial catch-up terminal and no pending request;
- unique identity/account/character ownership;
- `expectedAdmittedCount >= 1` for the selected bounded acceptance timezone/window;
- `actualOnlineIds.size() == expectedAdmittedCount`;
- `actualOnlineIds` is a subset of `desiredActiveIds`;
- actual online never exceeds ACTIVE cap 5;
- no profile whose schedule state is non-ACTIVE is online/materialized for this population gate.

Do NOT require `actualOnline == 5` unless desired ACTIVE count itself is >=5.

If actual and expected differ, that is a genuine production-composition blocker and must be diagnosed; do not relax the assertion.

Persist bounded per-profile evidence to run manifest/report:
`profileId, scheduleTemplate, phaseMinutes, desiredState, online`.
No names/passwords/secrets beyond existing safe test identity evidence.

## Acceptance timezone
The harness may retain a bounded IANA timezone selection to place wall-clock time inside a broad LIVING ACTIVE window, but it must prove rather than assume the resulting desired count.

Do not mutate durable schedules/phases after creation.
Do not create a special all-ACTIVE production schedule.
Do not fake/inject Player online state.

## Fix B — native restart day gate
Current harness only writes `ServerRestartSchedule`; inherited `ServerRestartDays=4` can move restart to Wednesday.

Sandbox only:
- set `ServerRestartScheduleEnabled=True`;
- set `ServerRestartDays=1,2,3,4,5,6,7`;
- set exact scheduled HH:mm at now + bounded lead;
- preserve short countdown already used.

Before waiting for restart, require log/observable scheduling evidence that the chosen restart instant is within the expected bounded lead window in the process timezone.
If the logged restart is on another date/day outside tolerance, fail immediately instead of waiting.

Do not change production default `Server.ini`.

## Generation 1
PASS requires:
- real LoginServer JVM READY;
- real GameServer JVM READY/registered;
- schedule-aware population parity above;
- capture immutable identity/ecology fingerprint;
- native scheduled restart invokes normal `Shutdown`;
- Phantom drain evidence;
- success path no force kill;
- expected exit code semantics;
- LoginServer stays alive.

## Generation 2
Start a second real GameServer JVM against the same guarded test state.

PASS requires:
- READY/registered;
- same durable identities and immutable ecology assignment;
- no duplicate account/character/profile;
- new generation's schedule-aware expected/actual online parity is independently valid at its own acceptance instant;
- online count may legitimately differ from generation 1 if a real schedule boundary was crossed;
- native restart/shutdown evidence as required by existing Goal034 contract.

Do NOT require identical online sets/counts across time; require identical identities/ecology plus correct schedule behavior at each sampled instant.

## Cleanup/integrity
PASS and FAIL:
- exact run-owned population cleanup;
- exact registration cleanup;
- exact child process cleanup;
- no orphan ports/PIDs;
- no forced cleanup on SUCCESS;
- working integrity true;
- canonical data fingerprint unchanged;
- production DB unused.

## Focused tests before full verify
Extend Goal034 contract to prove at least:

1. `activeTarget=5`, desired=2 => expected admitted=2;
2. desired=3 => expected admitted=3;
3. desired=5 => 5;
4. desired=8 => cap 5;
5. online set containing non-ACTIVE profile => reject;
6. exact-five old oracle is absent;
7. restart sandbox has all-days gate;
8. scheduled restart date/instant validation rejects inherited wrong-day behavior.

Prefer testing a small pure/test-side helper if useful; do not create production API solely for the harness.

## Validation budget
Known fixes A/B happen BEFORE full verify and do not count as new blockers.

Phase A:
- compile affected;
- Goal034 contract focused PASS;
- DB negative guard PASS.

Phase B:
- one fresh `ant verify`;
- one repeat permitted after one proven new predecessor blocker;
- second independent predecessor blocker => BLOCKED.
- after green verify: one standalone final `ant jar`.

Phase C:
- run real Goal034 black-box.
- maximum 2 genuinely new independent real-stack blockers;
- exact evidence -> minimal fix -> focused regression;
- code change requires one honest verify/jar post-fix cycle;
- repeat black-box.
Third new Phase-C blocker => BLOCKED.

Do not count the corrected exact-five oracle or known ServerRestartDays issue as new blockers.

## Documentation truth
On SUCCESS update current Roadmap v4/current status/handoff so Goal034 is `SUCCESS`.

Also correct misleading current wording such as:
`online=2..3 вместо 5`
to schedule-aware truth:
`ACTIVE cap=5; observed online follows durable schedules/admission`.

Historical BLOCKED reports remain immutable.

Create:
`docs/phantoms/reports/034-automated-black-box-local-stack-acceptance-success.md`

## Report
<=160 lines, include:
- status/parent;
- proof that ACTIVE=5 is cap, not constant online target;
- schedule evidence table/histogram for gen1 and gen2;
- expected vs actual admitted counts;
- PIDs/ports;
- LS/GS READY/registration;
- restart scheduled instant and actual exit;
- Phantom drain;
- identity/ecology continuity;
- cleanup/orphans/integrity;
- verify/jar/black-box counts;
- DB safety;
- changed files;
- elapsed/tokens;
- commit/push.

## Out of scope
No Goal035/036/037/038.
No gameplay/rates/quest changes.
No production population semantics changes merely to satisfy exact 5.
No schema redesign.
No permanent admin/test API.
No new scheduler/thread.
No unrelated user files.

## Git
Before changes:
- fetch;
- HEAD and origin exact required parent.

Allowed:
- bounded status/diff/show/log/rev-parse;
- exact-path add;
- one commit;
- non-force push.

Forbidden:
reset/restore/rebase/merge/amend/force/history rewrite.

SUCCESS subject:
`phantom(goal-034): complete schedule-aware black-box acceptance`

BLOCKED subject:
`phantom(goal-034): record schedule-aware closure blocker`

## SUCCESS
Goal034 becomes SUCCESS only when:
- schedule-aware oracle is proven;
- ACTIVE count obeys real durable schedules and cap=5;
- fresh verify green;
- final jar green;
- real LS/GS gen1 READY/registered;
- native restart/drain succeeds;
- gen2 READY/registered;
- durable identity/ecology continuity succeeds;
- exact cleanup/no orphans/integrity succeeds;
- production DB unused;
- current docs/report/commit/push complete.

After SUCCESS next Goal is Goal035.
