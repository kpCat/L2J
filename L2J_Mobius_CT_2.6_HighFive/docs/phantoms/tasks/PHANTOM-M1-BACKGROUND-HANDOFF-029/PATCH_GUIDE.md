# PATCH_GUIDE029

Все CURRENT ниже прочитаны в base2b9496c. Это не apply-ready server patch:
применение определяется RED-tests и exact current interfaces. Содержимое файла
не следует заменять целиком. Сохранять native lifecycle027.

## 1. All-or-nothing legacy eligibility
File: java/org/l2jmobius/gameserver/phantoms/background/PhantomNativeContext.java

```java
// CURRENT
public boolean simulationEligible() { return (phase == Phase.COMPLETED) && (afterEligibility == Eligibility.SUPPORTED) && (afterPoints == 1); }
```

PROBLEM: V1 simulation поддерживает только depleted scalar. Допустить16361 не изменив
reward/consume model означает неверную экономику. POSITION_REQUIRES_NATIVE также
смешивает запрет farming и запрет безопасного travel.

REQUIRED SHAPE (API proposal, адаптировать к existing record без ломки старых constructors):
```java
enum Operation { FARM, TRAVEL, REST }
// Legacy simulationEligible remains legacy. Ordinary V2 callers must use the operation contract.
boolean permits(Operation operation, PolicyBinding current);
record PolicyBinding(long stateVersion, String stateDigest, String rulesFingerprint) { }
```
`permits` не делает I/O и не заменяет transaction-side owner/CAS verification.
PNC1 no explicit V2 facts => legacy path only. New policy never manufactures a native
attestation from canonical point!=1. Capture/codec changes must preserve old constructor
semantics and explicit before/after snapshots; see COMPATIBILITY.

## 2. Native policy capture
File: .../L2jPhantomBackgroundAuthority.java, nativeContext and combatFacts
```java
// CURRENT
final boolean ordinaryPolicy = !PlayerConfig.ENABLE_VITALITY || ((consume == 1) && !player.getNevitSystem().isAdventBlessingActive());
return new PhantomNativeContext.Capture(points, (points == 1) && ordinaryPolicy
    ? PhantomNativeContext.Eligibility.SUPPORTED : PhantomNativeContext.Eligibility.VITALITY_REQUIRES_NATIVE);
```
PROBLEM: valid high vitality always forces native, even after successful dematerialization.
REQUIRED: capture independent ordinary scalar/bonus/position facts under native snapshot
boundary. Actual points remain16361/421. Do not change global config or Player stats.

`combatFacts` currently embeds getExpBonusMultiplier/getSpBonusMultiplier (already
containing vitality and max caps). V2 must use independently captured bonus inputs and
replace per-encounter multiplier evaluation, NOT multiply total combat multiplier again.

## 3. Award ordering oracle (READ ONLY native code)
File: .../model/actor/Attackable.java, calculateRewards path:
```java
// CURRENT
attacker.addExpAndSp(addExp, addSp, useVitalityRate());
if ((addExp > 0) && useVitalityRate())
{
    attacker.updateVitalityPoints(getVitalityPoints(attacker.getLevel(), damage), true, false);
    PcCafePointsManager.getInstance().givePcCafePoint(attacker, exp);
}
```
Meaning: bonus observes PRE-consume vitality; point loss uses level after reward/level-up.
Preserve separate truncation of base SP and Math.round of final stats. No requirement to
simulate PcCafe subsystem in ordinary M1; unsupported account features remain native-only.

Native points function:
```java
// CURRENT
final long expReward = getExpReward(level);
final float divider = (getLevel() > 0) && (expReward > 0) ? (getTemplate().getBaseHpMax() * 9 * getLevel() * getLevel()) / (100 * expReward) : 0;
if (divider == 0) { return 0; }
return -Math.min(damage, getMaxHp()) / divider;
```
Use actual template/reward inputs. No arbitrary per-kill vitality decrement.

## 4. Existing model integration
File: .../PhantomBackgroundModel.java inside evaluate encounter loop:
```java
// CURRENT
final Rewards rewards = calculateRewards(state.progress().level(), target, request.rewardPolicy(), state.combat());
experience = Math.addExact(experience, rewards.experience());
skillPoints = Math.addExact(skillPoints, rewards.skillPoints());
```
REQUIRED: explicit ordinary V2 overload, local vitality state carried through encounters;
legacy path unchanged. Return proposed scalar state along with BatchResult (typed wrapper
is acceptable to avoid breaking acquisition signatures). Never use native counters as
input reward source. Stop/revalidate bounded work when level/loadout policy changes.

## 5. Atomic write
File: .../PhantomBackgroundTransaction.java
```java
// CURRENT (LOCK_CHARACTER includes vitality_points, UPDATE_MAIN doesn't)
private static final String UPDATE_MAIN = "UPDATE characters SET level = ?, exp = ?, expBeforeDeath = ?, sp = ?, curHp = ?, curMp = ?, curCp = ?, x = ?, y = ?, z = ?, heading = ? WHERE charId = ?";
```
REQUIRED: ordinary V2 command updates vitality_points and V2 context in SAME existing
transaction as rewards/state/catchup. Legacy command preserves its scalar. Use typed
expected/proposed policy, same stable locks and conflict keys, no second connection/commit.
No free-form SQL repair task and no global schema update.

## 6. Position
File: .../L2jPhantomBackgroundAuthority.java, nativeAnchor/atCanonicalAnchor/advanceTravel.
CURRENT captureOwnedNative keeps trueXYZ with prior/goal anchor even if outside area;
FARM requires atCanonicalAnchor. TRAVEL also currently checks atCanonicalAnchor.
REQUIRED: new bounded return-to-area travel from exact storedXYZ; no earlier coordinate
normalization. Reuse existing topology/path APIs and travel clock. Before receipt records
true storedXYZ, travel receipt records from/to/time, FARM at destination uses fresh policy.
Unreachable route => typed no-op failure with bounded replan, never invisible instant snap.

## 7. Observer membership
File: task028/Contract028Observer.java used as source for task029 observer.
```java
// CURRENT after attach
selection = new Selection(Map.copyOf(selected), output, spec.getProperty("codeSha"));
// Later periodic/hook code filters by selection.profiles().
```
REQUIRED: keep that telemetry snapshot, add separate receipt union keyed by exact lifetime,
registered before possible checkpoint. Every retained scope records temporary/permanent,
terminal state and latest exact receipt lineage. Never overwrite initialEpoch.
Use append-only immutable exports, existing ring; latest cache failure is typed and cannot
silently erase evidence. Full JSONL/manifest is authoritative; do not grow per-tick wrappers.

## 8. Returned local goal and regression fixes
Before changing history/travel/AutoPlay, compare exact current goal, revision, nativeXYZ,
knowledge generation and pending store. No failed prior route may categorically ban an
independent local reachable target, but actual negative geo result must remain negative.
New type/class-dependent tactical AI not required here. Keep native owner/lifecycle API.
