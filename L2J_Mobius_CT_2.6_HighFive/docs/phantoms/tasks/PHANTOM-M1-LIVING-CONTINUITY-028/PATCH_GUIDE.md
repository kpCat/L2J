# PATCH_GUIDE — source-grounded implementation
Все CURRENT сверены на required base; точные строки получать из own pinned source,
номера строк в SOURCE_MAP не являются разрешением менять соседний код.

## A. Existing runner dependency
CURRENT Observe026/Census023:
```powershell
$visible026=@(VisibleCensus026)
if(-not $script:fullStarted026){ ... FullObserve ... }
$latest026=Join-Path $OutputRoot 'full-native/full-cohort-latest.json'
return @(Get-Content $latest026 -Raw | ConvertFrom-Json)
```
PROBLEM: mailbox вызывается и после enrollment, хотя sample затем берётся из exporter.
REQUIRED: один собственный Observe028.ps1 с явными named phases и прямым чтением
existing FullObserve. Не добавлять `.Replace('027','028')` поверх цепи024→025→026→027.
Разрешено копировать именно нужные стабильные функции runner с attribution, не entire
legacy orchestrators. Не проводить новый read-only IPC ради каждого уже снятого sample.

## B. Synthetic TTL
CURRENT LocalPlaySyntheticHumanService.start():
```java
_deadlineNanos = System.nanoTime() + 525_000_000_000L;
```
CURRENT writeState():
```java
state.setProperty("expiresUtcMillis", Long.toString(System.currentTimeMillis() + 600000));
```
REQUIRED SHAPE: capture absolute advertised expiry once at successful start, publish
that same expiry in later writeState. Actual monotonic525s enforcement remains.
Add test: writeState after elapsed100/300s does NOT advertise a fresh600s lease.
Expose read-only remaining/deadline for runner if needed; never renew at heartbeat.

## C. Claim/result
CURRENT process():
```java
claim.setProperty("state", "CLAIMED");
LocalPlayPilotService.writeProperties(journal, claim);
_sequence++; writeState();
try { var outcome = _session.execute(request); ... }
catch (RuntimeException exception) { status="UNCERTAIN"; ... }
LocalPlayPilotProtocol.writeResult(...);
if ("UNCERTAIN".equals(status)) { ... close(); }
```
REQUIRED: capture exact existing request first, then locate first failure. If diagnosed,
minimal fix at producer/serialization/capture, preserving claims/sequence/identity.
Optional bounded stage diagnostic records exact request identity + phase without
credentials/candidate snapshots; do not record SUCCESS before atomic publication.
`Invoke` verifies runId in addition to current request/session/sequence/operation;
late-read resolver reads the SAME result path, never resubmits action.

## D. Existing native movement
CURRENT LocalPlayPilotActions.move():
```java
actor.getAI().setIntention(Intention.MOVE_TO, new Location(x, y, z));
actor.onActionRequest();
return Outcome.of("ACCEPTED", "ARRIVAL_PENDING");
```
REQUIRED: receipt→native XYZ/instance/moving evidence→arrival with tolerance.
Intermediate path Z is geo-resolved and guards remain<=400XY/200Z.
No blind resend when ACCEPTED is followed by missing result/sample.

## E. Existing behavior
CURRENT typedVisibleStart already reuses exact running AutoPlay and dispatches
ARRIVED/PENDING/STALE_GOAL/TERMINAL. history.prepareVisibleDecision already
synchronizes runtime and uses `_store.replacePlan` with publication+rebind.
REQUIRED: use this path; any failed008-era behavior must be reproduced on028 base.
Allowed examples after RED: specific target/route witness incorrectly bans viable
nearby activity, REST never exits despite recovered resources, new roots self-pin
retirement, or reason-only state erases deadline/ownership. No replacement scheduler.
Preserve exact components/claim/catchup guards; don't permit arbitrary goal drift.

## F. Retention
CURRENT nativeFacts:
```java
(admittedActions > 0) || player.isMoving() || player.isAttackingNow()
|| player.isCastingNow() || player.isTeleporting() || player.isInCombat()
```
This protects real activity. Do NOT remove ACTIVE_ACTION to force a screenshot.
If perpetual new auto-work holds actor offscreen, stop issuing only NEW roots through
existing session controller on explicit retirement intent, then wait for native
completion. beforeStore/FINALIZE/removal stay under lifecycle027.

## G. Snapshot purity
LocalPlaySyntheticHumanSession.execute calls onTeleported() after actions when needed.
Do not assume every operation named SNAPSHOT is pure without checking its complete
call path. A bounded inspection-only new method must never finish teleport/store
or modify target. Capture factual observer movement; do not fail a READ for unrelated
legitimate movement unless the stationary scene explicitly requires no movement.
