# PLAN
Target <=90 minutes plus one manual login/logout gate. No subagents.

## A root proof <=15m
1. Verify exact base/branch.
2. Read TASK018/019 evidence.
3. Read retained observe019a DB read-only for profile110:
   catchup status/requestId, goal status/revision, background state.
4. Prove DecisionEngine admission uses permitsNormalOperation and this exact profile is
   denied because catchup is non-COMPLETE.
5. Write ADMISSION_PRESTATE.md.
If false: BLOCKED_HYPOTHESIS_FALSE.

## B RED/GREEN <=30m
1. Add V01-V18 and obtain genuine RED.
2. Implement CONTRACT.md in HistoricalBackgroundService.
3. Change only PhantomSystem semantic binding.
4. Add DIAGNOSTICS.md path in LocalPlayPilotActions, read-only only.
5. GREEN regressions + jar.
6. Diff review: only two semantic production files; LocalPlay file diagnostic only.
7. Exact-path commit + normal push same experiment branch.

## C observe020 <=35m
Fresh clone `l2jmobiush5_localplay_observe020`.
Runtime 1280/8/8/10000, diagnostics=True, TestAdmin100, Pilot AutoAttach,
Synthetic=False, GM hide/invisible/invulnerable/silence=False.

Ask:
`Сервер готов. Войди вручную TestAdmin и напиши "в игре".`
WAIT.

After IN_GAME/REAL_LOGIN/ARMED_IDLE:
- NEVER PREPARE_M1_ENVELOPE;
- never move/teleport TestAdmin;
- never target/attack Phantom.

Use only read-only:
SNAPSHOT_PHANTOMS includeCensus=true,
SELECT_VISIBLE_PHANTOM_TRACE,
SNAPSHOT_SELECTED_PHANTOM_TRACE,
causal trace if useful.

Choose one naturally visible ordinary Phantom (profile110 only if naturally present).

Baseline:
profile/object/materializedAtNanos, TestAdmin XYZ, decisionSequence,
runtimeReason/currentActionGuard, AutoPlay/intention/target,
nativeEvidenceEpoch, damage/kill/reward/target/farmCycle/loot sequences,
nativeExpGained/nativeSpGained.

Passive observe same epoch up to120s.

PASS:
- decisionSequence increases;
- no persistent goal.reloaded/sequence0;
- existing native visible plan executes;
- nativeFarmCycleSequence delta>=5;
- nativeKillSequence delta>=5;
- nativeRewardSequence delta>=5;
- nativeExpGained delta>0;
- nativeSpGained delta>0;
- nativeEvidenceOverflow=false;
- same profile/object/epoch.

Record nativeLootSequence delta exactly. Zero loot is NO_LOOT_OBSERVED, not fabricated
failure unless exact target/catalog proves guaranteed loot.

If actor still idles, classify one exact D3-D12 using census+trace and STOP.
No second semantic fix in TASK020.

## D shutdown <=10m
Ask user to exit to character select and WAIT for `вышел`.
Verify online0 + saved level/exp/sp/x/y/z.
Graceful exact-owned Game/Login stop. No force without explicit permission.
Publish RESULT/HANDOFF. M1=OPEN.
