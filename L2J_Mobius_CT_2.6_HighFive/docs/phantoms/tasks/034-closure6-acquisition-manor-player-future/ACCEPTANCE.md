# Goal034 closure 6 acceptance

PASS iff:
- exact `acquisition-manor-active.after-all` Player-future blocker is deterministically explained and regression-covered;
- no live `_skillListTask` or other Player future survives canonical Phantom dematerialization;
- no sleep/wait/filter/reflection cancellation is used as the fix;
- focused manor + materialization/lifecycle regressions are green;
- Goal033 ecology and Goal034 contract remain green when affected;
- fresh full `ant verify` is green under the bounded retry budget;
- standalone final `ant jar` is green;
- one fresh real Goal034 gen1 -> native restart/drain -> gen2 run reaches schedule-aware parity and exact cleanup;
- production `l2jmobiush5` is unused and `prepare-phantom-test-db` is not executed;
- Goal034 current docs/report become SUCCESS only after the real-stack PASS;
- Goal035 is NOT started; Roadmap v5 is deferred to the next documentation-only task;
- exact commit + non-force push complete and user-owned changes remain untouched.
