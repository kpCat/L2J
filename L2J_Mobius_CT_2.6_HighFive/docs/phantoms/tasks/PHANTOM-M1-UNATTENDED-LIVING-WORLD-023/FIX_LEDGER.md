# TASK023 causal repair ledger

Round 1: native participant roles and write admission.
RED_N02_VALID.log/.txt/.xml: stock MagicUseTask published by the first exact owner;
second OPEN owner writes real NPC HP (75 to 17, damage 58) before launch. Launch
recaptures aggro and rejects the genuine newcomer; origin is DRAINING with CAST=true.
No fake damage metadata, reward grant or passive NPC is used by this new test.

Read: PlayerNativeWork, PhantomNativeWorkScope, Creature cast phases, MagicUseTask,
Attackable.calculateRewards/drop, Player damage/loot, Party distribution, native
dynamic checks and guarded headless environment. Reuse ParticipantWork, exact
running parents, cancellable roots and earned children; no engine or scheduler rewrite.

Role contract:
- Delayed actor/direct target: exact original Player/Owner/epoch, strict parent fence.
- Damage transfer recipient: fresh synchronous native HP operation, frozen before writer.
- Reward/loot recipient: fresh synchronous death reward operation, frozen before any drop.
- Future party/aggro roster is not a delayed cast-phase participant snapshot.

Before-write admission reserves cancellable work for every managed recipient, starts
all exact tickets, then reserves earned children for every writer. Failed partial
admission retires unpublished reservations without poisoning already captured earned
work. A real body exception remains failure. Drain cannot detach a running admission
root; new roots on SEALED/DRAINING owners remain refused. Generic run/schedule do not
automatically switch to fresh admission. All recipients are exact references, never IDs.

Unverified: paired dynamic party/transfer controls, stale fence, writer exceptions,
drain races, natural farming and lifecycle acceptance. Production changes are not yet
claimed safe or complete. Full server clone a dead-vitals RED remains outstanding.

R1 verification: dynamic 6/6; initial boundary suite 6/6. Stronger N02 with two managed
and an ordinary third Player, enabled NPC AI, real ordinary delayed attack and native
EXP for all three passes. No EXP/items were issued to a Phantom.

N06 initial fixture was INVALID (same skill was still under native reuse); it did not
prove a production defect. Fixed fixture uses another affordable stock skill and a
12-second bounded disposal wait for already published stock earned callbacks.
Own RED_N06_VALID evidence then proves next target HP75 unchanged after old cancelled
callback aborts the new lawful cast. All six other checks passed and exact cleanup passed.
Fix: task identity per stock cast slot, captured managed-lifetime flag, cancelled/stale
callback returns while ParticipantWork still retires its earned ticket. Real task body
exception remains thrown and only the exact current task is eligible for native abort.

Platform review rejected global Player HP/transfer-getter rewiring; no such edit applied.
Unused proposed helper removed. Allowed Creature direct-effect boundary instead admits
the actual effect targets/transfer chain; existing strict PlayerStatus wrapper freezes
its own transfer reference and remains fail-closed for a later managed pointer change.
Direct physical late-transfer and concurrent pointer changes remain to be checked.

Round 1 final focused boundaries: R1_BOUNDARIES_GREEN 12/12. Own physical RED was
RED_PHYSICAL_VALID (ordinary positive passes, managed OPEN/SEALED fail at HitTask HP).
Own earned-drain RED was RED_EARNED_BODY: already published EARNED incorrectly deferred.
Creature hit direct-effect entry reuses the same frozen damage recipient admission.
Only already RUNNING earned exact owners bypass the health precheck; stale/current and
scope reserve/start checks are unchanged. All new recipients still require healthy roots.
Real post-HP failure already passed; no exception-policy change was needed.
Raw S11/S12/S13 in a fresh guarded JVM: 3/3. Aggregate fixture contamination remains
unexplained until composed fresh mage/fighter/ordinary and native closure checks finish.

Round 2 RED: owned full server night023a, GameServer-20261007-012754-stderr.log,
DEAD max-vitals mismatch with stale knowledge hash on four actors; final retained=4.
Read BackgroundService after-load/refresh/recover, HistoricalService RecoveryClaim,
BackgroundDecision production composition, NativeContextHandoffSuite fixture and codec.
Reuse refreshCanonicalBaseline HISTORICAL_BASELINE with exact COMPLETE claim and goal,
then leave normal native recovery unchanged. No hash guard weakening, fake revive,
receipt reset or earned progress/item change. Add two production paths within SOURCE_MAP.
One bounded extra test path: PhantomNativeContextHandoffSuite H15 (max5 allowance).
Unverified: this preflight integration, actual natural death recovery and final drain.

