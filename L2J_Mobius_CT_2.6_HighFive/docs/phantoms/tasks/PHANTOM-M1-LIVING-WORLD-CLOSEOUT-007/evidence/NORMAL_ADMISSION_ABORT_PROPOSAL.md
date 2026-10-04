# NORMAL admission-abort closure: read-only proposal

Status: production RED confirmed; proposal only. No source changes, Ant, DB, native JVM, or Git commands were run for this pass. The frozen Historical/Background pair remains unchanged. No native GREEN is claimed.

## Evidence and exact source context

Root's original `historical_native_normal01` safe TXT SHA is `7AEF9575FF366BC5316A6F140EA9BF3BE175C5001F064564443FC1AC4BA4C2DB`; XML SHA is `979E06326A0FCAD8499E07C2AA75A5F5ACB79ADB46A21DD8FEB1409A4456A488`. The actual selected imported DEAD profile refused NORMAL native admission with `hashMatch=false`. At refusal no PREPARE/STORE/finalize marker had occurred. Abort then completed one of each and replaced old B4 maxima/combat/hash with the current native values. The full original 124-table fixture restored successfully; this restoration does not erase the product defect.

Paths below are relative to `L2J_Mobius_CT_2.6_HighFive` in the isolated `m1-living-closeout-007` worktree. These are read snapshots, not authorization to change every listed path.

| Exact file | SHA256 |
| --- | --- |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java.pending` | `7D4097ABB6E3FB4F76126D2E13E56A890FEF518E060A3F9AFDDB93EC18204BF7` |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java.pending` | `31E375713652A5BE40DBF5A28C9A31E37144A35764937D0CE051F6A7830B46B0` |
| `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java` | `4F07ADB382C93AAF0933BE78F2495C7498EECFE8F99CF2287457A3C39D90B866` |
| `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java` | `7170FFD0A44E2BFC44D493C19807D4C10E184F74B891AFEC37E7784D030D906E` |
| `java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecyclePort.java` | `A151FD19BB1769213F052488EC42F2E8637A380C11ED9B0DF81CCEB2E8DECAA5` |
| `java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java` | `808FB10DD732E523446C8F7915ECA34400340A62D96D08A0C10498309198E3D5` |
| `java/org/l2jmobius/gameserver/model/actor/Player.java` | `E010711ECBB1BD3886D330519F108CA9CA15E919000F49CC18495721BB775DB8` |
| `java/org/l2jmobius/gameserver/model/itemcontainer/ItemContainer.java` | `D542E57A65A0413E7F3E962C5F49B58718C1BC2ED63888C913B5F34FF13037CF` |
| `java/org/l2jmobius/gameserver/model/actor/holders/creature/EffectList.java` | `3008F3435DA5C9152F311DCED0D4640C425C614AEAB37100A75ED408364759D6` |
| `java/org/l2jmobius/gameserver/model/skill/BuffInfo.java` | `080E5E10E1B2BC97207058329500E51A74433A852F5279E01740BDEAF3E09102` |
| `test/java/org/l2jmobius/gameserver/phantoms/PhantomM1HistoricalNativeRefreshChecks.java` | `4CB8CDCD8E232F1D8F254388405A48CD7448F294171EC456AD0C32C7A3FD7A97` |

## Proven Important finding

`PhantomBackgroundService.afterPlayerLoad` pending line1513 installs the owned STORE boundary before load/context/runtime validation. The original NORMAL refresh rejects a stale authority hash at `refreshNativeVitalsLocked` line1618, and line1532 throws an ordinary `IllegalStateException`. `PhantomMaterializedPlayer.materialize` lines294–307 always calls cleanup on this failure. Cleanup lines377–407 always runs terminal STORE. The attached boundary captures current native derived facts and finalizes them despite rejection. This is the exact observed path; the historical-only exception in the frozen pair does not authorize NORMAL refresh.

Three adjacent native side effects make an Actor-only STORE skip insufficient:

1. `PhantomMaterializedPlayer` line243 restores effects before the lifecycle callback. `Player.restoreEffects` applies original effects and deletes their saved rows at line8679. The lifecycle failure therefore occurs after a native DB side effect for actors with saved effects. The selected RED does not prove the saved-effect case.
2. `Player.prepareNativeCleanup` line12232 calls `notifyNativeLogout`; `Player.deleteMe` line12198 also calls it when cleanup was not prepared. This can create a new logout-domain reward. Merely avoiding terminal STORE could then lose that newly admitted reward or persist it outside the expected boundary.
3. `Player.deleteMeNative` line12321 updates online/lastAccess and lines12582–12600 delete inventory/warehouse/freight through `ItemContainer.deleteMe` line550. That method force-writes each item and item variables before removing native objects. Boundary-null `Player.storeQuiescent` line7992 performs ordinary native STORE. Neither detaching the boundary nor ordinary delete is a non-persistent abort API.

`SkillFinishType.SILENT` is not a resource-only disposal mode: `BuffInfo.finishEffectsNative` lines445–451 always invokes `effect.onExit`; `EffectList.stopAndRemoveNative` also applies END scope for a finish type other than REMOVED. Clearing item collections through public `getItems()` would bypass the original stop/remove loop and is not a validated local cleanup pattern.

## Existing local patterns to reuse

