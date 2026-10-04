# Native summon phase follow-up: TEST stage and production proposal

Source/isolated compilation only. Native summon RED, independent nonauthor review, subsequent GREEN and full W remain REQUIRED. No production implementation is authorized by this report.

Read-first pass: Task007 SCENARIOS Q13, promoted Evidence/Work/CreatureStatus phase code, Summon/Servitor/SummonAI, original Creature.doAttack/HitTask event ordering, stock1225/1 XML and original handlers.skill.effects.Summon, PhaseChecks/QueuedWorkChecks, native summon local analogue in PhantomCombatServerIntegrationSuite, and current root Suite fixtures. Task007 Q13 is the timer/grandchild case; stock Servitor setup comes from the nearby combat suite, rather than a claimed Q13 summon factory.

Current confirmed production gap:

- PlayerNativeWork.runCombat line235 selects only actor instanceof Player. Existing lifetime capture line360 already selects Summon.getOwner(), so owned native work and observed combat attribution differ.
- CreatureStatus.reduceHp line179 selects only attacker instanceof Player. Its original status assignment and after-monitor evidence bridge therefore omit positive Servitor damage.
- Creature.onHitTimer lines5361-5362 calls original target.reduceCurrentHp before synchronous notifyDamageReceived. The TEST listener observes that original HP loss on the actual HitTask.run stack, rather than supplying a damage writer.
- PlayerNativeWork.nativeCombatPassive line101 and CombatBinding.complete line135 inspect only owner Player attack/cast/target/AI flags. SummonAI.thinkAttack line121 and thinkCast line138 have their own original ATTACK/CAST lifecycle. Summon.isInCombat line863 also delegates to its owner and is not an independent summon engagement predicate.
- Summon.getOwner line274 and public setOwner line781 use the mutable _owner field. Player.getSummon and setPet are separate pointer operations. Exact owner and summon identity checks must cover both directions, not objectId equality alone.

Production before SHA256 (read-only, unchanged by this task):

| Path under module | SHA256 |
|---|---|
| java/org/l2jmobius/gameserver/model/actor/PlayerNativeEvidence.java | A7C2FE1C003A7022FC31B659261DD491FFE19C382D73F5CED208B18AAEA08C41 |
| java/org/l2jmobius/gameserver/model/actor/PlayerNativeWork.java | 2F7DA7E04FE3ED4A9CB9312C976289FC128E247EE6D4E53B67A67DDEFCD703F0 |
| java/org/l2jmobius/gameserver/model/actor/status/CreatureStatus.java | 1894161AE3D41480246A456CD4EDFEBAAB148C474B15EA1884DA09E2289A5087 |
| java/org/l2jmobius/gameserver/model/actor/Summon.java | 951137BEF151EAFFBDAF5895675BF572E09980B22834A49536C698FB8A145F29 |
| java/org/l2jmobius/gameserver/ai/SummonAI.java | D708C4EE924505219ECA1DF165B6FF4A0D9934526675E4E1FB38BDEDBDD49B64 |

Bounded proposal, gated on actual RED:

1. In existing Work.runCombat, capture a direct Player or exact current Summon owner at the original operation boundary. Validate owner Player pointer/native owner/epoch/World/sensor, Summon owner pointer, owner.getSummon pointer and exact native actor identity. In inherited earned bodies require the captured running ticket for that same owner; never resolve another live owner after task entry and borrow its sensor. Ordinary actors retain the original path. Existing immutable COMBAT episode, ParticipantWork tickets, reserve/start/cancel/finally lifecycle and timer descendants remain the accounting source.
2. Extend the existing observation episode with bounded scalar actor objectIds plus weak actor identity witnesses, without a new permanent strong Actor/Player/Owner/Scope reference or a global registry. A repeated invocation or joined direct/summon actor must retain the first immutable since/deadline and existing120s horizon. Probe original actor state outside the sensor monitor, then compare the exact episode and witness version before retirement. Retire only when pending native work is zero and every still-current actor is passive; owner IDLE alone cannot retire a live summon attack/cast.
3. In the original CreatureStatus HP bridge capture the exact acting participant/actor at the original writer, then attribute only the real beforeHp minus assignedHp under the original status monitor. Revalidate captured participant and actor pointers after that monitor before sensor credit. This is an observation extension; no formula, HP assignment, reward/drop/rate or gameplay admission changes. An unexpected reassignment cannot credit the replacement owner or resurrect the old epoch token.
4. Add only passive observation probes at original SummonAI completion/state transitions (changeIntention after original transition, original READY processing, onActionFinishCasting after its original follow/attack decision). Existing original physical attack-ready and cast-finalizer tickets continue to finish in their real finally blocks. No completion inferred from Future.isDone, owner flags, callbacks before HP assignment or cancellable idle delay.
5. Reassignment is not proved by a stable fixture. Current _owner is nonvolatile and owner/setPet changes are not an atomic pair. Before claiming concurrency safety, use a focused public-setOwner/setPet barrier control and decide whether the bounded existing _owner publication needs volatile plus an observation-only old-episode probe. This possible fifth path is proposed, not changed/approved here. A transitional pair mismatch must refuse observation; failed/UNKNOWN owner scopes retain incidents and do not retire or reopen.

