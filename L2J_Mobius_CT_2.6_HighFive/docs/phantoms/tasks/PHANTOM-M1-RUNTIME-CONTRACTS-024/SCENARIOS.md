# Проверки, которые закрывают контракты

## B — geometry independent durable snapshot (B01–B08)
B01 Actual earned EXP/SP, walk away from previous anchor/area, native stop store: exact
    XYZ/EXP/SP/vitals/items persisted, no fake projection coordinate.
B02 Off-area persists POSITION_REQUIRES_NATIVE through prepare/native/finalize/restart.
    No virtual income/travel/loot while not eligible; actual materialization possible.
B03 Valid area remains supported when native vitality policy supports; otherwise vitality
    barrier survives. Instance and previous/goal identity not replaced.
B04 Wrong goal/epoch/hash or changed inventory during snapshot remains rejection.
B05 Legacy UNKNOWN/SUPPORTED/VITALITY payloads exact encode/decode. New enum ordinal appended.
B06 Arrival at actual new valid local area reattests position and native facts; exact
    stored XYZ unchanged unless actor genuinely moved; no repeated SQL correction.
B07 Death and negative EXP loss persisted from latest native snapshot, not max counters.
B08 PREPARE/NATIVE/FINALIZE/replay matrix both bound/off-area; missing data not classifiedsafe.

## C — lifetime/dependency order (C01–C05)
C01 Full configured topology+background+materialization with actual POST_STORE, not inert
    mocks only: leave/stop succeeds with earned actors, retained0 and SQL equals snapshot.
C02 finalization succeeds then publication fails: distinguish phases; retry no duplicate
    canonical reward/receipt; never report DATA_LOSS when only index missing.
C03 in-flight callback and repeated stop: no premature dependency stop, no deadlock from
    systemmonitor waiting. OPEN new work rejected, captured earned finishes.
C04 Native failure remains failure and retention diagnostics exact. No clearing incidents
    to force store; controlled safety case kept separate from healthy finalscene.
C05 BeginStop RUNNING and retryFAILED obey same graph; shutdown/regeneration/task refszero.

## A — new action vs old continuation (A01–A08)
A01 A native HitTask, active NPC, native AI attacks OPEN B while A earned context active:
    no false NATIVE_EARNED_RECIPIENT_NOT_CAPTURED, native HPchanges, A/B continue.
A02 Same B SEALED/DRAINING: new attack is deferred before publication/writer, A earned
    hit still completes; no new owner and no reopened failed lifetime.
A03 Stale original actor / recycled charId / changed owner epoch: cannot become fresh.
A04 Old delayed callback changing explicit target to new player remains rejected.
A05 Player original attack and spell entries preserve actual MP/shot/reuse/target legality.
A06 NPC→NPC/ordinary→ordinary path remains stock; ordinary third participant unaffected.
A07 nested two retaliations/party/transfer recipient windows preserve exact roots;
    writer throw after HP never silently retried or relabeled before-publication defer.
A08 all leases/timers/cast finalizers settle; no permanent CAST/registration death merely
    because a legal new native action was nested in another callback.

## D — repeatable local farm (D01–D07)
D01 Player inside valid FARM area with anchor center>2000: remains locally suitable.
D02 Already useful actual arrival cannot fail old journey deadline before arrival check.
D03 One rejected water-crossing waypoint must not blacklist all npc@area while another
    factual dry route exists; no retry of same route witness/infinite deadline reset.
D04 Late navigation callback after revision replan cannot issue MOVE_TO.
D05 No nearby suitable mobs: bounded alternate local search, reason/backoff, no everlasting
    start_retry; do not pretend successful farm. Actual fullscene ample supply remains PASS gate.
D06 Owner/goal revocation and no-reward health failures stop safely; not bypassed by recovery.
D07 Actual movement is native, no live teleport; distribution not every actor same center.

## Natural A/B scenes (mandatory, frozen SHA)
Use complete GameServer stock NPC AI/scheduler and original geodata, no fakelevel/HP/items.
Before baseline preselect≥4 natural actors per scene and2primaries deterministically.
360–420s each; samples≤10s; primary≥5 same-epoch cycles, others≥2; ALL reward in final120s,
no unexplained idle>90s, no incident/overflow/missing actor quietly ignored. Full cohort
is reported even if actor dies/leaves: separate legitimate death cannot silently convert
scene to a smaller successful set. Baseline code/data/config hash identical A/B.
At least one fighter and mage across scenes where naturally eligible; if unavailable
record coverage gap, don't reroll failed scene/createactor. Positive behavior must persist
beyond opening burst. Stock loot direct inventory events+SQL; RNG absence not fabricated.

## Lifecycle extensions (full M1 server gate, same reviewed source)
Two selected natural actors: actual native death then recovery/farm; controlled severe
encounter only in separate lifecycle fixture, no direct revive used as proof.
Synthetic dry leave→soft demat→background continuation→return→remat/farm. Snapshot epoch
changes explicit. Save/restart sameDB retains exact prior committed rewards/items/XYZ,
then new real earned progress allowed. Planned crash matrix as CRASH_WINDOWS.
Known guards/fixtures must not remain UNKNOWN while declaring SERVER_M1_PASS.
