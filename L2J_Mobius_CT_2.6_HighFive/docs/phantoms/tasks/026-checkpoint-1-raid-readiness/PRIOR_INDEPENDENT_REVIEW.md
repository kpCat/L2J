# Prior independent review — authorization for Goal 026 Checkpoint 1

## Accepted baseline

Independent review of remote commit `5517081fb2bbf2aa9ad8295130714df2d4b45921` is complete.

```text
R025A-01: CLOSED
R025A-02: CLOSED
Goal025A: ACCEPT
Goal025 overall: ACCEPT
Accepted baseline: 5517081fb2bbf2aa9ad8295130714df2d4b45921
Goal026 Checkpoint 1: AUTHORIZED
Goal026 Checkpoint 2+: NOT_STARTED
Goal027+: NOT_STARTED
```

The reviewer verified the corrective production call paths, not only the Codex self-report:

- PARTY HELP_REQUEST now uses exact Goal017 `helpCounterpart`;
- hostile Player remains the PvP combat target and is not substituted as the PARTY expected counterpart;
- Goal020 production dispatch reaches SENT for a current Party member and fails closed after membership becomes stale;
- PvP causal observation now happens before cooldown admission;
- active cooldown blocks only the same pair's proactive source;
- a different exact counterpart and fresh ACTUAL_ATTACK/PARTY_DEFENSE are not profile-globally suppressed;
- one encounter/profile remains the persistence model.

The historical Goal014A verifier that asserts `Goal 025: NOT_STARTED` remains a known process-only incompatibility.
Do not fix it here and do not run plain `ant verify`.

Goal025 architecture already independently accepted and is not to be reopened in this checkpoint.