No new layer/schema/goal/policy/reset is proposed. DEATH/REGEN/ROUTE precedence, useful damage/reward counters, observation overflow and W90s debt retain existing semantics. Summon phases cannot invent useful progress or extend gameplay deadlines.

Frozen TEST source and private integration:

| Artifact | SHA256 |
|---|---|
| test/java/org/l2jmobius/tests/phantoms/PhantomM1NativePhaseChecks.java.pending | 168BBD56F7F94B5A921BE0C0ED3D57380BB1A3A41F55188024E62E3DCE583D10 |
| private source/org/l2jmobius/tests/phantoms/PhantomM1NativePhaseChecks.java | 168BBD56F7F94B5A921BE0C0ED3D57380BB1A3A41F55188024E62E3DCE583D10 |
| private source/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java | 701FCC17F4C00B45ED7659DAB5AA8F39D6B337410B0699605F0A6CF6C492FAD5 |
| unchanged published PhaseChecks.java | 70165EEAA1071870873E938345F915902AAB8F3D0CAE307802698EC43DBDA814 |
| unchanged published root Suite.java | 450FF63202176E18B732C3A42DA674EA13607BBBFFB9089BC80E1919D73F6995 |

Private root: C:/Users/ZBook/L2J_Mobius/.phantom-author-check-007/native-summon-phase-007. Root Suite.pending was not edited. Central before-write rows cover canonical pending, private copies/integration and this report.

New TEST APIs, confirmed by isolated compiler:

```java
Servitor createSummon(Player owner);
void summon(PhantomTestContext context, Player owner,
    PhantomMaterializationService service, long profileId,
    Servitor summon, Monster target) throws Exception;
void ordinarySummon(PhantomTestContext context, Player owner,
    Servitor summon, Monster target) throws Exception;
```

createSummon is explicit TEST setup through the loaded original1225/1 effect, not evidence that the level7 fixture learned that skill. Original effect creates14159 with original1200s lifetime, native fullRestore/setPet/spawn; TEST does not set HP/stat/template/rate/lifetime or inject phase. Original follow/AI stop APIs leave the owner passive and select one controlled original doAttack. The fresh factual20534 target and exact owner are caller-owned fixtures.

Private integration adds only native-summon-phase to existing literal environment/validation/family lists and registers two cases: ordinary original Servitor writer control first, managed owner COMBAT second. Old native-phase registry entry and regen body remain unchanged. Each caller disposes its original summon through unSummon/deleteMe before existing strict fixture cleanup; no normal cleanup bypass or failed-scope reset. Ordinary target uses original Spawn.stopRespawn followed by doSpawn(false), retaining native initialization while preventing a later TEST respawn.

The transparent targeted pool forwards every unrelated original submission to the same original executor. Only original Creature.doAttackHitSimple submission is gated; original Runnable, delay, formula and executor are preserved. Frontend NONE and pending owner count are sampled before body entry. After release the synchronous damage listener records real positive target HP loss, original HitTask stack, exact summon/owner/epoch, owner sensor and running count, holding the native callback briefly until released. Product assertions occur only after actual original completion/reuse and zero outstanding work. At most three actual attacks are attempted; all physical misses produce INVALID fixture and cannot count as a phase RED. No test writer or fake damage event is injected.

Additional actual controls REQUIRED before claiming the production proposal closed:

- Managed writer must first produce valid original HP delta/completion with NONE at writer as the native RED, then COMBAT and owner damageSequence as GREEN after reviewed fix.
- Ordinary stock control must remain positive with no native owner attachment.
- Owner IDLE with a still-live summon ATTACK/CAST and live target must retain the same published episode between real callbacks; terminal summon passivity must clear only that episode. Current single-shot helper establishes running/native completion, not a perpetual engagement proof.
- Public owner reassignment before queued entry, old Player/owner/epoch replacement, and transitional setPet/owner mismatch must never credit new owner or stale lifetime. Negative failed lifetime disposal must reuse approved guarded TEST quarantine; this helper does not clear failures.
- Actual native timer/grandchild accounting Q13, rejected/inline/cancel controls, direct native-phase control and full W remain required regressions.

Verification: coherent JDK25 javac --release25/UTF-8/-proc:none of the exact two private sources exited0 against immutable .phantom-local/task007-frozen-phase09-classpath/{production,tests} and original dist/libs. Original regen/helper suffix equality is true after line-ending normalization; published helper and Suite before hashes match. No native fixture or Ant was executed. Git commands were not used.

- Mojibake markers: checked separately in changed helper/private sources/report; no matches.
- Escaped Cyrillic/XML escaped Cyrillic: checked separately in those files; no matches.
