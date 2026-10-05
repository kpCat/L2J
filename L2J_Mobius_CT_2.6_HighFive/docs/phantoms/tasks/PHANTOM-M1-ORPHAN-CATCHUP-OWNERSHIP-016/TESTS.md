# TESTS

Minimum deterministic tests:

O01 exact orphan PENDING:
- ecology idle at cursor C;
- historical PENDING from C target T;
- seed exact;
- one worker turn adopts requestId/T into ecology;
- historical row/version/payload unchanged;
- ecology cursor unchanged.

O02 exact orphan RUNNING:
- historical cursor > from;
- adoption preserves all historical progress;
- ecology outer cursor remains from.

O03 exact orphan FAILED_REPLAN_REQUIRED native-context:
- adoption succeeds;
- later human materialization demand reaches existing task011
  `ecology.native_materialization_required` foreground permission;
- background due/readiness remain fenced.

O04 historical COMPLETE + ecology idle:
- no adoption;
- ordinary sequential renewal behavior unchanged.

O05 from mismatch:
- no adoption;
- typed conflict;
- no new historical begin/write.

O06 target invalid/conflict:
- no adoption.

O07 deterministicSeed mismatch:
- no adoption.

O08 ecology already owns exact request:
- idempotent ordinary path; no extra ecology version/write.

O09 ecology owns different request:
- no adoption and no ownership steal.

O10 materialized/native owner or non-empty safeBoundary:
- no adoption until safe; no terminal false corruption.

O11 ecology CAS race:
- failed adoption write does not alter historical component;
- bounded retry can later adopt exact same request.

O12 restart:
- after adopted ecology state is reloaded, no second adoption write;
- historical resumes exact request.

Existing required regressions:
- ecology goal033 18/18 or updated count if only these named controls are appended;
- population-ecology-handoff regression 6/6;
- task011 native materialization exception preserved.
