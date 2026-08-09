# Goal 023B context

## Baseline

```text
branch: feature/phantom-world
required parent: 563752f6844076fdbaeb3be7c5cae979c757960a
Goal 023 baseline: CHANGES_REQUIRED
Goal 023A baseline: CHANGES_REQUIRED by this independent review
Goal 024+: NOT_STARTED
```

## What Goal 023A already fixed

Preserve these results:

- Goal017-owned content binding exists.
- Dedicated `ENSURE_PARTY_BINDING` stage exists.
- Managed invitation policy registry exists and production composition registers
  `riftService::evaluateManagedInvitation`.
- Pre-invite candidate/roster/source/binding evidence is re-read immediately before invite.
- `rift.preparation` schema v2 stores binding/candidate/full invitation identity.
- v1 state decodes as untrusted and replans before mutation.
- canonical invitation expiry and typed terminal mapping are used.
- pending/refusal typed semantic facts exist.
- candidate discovery is local, Phantom-first and capped at 32.
- Goal 018 relationship modifier is used.
- no new Rift entry/combat side effects were introduced.

## Why 023B exists

Route stability crosses a different runtime ownership layer than membership
operation phase. Goal 017 writes active route manifests as `ROUTE + COMMITTED`,
and a route may also exist only in the route coordinator while planning.
Goal 023A binding can ignore those states and overwrite durable route identity.

The second issue is exact target-side policy proof/current eligibility:
the integration case substitutes `ignored -> ACCEPT`, while production uses
`riftService::evaluateManagedInvitation`.

These are one coherent boundary: content binding/consent must not act on stale
or still-owned Goal 017 state.

## Task-package file rule

This package intentionally has no arbitrary maximum file count.

It gives an exact pre-audited read set and expected change set. If another High
Five file is genuinely required by the exact call path:

1. Codex may read it without asking;
2. Codex may change it only when needed for the invariant;
3. the report lists the file, exact reason and why the expected set was insufficient;
4. other chronicles, broad exploration and unrelated refactors remain forbidden.

Do not mark BLOCKED just because one more justified file is needed.
