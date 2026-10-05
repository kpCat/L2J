# ACCEPTANCE

Engineering:
- A01–A08 pass;
- manual arm compatible;
- `ant -q jar` exit 0;
- only SOURCE_MAP modify rows + task docs changed;
- allowlist fail-closed;
- REAL_LOGIN semantics not weakened.

Runtime:
- fresh clone DB;
- Phantom effective config = target1280 / active8 / cap8 / scheduled10000;
- system enabled before manual login;
- TestAdmin manual IN_GAME => REAL_LOGIN;
- Pilot becomes ARMED_IDLE without `.playtest arm`;
- no lease stealing.

Observation:
report separately:
`RUNTIME_BEHAVIOR=OBSERVED|NOT_OBSERVED`
`AUTOPLAY_5_CYCLES=PASS|FAIL|NOT_OBSERVED`

Task010 GREEN != M1 CLOSED.
No gameplay fix in this task.
