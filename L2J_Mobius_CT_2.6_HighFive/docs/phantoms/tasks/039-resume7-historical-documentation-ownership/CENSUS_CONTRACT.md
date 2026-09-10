# Historical documentation assertion census

## Purpose

Avoid another sequence of "current roadmap changed, old test fails" blockers.

Before editing, search test sources for historical Goal suites that reference
mutable current navigation/config artifacts, especially:

- `PHANTOM_CURRENT_STATUS.md`
- `NEW_DIALOG_START_MESSAGE.txt`
- `PHANTOM_BOTS_ROADMAP.md`
- exact roadmap version strings
- `Следующий Goal`
- exact entire PhantomPlayers config key counts/key-set equality
- hardcoded future goal tails

## Classification

For each hit classify:

### OWNED_HISTORICAL_INVARIANT
The assertion belongs to that Goal's durable acceptance contract.
Keep it.

### CURRENT_RELEASE_OWNER
The assertion is correctly in Goal039/final release validation.
Keep it there.

### STALE_FORWARD_STATE_OWNERSHIP
A historical suite freezes future/current navigation or later additive config
outside its original Goal.
Allowed Resume-7 correction.

### CURRENT_DOC_SMOKE
Historical test only checks existence/non-contradiction, not exact future state.
Usually keep.

## Known audit result before task

`PhantomLocalPlayGoal031Suite` documentation mode is comparatively healthy:
- checks its quickstart/release/startup contract;
- does not hardcode a current next Goal or exact roadmap version.

`PhantomPopulationResetDocumentationGoal032Suite` is the proven stale owner.

Do not broaden changes simply because a test reads documentation.

## Scope rule

If additional `STALE_FORWARD_STATE_OWNERSHIP` assertions are found, they may be
corrected in this Resume ONLY when:
- they are test-only;
- historical accepted source/report proves the narrower original ownership;
- no production behavior or current docs need weakening.

If a different substantive documentation contradiction is found, treat it as a
new independent blocker and stop after one focused confirmation.