H15 initial contract attempts were INVALID: level/goal setup first, then hand-edited
DEAD component lacked the native-context state binding, then capture input used DEAD
instead of required MATERIALIZED. These failures are retained and are not product RED.
Use stock native doDie before baseline, exact old derived maxima in ordinary TEST rows,
MATERIALIZED capture input and existing transaction to derive DEAD/canonical context.
Failed constructor left own profile74469/char268435465/row0, created02:34:39.637;
only goal.runtime hash7f060d35...92ee remained, no character, no other components.
OWN_FAILED_TEST_FIXTURE.tsv preserves fingerprints. Exact guarded SQL cleanup deleted1,
retained0; no foreign PID existed. Constructor now cleans its own profile on setup failure.
Typed preflight Result reuses existing rejection reasons; no hidden safety bypass.

R2 H15 final legal fixture: stock native death before baseline, COMPLETE claim and
native-context binding retained. R2_HANDOFF_ORDER_VALID: 15/15 PASS.
Final current-intent 10/10, local-recovery 7/7, closure 9/9, timers 5/5,
dynamic OPEN/SEALED/ordinary 6/6, active mage and ordinary controls PASS.
Owned store initial mage assertion truncated the producer stack to32 frames after
the new wrappers. Retained producer shows real MagicalDamage through callSkillNative.
One additional bounded affected Suite path: PhantomBackgroundSuite, stack bound96;
original onMagicHitTimer assertion and persistence checks unchanged. WIDE_STACK_OWNED_STORE
3/3 PASS, including intentional timeout retained-state negative control.

Fixture audit found fighter Wind Strike and an illegal Hurricane in new N02/N06 setup.
Use exact ordinary TEST Human Mystic class10, level1 (N02) or7 (N06), stock skill-tree
1177/1 and1184/1 respectively, before native ownership. Ordinary third keeps melee.
Keltir died on one lawful mage hit; Orc survived two hits (143->72 before first callback).
Stock Giant Toad20121 selected; no altered HP/damage metadata. Failed fixtures retained.
Exact unchanged required-base JAR comparison uses the same compiled lawful fixture;
BASE_LAWFUL_N02_RED reproduces NATIVE_EARNED_RECIPIENT_NOT_CAPTURED before launch.
Raw S11/S12 now use legal ordinary mage setup, active NPC and exact loaded-Player cleanup,
preventing composition with prior live farm fixtures. No production mechanics changed.

## R3 — continuous evidence, cooperative death, native range

Own full-server PROBE_B RED: primary272 had20 actual completed cycles/EXP1740/SP200,
but REGEN phase deadline overflowed while native offensive work kept MP below maximum.
R3_REST_OFFENSE_RED reproduces scalar actual-offense/rest contract. Positive damage now
retires a bounded regeneration gap; repeated regen/pause alone never renews it.
R3_REST_OFFENSE_GREEN4/4 and original actual native phase1/1 PASS.

R3_SHARED_KILL_RED: lawful second damaging reward recipient got actual EXP but missed
actual death because stock doDie only called killer evidence. Existing frozen genuine
damage recipients now receive killedIfDamaged after stock reward body. Killer excluded
to avoid duplicate target insertion after its original evidence completes.
R3_SHARED_BOUNDARIES_GREEN12/12 PASS, including ordinary/native and safety negatives.

Natural PROBE_B short-range0/long-range2 eligible stock targets; long-range fixture first
damage happened but short setting completed only4 cycles/90s. Phantom settings use existing
stock bounded1400 long range. R3_NATIVE_RANGE_GREEN five actual cycles PASS; no target/AI
engine rewrite or resource grants. Natural final scenes remain separate acceptance.

## R4 — configured ecology shutdown handoff

Own full-server b stock stop RED: both processes exited, Phantom retained8 and stateRUNNING.
Initial/final ecology finish checks lasted8ms/1ms; shared pool then stopped before pending
canonical ecology worker finally finished. PIDs0 was explicitly CLEANUP_FAIL.
R4_ECOLOGY_DRAIN_RED uses actual configured ecology metadata worker and bounded delayed
release in stock ThreadPool; original shutdown returned before worker finally.
PhantomSystem waits up to10s outside both system monitors, then original configured drain.
R4_ECOLOGY_DRAIN_GREEN1/1; R4_SHUTDOWN_REGRESSION8/8 PASS (retained failure/old native gates).

Both rounds committed in1b71464a9e09a80dad890e2bfd39df0f3b5d3b96. Frozen rebuild PASS.
Final native dynamic6/6, closure9/9, timers5/5, owned store3/3 PASS.
Four semantic rounds used. No production edits between final A/B or after frozen SHA.
Remaining final natural continuous/death/return/drain/restart/crash gates are reported
individually; unit/fixture success cannot replace server acceptance.
