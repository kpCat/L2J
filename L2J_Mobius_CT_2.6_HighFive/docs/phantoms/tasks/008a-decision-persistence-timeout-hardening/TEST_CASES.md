# TEST CASES — Goal 008A

- guarded fake store proves no store method runs under engine monitor;
- blocked attach plus beginStop and no late publication;
- blocked mutation A plus responsive cancellation token/stop for B;
- terminal persistence plus concurrent mutation BUSY;
- detach and finishStop retain in-flight persistence;
- conflict vs database failure states and explicit reload;
- step timeout when first attempt begins at logical zero;
- separate total timeout regression;
- final ordinary SUCCESS completes plan but leaves ACTIVE goal for replan;
- evidence reset after goal/activity/conflict boundaries;
- core ×3, persistence ×3, performance ×2, all scheduler/lifecycle regressions.
