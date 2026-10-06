# Tests

Before semantic edit create one deterministic RED for the exact D-class.

After fix:
- RED -> GREEN
- affected existing visible/background/decision suites
- TASK018 native-context-handoff suite GREEN
- ecology 30/30
- population-ecology-handoff 6/6
- runtime-flight-recorder 3/3
- ant -q jar GREEN

Rerun broad production-materialization once.
TASK018 had unchanged 22/23 failure where a timeout fixture holds ActionLease on its own
draining thread and gets NATIVE_WORK_SELF_DRAIN.
Classify only:
UNCHANGED_TEST_CONTRACT_MISMATCH or PRODUCT_REGRESSION.
Do not modify native-drain product semantics in TASK019.
A new/different broad failure => STOP.
