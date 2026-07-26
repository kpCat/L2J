# REVIEW FINDINGS — Goal 008

## P1 — global monitor owns JDBC
Attach, mutation, reload and terminal persistence invoke the goal store while
holding the engine-wide monitor. A blocked DB call blocks all profiles,
cancellation and `beginStop`, reintroducing an unbounded server-shutdown path.

## P1 — persistence has no two-phase ownership
Moving calls outside the monitor requires an exact per-runtime operation token,
pending-attach reservation and detach/stop retention.

## P1/P2 — logical-zero step timeout
Step start uses zero as both a valid logical time and an unset sentinel. The
focused suite tests total timeout only.

## P2 — persistence failure classification
Explicit mutations leak generic runtime failures while terminal persistence
labels all failures as optimistic conflict.

## P2 — stale snapshot evidence
Goal/activity boundaries retain candidate/score/explanations from the previous
goal or plan.
