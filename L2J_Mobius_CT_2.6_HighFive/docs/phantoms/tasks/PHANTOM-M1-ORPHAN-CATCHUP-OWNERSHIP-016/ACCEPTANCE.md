# ACCEPTANCE

Engineering GREEN:
- population-wide census created;
- profiles110/175 classified;
- exact adoption requires from/cursor + target + seed + READY + no owner/safety block;
- no profile IDs hardcoded;
- historical component never rewritten by recovery;
- ecology cursor never fabricated;
- deterministic RED->GREEN;
- existing ecology/handoff/native-materialization contracts GREEN;
- jar GREEN;
- only EcologyService production semantics changed.

Runtime minimum PASS:
- recovered ownership is observable on fresh clone when orphan exists;
- `READY_PASS` appears;
- `MATERIALIZE_CALL` appears.

Preferred runtime PASS:
- MATERIALIZE_RESULT SUCCESS;
- MAT_WORLD_SPAWN;
- worldPresent natural Phantom.

Hard STOP:
- `BLOCKED_ORPHAN_CONFLICT` on request/window/seed mismatch;
- `BLOCKED_NEXT_EDGE` if recovery works but a later materialization boundary fails.

No 5-cycle farm requirement.
M1=OPEN.
