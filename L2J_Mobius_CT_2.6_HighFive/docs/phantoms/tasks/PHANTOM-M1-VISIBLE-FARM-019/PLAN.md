# Plan

Target <=110m, maximum TWO manual login/logout cycles.

## A observe019a — unmodified diagnosis
Fresh clone `l2jmobiush5_localplay_observe019a`.
Settings: 1280/8/8/10000, diagnostics=True, TestAdmin100, AutoAttach, Synthetic=False,
GM hide/invis/invul/silence=False.

Ask user:
`Сервер готов (диагностика visible farm). Войди TestAdmin и напиши "в игре".`
WAIT. Verify REAL_LOGIN/IN_GAME/ARMED_IDLE.

Do not move/fight with TestAdmin.
SNAPSHOT_PHANTOMS.
If no visible Phantom for 30s => BLOCKED_NO_VISIBLE_ACTOR.

Select nearest visible Phantom trace.
Capture trace t0/+2s/+5s; replay read-only if healthy.
Prepare INITIAL M1 envelope for SAME profile and snapshot includeCensus=true twice.
Write VISIBLE_DIAGNOSIS.md with one D-class.

Ask user exit character select; verify online0/save; graceful stop.

If ambiguous/outside => STOP, no second login.

## B bounded fix
One exact RED. Modify <=2 production files. Run mandatory tests/build. Commit/push.

## C observe019b — five-cycle proof
Fresh `l2jmobiush5_localplay_observe019b`, same settings.
Ask user login again; verify.

Select one natural client-visible Phantom.
Prepare INITIAL M1 envelope for same profile.
Capture baseline native evidence.
Observe passively <=180s; snapshot every 2-5s.

PASS on SAME materialization epoch:
- worldPresent=true
- clientVisible=true at least once
- nativeEvidenceOverflow=false
- nativeAutoPlay=true at least once
- nativeTargetSequence delta >=6
- nativeDamageSequence delta >=5
- nativeKillSequence delta >=5
- nativeRewardSequence delta >=5
- nativeFarmCycleSequence delta >=5
- nativeExpGained delta >0
- nativeSpGained delta >0
- nativeLootSequence delta >=1 OR exact evidence no eligible loot was generated across
  the five completed cycles.

IDLE between targets is allowed; persistent idle/no progress is not.
If death before 5 cycles: record and STOP, no death fix here.
If no progress for 90s: selected trace/census exact blocker and STOP, no second fix.

Ask user logout; verify save; graceful stop.
Publish result. M1=OPEN.
