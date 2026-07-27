# TEST CASES — Goal 009A

- expired request: zero backend calls;
- impossible route budget: zero backend calls;
- blocked intermediate computed segment;
- blocked automatically appended destination segment;
- valid appended segment;
- obstruction/cancellation/deadline/backend failure never cache;
- cooldown after obstruction, direct route still bypasses;
- accepted and rejected dispatcher versus beginStop;
- inline dispatcher ownership;
- navigation-only shutdown blocker snapshot/logging;
- navigation core >=44 ×3;
- performance ×2 deterministic;
- shutdown handoff ×3;
- all cumulative Goal 001–009 regressions.
