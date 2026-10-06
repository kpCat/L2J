# TESTS

Required diagnostics regression:

T01 OFFLINE topology probes do not consume recorder watch slots.

T02 The first <=8 accepted ONLINE/local candidates do consume watch slots.

T03 Predicate result is unchanged:
for the same online predicate/input sequence, candidate ids before/after diagnostic
refactor are identical.

T04 Existing recorder suite remains GREEN.

T05 Diagnostics OFF: no retention and no semantic behavior change.

T06 Build jar GREEN.

Implementation choice:
- Prefer an existing DB-free locality/topology suite if one already provides the needed
  test doubles.
- Otherwise add one small DB-free `PhantomHumanLocalityTraceSuite`.
- Do not create a database fixture merely to test diagnostic watch selection.