- Existing `AdmissionRejectedException` in `PhantomMaterializationLifecyclePort` line49 is typed admission control but currently carries only a message. It is not proof of zero native writes or absence of earned work.
- Original `PhantomMaterializationService.materialize` lines222–238 catches this type before Actor construction, invokes `abortPreserving`, and returns `CATCHUP_FENCED`; no Player/map/permit/identity lifetime is created at this stage.
- Original `PhantomNativeWorkScope.drainAndSeal`, `teardown`, and `detach` lines197/213/270 prove exact current owner and drain actual bodies/timers before release. Teardown may fork earned work and never reopens ordinary admission. Reuse these gates; do not clear a failed scope or infer completion from cancelled Future state.
- The original Player pre-STORE/effect/DELETE split is the cleanup sequencing analogue, and ItemContainer's stop/remove iteration is the resource traversal analogue. They require a narrowly guarded new disposal mode if their persistence/domain behavior is excluded.
- E04 TEST discard deliberately leaves failed production maps/permits quarantined. It is not production authorization or an appropriate implementation for this rejection.

## Bounded options and recommendation

**A. Upstream stale-hash NORMAL fence (one existing service path).** After original READY/DEAD reconciliation in `beforeMaterialize`, compare pinned current authority hashes and reject only NORMAL stale-hash admission with the existing typed exception. HISTORICAL_BASELINE keeps its exact current claim path. This strengthens the same hash restriction already enforced by the refresh method before creating Player resources. It needs a before-row against Background7D409 and root ownership coordination; it does not need Actor changes.

This is a safe minimal containment option for the exact old-hash entry, but it does not satisfy the unchanged same-native gate: `PhantomM1HistoricalNativeRefreshChecks.assertNegative` lines306–314 requires one actual LOAD, an after-load refusal, and `hashMatch=false`. An upstream refusal cannot be reported as this control's GREEN. It also leaves same-hash runtime/context after-load refusals unresolved. No helper/oracle change is proposed.

**B. Strict after-LOAD pre-admission abort closure (at least Background + Actor + Player; resource paths may also be necessary).** Preserve the original negative oracle and install/retain the normal STORE path only after exact validation, except when the existing authorized historical refresh needs it. Before any authorized native STORE, a precise refusal can carry a newly proposed immutable rejection proof bound to profile/object/exact Player/current native owner/epoch and the actual after-load stage. A proposed field/factory is not an existing API. A generic `AdmissionRejectedException` must never imply discard eligibility.

The Actor branch must require: lifecycle callback has not completed; login-domain callback, identity/output/action attachment, online activation, World spawn and native play have not occurred; exact owner/identity still current; no pending/completed owned STORE; original drain/seal succeeds without failure; and the proof still matches this lifetime. Any stale proof, running body, earned mutation, actual native failure, receipt, or ambiguous partial load stays on original retain/reconcile/STORE behavior. Catching generic RuntimeException/Error or matching message text is prohibited.

To provide genuinely clean release after that proof, Player needs an explicitly guarded **new pre-admission disposal entry point**, rather than calling ordinary delete or normal logout teardown. It must stop actual producers and release exact resource references without generating a fresh logout-domain event or writing character/items/vars. Proposed resource-only ItemContainer/effect methods must be distinguished from existing APIs and scoped before implementation. The likely exact candidate source set is seven paths: Background, Actor, Player, LifecyclePort, ItemContainer, EffectList and BuffInfo. No Transaction, Historical, model, config, schema or scheduler change is justified by the current trace. If source proof shows an existing lower-level method safely completes part of this work, remove the corresponding path before approval.

There is a remaining safety problem before authorizing B: effect restoration and Player.load can run native work before refusal. The trace proves no new login/farm/logout domain has been admitted at this point; it does **not** prove that every loaded effect's already-running tick/onStart is incapable of earned mutation. Resource disposal must never silently discard those results. Likewise, simply moving validation ahead of restoreEffects can change native maxima/stats and is not proven equivalent. For saved effects, preserve original saved rows until successful native admission through a source-proved delayed-consumption flow or retain the rejected owner; never restore DB images or suppress already-earned STORE to force a clean result.

Recommendation: keep the pair frozen, take root's explicit choice between A as containment and B as the actual after-load closure, and authorize the smallest exact path set only after the unresolved pre-admission earned/effect-consumption contract is addressed. Actor as a third path alone is insufficient for general full-image clean release. Until that contract is proven, a retained exact failed lifetime is the truthful fail-closed safety outcome, not a completed native gate. This proposal does not claim an unsolved disposal API exists.

## Required controls before acceptance

1. Re-run the unchanged original NORMAL_OLD_HASH control: actual native LOAD reaches original refusal; durable/native/full item/component images remain identical after abort; exact native ownership ends before original full snapshot restore. Preserve its one-LOAD and hash-reason assertions.
2. Preserve original HISTORICAL_BASELINE positive attestation and wrong-request/stale-version/stale-payload controls. Verify completed/PENDING receipt and stale owner/epoch never use rejection disposal. Generic native Error retains its first incident and original suppression behavior.
3. Actual pre-admission saved effect/tick control must prove loaded earned work is retained/saved, or deliberately remains retained with its journal; a cancelled Future is insufficient. Prove no newly generated logout reward on a never-admitted rejection. Original accepted-player logout reward, buff preservation, partial-arrow inventory and ordinary stock STORE remain unchanged.
4. Race rejection against original dematerialize/shutdown: no monitors held during external drain; exact map/permit/identity release occurs only after successful resource cleanup; failed/unknown cleanup cannot be declared clean or restored away.

No native run, compile, or source edit was performed for this proposal. No schema/data reset, forced calendar/cursor/reward/COMPLETE, generic FAILED recovery, or native-only exclusion is proposed.

- Mojibake markers in changed report/ledger: checked separately.
- Escaped Cyrillic in changed report/ledger: checked separately.
- Git commands: none.
