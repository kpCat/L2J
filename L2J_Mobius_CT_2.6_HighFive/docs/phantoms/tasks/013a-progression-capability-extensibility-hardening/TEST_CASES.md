# Test cases — Goal 013A

Deterministic seed: `130013`.

## A. Production composition

1. Start ordinary Game Knowledge, not inert fixture.
2. Enumerate every paged Game Knowledge class capability source fact.
3. Parse progression source variants independently.
4. Build progression through production dependency order.
5. Compare exact identity/provenance sets.
6. Repeat build at least three times; hashes byte-identical.
7. Record fixture-only and production hashes separately.

## B. Capability variants

1. A class has multiple capability groups.
2. A class has two variants with the same capability group.
3. Both variants survive catalog build and query.
4. Variant A learned, variant B unlearned.
5. Both learned; A blocked by resource, B ready.
6. Both learned; A on reuse, B ready.
7. Same coarse archetype classes have distinct exact evidence.
8. Data-only new variant requires no planner/combat class branch.
9. A higher static-rank unsupported variant does not hide a lower-rank
   supported variant.
10. Static catalog rank is not treated as final tactical suitability.
11. Unsupported mode returns empty/fallback.

## C. Resource truth

1. Skill item present at exact count → eligible.
2. One item short → `READY_NOW=false`.
3. Curated and skill item requirement merge without double count.
4. Unknown positive item ID fails build.
5. MP insufficient.
6. HP insufficient.
7. Dynamic condition fails.
8. Reuse/disabled fails.
9. Snapshot contains every referenced resource ID.
10. No loader call occurs during repeated evaluations.

## D. Main/subclass

1. Materialize canonical test Player.
2. Observe main class ID/index/skills.
3. Add/configure subclass test-only through canonical server test setup.
4. Switch active class.
5. Observe exact subclass ID/index/skills.
6. Verify main-only skill is absent unless canonically persistent.
7. Verify certification/persistent skill is classified separately.
8. Switch to main and verify restoration.
9. Production package contains no class/subclass mutation method.

## E. Summon/pet/cubic

1. Multiple summon variants of one owner class are distinct.
2. Exact summon skill/NPC/item references resolve.
3. Servitor lifetime/upkeep/EXP/shots preserved.
4. Pet control item/food/inventory/pickup preserved.
5. BabyPet heal/recharge/buff evidence derives from loaded skills.
6. Servitor own skills/mechanics remain queryable.
7. Cubic has no body object/position/HP/MP or body commands.
8. Body-bearing runtime snapshot contains exact position/instance/HP/MP/target.
9. No mutable server object escapes.
10. New generic fixture variant requires no class switch.

## F. Equipment

1. Actor owns >64 mixed equippable items.
2. Query by family/body part.
3. Page size <=64.
4. Paging yields every matching exact object ID once.
5. A lower-grade matching item is not hidden by a higher-grade unrelated item.
6. No universal preference score field/order.
7. Foreign object ID rejected.
8. Incompatible item rejected.
9. Exact owned compatible item equipped canonically.
10. No item create/purchase/enchant path.

## G. Skill learning

1. Exact trainer/class SkillLearn success.
2. Wrong trainer/range/class reject.
3. Previous skill missing.
4. Level too low.
5. SP too low.
6. Prerequisite missing.
7. Required item missing.
8. Duplicate required item IDs aggregate.
9. Cancellation before side effects.
10. Inject failure at multi-item boundary; no prefix loss.
11. SP/items/skill exact on success.
12. Event emitted only after successful reconciliation.
13. Repeated request idempotent.
14. No packet/bypass handler invocation.

## H. Lifecycle/performance/static

1. Zero new worker/thread/task/Future.
2. Operation and actor lease counts drain to zero.
3. Disabled path remains inert.
4. 100,000 indexed queries.
5. 100,000 variant evaluations.
6. 50,000 equipment filter/page queries.
7. Fixed elapsed bound <=120,000 ms.
8. Verifier detects each prohibited regression by source inspection or negative
   fixture, not only by searching for a positive method name.

## I. Canonical Player CP

1. Snapshot current CP equals exact `Player.getCurrentCp()`.
2. Snapshot maximum CP equals exact `Player.getMaxCp()`.
3. A later snapshot observes changed canonical CP while the earlier snapshot
   stays immutable.
4. Snapshot creation does not mutate canonical Player HP/MP/CP.
5. HP, MP and CP values remain distinct.
6. Servitor/pet/cubic do not receive fabricated Player CP.
7. Disabled combat backend remains inert.

## Required commands

Codex may add exact focused Ant target names, but the final report must include
all commands and results. Minimum final gates:

```text
ant -Dphantom.test.seed=130013 <all Goal 013A focused targets>
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools/phantoms/verify-task-013a.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/phantoms/verify-task-013a.ps1
```

Verifier outputs must be byte-identical and their SHA-256 must match.
