# CP5 partial independent review

Reviewed remote `a44421c1cec30e027aeb33e5588fb00373e30f1b`.
Direct parent / last fully accepted baseline: `f6402b512d5b22982e44f256506d7383a6b3d7c1`.
Remote branch is exactly one commit ahead.

Verdict: `CP5 = PARTIAL_CONTINUE`.

Keep the delivered additive raid Combat path. It correctly preserves ordinary
normalMonster safety and adds exact RAID/GRAND_BOSS live engagement validation,
shared ownership/loadout/shots/loot/cleanup and TARGET_LOST on disappearance.

Two integration findings must be closed inside this continuation, not as
separate micro-correctives:

## H0265-01 exact dead identity
`processRaid()` currently checks dead/alikeDead before re-checking exact
objectId+npcId+kind+instance. Add an identity predicate separate from the live
`validFor` predicate. Dead-target VICTORY/LOOTING requires the same requested
identity and actor-level policy. Wrong/replaced dead identity => TARGET_LOST.

## H0265-02 authority hash truth
`attemptAuthorityHash` is currently an opaque exact operation identity; Combat
does not semantically authenticate arbitrary hashes. Final
`PhantomRaidAttemptService` must mint it from exact attempt/CP4 evidence and be
the production owner that starts/tracks raid sessions. Do not overclaim Combat
as a semantic hash authenticator.

Still missing from CP5: ENTRY_GATED, Queen Ant, Zaken83, AttemptService,
support/offense, retreat, canonical final victory/native loot and Decision E2E.
